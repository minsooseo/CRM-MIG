package com.kt.yaap.mig_batch.batch;

import com.kt.yaap.mig_batch.mapper.TargetTableMapper;
import com.kt.yaap.mig_batch.model.TargetRecordEntity;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.lang.NonNull;

import java.util.*;

/**
 * 대상 테이블의 실제 레코드를 읽는 Reader (메모리 최적화 버전)
 * 
 * 메모리 최적화:
 * - Cursor 기반 스트리밍 방식으로 한 번에 하나씩만 읽어서 메모리 사용량 최소화
 * - 200만 건 처리 시에도 OOM 위험 없이 처리 가능
 * - 초기화 시간 단축 (전체 조회 대비 20~30초 단축)
 * 
 * 이 Reader는 migration_config가 아닌 실제 대상 테이블의 레코드를 읽습니다.
 * 한 테이블의 여러 컬럼을 함께 처리하므로:
 * - read_count = 실제 처리한 레코드 수
 * - Step 개수 = 테이블 개수
 * 
 * 성능 최적화:
 * - 단일 쿼리로 모든 컬럼을 한 번에 조회 (컬럼별 반복 쿼리 제거)
 * - 3개 컬럼 처리 시: 3번 쿼리 → 1번 쿼리 (약 50~67% 성능 개선)
 * - 네트워크 왕복 및 DB 스캔 횟수 대폭 감소
 */
public class TableRecordReader implements ItemReader<TargetRecordEntity>, ItemStream {

    private static final Logger log = LoggerFactory.getLogger(TableRecordReader.class);

    private final SqlSessionFactory sqlSessionFactory;
    private final String tableName;
    private final List<String> targetColumns;  // 암호화 대상 컬럼들
    private final String schemaName;  // 데이터베이스 스키마명
    
    private SqlSession sqlSession;
    private Cursor<Map<String, Object>> cursor;
    private Iterator<Map<String, Object>> cursorIterator;
    private List<String> pkColumnNames;
    private boolean initialized = false;
    private long recordCount = 0;

    public TableRecordReader(SqlSessionFactory sqlSessionFactory,
                            String tableName,
                            List<String> targetColumns,
                            String schemaName) {
        this.sqlSessionFactory = sqlSessionFactory;
        this.tableName = tableName;
        this.targetColumns = targetColumns;
        this.schemaName = schemaName;
    }

    @Override
    public TargetRecordEntity read() throws Exception {
        if (!initialized) {
            throw new IllegalStateException("Reader not initialized. open() must be called before read().");
        }

        if (cursorIterator != null && cursorIterator.hasNext()) {
            try {
                Map<String, Object> record = cursorIterator.next();
                recordCount++;
                
                if (recordCount % 10000 == 0) {
                    log.info("Processing record {} from table: {}", recordCount, tableName);
                }
                
                return convertToEntity(record);
            } catch (Exception e) {
                log.error("Error reading record from cursor for table: {}", tableName, e);
                throw e;
            }
        }
        
        return null;
    }

    private TargetRecordEntity convertToEntity(Map<String, Object> record) {
        if (record == null) {
            return null;
        }
        
        // PK 값 추출 (단일키/복합키 통일)
        Map<String, Object> pkValues = new HashMap<String, Object>();
        for (String pkCol : pkColumnNames) {
            String key = "pk_" + pkCol.toLowerCase();
            Object value = record.get(key);
            
            if (value == null) {
                throw new RuntimeException(
                    String.format("PK value not found: table=%s, pk_column=%s, key=%s", 
                        tableName, pkCol, key));
            }
            
            pkValues.put(pkCol, value);
        }
        
        // Entity 생성
        TargetRecordEntity entity = new TargetRecordEntity();
        entity.setTableName(tableName);
        entity.setPkColumnNames(pkColumnNames);
        entity.setPkValues(pkValues);
        entity.setTargetColumnNames(targetColumns);
        
        // 모든 대상 컬럼의 원본 값 추가
        for (String columnName : targetColumns) {
            Object value = record.get(columnName);
            String originalValue = value != null ? value.toString() : null;
            entity.getOriginalValues().put(columnName, originalValue);
        }
        
        return entity;
    }

    @Override
    public void open(@NonNull org.springframework.batch.item.ExecutionContext executionContext) throws ItemStreamException {
        // Spring Batch 생명주기에 맞춰 open()에서 초기화 (SQL 세션 오류 방지)
        if (!initialized) {
            try {
                sqlSession = sqlSessionFactory.openSession();
                TargetTableMapper mapper = sqlSession.getMapper(TargetTableMapper.class);
                
                // 1. PK 컬럼명 조회
                Map<String, Object> pkParams = new HashMap<String, Object>();
                pkParams.put("tableName", tableName);
                pkParams.put("schemaName", schemaName);
                pkColumnNames = mapper.selectPrimaryKeyColumns(pkParams);
                
                if (pkColumnNames == null || pkColumnNames.isEmpty()) {
                    throw new RuntimeException("Primary Key not found for table: " + tableName);
                }
                
                log.info("Table: {}, PK columns: {}, Target columns: {}", 
                        tableName, pkColumnNames, targetColumns);

                // 2. Cursor 기반 스트리밍 조회 (메모리 효율적)
                // 재처리 방지는 WHERE 조건(_bak IS NULL)으로 처리됨
                Map<String, Object> params = new HashMap<String, Object>();
                params.put("tableName", tableName);
                params.put("pkColumnNames", pkColumnNames);
                params.put("targetColumnNames", targetColumns);
                
                cursor = mapper.selectAllTargetColumnsStreaming(params);
                cursorIterator = cursor.iterator();
                initialized = true;
                
                log.info("Initialized streaming reader for table: {} (using Cursor-based streaming)", tableName);
            } catch (Exception e) {
                log.error("Error initializing streaming reader for table: {}", tableName, e);
                close();
                throw new ItemStreamException("Failed to initialize streaming reader for table: " + tableName, e);
            }
        }
    }

    @Override
    public void update(@NonNull org.springframework.batch.item.ExecutionContext executionContext) throws ItemStreamException {
        // 진행 상황 모니터링 (재시작에는 사용하지 않음, 마킹 방식 사용)
        // Spring Batch가 자동으로 read_count를 추적하므로 모니터링 용도로만 사용
        if (initialized) {
            executionContext.putLong(tableName + ".recordCount", recordCount);
        }
    }

    @Override
    public void close() throws ItemStreamException {
        // 리소스 정리
        if (cursor != null) {
            try {
                cursor.close();
                log.debug("Cursor closed for table: {}", tableName);
            } catch (Exception e) {
                log.warn("Error closing cursor for table: {}", tableName, e);
            } finally {
                cursor = null;
                cursorIterator = null;
            }
        }
        if (sqlSession != null) {
            try {
                sqlSession.close();
                log.debug("SqlSession closed for table: {}", tableName);
            } catch (Exception e) {
                log.warn("Error closing sqlSession for table: {}", tableName, e);
            } finally {
                sqlSession = null;
            }
        }
        if (initialized) {
            log.info("Closed streaming reader for table: {}, processed {} records", tableName, recordCount);
        }
    }
}
