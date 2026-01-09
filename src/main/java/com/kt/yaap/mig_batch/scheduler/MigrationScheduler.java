package com.kt.yaap.mig_batch.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 배치 Job을 스케줄링하는 Scheduler
 * 필요에 따라 사용하거나 제거 가능
 * 
 * 주의: Job Bean이 없으면 애플리케이션 시작이 실패합니다.
 * Job Bean을 생성하지 않으려면 이 클래스를 제거하거나 @Component 주석 처리하세요.
 */
@Component
public class MigrationScheduler {

    private static final Logger log = LoggerFactory.getLogger(MigrationScheduler.class);

    @Autowired(required = false)
    private JobLauncher jobLauncher;

    @Autowired(required = false)
    @Qualifier("migrationJob")
    private Job migrationJob;

    /**
     * 매일 새벽 2시에 실행
     * cron 표현식: 초 분 시 일 월 요일
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void runMigrationJob() {
        if (jobLauncher == null || migrationJob == null) {
            log.warn("JobLauncher 또는 migrationJob이 주입되지 않았습니다. Job 실행을 건너뜁니다.");
            return;
        }
        
        try {
            log.info("=== 마이그레이션 Job 시작 ===");
            
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(migrationJob, jobParameters);
            
            log.info("=== 마이그레이션 Job 완료 ===");
        } catch (Exception e) {
            log.error("마이그레이션 Job 실행 중 오류 발생", e);
        }
    }

    /**
     * 수동 실행용 메서드
     * REST API 컨트롤러에서 호출하거나 테스트에서 사용
     */
    public void runMigrationJobManually() {
        if (jobLauncher == null || migrationJob == null) {
            throw new IllegalStateException("JobLauncher 또는 migrationJob이 주입되지 않았습니다. BatchConfig에서 Job Bean을 생성해주세요.");
        }
        
        try {
            log.info("=== 수동 마이그레이션 Job 시작 ===");
            
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(migrationJob, jobParameters);
            
            log.info("=== 수동 마이그레이션 Job 완료 ===");
        } catch (Exception e) {
            log.error("수동 마이그레이션 Job 실행 중 오류 발생", e);
            throw new RuntimeException("마이그레이션 실행 실패", e);
        }
    }
}


