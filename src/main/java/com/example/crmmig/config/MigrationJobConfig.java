package com.kt.yaap.mig_batch.config;

import com.kt.yaap.mig_batch.batch.MigrationItemProcessor;
import com.kt.yaap.mig_batch.batch.MigrationItemWriter;
import com.kt.yaap.mig_batch.model.MigrationConfigEntity;
import com.kt.yaap.mig_batch.model.TargetUpdateEntity;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 마이그레이션 Job 설정
 */
@Configuration
public class MigrationJobConfig {

    /**
     * 마이그레이션 Job 생성
     * 
     * 실행 순서:
     * 1. createBackupColumnStep: 백업 컬럼 자동 생성
     * 2. migrationStep: 암호화 처리 (Reader → Processor → Writer)
     */
    @Bean
    public Job migrationJob(JobRepository jobRepository, 
                           @Qualifier("createBackupColumnStep") Step createBackupColumnStep,
                           @Qualifier("migrationStep") Step migrationStep) {
        return new JobBuilder("migrationJob", jobRepository)
                .start(createBackupColumnStep)
                .next(migrationStep)
                .build();
    }
}



