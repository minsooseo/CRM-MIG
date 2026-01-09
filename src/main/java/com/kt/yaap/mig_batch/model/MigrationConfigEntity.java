package com.kt.yaap.mig_batch.model;

/**
 * 마이그레이션 설정 엔티티
 * migration_config 테이블에서 읽어올 마이그레이션 설정 정보
 */
public class MigrationConfigEntity {
    private String targetTableName;      // 대상 테이블명
    private String targetColumnName;     // 대상 컬럼명 (PK는 INFORMATION_SCHEMA에서 조회)
    private String pkColumnName;         // Primary Key 컬럼명 (동적 조회용, DB 저장 안함)

    public MigrationConfigEntity() {
    }

    public MigrationConfigEntity(String targetTableName, String targetColumnName) {
        this.targetTableName = targetTableName;
        this.targetColumnName = targetColumnName;
    }

    // Getters and Setters
    public String getTargetTableName() {
        return targetTableName;
    }

    public void setTargetTableName(String targetTableName) {
        this.targetTableName = targetTableName;
    }

    public String getTargetColumnName() {
        return targetColumnName;
    }

    public void setTargetColumnName(String targetColumnName) {
        this.targetColumnName = targetColumnName;
    }

    public String getPkColumnName() {
        return pkColumnName;
    }

    public void setPkColumnName(String pkColumnName) {
        this.pkColumnName = pkColumnName;
    }
}


