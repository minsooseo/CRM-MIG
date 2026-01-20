package com.kt.yaap.mig_batch.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 대상 테이블 Mapper
 */
public interface TargetTableMapper {

    /**
     * INFORMATION_SCHEMA에서 테이블의 Primary Key 컬럼명 조회 (PostgreSQL)
     * 
     * @param params 조회 파라미터 (tableName, schemaName)
     * @return PK 컬럼명 (복합키인 경우 첫 번째 컬럼 반환)
     * @deprecated 복합키 지원을 위해 selectPrimaryKeyColumns 사용 권장
     */
    @Deprecated
    String selectPrimaryKeyColumn(@Param("params") Map<String, Object> params);

    /**
     * INFORMATION_SCHEMA에서 테이블의 모든 Primary Key 컬럼명 조회 (복합키 지원)
     * 
     * @param params 조회 파라미터 (tableName, schemaName)
     * @return PK 컬럼명 리스트 (ordinal_position 순서대로 정렬)
     */
    List<String> selectPrimaryKeyColumns(@Param("params") Map<String, Object> params);

    /**
     * 대상 테이블에서 PK와 컬럼 값 조회 (복합키 지원)
     * 
     * @param params 조회 파라미터 (tableName, columnName, pkColumnNames)
     * @return PK와 컬럼 값 목록 (Map 형태로 반환, Java에서 TargetRecordEntity로 변환)
     */
    List<Map<String, Object>> selectTargetRecords(@Param("params") Map<String, Object> params);

    /**
     * 대상 테이블 업데이트 (여러 컬럼을 한 번에 처리 - foreach 사용)
     * 
     * @param params 업데이트 파라미터 (tableName, pkColumnName, pkValue, columnUpdates)
     *               columnUpdates는 Map 리스트로 각 항목은 {columnName, backupColumnName, originalValue, encryptedValue} 포함
     * @return 업데이트된 행 수
     */
    int updateTargetRecordWithMultipleColumns(@Param("params") Map<String, Object> params);

    /**
     * 대상 테이블 벌크 업데이트 (여러 레코드를 한 번의 SQL로 처리)
     * 
     * @param params 벌크 업데이트 파라미터
     *               - tableName: 대상 테이블명
     *               - pkColumnNames: PK 컬럼명 리스트
     *               - records: 업데이트할 레코드 리스트
     *                 각 레코드는 Map 형태로 {pkValues, columnUpdates} 포함
     *                 pkValues: PK 값들 (Map<String, Object>)
     *                 columnUpdates: 컬럼 업데이트 정보 (List<Map>)
     * @return 업데이트된 행 수
     */
    int bulkUpdateTargetRecords(@Param("params") Map<String, Object> params);
}


