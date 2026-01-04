# 🔄 Spring Batch Job 재실행 가이드

## 🚫 문제: Spring Batch의 중복 실행 방지

Spring Batch는 기본적으로 **동일한 JobParameters로 같은 Job을 중복 실행하지 않습니다**.
이미 성공한 Job을 다시 실행하려면 다음과 같은 방법이 있습니다.

---

## ✅ 방법 1: JobParameters에 고유 값 추가 (현재 구현됨)

현재 `MigrationScheduler`는 이미 **timestamp**를 사용하여 매번 다른 JobParameters를 생성합니다.

```java
JobParameters jobParameters = new JobParametersBuilder()
        .addLong("timestamp", System.currentTimeMillis())  // ← 매번 다른 값
        .toJobParameters();
```

**결과**: 매번 새로운 Job 인스턴스로 실행 가능 ✅

---

## ✅ 방법 2: 기존 Job 인스턴스 삭제 후 재실행

데이터베이스에서 기존 Job 인스턴스를 삭제하고 재실행합니다.

### 2-1. SQL로 삭제 (PostgreSQL)

```sql
-- 특정 Job 인스턴스 삭제
DELETE FROM batch_job_execution 
WHERE job_instance_id IN (
    SELECT job_instance_id 
    FROM batch_job_instance 
    WHERE job_name = 'migrationJob'
);

-- Job 인스턴스 삭제
DELETE FROM batch_job_instance 
WHERE job_name = 'migrationJob';
```

### 2-2. 또는 전체 배치 메타데이터 삭제

```sql
-- 주의: 모든 배치 히스토리가 삭제됩니다
DELETE FROM batch_job_execution_context;
DELETE FROM batch_step_execution_context;
DELETE FROM batch_step_execution;
DELETE FROM batch_job_execution;
DELETE FROM batch_job_instance;
DELETE FROM batch_job_execution_params;
```

---

## ✅ 방법 3: JobLauncher 옵션 사용

`SimpleJobLauncher` 대신 재시작 가능한 옵션을 사용합니다.

### 3-1. BatchConfig에 JobLauncher Bean 추가

```java
@Bean
public JobLauncher jobLauncher(JobRepository jobRepository) {
    SimpleJobLauncher jobLauncher = new SimpleJobLauncher();
    jobLauncher.setJobRepository(jobRepository);
    jobLauncher.setTaskExecutor(new SimpleAsyncTaskExecutor());
    return jobLauncher;
}
```

### 3-2. 재시작 가능하도록 Job 설정

```java
@Bean
public Job migrationJob(JobRepository jobRepository, ...) {
    return new JobBuilder("migrationJob", jobRepository)
            .start(createBackupColumnStep)
            .next(migrationStep)
            .next(postMigrationStep)
            .preventRestart(false)  // 재시작 허용
            .build();
}
```

---

## ✅ 방법 4: Command Line 인자로 재실행

애플리케이션 시작 시 Command Line 인자를 통해 실행합니다.

### 4-1. application.yml 설정

```yaml
spring:
  batch:
    job:
      enabled: false  # 기본값: 자동 실행 안 함
      names: migrationJob
```

### 4-2. 실행 시 인자 추가

```bash
# Job 실행
java -jar crm-mig-1.0.0.jar --spring.batch.job.enabled=true --spring.batch.job.names=migrationJob

# 또는 매번 다른 timestamp 추가
java -jar crm-mig-1.0.0.jar \
  --spring.batch.job.enabled=true \
  --spring.batch.job.names=migrationJob \
  --job.param.timestamp=$(date +%s%3N)
```

---

## 🔍 Job 실행 상태 확인

### SQL로 확인

```sql
-- 최근 실행된 Job 목록
SELECT 
    ji.job_instance_id,
    ji.job_name,
    je.job_execution_id,
    je.status,
    je.start_time,
    je.end_time,
    je.exit_code,
    je.exit_message
FROM batch_job_instance ji
LEFT JOIN batch_job_execution je ON ji.job_instance_id = je.job_instance_id
WHERE ji.job_name = 'migrationJob'
ORDER BY je.job_execution_id DESC
LIMIT 10;
```

### 로그로 확인

```
INFO  - === 마이그레이션 Job 시작 ===
INFO  - Processing migration config: Table=customer, Columns=phone,email
INFO  - Successfully updated 200 records
INFO  - === 마이그레이션 Job 완료 ===
```

---

## ⚠️ 주의사항

### 1. 중복 실행 방지 메커니즘

Spring Batch는 `BATCH_JOB_INSTANCE` 테이블에 JobName + JobParameters를 키로 저장합니다.
동일한 키가 이미 존재하면 **JobInstanceAlreadyCompleteException** 또는 **JobRestartException**이 발생합니다.

### 2. 해결 방법

**항상 고유한 JobParameters 사용**:
```java
JobParameters jobParameters = new JobParametersBuilder()
        .addLong("timestamp", System.currentTimeMillis())  // ✅ 고유 값
        .addString("runId", UUID.randomUUID().toString())   // ✅ 더 안전한 방법
        .toJobParameters();
```

### 3. 테스트 환경에서 재실행

테스트할 때는 배치 메타데이터를 자주 삭제하거나, 
매번 다른 JobParameters를 사용하도록 주의해야 합니다.

---

## 📝 권장 사항

### 프로덕션 환경
- ✅ **스케줄러 방식**: 정기적으로 자동 실행 (매번 다른 timestamp)

### 개발/테스트 환경
- ✅ **SQL 삭제**: 개발 중에는 메타데이터 삭제 후 재실행
- ✅ **Command Line**: 각 테스트마다 다른 인자 전달

---

## 🎯 결론

현재 구현된 코드는 이미 **timestamp**를 사용하여 매번 다른 JobParameters를 생성하므로,
**이론적으로는 언제든지 재실행이 가능**합니다.

만약 "이미 실행된 Job을 정확히 같은 조건으로 다시 실행"하려면:
1. 기존 Job 인스턴스를 DB에서 삭제
2. 또는 MigrationScheduler의 runMigrationJobManually() 메서드를 직접 호출

