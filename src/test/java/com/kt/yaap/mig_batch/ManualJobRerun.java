package com.kt.yaap.mig_batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * STS에서 성공한 Job을 수동으로 재실행하기 위한 클래스
 * 
 * Spring Batch는 동일한 JobParameters로는 재실행이 불가능하므로,
 * 매번 다른 timestamp를 사용하여 새로운 Job 인스턴스로 실행합니다.
 * 
 * 실행 방법:
 * 1. 이 클래스를 우클릭 → Run As → Java Application
 * 2. 또는 이 클래스에서 main 메서드를 선택하고 Run 버튼 클릭
 */
public class ManualJobRerun {

    public static void main(String[] args) {
        // Spring 애플리케이션 컨텍스트 시작
        ConfigurableApplicationContext context = SpringApplication.run(CrmMigrationApplication.class, args);
        
        try {
            // JobLauncher와 Job Bean 가져오기
            JobLauncher jobLauncher = context.getBean(JobLauncher.class);
            Job migrationJob = context.getBean("migrationJob", Job.class);
            
            System.out.println("========================================");
            System.out.println("성공한 Job 재실행 시작");
            System.out.println("========================================");
            
            // 매번 다른 timestamp를 사용하여 새로운 Job 인스턴스 생성
            // 이렇게 하면 동일한 JobParameters 문제를 피할 수 있습니다
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("rerun", "true")  // 재실행임을 표시
                    .toJobParameters();
            
            System.out.println("JobParameters: timestamp=" + jobParameters.getLong("timestamp"));
            System.out.println("Job 실행 중...");
            
            // Job 실행
            jobLauncher.run(migrationJob, jobParameters);
            
            System.out.println("========================================");
            System.out.println("Job 재실행 완료");
            System.out.println("========================================");
            
        } catch (Exception e) {
            System.err.println("========================================");
            System.err.println("Job 재실행 중 오류 발생");
            System.err.println("========================================");
            System.err.println("오류 메시지: " + e.getMessage());
            e.printStackTrace();
            
            // JobInstanceAlreadyCompleteException 또는 JobRestartException 발생 시
            if (e.getMessage() != null && 
                (e.getMessage().contains("JobInstanceAlreadyCompleteException") || 
                 e.getMessage().contains("JobRestartException") ||
                 e.getMessage().contains("already exists"))) {
                System.err.println();
                System.err.println("⚠️ 동일한 JobParameters로는 재실행이 불가능합니다.");
                System.err.println("해결 방법:");
                System.err.println("1. 이 프로그램을 다시 실행 (자동으로 다른 timestamp 생성)");
                System.err.println("2. 또는 DB에서 기존 Job 인스턴스 삭제 후 재실행");
                System.err.println("   SQL: DELETE FROM batch_job_instance WHERE job_name = 'migrationJob';");
            }
        } finally {
            // 애플리케이션 종료
            context.close();
            System.exit(0);
        }
    }
}





