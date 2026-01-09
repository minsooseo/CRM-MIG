package com.kt.yaap.mig_batch.mapper;

import com.kt.yaap.mig_batch.model.MigrationConfigEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 마이그레이션 설정 Mapper
 */
public interface MigrationConfigMapper {

    /**
     * 활성화된 마이그레이션 설정 목록 조회
     * (status = 'ACTIVE' 또는 NULL인 설정만 조회, 'COMPLETE' 상태는 제외)
     * 
     * @return 마이그레이션 설정 목록
     */
    List<MigrationConfigEntity> selectActiveConfigs();

    /**
     * 마이그레이션 설정의 상태를 업데이트
     * 
     * @param targetTableName 대상 테이블명 (PK)
     * @param status 업데이트할 상태 ('ACTIVE', 'INACTIVE', 'COMPLETE' 등)
     * @return 업데이트된 행 수
     */
    int updateStatus(@Param("targetTableName") String targetTableName, @Param("status") String status);

    /**
     * 특정 테이블의 마이그레이션 설정 조회
     * 
     * @param targetTableName 대상 테이블명
     * @return 마이그레이션 설정 (없으면 null)
     */
    MigrationConfigEntity selectByTableName(@Param("targetTableName") String targetTableName);
}


