package com.kt.yaap.mig_batch.config;

import com.kt.yaap.mig_batch.batch.MigrationItemReader;
import com.kt.yaap.mig_batch.mapper.MigrationConfigMapper;
import org.springframework.batch.item.ItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Migration Reader 설정
 */
@Configuration
public class MigrationReaderConfig {

    @Bean
    public ItemReader<com.kt.yaap.mig_batch.model.MigrationConfigEntity> reader(MigrationConfigMapper migrationConfigMapper) {
        return new MigrationItemReader(migrationConfigMapper);
    }
}

