package com.kt.yaap.mig_batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 마이그레이션 설정 Properties
 * 
 * application.yml의 migration.* 설정을 읽어옵니다.
 */
@Component
@ConfigurationProperties(prefix = "migration")
public class MigrationProperties {
    
    private int chunkSize = 1000;
    private String configTable = "migration_config";
    private String schemaName = "public";

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public String getConfigTable() {
        return configTable;
    }

    public void setConfigTable(String configTable) {
        this.configTable = configTable;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }
}
