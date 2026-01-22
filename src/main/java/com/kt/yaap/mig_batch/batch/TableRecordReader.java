package com.kt.yaap.mig_batch.batch;

import com.kt.yaap.mig_batch.mapper.TargetTableMapper;
import com.kt.yaap.mig_batch.model.TargetRecordEntity;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;

import java.util.*;

/**
 * 대상 테이블의 실제 레코드를 읽는 Reader
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
public class TableRecordReader implements ItemReader<TargetRecordEntity> {

    private static final Logger log = LoggerFactory.getLogger(TableRecordReader.class);

    private final SqlSessionFactory sqlSessionFactory;
    private final String tableName;
    private final List<String> targetColumns;  // 암호화 대상 컬럼들
    private final String schemaName;  // 데이터베이스 스키마명
    
    private Iterator<TargetRecordEntity> recordIterator;
    private boolean initialized = false;

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
            long totalStart = System.currentTimeMillis();
            SqlSession sqlSession = null;
            try {
                sqlSession = sqlSessionFactory.openSession();
                TargetTableMapper mapper = sqlSession.getMapper(TargetTableMapper.class);
                
                // 1. PK 컬럼명 조회
                long pkQueryStart = System.currentTimeMillis();
                Map<String, Object> pkParams = new HashMap<String, Object>();
                pkParams.put("tableName", tableName);
                pkParams.put("schemaName", schemaName);
                List<String> pkColumnNames = mapper.selectPrimaryKeyColumns(pkParams);
                long pkQueryElapsed = System.currentTimeMillis() - pkQueryStart;
                
                if (pkColumnNames == null || pkColumnNames.isEmpty()) {
                    throw new RuntimeException("Primary Key not found for table: " + tableName);
                }
                
                log.info("Table: {}, PK columns: {}, Target columns: {} (PK query: {}ms)", 
                        tableName, pkColumnNames, targetColumns, pkQueryElapsed);
                
                // 2. 모든 컬럼을 한 번에 조회 (단일 쿼리 - 성능 최적화)
                long selectStart = System.currentTimeMillis();
                Map<String, Object> params = new HashMap<String, Object>();
                params.put("tableName", tableName);
                params.put("pkColumnNames", pkColumnNames);
                params.put("targetColumnNames", targetColumns);
                
                List<Map<String, Object>> records = mapper.selectAllTargetColumns(params);
                long selectElapsed = System.currentTimeMillis() - selectStart;
                
                // 각 레코드를 Entity로 변환
                long convertStart = System.currentTimeMillis();
                List<TargetRecordEntity> entityList = new ArrayList<TargetRecordEntity>();
                
                for (Map<String, Object> record : records) {
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
                    
                    entityList.add(entity);
                }
                long convertElapsed = System.currentTimeMillis() - convertStart;
                
                recordIterator = entityList.iterator();
                initialized = true;
                
                long totalElapsed = System.currentTimeMillis() - totalStart;
                
                log.info("Loaded {} records from table: {} with {} columns - PK query: {}ms, SELECT: {}ms, Convert: {}ms, Total: {}ms", 
                        entityList.size(), tableName, targetColumns.size(), 
                        pkQueryElapsed, selectElapsed, convertElapsed, totalElapsed);
                
            } finally {
                if (sqlSession != null) {
                    sqlSession.close();
                }
            }
        }

        if (recordIterator != null && recordIterator.hasNext()) {
            return recordIterator.next();
        }
        
        return null;
    }
}
