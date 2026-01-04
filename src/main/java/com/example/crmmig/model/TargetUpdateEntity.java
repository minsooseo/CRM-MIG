package com.kt.yaap.mig_batch.model;

/**
 * 대상 테이블 업데이트 엔티티
 * PK와 업데이트할 컬럼 정보를 담음
 */
public class TargetUpdateEntity {
    private Long pkValue;                // Primary Key 값
    private String targetTableName;      // 대상 테이블명
    private String targetColumnName;     // 대상 컬럼명
    private String pkColumnName;         // PK 컬럼명
    private String originalValue;        // 원본 값
    private String encryptedValue;       // SafeDB 적용된 암호화 값
    private Long configId;               // 설정 ID

    public TargetUpdateEntity() {
    }

    public TargetUpdateEntity(Long pkValue, String targetTableName, String targetColumnName, 
                             String pkColumnName, String originalValue, String encryptedValue, Long configId) {
        this.pkValue = pkValue;
        this.targetTableName = targetTableName;
        this.targetColumnName = targetColumnName;
        this.pkColumnName = pkColumnName;
        this.originalValue = originalValue;
        this.encryptedValue = encryptedValue;
        this.configId = configId;
    }

    // Getters and Setters
    public Long getPkValue() {
        return pkValue;
    }

    public void setPkValue(Long pkValue) {
        this.pkValue = pkValue;
    }

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

    public String getOriginalValue() {
        return originalValue;
    }

    public void setOriginalValue(String originalValue) {
        this.originalValue = originalValue;
    }

    public String getEncryptedValue() {
        return encryptedValue;
    }

    public void setEncryptedValue(String encryptedValue) {
        this.encryptedValue = encryptedValue;
    }

    public Long getConfigId() {
        return configId;
    }

    public void setConfigId(Long configId) {
        this.configId = configId;
    }
}

