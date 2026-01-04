package com.kt.yaap.mig_batch.batch;

import com.kt.yaap.mig_batch.mapper.TargetTableMapper;
import com.kt.yaap.mig_batch.model.TargetUpdateEntity;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 타겟 테이블에 SafeDB 적용된 값을 UPDATE하는 Writer (MyBatis 기반)
 */
@Component
public class MigrationItemWriter implements ItemWriter<List<TargetUpdateEntity>> {

    private static final Logger log = LoggerFactory.getLogger(MigrationItemWriter.class);

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Override
    public void write(List<? extends List<TargetUpdateEntity>> items) throws Exception {
        int totalCount = 0;
        SqlSession sqlSession = null;
        
        try {
            sqlSession = sqlSessionFactory.openSession();
            TargetTableMapper mapper = sqlSession.getMapper(TargetTableMapper.class);
            
            // Processor에서 반환된 각 설정별 레코드 리스트를 처리
            for (List<TargetUpdateEntity> updateList : items) {
                if (updateList == null || updateList.isEmpty()) {
                    continue;
                }

                // 테이블별로 그룹화하여 처리
                Map<String, List<TargetUpdateEntity>> groupedByTable = groupByTable(updateList);

                for (Map.Entry<String, List<TargetUpdateEntity>> entry : groupedByTable.entrySet()) {
                    String tableName = entry.getKey();
                    List<TargetUpdateEntity> tableUpdates = entry.getValue();
                    
                    // PK별로 그룹화하여 여러 컬럼을 한 번에 처리
                    Map<Long, List<TargetUpdateEntity>> groupedByPk = groupByPk(tableUpdates);
                    String pkColumnName = null;
                    
                    for (Map.Entry<Long, List<TargetUpdateEntity>> pkEntry : groupedByPk.entrySet()) {
                        Long pkValue = pkEntry.getKey();
                        List<TargetUpdateEntity> pkUpdates = pkEntry.getValue();
                        
                        if (pkColumnName == null && !pkUpdates.isEmpty()) {
                            pkColumnName = pkUpdates.get(0).getPkColumnName();
                        }
                        
                        log.debug("Updating table: {}, PK: {}, columns count: {}", 
                                tableName, pkValue, pkUpdates.size());
                        
                        // 여러 컬럼 정보를 리스트로 구성
                        List<Map<String, Object>> columnUpdates = new ArrayList<Map<String, Object>>();
                        for (TargetUpdateEntity item : pkUpdates) {
                            Map<String, Object> columnInfo = new HashMap<String, Object>();
                            columnInfo.put("columnName", item.getTargetColumnName());
                            columnInfo.put("backupColumnName", item.getTargetColumnName() + "_BAK");
                            columnInfo.put("originalValue", item.getOriginalValue());
                            columnInfo.put("encryptedValue", item.getEncryptedValue());
                            columnUpdates.add(columnInfo);
                        }
                        
                        // 여러 컬럼을 한 번에 업데이트
                        Map<String, Object> updateParams = new HashMap<String, Object>();
                        updateParams.put("tableName", tableName);
                        updateParams.put("pkColumnName", pkColumnName);
                        updateParams.put("pkValue", pkValue);
                        updateParams.put("columnUpdates", columnUpdates);
                        
                        try {
                            mapper.updateTargetRecordWithMultipleColumns(updateParams);
                            log.debug("Updated multiple columns: PK={}, Columns={}", pkValue, pkUpdates.size());
                            totalCount += pkUpdates.size();
                        } catch (Exception e) {
                            // 여러 컬럼 업데이트 실패 시 개별 컬럼으로 폴백 처리
                            log.warn("Failed to update multiple columns for PK={}: {}. Trying individual updates.", 
                                    pkValue, e.getMessage());
                            
                            // 각 컬럼을 개별적으로 업데이트 시도
                            for (TargetUpdateEntity item : pkUpdates) {
                                String backupColumnName = item.getTargetColumnName() + "_BAK";
                                Map<String, Object> singleUpdateParams = new HashMap<String, Object>();
                                singleUpdateParams.put("tableName", item.getTargetTableName());
                                singleUpdateParams.put("columnName", item.getTargetColumnName());
                                singleUpdateParams.put("backupColumnName", backupColumnName);
                                singleUpdateParams.put("pkColumnName", item.getPkColumnName());
                                singleUpdateParams.put("pkValue", item.getPkValue());
                                singleUpdateParams.put("originalValue", item.getOriginalValue());
                                singleUpdateParams.put("encryptedValue", item.getEncryptedValue());
                                
                                try {
                                    mapper.updateTargetRecordWithBackup(singleUpdateParams);
                                    log.debug("Updated single column with backup: PK={}, Column={}", 
                                            item.getPkValue(), item.getTargetColumnName());
                                    totalCount++;
                                } catch (Exception e2) {
                                    // 백업 컬럼 업데이트 실패 시 대상 컬럼만 업데이트
                                    log.warn("Backup column does not exist. Updating target column only: PK={}, Column={}", 
                                            item.getPkValue(), item.getTargetColumnName());
                                    Map<String, Object> updateOnlyParams = new HashMap<String, Object>();
                                    updateOnlyParams.put("tableName", item.getTargetTableName());
                                    updateOnlyParams.put("columnName", item.getTargetColumnName());
                                    updateOnlyParams.put("pkColumnName", item.getPkColumnName());
                                    updateOnlyParams.put("pkValue", item.getPkValue());
                                    updateOnlyParams.put("encryptedValue", item.getEncryptedValue());
                                    mapper.updateTargetRecord(updateOnlyParams);
                                    totalCount++;
                                }
                            }
                        }
                    }
                    
                    log.info("Completed updating table: {}, total records: {}", tableName, totalCount);
                }
                
                // 트랜잭션 커밋
                sqlSession.commit();
            }

            log.info("Successfully updated {} records", totalCount);
            
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

    /**
     * 테이블명별로 그룹화
     */
    private Map<String, List<TargetUpdateEntity>> groupByTable(List<TargetUpdateEntity> items) {
        Map<String, List<TargetUpdateEntity>> grouped = new HashMap<String, List<TargetUpdateEntity>>();
        
        for (TargetUpdateEntity item : items) {
            String tableName = item.getTargetTableName();
            if (!grouped.containsKey(tableName)) {
                grouped.put(tableName, new ArrayList<TargetUpdateEntity>());
            }
            grouped.get(tableName).add(item);
        }
        
        return grouped;
    }

    /**
     * PK별로 그룹화 (여러 컬럼을 한 번에 처리하기 위함)
     */
    private Map<Long, List<TargetUpdateEntity>> groupByPk(List<TargetUpdateEntity> items) {
        Map<Long, List<TargetUpdateEntity>> grouped = new HashMap<Long, List<TargetUpdateEntity>>();
        
        for (TargetUpdateEntity item : items) {
            Long pkValue = item.getPkValue();
            if (!grouped.containsKey(pkValue)) {
                grouped.put(pkValue, new ArrayList<TargetUpdateEntity>());
            }
            grouped.get(pkValue).add(item);
        }
        
        return grouped;
    }
}

