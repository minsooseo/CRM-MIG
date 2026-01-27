package com.kt.yaap.mig_batch.batch;

import com.kt.yaap.mig_batch.mapper.TargetTableMapper;
import com.kt.yaap.mig_batch.model.TargetRecordEntity;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 여러 컬럼을 UPDATE하는 Writer (벌크 업데이트 방식)
 * 
 * 역할:
 * - 암호화된 값을 대상 테이블에 UPDATE
 * - 원본 값을 _bak 컬럼에 백업 (PostgreSQL은 소문자)
 * - NULL 값도 마킹하여 재처리 방지
 * 
 * 성능 최적화:
 * - PostgreSQL 벌크 업데이트 사용 (UPDATE ... FROM (VALUES ...))
 * - 3500건 처리 시: 3500개 SQL → 1개 SQL (약 3~5배 성능 개선)
 * - SQL 파싱: 3500회 → 1회
 * - 네트워크 왕복: 3500회 → 1회
 * 
 * 주의:
 * - migration_config status 업데이트는 MigrationStatusListener에서 처리
 * - Writer는 데이터 업데이트만 담당 (단일 책임 원칙)
 * - 모든 레코드는 동일한 테이블이어야 함 (Spring Batch Chunk 보장)
 */
@Component
public class EncryptionWriter implements ItemWriter<TargetRecordEntity> {

    private static final Logger log = LoggerFactory.getLogger(EncryptionWriter.class);
    
    /** NULL 값 마킹 상수 (Processor와 동일) */
    private static final String NULL_MARKED = "NULL_MARKED";
    
    /** NULL 값 마킹 시 _bak 컬럼에 저장할 값 */
    private static final String NULL_MARKER = "X";

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Override
    public void write(@NonNull List<? extends TargetRecordEntity> items) throws Exception {
        if (items == null || items.isEmpty()) {
            return;
        }
        
        SqlSession sqlSession = null;
        try {
            // 단일 쿼리로 처리하므로 일반 모드 사용 (BATCH 모드 불필요)
            sqlSession = sqlSessionFactory.openSession();
            TargetTableMapper mapper = sqlSession.getMapper(TargetTableMapper.class);
            
            String tableName = items.get(0).getTableName();
            List<String> pkColumnNames = items.get(0).getPkColumnNames();
            
            // 모든 컬럼명 수집 (중복 제거, 정렬)
            Set<String> allColumnNamesSet = new LinkedHashSet<String>();
            List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
            
            for (TargetRecordEntity item : items) {
                // 레코드별 컬럼값 맵 구성
                Map<String, Map<String, String>> columnValues = new HashMap<String, Map<String, String>>();
                
                for (String columnName : item.getTargetColumnNames()) {
                    String originalValue = item.getOriginalValues().get(columnName);
                    String encryptedValue = item.getEncryptedValues().get(columnName);
                    
                    // encryptedValue가 있는 경우 (암호화된 값 또는 마킹 값)
                    if (encryptedValue != null) {
                        allColumnNamesSet.add(columnName);
                        
                        Map<String, String> colValue = new HashMap<String, String>();
                        
                        // 마킹 값인 경우 처리
                        if (NULL_MARKED.equals(encryptedValue)) {
                            // NULL 값 마킹: _bak에 마킹 값 저장, 원본 컬럼은 NULL 유지
                            colValue.put("originalValue", NULL_MARKER);
                            colValue.put("encryptedValue", null);
                            
                            log.debug("Marking NULL value: table={}, column={}, pk={}", 
                                    tableName, columnName, item.getPkDisplay());
                        } else {
                            // 암호화된 값: 정상 처리
                            colValue.put("originalValue", originalValue);
                            colValue.put("encryptedValue", encryptedValue);
                        }
                        
                        columnValues.put(columnName, colValue);
                    }
                }
                
                // 업데이트할 컬럼이 있는 경우에만 레코드 추가
                if (!columnValues.isEmpty()) {
                    Map<String, Object> record = new HashMap<String, Object>();
                    record.put("pkValues", item.getPkValues());
                    record.put("columnValues", columnValues);
                    records.add(record);
                }
            }
            
            if (records.isEmpty()) {
                log.debug("No records to update for table: {}", tableName);
                return;
            }
            
            // 컬럼명 리스트 (정렬된 순서)
            List<String> allColumnNames = new ArrayList<String>(allColumnNamesSet);
            
            // 벌크 업데이트 파라미터 구성
            Map<String, Object> bulkParams = new HashMap<String, Object>();
            bulkParams.put("tableName", tableName);
            bulkParams.put("pkColumnNames", pkColumnNames);
            bulkParams.put("allColumnNames", allColumnNames);
            bulkParams.put("records", records);
            
            // 벌크 업데이트 실행
            int updateCount = mapper.bulkUpdateTargetRecords(bulkParams);
            
            // 트랜잭션 커밋
            sqlSession.commit();
            
            log.info("Successfully bulk updated {} records for table: {} (columns: {})", 
                    updateCount, tableName, allColumnNames);
            
        } catch (Exception e) {
            if (sqlSession != null) {
                sqlSession.rollback();
            }
            log.error("Error bulk updating records", e);
            throw e;
        } finally {
            if (sqlSession != null) {
                sqlSession.close();
            }
        }
    }
}
