package com.kt.yaap.mig_batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CrmMigrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmMigrationApplication.class, args);
    }
}

