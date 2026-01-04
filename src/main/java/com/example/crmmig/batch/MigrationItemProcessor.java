package com.kt.yaap.mig_batch.batch;

import com.kt.yaap.mig_batch.mapper.TargetTableMapper;
import com.kt.yaap.mig_batch.model.MigrationConfigEntity;
import com.kt.yaap.mig_batch.model.TargetUpdateEntity;
import com.kt.yaap.mig_batch.util.SafeDBUtil;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 마이그레이션 설정을 기반으로 대상 테이블의 PK를 조회하고 SafeDB를 적용하는 Processor
 */
@Component
public class MigrationItemProcessor implements ItemProcessor<MigrationConfigEntity, List<TargetUpdateEntity>> {

    private static final Logger log = LoggerFactory.getLogger(MigrationItemProcessor.class);

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private SafeDBUtil safeDBUtil;

    @Override
    public List<TargetUpdateEntity> process(MigrationConfigEntity config) throws Exception {
        log.info("Processing migration config: Table={}, Columns={}", 
                config.getTargetTableName(), config.getTargetColumnName());

        List<TargetUpdateEntity> updateList = new ArrayList<TargetUpdateEntity>();

        SqlSession sqlSession = null;
        try {
            sqlSession = sqlSessionFactory.openSession();
            TargetTableMapper mapper = sqlSession.getMapper(TargetTableMapper.class);

            // 1. INFORMATION_SCHEMA에서 Primary Key 컬럼명 조회
            String pkColumnName = fetchPrimaryKeyColumn(mapper, config);
            if (pkColumnName == null || pkColumnName.trim().isEmpty()) {
                throw new RuntimeException("Primary Key를 찾을 수 없습니다. 테이블: " + config.getTargetTableName());
            }
            config.setPkColumnName(pkColumnName);
            log.info("Found Primary Key column: {} for table: {}", pkColumnName, config.getTargetTableName());

            // 2. target_column_name을 쉼표로 구분하여 여러 컬럼 처리
            String[] columnNames = config.getTargetColumnName().split(",");
            
            for (String columnName : columnNames) {
                columnName = columnName.trim(); // 공백 제거
                if (columnName.isEmpty()) {
                    continue;
                }
                
                log.info("Processing column: {} for table: {}", columnName, config.getTargetTableName());

                // 3. 대상 테이블에서 PK 목록과 해당 컬럼 값 조회 (MyBatis 사용)
                List<TargetUpdateEntity> records = fetchTargetRecords(mapper, config, columnName);

                // 4. 각 레코드에 SafeDB 적용
                for (TargetUpdateEntity record : records) {
                    String originalValue = record.getOriginalValue();
                    
                    if (originalValue != null && !originalValue.trim().isEmpty()) {
                        // SafeDB 암호화 적용
                        String encryptedValue = safeDBUtil.encrypt(originalValue);
                        record.setEncryptedValue(encryptedValue);
                        
                        updateList.add(record);
                        log.debug("Encrypted: PK={}, Column={}, Original={}, Encrypted={}", 
                                record.getPkValue(), columnName, originalValue, encryptedValue);
                    } else {
                        log.debug("Skipping empty value: PK={}, Column={}", record.getPkValue(), columnName);
                    }
                }
            }

            log.info("Processed {} records for table: {}, columns: {}", 
                    updateList.size(), config.getTargetTableName(), config.getTargetColumnName());
            
        } catch (Exception e) {
            log.error("Error processing migration config: table={}, columns={}", 
                    config.getTargetTableName(), config.getTargetColumnName(), e);
            throw e;
        } finally {
            if (sqlSession != null) {
                sqlSession.close();
            }
        }

        return updateList;
    }

    /**
     * INFORMATION_SCHEMA에서 Primary Key 컬럼명 조회
     */
    private String fetchPrimaryKeyColumn(TargetTableMapper mapper, MigrationConfigEntity config) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("tableName", config.getTargetTableName());
        params.put("schemaName", "public"); // 기본 스키마, 필요시 설정에서 가져올 수 있음
        
        log.debug("Fetching Primary Key column for table: {}", config.getTargetTableName());
        return mapper.selectPrimaryKeyColumn(params);
    }

    /**
     * 대상 테이블에서 PK와 대상 컬럼 값을 조회 (MyBatis 사용)
     */
    private List<TargetUpdateEntity> fetchTargetRecords(TargetTableMapper mapper, MigrationConfigEntity config, String columnName) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("tableName", config.getTargetTableName());
        params.put("columnName", columnName);
        params.put("pkColumnName", config.getPkColumnName());
        params.put("whereCondition", null); // whereCondition 필드 제거로 인해 null로 설정

        log.debug("Fetching records from table: {}, column: {}", 
                config.getTargetTableName(), columnName);

        return mapper.selectTargetRecords(params);
    }
}
