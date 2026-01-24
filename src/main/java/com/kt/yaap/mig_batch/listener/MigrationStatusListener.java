package com.kt.yaap.mig_batch.listener;

import com.kt.yaap.mig_batch.batch.TableRecordReader;
import com.kt.yaap.mig_batch.mapper.MigrationConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

/**
 * Step 실행 전후에 migration_config 상태를 업데이트하고 리소스를 정리하는 리스너
 * 
 * 역할:
 * - beforeStep: Step 시작 시 상태 확인 (선택)
 * - afterStep: Step 완료 시 
 *   1. status를 'COMPLETE'로 업데이트
 *   2. Reader의 리소스(Cursor, SqlSession) 명시적으로 해제
 * 
 * 주의:
 * - Writer가 아닌 Step 완료 시점에 한 번만 업데이트하여 성능 최적화
 * - Step이 실패해도 리소스는 정리 (Connection 누수 방지)
 * 
 * 사용법:
 * - Spring 빈이 아님! 각 Step마다 new로 직접 생성하여 사용
 * - BatchConfig에서 Step 생성 시: new MigrationStatusListener(mapper, tableName, reader)
 * - @Component 어노테이션 제거 (생성자에 String 파라미터가 있어서 빈 등록 불가)
 */
public class MigrationStatusListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(MigrationStatusListener.class);

    private final MigrationConfigMapper migrationConfigMapper;
    private final String tableName;
    private final TableRecordReader reader;

    /**
     * 생성자 (Step 생성 시 테이블명, Mapper, Reader 주입)
     * 
     * @param migrationConfigMapper MigrationConfig Mapper
     * @param tableName 테이블명
     * @param reader TableRecordReader (리소스 정리를 위해 필요)
     */
    public MigrationStatusListener(MigrationConfigMapper migrationConfigMapper, 
                                   String tableName, 
                                   TableRecordReader reader) {
        this.migrationConfigMapper = migrationConfigMapper;
        this.tableName = tableName;
        this.reader = reader;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("Starting encryption step for table: {}", tableName);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        // 1. 리소스 정리 (성공/실패 여부와 관계없이 항상 실행)
        try {
            if (reader != null) {
                reader.close();
                log.debug("✅ Reader resources closed for table: {}", tableName);
            }
        } catch (Exception e) {
            log.error("❌ Failed to close reader resources for table: {}", tableName, e);
            // 리소스 정리 실패해도 Step은 계속 진행
        }
        
        // 2. Step이 성공적으로 완료된 경우에만 status 업데이트
        if (stepExecution.getExitStatus().getExitCode().equals(ExitStatus.COMPLETED.getExitCode())) {
            try {
                int statusUpdated = migrationConfigMapper.updateStatus(tableName, "COMPLETE");
                
                if (statusUpdated > 0) {
                    log.info("✅ Updated migration_config status to COMPLETE for table: {} (processed {} records)", 
                            tableName, stepExecution.getReadCount());
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
