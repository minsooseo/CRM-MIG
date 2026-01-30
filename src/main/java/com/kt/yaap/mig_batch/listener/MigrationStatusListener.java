package com.kt.yaap.mig_batch.listener;

import com.kt.yaap.mig_batch.mapper.MigrationConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.lang.NonNull;

/**
 * Step 실행 전후에 migration_config 상태를 업데이트하는 리스너
 * 
 * 역할:
 * - beforeStep: Step 시작 시 상태 확인 (선택)
 * - afterStep: Step 성공 완료 시 status를 'COMPLETE'로 업데이트
 * 
 * 주의:
 * - Writer가 아닌 Step 완료 시점에 한 번만 업데이트하여 성능 최적화
 * - Step이 실패하면 status를 업데이트하지 않아 재실행 가능
 * 
 * 사용법:
 * - Spring 빈이 아님! 각 Step마다 new로 직접 생성하여 사용
 * - BatchConfig에서 Step 생성 시: new MigrationStatusListener(mapper, tableName)
 * - @Component 어노테이션 제거 (생성자에 String 파라미터가 있어서 빈 등록 불가)
 */
public class MigrationStatusListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(MigrationStatusListener.class);

    private final MigrationConfigMapper migrationConfigMapper;
    private final String tableName;

    /**
     * 생성자 (Step 생성 시 테이블명과 Mapper 주입)
     */
    public MigrationStatusListener(MigrationConfigMapper migrationConfigMapper, String tableName) {
        this.migrationConfigMapper = migrationConfigMapper;
        this.tableName = tableName;
    }

    @Override
    public void beforeStep(@NonNull StepExecution stepExecution) {
        log.info("Starting encryption step for table: {}", tableName);
    }

    @Override
    public ExitStatus afterStep(@NonNull StepExecution stepExecution) {
        // Step 실행 통계 로그 출력 (메타 테이블에 기록된 값)
        long readCount = stepExecution.getReadCount();           // 전체 읽은 레코드 수
        long writeCount = stepExecution.getWriteCount();         // 실제 업데이트한 레코드 수 (암호화 처리)
        long filterCount = stepExecution.getFilterCount();       // 스킵된 레코드 수 (이미 암호화됨)
        long skipCount = stepExecution.getSkipCount();            // 예외로 스킵된 건수
        
        log.info("📊 Step execution statistics for table: {} | Read: {}, Write: {}, Filter (Skipped): {}, Skip (Error): {}", 
                tableName, readCount, writeCount, filterCount, skipCount);
        
        // Step이 성공적으로 완료된 경우에만 status 업데이트
        if (stepExecution.getExitStatus().getExitCode().equals(ExitStatus.COMPLETED.getExitCode())) {
            try {
                int statusUpdated = migrationConfigMapper.updateStatus(tableName, "COMPLETE");
                
                if (statusUpdated > 0) {
                    log.info("✅ Updated migration_config status to COMPLETE for table: {} | Processed: {}, Skipped: {}", 
                            tableName, writeCount, filterCount);
                } else {
                    // 워닝이지만 치명적이지 않음 (테스트 테이블 등)
                    log.warn("⚠️ No migration_config record found for table: {} (test table?)", tableName);
                }
            } catch (Exception e) {
                log.error("❌ Failed to update status for table: {}", tableName, e);
                // 상태 업데이트 실패해도 Step은 성공으로 처리 (데이터는 이미 처리됨)
            }
        } else {
            log.warn("⚠️ Step for table {} did not complete successfully: {}", 
                    tableName, stepExecution.getExitStatus());
        }
        
        return stepExecution.getExitStatus();
    }
}
