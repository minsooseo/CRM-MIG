package com.kt.yaap.mig_batch.service;

import com.kt.yaap.mig_batch.mapper.MigrationConfigMapper;
import com.kt.yaap.mig_batch.mapper.TargetTableMapper;
import com.kt.yaap.mig_batch.model.MigrationConfigEntity;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 백업 컬럼 자동 생성 서비스
 */
@Service
public class BackupColumnService {

    private static final Logger log = LoggerFactory.getLogger(BackupColumnService.class);

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private MigrationConfigMapper migrationConfigMapper;

    /**
     * 모든 활성화된 설정에 대해 백업 컬럼 생성
     */
    public void createBackupColumns() {
        log.info("=== 백업 컬럼 자동 생성 시작 ===");

        SqlSession targetSession = null;

        try {
            targetSession = sqlSessionFactory.openSession();
            TargetTableMapper targetMapper = targetSession.getMapper(TargetTableMapper.class);

            // 활성화된 모든 설정 조회
            List<MigrationConfigEntity> configs = migrationConfigMapper.selectActiveConfigs();

            if (configs == null || configs.isEmpty()) {
                log.info("생성할 백업 컬럼이 없습니다. (활성화된 설정이 없음)");
                return;
            }

            int createdCount = 0;
            int skippedCount = 0;

            for (MigrationConfigEntity config : configs) {
                // target_column_name을 쉼표로 구분하여 각 컬럼 처리
                String[] columnNames = config.getTargetColumnName().split(",");

                for (String columnName : columnNames) {
                    columnName = columnName.trim();
                    if (columnName.isEmpty()) {
                        continue;
                    }

                    String backupColumnName = columnName + "_BAK";
                    boolean created = createBackupColumnIfNotExists(
                        targetMapper, 
                        config.getTargetTableName(), 
                        columnName, 
                        backupColumnName
                    );

                    if (created) {
                        createdCount++;
                        log.info("백업 컬럼 생성 완료: {}.{}", config.getTargetTableName(), backupColumnName);
                    } else {
                        skippedCount++;
                        log.debug("백업 컬럼 이미 존재: {}.{}", config.getTargetTableName(), backupColumnName);
                    }
                }
            }

            targetSession.commit();
            log.info("=== 백업 컬럼 자동 생성 완료: 생성={}, 건너뜀={} ===", createdCount, skippedCount);

        } catch (Exception e) {
            if (targetSession != null) {
                targetSession.rollback();
            }
            log.error("백업 컬럼 생성 중 오류 발생", e);
            throw new RuntimeException("백업 컬럼 생성 실패", e);
        } finally {
            if (targetSession != null) {
                targetSession.close();
            }
        }
    }

    /**
     * 백업 컬럼이 없으면 생성
     */
    private boolean createBackupColumnIfNotExists(TargetTableMapper mapper, 
                                                   String tableName, 
                                                   String columnName, 
                                                   String backupColumnName) {
        try {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("tableName", tableName);
            params.put("columnName", backupColumnName);
            params.put("schemaName", "public");

            // 백업 컬럼이 이미 존재하는지 확인
            int exists = mapper.checkColumnExists(params);
            if (exists > 0) {
                log.debug("백업 컬럼이 이미 존재합니다: {}.{}", tableName, backupColumnName);
                return false;
            }

            // 원본 컬럼의 데이터 타입 조회
            params.put("columnName", columnName);
            String dataType = mapper.selectColumnDataType(params);

            if (dataType == null || dataType.trim().isEmpty()) {
                log.warn("원본 컬럼의 데이터 타입을 찾을 수 없습니다: {}.{}", tableName, columnName);
                return false;
            }

            // 백업 컬럼 생성
            params.clear();
            params.put("tableName", tableName);
            params.put("backupColumnName", backupColumnName);
            params.put("dataType", dataType);

            mapper.createBackupColumn(params);

            log.info("백업 컬럼 생성 완료: {}.{} (타입: {})", tableName, backupColumnName, dataType);
            return true;

        } catch (Exception e) {
            String errorMsg = e.getMessage();
            // 이미 존재하는 컬럼을 생성하려고 할 때 발생하는 오류
            if (errorMsg != null && (errorMsg.contains("already exists") || errorMsg.contains("duplicate"))) {
                log.info("백업 컬럼이 이미 존재합니다: {}.{}", tableName, backupColumnName);
                return false;
            }
            log.error("백업 컬럼 생성 실패: {}.{}", tableName, backupColumnName, e);
            throw e; // 기타 오류는 상위로 전파
        }
    }
}

