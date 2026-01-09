package com.kt.yaap.mig_batch.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 대상 테이블의 실제 레코드를 나타내는 Entity
 * 한 레코드에 여러 컬럼의 데이터를 담을 수 있음
 * 
 * Reader가 실제 테이블 레코드를 직접 읽어서 사용하므로
 * read_count = 실제 처리 레코드 수가 됩니다.
 */
public class TargetRecordEntity {
    
    private String tableName;                           // 테이블명
    
    // PK 정보
    private List<String> pkColumnNames;                 // PK 컬럼명들 (단일 또는 복합)
    private Map<String, Object> pkValues;               // PK 값들
    
    // 암호화 대상 컬럼들 (여러 컬럼 지원)
    private List<String> targetColumnNames;             // 암호화 대상 컬럼명들
    private Map<String, String> originalValues;         // 컬럼명 → 원본 값
    private Map<String, String> encryptedValues;        // 컬럼명 → 암호화된 값
    
    public TargetRecordEntity() {
        this.pkValues = new HashMap<String, Object>();
        this.originalValues = new HashMap<String, String>();
        this.encryptedValues = new HashMap<String, String>();
    }
    
    // Getters and Setters
    public String getTableName() {
        return tableName;
    }
    
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
    
    public List<String> getPkColumnNames() {
        return pkColumnNames;
    }
    
    public void setPkColumnNames(List<String> pkColumnNames) {
        this.pkColumnNames = pkColumnNames;
    }
    
    public Map<String, Object> getPkValues() {
        return pkValues;
    }
    
    public void setPkValues(Map<String, Object> pkValues) {
        this.pkValues = pkValues;
    }
    
    public List<String> getTargetColumnNames() {
        return targetColumnNames;
    }
    
    public void setTargetColumnNames(List<String> targetColumnNames) {
        this.targetColumnNames = targetColumnNames;
    }
    
    public Map<String, String> getOriginalValues() {
        return originalValues;
    }
    
    public void setOriginalValues(Map<String, String> originalValues) {
        this.originalValues = originalValues;
    }
    
    public Map<String, String> getEncryptedValues() {
        return encryptedValues;
    }
    
    public void setEncryptedValues(Map<String, String> encryptedValues) {
        this.encryptedValues = encryptedValues;
    }
    
    /**
     * 복합키 여부 확인
     */
    public boolean isCompositeKey() {
        return pkColumnNames != null && pkColumnNames.size() > 1;
    }
    
    /**
     * PK 값을 문자열로 표시 (로깅용)
     */
    public String getPkDisplay() {
        if (pkValues == null || pkValues.isEmpty()) {
            return "null";
        }
        return pkValues.toString();
    }
}
