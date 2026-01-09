package com.kt.yaap.mig_batch.service;

import com.kt.yaap.mig_batch.config.MigrationProperties;
import com.kt.yaap.mig_batch.mapper.MigrationConfigMapper;
import com.kt.yaap.mig_batch.mapper.TargetTableMapper;
import com.kt.yaap.mig_batch.model.MigrationConfigEntity;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
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
    private MigrationProperties migrationProperties;

    /**
     * 모든 활성화된 설정에 대해 백업 컬럼 생성
     * 
     * 자동 커밋 모드로 SqlSession을 열어 Spring Batch 트랜잭션과 분리합니다.
     * DDL 작업(ALTER TABLE)은 자동 커밋 모드에서 안전하게 실행됩니다.
     */
    public void createBackupColumns() {
        log.info("=== 백업 컬럼 자동 생성 시작 ===");

        // 자동 커밋 모드로 SqlSession 열기 (DDL 작업용, Spring Batch 트랜잭션과 분리)
        SqlSession targetSession = sqlSessionFactory.openSession(true); // autoCommit = true

        try {
            TargetTableMapper targetMapper = targetSession.getMapper(TargetTableMapper.class);
            MigrationConfigMapper configMapper = targetSession.getMapper(MigrationConfigMapper.class);

            // 활성화된 모든 설정 조회 (자체 SqlSession에서 실행하여 트랜잭션 분리)
            List<MigrationConfigEntity> configs = configMapper.selectActiveConfigs();

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

                    // PostgreSQL은 소문자로 컬럼명 저장하므로 소문자로 변환
                    String backupColumnName = (columnName + "_bak").toLowerCase();
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

            // 자동 커밋 모드이므로 commit() 불필요
            log.info("=== 백업 컬럼 자동 생성 완료: 생성={}, 건너뜀={} ===", createdCount, skippedCount);

        } catch (Exception e) {
            // 자동 커밋 모드이므로 rollback() 불필요
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
            params.put("schemaName", migrationProperties.getSchemaName());

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
            // PostgreSQL에서 컬럼이 이미 존재할 때 발생하는 오류 처리
            if (isDuplicateColumnError(e)) {
                log.info("백업 컬럼이 이미 존재합니다: {}.{}", tableName, backupColumnName);
                return false;
            }
            log.error("백업 컬럼 생성 실패: {}.{}", tableName, backupColumnName, e);
            throw e; // 기타 오류는 상위로 전파
        }
    }

    /**
     * 중복 컬럼 오류인지 확인
     * 
     * PostgreSQL에서 컬럼이 이미 존재할 때:
     * - SQLSTATE: 42701 (duplicate_column)
     * - 오류 메시지: "column ... already exists" 또는 "duplicate"
     */
    private boolean isDuplicateColumnError(Exception e) {
        // 전체 예외 체인을 순회하며 확인
        Throwable cause = e;
        while (cause != null) {
            // SQLException인 경우 SQLSTATE 확인
            if (cause instanceof SQLException) {
                SQLException sqlEx = (SQLException) cause;
                // PostgreSQL duplicate_column 오류 코드: 42701
                if ("42701".equals(sqlEx.getSQLState())) {
                    log.debug("Detected duplicate column error by SQLSTATE: 42701");
                    return true;
                }
            }
            
            // 오류 메시지 확인
            String errorMsg = cause.getMessage();
            if (errorMsg != null) {
                String lowerMsg = errorMsg.toLowerCase();
                if (lowerMsg.contains("already exists") 
                    || lowerMsg.contains("duplicate column")
                    || (lowerMsg.contains("column") && lowerMsg.contains("exist"))
                    || lowerMsg.contains("42701")) {
                    log.debug("Detected duplicate column error by message: {}", errorMsg);
                    return true;
                }
            }
            
            cause = cause.getCause();
        }

        return false;
    }
}


