package com.kt.yaap.mig_batch.batch;

import com.kt.yaap.mig_batch.mapper.MigrationConfigMapper;
import com.kt.yaap.mig_batch.mapper.TargetTableMapper;
import com.kt.yaap.mig_batch.model.TargetRecordEntity;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 여러 컬럼을 UPDATE하는 Writer
 * 
 * 역할:
 * - 암호화된 값을 대상 테이블에 UPDATE
 * - 원본 값을 _bak 컬럼에 백업 (PostgreSQL은 소문자)
 * - 처리 완료 후 migration_config status를 'COMPLETE'로 업데이트
 */
@Component
public class EncryptionWriter implements ItemWriter<TargetRecordEntity> {

    private static final Logger log = LoggerFactory.getLogger(EncryptionWriter.class);

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Override
    public void write(List<? extends TargetRecordEntity> items) throws Exception {
        if (items == null || items.isEmpty()) {
            return;
        }
        
        SqlSession sqlSession = null;
        try {
            sqlSession = sqlSessionFactory.openSession();
            TargetTableMapper mapper = sqlSession.getMapper(TargetTableMapper.class);
            MigrationConfigMapper configMapper = sqlSession.getMapper(MigrationConfigMapper.class);
            
            int updateCount = 0;
            String tableName = null;
            
            for (TargetRecordEntity item : items) {
                tableName = item.getTableName();
                
                // 여러 컬럼 정보를 리스트로 구성
                List<Map<String, Object>> columnUpdates = new ArrayList<Map<String, Object>>();
                for (String columnName : item.getTargetColumnNames()) {
                    String originalValue = item.getOriginalValues().get(columnName);
                    String encryptedValue = item.getEncryptedValues().get(columnName);
                    
                    if (encryptedValue != null) {
                        Map<String, Object> columnInfo = new HashMap<String, Object>();
                        columnInfo.put("columnName", columnName);
                        columnInfo.put("backupColumnName", (columnName + "_bak").toLowerCase());
                        columnInfo.put("originalValue", originalValue);
                        columnInfo.put("encryptedValue", encryptedValue);
                        columnUpdates.add(columnInfo);
                    }
                }
                
                if (columnUpdates.isEmpty()) {
                    continue;
                }
                
                // UPDATE 파라미터 구성 (단일키/복합키 통일)
                Map<String, Object> updateParams = new HashMap<String, Object>();
                updateParams.put("tableName", tableName);
                updateParams.put("columnUpdates", columnUpdates);
                updateParams.put("pkColumnNames", item.getPkColumnNames());
                updateParams.put("pkValues", item.getPkValues());
                
                // UPDATE 실행
                mapper.updateTargetRecordWithMultipleColumns(updateParams);
                updateCount++;
                
                log.debug("Updated record: table={}, pk={}, columns={}", 
                        tableName, item.getPkDisplay(), columnUpdates.size());
            }
            
            // migration_config status 업데이트 (같은 트랜잭션 내에서)
            if (tableName != null) {
                int statusUpdated = configMapper.updateStatus(tableName, "COMPLETE");
                if (statusUpdated > 0) {
                    log.info("Updated migration_config status to COMPLETE for table: {}", tableName);
                } else {
                    log.warn("Failed to update status for table: {} (no matching record)", tableName);
                }
            }
            
            sqlSession.commit();
            log.info("Successfully updated {} records for table: {}", updateCount, tableName);
            
        } catch (Exception e) {
            if (sqlSession != null) {
                sqlSession.rollback();
            }
            log.error("Error updating records", e);
            throw e;
        } finally {
            if (sqlSession != null) {
                sqlSession.close();
            }
        }
    }
}
