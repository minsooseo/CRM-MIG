package com.kt.yaap.mig_batch.batch;

import com.kt.yaap.mig_batch.model.TargetRecordEntity;
import com.kt.yaap.mig_batch.util.SafeDBUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 여러 컬럼을 암호화하는 Processor
 * 
 * 역할: 원본 값을 SafeDB로 암호화만 수행
 * - 복잡한 로직 없이 암호화만 담당
 * - null이나 빈 값은 스킵
 */
@Component
public class EncryptionProcessor implements ItemProcessor<TargetRecordEntity, TargetRecordEntity> {

    private static final Logger log = LoggerFactory.getLogger(EncryptionProcessor.class);

    @Autowired
    private SafeDBUtil safeDBUtil;

    @Override
    public TargetRecordEntity process(TargetRecordEntity item) throws Exception {
        Map<String, String> encryptedValues = new HashMap<String, String>();
        boolean hasValue = false;
        int encryptedCount = 0;
        
        // 각 컬럼의 값을 암호화
        for (String columnName : item.getTargetColumnNames()) {
            String originalValue = item.getOriginalValues().get(columnName);
            
            if (originalValue != null && !originalValue.trim().isEmpty()) {
                try {
                    String encryptedValue = safeDBUtil.encrypt(originalValue);
                    encryptedValues.put(columnName, encryptedValue);
                    hasValue = true;
                    encryptedCount++;
                    
                    log.debug("Encrypted: table={}, column={}, pk={}", 
                            item.getTableName(), columnName, item.getPkDisplay());
                } catch (Exception e) {
                    log.error("Encryption failed for table={}, column={}, pk={}: {}", 
                            item.getTableName(), columnName, item.getPkDisplay(), e.getMessage());
                    throw e;
                }
            } else {
                log.debug("Skipping empty value: table={}, column={}, pk={}", 
                        item.getTableName(), columnName, item.getPkDisplay());
            }
        }
        
        // 암호화할 값이 하나도 없으면 null 반환 (Writer로 전달 안 됨)
        if (!hasValue) {
            log.debug("No values to encrypt for pk={}", item.getPkDisplay());
            return null;
        }
        
        item.setEncryptedValues(encryptedValues);
        log.debug("Processed record: table={}, pk={}, encrypted {} columns", 
                item.getTableName(), item.getPkDisplay(), encryptedCount);
        
        return item;
    }
}
