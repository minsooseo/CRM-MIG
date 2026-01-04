package com.kt.yaap.mig_batch.config;

import com.kt.yaap.mig_batch.batch.MigrationItemProcessor;
import com.kt.yaap.mig_batch.batch.MigrationItemWriter;
import com.kt.yaap.mig_batch.model.MigrationConfigEntity;
import com.kt.yaap.mig_batch.model.TargetUpdateEntity;
import com.kt.yaap.mig_batch.service.BackupColumnService;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 배치 Step 설정
 * 
 * Job 설정은 MigrationJobConfig에서 관리합니다.
 */
@Configuration
public class BatchConfig {

    @Value("${migration.chunk-size:1000}")
    private int chunkSize;

    @Autowired
    private BackupColumnService backupColumnService;

    /**
     * 백업 컬럼 생성 Step
     */
    @Bean
    public Step createBackupColumnStep(JobRepository jobRepository,
                                       PlatformTransactionManager transactionManager) {
        return new StepBuilder("createBackupColumnStep", jobRepository)
                .tasklet(createBackupColumnTasklet(), transactionManager)
                .build();
    }

    /**
     * 암호화 처리 Step (데이터 조회 → SafeDB 적용 → UPDATE)
     */
    @Bean
    public Step migrationStep(JobRepository jobRepository,
                             PlatformTransactionManager transactionManager,
                             org.springframework.batch.item.ItemReader<MigrationConfigEntity> reader,
                             MigrationItemProcessor processor,
                             MigrationItemWriter writer,
                             TaskExecutor taskExecutor) {
        return new StepBuilder("migrationStep", jobRepository)
                .<MigrationConfigEntity, java.util.List<TargetUpdateEntity>>chunk(chunkSize, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .taskExecutor(taskExecutor)
                .build();
    }

    /**
     * 비동기 TaskExecutor (병렬 처리)
     */
    @Bean
    public TaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setConcurrencyLimit(5); // 동시 실행 스레드 수
        executor.setThreadNamePrefix("migration-");
        return executor;
    }

    /**
     * 백업 컬럼 생성 Tasklet
     */
    private Tasklet createBackupColumnTasklet() {
        return new Tasklet() {
            @Override
            public RepeatStatus execute(org.springframework.batch.core.StepContribution contribution, 
                                       org.springframework.batch.core.scope.context.ChunkContext chunkContext) throws Exception {
                System.out.println("=== Step 1: 백업 컬럼 자동 생성 시작 ===");
                try {
                    backupColumnService.createBackupColumns();
                    System.out.println("=== Step 1: 백업 컬럼 자동 생성 완료 ===");
                } catch (Exception e) {
                    System.err.println("백업 컬럼 생성 실패: " + e.getMessage());
                    throw e;
                }
                return RepeatStatus.FINISHED;
            }
        };
    }
}

