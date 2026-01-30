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
 * - NULL이나 빈 값은 스킵 (업데이트하지 않음)
 */
@Component
public class EncryptionProcessor implements ItemProcessor<TargetRecordEntity, TargetRecordEntity> {

    private static final Logger log = LoggerFactory.getLogger(EncryptionProcessor.class);

    @Autowired
    private SafeDBUtil safeDBUtil;

    @Override
    public TargetRecordEntity process(@NonNull TargetRecordEntity item) throws Exception {
        Map<String, String> encryptedValues = new HashMap<String, String>();
        int processedCount = 0;
        int skippedCount = 0;  // 이미 암호화된 컬럼 수
        
        // 각 컬럼의 값을 암호화
        for (String columnName : item.getTargetColumnNames()) {
            String originalValue = item.getOriginalValues().get(columnName);
            
            // NULL 또는 빈 값은 스킵
            if (originalValue != null && !originalValue.trim().isEmpty()) {
                // 이미 암호화된 값인지 체크 (성능 최적화: 패턴 기반 체크)
                if (safeDBUtil.isEncrypted(originalValue)) {
                    skippedCount++;
                    log.debug("Already encrypted, skipping: table={}, column={}, pk={}", 
                            item.getTableName(), columnName, item.getPkDisplay());
                    continue;  // 이미 암호화된 값은 스킵
                }
                
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
            }
        }
        
        // 처리할 컬럼이 하나도 없으면 null 반환 (Writer로 전달 안 됨)
        // → Spring Batch 메타 테이블의 filterCount에 기록됨 (스킵 건수)
        if (processedCount == 0) {
            if (skippedCount > 0) {
                log.debug("All columns already encrypted, skipping record: table={}, pk={}, skipped {} columns", 
                        item.getTableName(), item.getPkDisplay(), skippedCount);
            } else {
                log.debug("No values to process (all NULL/empty): table={}, pk={}", 
                        item.getTableName(), item.getPkDisplay());
            }
            return null;
        }
        
        item.setEncryptedValues(encryptedValues);
        log.debug("Processed record: table={}, pk={}, processed {} columns, skipped {} columns", 
                item.getTableName(), item.getPkDisplay(), processedCount, skippedCount);
        
        return item;
    }
}
