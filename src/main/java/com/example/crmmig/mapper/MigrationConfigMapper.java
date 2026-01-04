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
     * 
     * @return 마이그레이션 설정 목록
     */
    List<MigrationConfigEntity> selectActiveConfigs();

    /**
     * 설정 ID로 조회
     * 
     * @param configId 설정 ID
     * @return 마이그레이션 설정
     */
    MigrationConfigEntity selectByConfigId(@Param("configId") Long configId);
}

