package com.kt.yaap.mig_batch.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

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
     * @deprecated 성능 개선을 위해 selectAllTargetColumns 사용 권장 (단일 쿼리)
     */
    @Deprecated
    List<Map<String, Object>> selectTargetRecords(@Param("params") Map<String, Object> params);

    /**
     * 대상 테이블에서 PK와 모든 대상 컬럼을 한 번에 조회 (단일 쿼리 - 성능 최적화)
     * 
     * @param params 조회 파라미터
     *               - tableName: 대상 테이블명
     *               - pkColumnNames: PK 컬럼명 리스트
     *               - targetColumnNames: 암호화 대상 컬럼명 리스트
     * @return PK와 모든 컬럼 값 목록 (Map 형태로 반환)
     *         각 Map은 PK 컬럼들과 대상 컬럼들의 값을 포함
     */
    List<Map<String, Object>> selectAllTargetColumns(@Param("params") Map<String, Object> params);

    /**
     * 대상 테이블에서 PK와 모든 대상 컬럼을 스트리밍 방식으로 조회 (Cursor 사용)
     * 
     * 스트리밍 방식의 장점:
     * - 메모리 효율: 한 번에 하나씩만 읽어서 메모리 사용량 최소화
     * - 빠른 시작: 초기화 시간 단축 (전체 조회 대비 20~30초 단축)
     * - 대용량 데이터 처리: OOM 위험 없이 처리 가능
     * 
     * @param params 조회 파라미터
     *               - tableName: 대상 테이블명
     *               - pkColumnNames: PK 컬럼명 리스트
     *               - targetColumnNames: 암호화 대상 컬럼명 리스트
     * @return Cursor로 스트리밍 조회 (메모리 효율적)
     */
    Cursor<Map<String, Object>> selectAllTargetColumnsStreaming(@Param("params") Map<String, Object> params);

    /**
     * 대상 테이블 업데이트 (여러 컬럼을 한 번에 처리 - foreach 사용)
     * 
     * @param params 업데이트 파라미터 (tableName, pkColumnName, pkValue, columnUpdates)
     *               columnUpdates는 Map 리스트로 각 항목은 {columnName, backupColumnName, originalValue, encryptedValue} 포함
     * @return 업데이트된 행 수
     */
    //int updateTargetRecordWithMultipleColumns(@Param("params") Map<String, Object> params);

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


