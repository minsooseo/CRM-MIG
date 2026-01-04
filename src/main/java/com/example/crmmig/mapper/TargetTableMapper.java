package com.kt.yaap.mig_batch.mapper;

import com.kt.yaap.mig_batch.model.TargetUpdateEntity;
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
     */
    String selectPrimaryKeyColumn(@Param("params") Map<String, Object> params);

    /**
     * 대상 테이블에서 PK와 컬럼 값 조회
     * 
     * @param params 조회 파라미터 (tableName, columnName, pkColumnName, whereCondition)
     * @return PK와 컬럼 값 목록
     */
    List<TargetUpdateEntity> selectTargetRecords(@Param("params") Map<String, Object> params);

    /**
     * 대상 테이블의 원본 값을 백업 컬럼에 저장
     * 
     * @param params 백업 파라미터 (tableName, columnName, backupColumnName, pkColumnName, pkValue, originalValue)
     * @return 업데이트된 행 수
     */
    int backupOriginalValue(@Param("params") Map<String, Object> params);

    /**
     * 대상 테이블 업데이트
     * 
     * @param params 업데이트 파라미터 (tableName, columnName, pkColumnName, pkValue, encryptedValue)
     * @return 업데이트된 행 수
     */
    int updateTargetRecord(@Param("params") Map<String, Object> params);

    /**
     * 대상 테이블 업데이트 (백업 컬럼과 대상 컬럼을 한 번에 처리)
     * 
     * @param params 업데이트 파라미터 (tableName, columnName, backupColumnName, pkColumnName, pkValue, originalValue, encryptedValue)
     * @return 업데이트된 행 수
     */
    int updateTargetRecordWithBackup(@Param("params") Map<String, Object> params);

    /**
     * 대상 테이블 업데이트 (여러 컬럼을 한 번에 처리 - foreach 사용)
     * 
     * @param params 업데이트 파라미터 (tableName, pkColumnName, pkValue, columnUpdates)
     *               columnUpdates는 Map 리스트로 각 항목은 {columnName, backupColumnName, originalValue, encryptedValue} 포함
     * @return 업데이트된 행 수
     */
    int updateTargetRecordWithMultipleColumns(@Param("params") Map<String, Object> params);

    /**
     * 대상 테이블 배치 업데이트
     * 
     * @param updateList 업데이트 목록
     * @return 업데이트된 행 수
     */
    int batchUpdateTargetRecords(@Param("list") List<TargetUpdateEntity> updateList);

    /**
     * 컬럼 존재 여부 확인
     * 
     * @param params 확인 파라미터 (tableName, columnName, schemaName)
     * @return 존재하면 1, 없으면 0
     */
    int checkColumnExists(@Param("params") Map<String, Object> params);

    /**
     * 컬럼의 데이터 타입 조회
     * 
     * @param params 조회 파라미터 (tableName, columnName, schemaName)
     * @return 데이터 타입 (예: VARCHAR(100), INTEGER 등)
     */
    String selectColumnDataType(@Param("params") Map<String, Object> params);

    /**
     * 백업 컬럼 생성
     * 
     * @param params 생성 파라미터 (tableName, backupColumnName, dataType, schemaName)
     */
    void createBackupColumn(@Param("params") Map<String, Object> params);
}

