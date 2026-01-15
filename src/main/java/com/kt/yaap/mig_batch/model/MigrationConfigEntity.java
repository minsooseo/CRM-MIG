package com.kt.yaap.mig_batch.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 마이그레이션 설정 엔티티
 * migration_config 테이블에서 읽어올 마이그레이션 설정 정보
 */
@Data
@NoArgsConstructor
public class MigrationConfigEntity {
    private String targetTableName;      // 대상 테이블명
    private String targetColumnName;     // 대상 컬럼명 (PK는 INFORMATION_SCHEMA에서 조회)
    private String pkColumnName;         // Primary Key 컬럼명 (동적 조회용, DB 저장 안함)
    
    public MigrationConfigEntity(String targetTableName, String targetColumnName) {
        this.targetTableName = targetTableName;
        this.targetColumnName = targetColumnName;
    }
}


