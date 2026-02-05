package com.kt.yaap.mig_batch.batch;

import com.kt.yaap.mig_batch.mapper.TargetTableMapper;
import com.kt.yaap.mig_batch.model.TargetRecordEntity;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 여러 컬럼을 UPDATE하는 Writer (레코드 단위 업데이트)
 *
 * 역할:
 * - 암호화된 값을 대상 테이블에 UPDATE (레코드마다 실제 업데이트할 컬럼만 SET)
 * - 재수행 시 레코드별 컬럼 세트가 달라도 NULL 덮어쓰기 없이 안전
 *
 * 성능:
 * - MyBatis BATCH 모드로 DB 왕복 횟수 감소
 */
@Component
public class EncryptionWriter implements ItemWriter<TargetRecordEntity> {

    private static final Logger log = LoggerFactory.getLogger(EncryptionWriter.class);

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Override
    public void write(@NonNull List<? extends TargetRecordEntity> items) throws Exception {
        if (items == null || items.isEmpty()) {
            return;
        }

        SqlSession sqlSession = null;
        try {
            sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
            TargetTableMapper mapper = sqlSession.getMapper(TargetTableMapper.class);

            int updateCount = 0;
            String tableName = null;

            for (TargetRecordEntity item : items) {
                tableName = item.getTableName();

                List<Map<String, Object>> columnUpdates = new ArrayList<Map<String, Object>>();
                for (String columnName : item.getTargetColumnNames()) {
                    String encryptedValue = item.getEncryptedValues().get(columnName);
                    if (encryptedValue != null && !"NULL_MARKED".equals(encryptedValue)) {
                        Map<String, Object> columnInfo = new HashMap<String, Object>();
                        columnInfo.put("columnName", columnName);
                        columnInfo.put("encryptedValue", encryptedValue);
                        columnUpdates.add(columnInfo);
                    }
                }

                if (columnUpdates.isEmpty()) {
                    continue;
                }

                Map<String, Object> updateParams = new HashMap<String, Object>();
                updateParams.put("tableName", tableName);
                updateParams.put("columnUpdates", columnUpdates);
                updateParams.put("pkColumnNames", item.getPkColumnNames());
                updateParams.put("pkValues", item.getPkValues());

                mapper.updateTargetRecordWithMultipleColumns(updateParams);
                updateCount++;
            }

            sqlSession.commit();
            log.info("Successfully updated {} records for table: {}", updateCount, tableName);

        } catch (Exception e) {
            if (sqlSession != null) {
                sqlSession.rollback();
            }
            log.error("Error updating records", e);
            throw e;
        } finally {
            if (sqlSession != null) {
                sqlSession.close();
            }
        }
    }
}
