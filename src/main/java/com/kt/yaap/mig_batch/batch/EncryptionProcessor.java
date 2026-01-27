package com.kt.yaap.mig_batch.batch;

import com.kt.yaap.mig_batch.model.TargetRecordEntity;
import com.kt.yaap.mig_batch.util.SafeDBUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 여러 컬럼을 암호화하는 Processor
 * 
 * 역할: 원본 값을 SafeDB로 암호화만 수행
 * - 복잡한 로직 없이 암호화만 담당
 * - null이나 빈 값은 마킹 처리하여 재처리 방지
 */
@Component
public class EncryptionProcessor implements ItemProcessor<TargetRecordEntity, TargetRecordEntity> {

    private static final Logger log = LoggerFactory.getLogger(EncryptionProcessor.class);
    
    /** NULL 값 마킹 상수 */
    private static final String NULL_MARKED = "NULL_MARKED";

    @Autowired
    private SafeDBUtil safeDBUtil;

    @Override
    public TargetRecordEntity process(@NonNull TargetRecordEntity item) throws Exception {
        Map<String, String> encryptedValues = new HashMap<String, String>();
        int processedCount = 0;  // 암호화 + 마킹 처리된 컬럼 수
        
        // 각 컬럼의 값을 암호화 또는 마킹
        for (String columnName : item.getTargetColumnNames()) {
            String originalValue = item.getOriginalValues().get(columnName);
            
            if (originalValue != null && !originalValue.trim().isEmpty()) {
                // 값이 있는 경우: 암호화 수행
                try {
                    String encryptedValue = safeDBUtil.encrypt(originalValue);
                    encryptedValues.put(columnName, encryptedValue);
                    processedCount++;
                    
                    log.debug("Encrypted: table={}, column={}, pk={}", 
                            item.getTableName(), columnName, item.getPkDisplay());
                } catch (Exception e) {
                    log.error("Encryption failed for table={}, column={}, pk={}: {}", 
                            item.getTableName(), columnName, item.getPkDisplay(), e.getMessage());
                    throw e;
                }
            } else {
                // NULL 또는 빈 값인 경우: 마킹 처리 (재처리 방지)
                encryptedValues.put(columnName, NULL_MARKED);
                processedCount++;
                
                log.debug("Marked NULL value: table={}, column={}, pk={}", 
                        item.getTableName(), columnName, item.getPkDisplay());
            }
        }
        
        // 처리할 컬럼이 하나도 없으면 null 반환 (Writer로 전달 안 됨)
        if (processedCount == 0) {
            log.debug("No values to process for pk={}", item.getPkDisplay());
            return null;
        }
        
        item.setEncryptedValues(encryptedValues);
        log.debug("Processed record: table={}, pk={}, processed {} columns", 
                item.getTableName(), item.getPkDisplay(), processedCount);
        
        return item;
    }
}
