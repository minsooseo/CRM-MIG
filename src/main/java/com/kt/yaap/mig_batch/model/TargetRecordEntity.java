package com.kt.yaap.mig_batch.model;

import lombok.Data;

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
@Data
public class TargetRecordEntity {
    
    private String tableName;                                              // 테이블명
    
    // PK 정보
    private List<String> pkColumnNames;                                    // PK 컬럼명들 (단일 또는 복합)
    private Map<String, Object> pkValues;                                  // PK 값들
    
    // 암호화 대상 컬럼들 (여러 컬럼 지원)
    private List<String> targetColumnNames;                                // 암호화 대상 컬럼명들
    private Map<String, String> originalValues;                            // 컬럼명 → 원본 값
    private Map<String, String> encryptedValues;                           // 컬럼명 → 암호화된 값
    
    /**
     * 기본 생성자 - Map 필드들을 명시적으로 초기화
     * NPE 방지를 위해 모든 Map을 빈 HashMap으로 초기화
     */
    public TargetRecordEntity() {
        this.pkValues = new HashMap<>();
        this.originalValues = new HashMap<>();
        this.encryptedValues = new HashMap<>();
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
