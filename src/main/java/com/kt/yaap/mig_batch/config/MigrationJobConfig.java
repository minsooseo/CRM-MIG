package com.kt.yaap.mig_batch.config;

import com.kt.yaap.mig_batch.mapper.MigrationConfigMapper;
import com.kt.yaap.mig_batch.model.MigrationConfigEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.job.builder.SimpleJobBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 마이그레이션 Job 설정
 * 
 * 실행 순서:
 * 1. createBackupColumnStep: 백업 컬럼 자동 생성
 * 2. encryptionStep_테이블명: 각 테이블별 암호화 처리
 * 
 * 특징:
 * - Reader가 실제 테이블 레코드를 직접 읽음
 * - read_count = 실제 처리한 레코드 수 (정확!)
 * - 한 테이블의 여러 컬럼을 함께 처리
 * - Step 개수 = 테이블 개수
 */
@Configuration
@EnableBatchProcessing
public class MigrationJobConfig {

    private static final Logger log = LoggerFactory.getLogger(MigrationJobConfig.class);

    @Autowired
    private JobBuilderFactory jobBuilderFactory;

    @Autowired
    private MigrationConfigMapper migrationConfigMapper;
    
    @Autowired
    private BatchConfig batchConfig;

    /**
     * 마이그레이션 Job 생성 (테이블별 Step 동적 생성)
     * 
     * migration_config에서 설정을 읽어 테이블별로 Step을 생성합니다.
     * 같은 테이블의 여러 컬럼은 하나의 Step에서 함께 처리됩니다.
     */
    @Bean
    public Job migrationJob(@Qualifier("createBackupColumnStep") Step createBackupColumnStep) {
        
        // migration_config에서 설정 조회
        List<MigrationConfigEntity> configs = migrationConfigMapper.selectActiveConfigs();
        
        // 테이블별로 그룹화 (target_column_name을 합침)
        Map<String, List<String>> tableColumnMap = new HashMap<String, List<String>>();
        for (MigrationConfigEntity config : configs) {
            String tableName = config.getTargetTableName();
            String[] columns = config.getTargetColumnName().split(",");
            
            tableColumnMap.computeIfAbsent(tableName, k -> new ArrayList<String>());
            for (String column : columns) {
                column = column.trim();
                if (!column.isEmpty() && !tableColumnMap.get(tableName).contains(column)) {
                    tableColumnMap.get(tableName).add(column);
                }
            }
        }
        
        log.info("Creating migrationJob with {} table-specific steps", tableColumnMap.size());
        for (Map.Entry<String, List<String>> entry : tableColumnMap.entrySet()) {
            log.info("  - Table: {}, Columns: {}", entry.getKey(), entry.getValue());
        }
        
        // Job 빌드 시작
        SimpleJobBuilder jobBuilder = jobBuilderFactory.get("migrationJob")
                .start(createBackupColumnStep);
        
        // 각 테이블별로 Step 생성하여 순차 연결
        for (Map.Entry<String, List<String>> entry : tableColumnMap.entrySet()) {
            String tableName = entry.getKey();
            List<String> columns = entry.getValue();
            
            Step tableStep = batchConfig.createTableEncryptionStep(tableName, columns);
            
            jobBuilder = jobBuilder.next(tableStep);
            log.info("Added encryption step for table: {}, columns: {}", tableName, columns);
        }
        
        return jobBuilder.build();
    }
}


