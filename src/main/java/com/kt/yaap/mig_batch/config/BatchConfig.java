package com.kt.yaap.mig_batch.config;

import com.kt.yaap.mig_batch.batch.EncryptionProcessor;
import com.kt.yaap.mig_batch.batch.EncryptionWriter;
import com.kt.yaap.mig_batch.batch.TableRecordReader;
import com.kt.yaap.mig_batch.config.MigrationProperties;
import com.kt.yaap.mig_batch.model.TargetRecordEntity;
import com.kt.yaap.mig_batch.service.BackupColumnService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 배치 Step 설정
 * 
 * Job 설정은 MigrationJobConfig에서 관리합니다.
 */
@Configuration
@EnableBatchProcessing
public class BatchConfig {

    @Value("${migration.chunk-size:1000}")
    private int chunkSize;

    @Autowired
    private StepBuilderFactory stepBuilderFactory;

    @Autowired
    private BackupColumnService backupColumnService;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;
    
    @Autowired
    private EncryptionProcessor encryptionProcessor;
    
    @Autowired
    private EncryptionWriter encryptionWriter;
    
    @Autowired
    private MigrationProperties migrationProperties;

    /**
     * 백업 컬럼 생성 Step
     * 
     * StepBuilderFactory는 @EnableBatchProcessing에 의해 자동 생성됩니다.
     */
    @Bean
    public Step createBackupColumnStep() {
        return stepBuilderFactory.get("createBackupColumnStep")
                .tasklet(createBackupColumnTasklet())
                .build();
    }

    /**
     * 테이블별 암호화 Step 생성 (동적 생성용)
     * 
     * 이 방식은 Reader가 실제 테이블 레코드를 직접 읽으므로:
     * - read_count = 실제 처리한 레코드 수 (정확!)
     * - 한 테이블의 여러 컬럼을 함께 처리
     * - Step 개수 = 테이블 개수
     * 
     * @param tableName 테이블명
     * @param targetColumns 암호화 대상 컬럼들
     * @return 테이블별 Step
     */
    public Step createTableEncryptionStep(String tableName, List<String> targetColumns) {
        
        // Reader: 대상 테이블의 실제 레코드 읽기 (여러 컬럼 포함)
        TableRecordReader reader = new TableRecordReader(
                sqlSessionFactory, tableName, targetColumns, migrationProperties.getSchemaName());
        
        String stepName = "encryptionStep_" + tableName;
        
        return stepBuilderFactory.get(stepName)
                .<TargetRecordEntity, TargetRecordEntity>chunk(chunkSize)
                .reader(reader)
                .processor(encryptionProcessor)
                .writer(encryptionWriter)
                .build();
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


