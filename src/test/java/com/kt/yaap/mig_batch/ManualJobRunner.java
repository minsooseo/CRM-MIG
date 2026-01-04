package com.kt.yaap.mig_batch;

import com.kt.yaap.mig_batch.scheduler.MigrationScheduler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * STS에서 수동으로 Job을 실행하기 위한 클래스
 * 
 * 실행 방법:
 * 1. 이 클래스를 우클릭 → Run As → Java Application
 * 2. 또는 이 클래스에서 main 메서드를 선택하고 Run 버튼 클릭
 */
@SpringBootApplication
public class ManualJobRunner {

    public static void main(String[] args) {
        // Spring 애플리케이션 컨텍스트 시작
        ConfigurableApplicationContext context = SpringApplication.run(CrmMigrationApplication.class, args);
        
        try {
            // MigrationScheduler Bean 가져오기
            MigrationScheduler scheduler = context.getBean(MigrationScheduler.class);
            
            System.out.println("========================================");
            System.out.println("마이그레이션 Job 수동 실행 시작");
            System.out.println("========================================");
            
            // Job 수동 실행
            scheduler.runMigrationJobManually();
            
            System.out.println("========================================");
            System.out.println("마이그레이션 Job 수동 실행 완료");
            System.out.println("========================================");
            
        } catch (Exception e) {
            System.err.println("Job 실행 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 애플리케이션 종료
            context.close();
        }
    }
}





