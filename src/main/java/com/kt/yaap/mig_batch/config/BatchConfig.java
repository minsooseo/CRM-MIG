package com.kt.yaap.mig_batch.config;

import com.kt.yaap.mig_batch.batch.EncryptionProcessor;
import com.kt.yaap.mig_batch.batch.EncryptionWriter;
import com.kt.yaap.mig_batch.batch.TableRecordReader;
import com.kt.yaap.mig_batch.listener.MigrationStatusListener;
import com.kt.yaap.mig_batch.mapper.MigrationConfigMapper;
import com.kt.yaap.mig_batch.model.TargetRecordEntity;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 배치 Step 설정
 * 
 * 테이블별 암호화 Step을 동적으로 생성합니다.
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
    private SqlSessionFactory sqlSessionFactory;
    
    @Autowired
    private EncryptionProcessor encryptionProcessor;
    
    @Autowired
    private EncryptionWriter encryptionWriter;
    
    @Autowired
    private MigrationConfigMapper migrationConfigMapper;

    @Value("${migration.schema-name:public}")
    private String schemaName;

    /**
     * 테이블별 암호화 Step 생성 (동적 생성용)
     * 
     * 이 방식은 Reader가 실제 테이블 레코드를 직접 읽으므로:
     * - read_count = 실제 처리한 레코드 수 (정확!)
     * - 한 테이블의 여러 컬럼을 함께 처리
     * - Step 개수 = 테이블 개수
     * 
     * Step 완료 시 MigrationStatusListener가 migration_config status를 'COMPLETE'로 업데이트합니다.
     * 
     * @param tableName 테이블명
     * @param targetColumns 암호화 대상 컬럼들
     * @return 테이블별 Step
     */
    public Step createTableEncryptionStep(String tableName, List<String> targetColumns) {
        
        // Reader: 대상 테이블의 실제 레코드 읽기 (여러 컬럼 포함)
        TableRecordReader reader = new TableRecordReader(
                sqlSessionFactory, tableName, targetColumns, schemaName);
        
        // Listener: Step 완료 시 status 업데이트
        MigrationStatusListener statusListener = new MigrationStatusListener(migrationConfigMapper, tableName);
        
        String stepName = "encryptionStep_" + tableName;
        
        return stepBuilderFactory.get(stepName)
                .<TargetRecordEntity, TargetRecordEntity>chunk(chunkSize)
                .reader(reader)
                .processor(encryptionProcessor)
                .writer(encryptionWriter)
                .listener(statusListener)  // Step 완료 시 status 업데이트
                .build();
    }
}


