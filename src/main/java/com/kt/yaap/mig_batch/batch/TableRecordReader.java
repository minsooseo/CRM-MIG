package com.kt.yaap.mig_batch.batch;

import com.kt.yaap.mig_batch.mapper.TargetTableMapper;
import com.kt.yaap.mig_batch.model.TargetRecordEntity;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;

import java.util.*;

/**
 * 대상 테이블의 실제 레코드를 읽는 Reader
 * 
 * 이 Reader는 migration_config가 아닌 실제 대상 테이블의 레코드를 읽습니다.
 * 한 테이블의 여러 컬럼을 함께 처리하므로:
 * - read_count = 실제 처리한 레코드 수
 * - Step 개수 = 테이블 개수
 */
public class TableRecordReader implements ItemReader<TargetRecordEntity> {

    private static final Logger log = LoggerFactory.getLogger(TableRecordReader.class);

    private final SqlSessionFactory sqlSessionFactory;
    private final String tableName;
    private final List<String> targetColumns;  // 암호화 대상 컬럼들
    private final String schemaName;  // 데이터베이스 스키마명
    
    private Iterator<TargetRecordEntity> recordIterator;
    private boolean initialized = false;

    public TableRecordReader(SqlSessionFactory sqlSessionFactory,
                            String tableName,
                            List<String> targetColumns,
                            String schemaName) {
        this.sqlSessionFactory = sqlSessionFactory;
        this.tableName = tableName;
        this.targetColumns = targetColumns;
        this.schemaName = schemaName;
    }

    @Override
    public TargetRecordEntity read() throws Exception {
        if (!initialized) {
            SqlSession sqlSession = null;
            try {
                sqlSession = sqlSessionFactory.openSession();
                TargetTableMapper mapper = sqlSession.getMapper(TargetTableMapper.class);
                
                // 1. PK 컬럼명 조회
                Map<String, Object> pkParams = new HashMap<String, Object>();
                pkParams.put("tableName", tableName);
                pkParams.put("schemaName", schemaName);
                List<String> pkColumnNames = mapper.selectPrimaryKeyColumns(pkParams);
                
                if (pkColumnNames == null || pkColumnNames.isEmpty()) {
                    throw new RuntimeException("Primary Key not found for table: " + tableName);
                }
                
                log.info("Table: {}, PK columns: {}, Target columns: {}", 
                        tableName, pkColumnNames, targetColumns);
                
                // 2. 각 컬럼별로 레코드를 조회하고 PK 기준으로 병합
                Map<String, TargetRecordEntity> recordMap = new LinkedHashMap<String, TargetRecordEntity>();
                
                for (String columnName : targetColumns) {
                    Map<String, Object> params = new HashMap<String, Object>();
                    params.put("tableName", tableName);
                    params.put("columnName", columnName);
                    params.put("whereCondition", null);
                    
                    if (pkColumnNames.size() > 1) {
                        params.put("pkColumnNames", pkColumnNames);
                    } else {
                        params.put("pkColumnName", pkColumnNames.get(0));
                    }
                    
                    List<Map<String, Object>> records = mapper.selectTargetRecords(params);
                    
                    // 각 레코드를 PK 기준으로 Entity에 추가
                    for (Map<String, Object> record : records) {
                        // PK 값 추출
                        Map<String, Object> pkValues = new HashMap<String, Object>();
                        if (pkColumnNames.size() > 1) {
                            // 복합키
                            for (String pkCol : pkColumnNames) {
                                String key = "pk_" + pkCol.toLowerCase();
                                Object value = record.get(key);
                                if (value == null) {
                                    for (String mapKey : record.keySet()) {
                                        if (mapKey.equalsIgnoreCase(key)) {
                                            value = record.get(mapKey);
                                            break;
                                        }
                                    }
                                }
                                pkValues.put(pkCol, value);
                            }
                        } else {
                            // 단일키
                            pkValues.put(pkColumnNames.get(0), record.get("pk_value"));
                        }
                        
                        // PK를 키로 사용 (문자열로 변환)
                        String pkKey = pkValues.toString();
                        
                        // 기존 Entity가 없으면 생성
                        TargetRecordEntity entity = recordMap.get(pkKey);
                        if (entity == null) {
                            entity = new TargetRecordEntity();
                            entity.setTableName(tableName);
                            entity.setPkColumnNames(pkColumnNames);
                            entity.setPkValues(pkValues);
                            entity.setTargetColumnNames(targetColumns);
                            recordMap.put(pkKey, entity);
                        }
                        
                        // 현재 컬럼의 원본 값 추가
                        String originalValue = (String) record.get("original_value");
                        entity.getOriginalValues().put(columnName, originalValue);
                    }
                }
                
                List<TargetRecordEntity> entityList = new ArrayList<TargetRecordEntity>(recordMap.values());
                recordIterator = entityList.iterator();
                initialized = true;
                
                log.info("Loaded {} records from table: {} with {} columns", 
                        entityList.size(), tableName, targetColumns.size());
                
            } finally {
                if (sqlSession != null) {
                    sqlSession.close();
                }
            }
        }

        if (recordIterator != null && recordIterator.hasNext()) {
            return recordIterator.next();
        }
        
        return null;
    }
}
