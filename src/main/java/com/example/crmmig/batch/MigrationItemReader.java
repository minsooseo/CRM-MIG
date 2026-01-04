package com.kt.yaap.mig_batch.batch;

import com.kt.yaap.mig_batch.mapper.MigrationConfigMapper;
import com.kt.yaap.mig_batch.model.MigrationConfigEntity;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;

import java.util.Iterator;
import java.util.List;

/**
 * MyBatis 기반 마이그레이션 설정 Reader
 */
public class MigrationItemReader implements ItemReader<MigrationConfigEntity> {

    private final MigrationConfigMapper migrationConfigMapper;
    private Iterator<MigrationConfigEntity> configIterator;

    public MigrationItemReader(MigrationConfigMapper migrationConfigMapper) {
        this.migrationConfigMapper = migrationConfigMapper;
    }

    @Override
    public MigrationConfigEntity read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        if (configIterator == null || !configIterator.hasNext()) {
            // 설정 목록 조회
            List<MigrationConfigEntity> configs = migrationConfigMapper.selectActiveConfigs();
            if (configs == null || configs.isEmpty()) {
                return null;
            }
            configIterator = configs.iterator();
        }

        return configIterator.hasNext() ? configIterator.next() : null;
    }
}

