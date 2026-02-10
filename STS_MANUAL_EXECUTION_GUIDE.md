# 🚀 STS에서 Job 수동 실행 가이드

## 📋 방법 1: ManualJobRunner 사용 (가장 간단)

### 1-1. 실행 방법

1. **프로젝트 탐색기**에서 다음 파일을 찾습니다:
   ```
   src/test/java/com/kt/yaap/mig_batch/ManualJobRunner.java
   ```

2. **파일을 우클릭** → **Run As** → **Java Application**

3. 또는 **파일을 열고** `main` 메서드에서 **우클릭** → **Run As** → **Java Application**

### 1-2. 실행 결과 확인

- **Console** 탭에서 실행 로그 확인
- 성공 시: "마이그레이션 Job 수동 실행 완료" 메시지 출력
- 실패 시: 에러 메시지와 스택 트레이스 출력

---

## 📋 방법 2: JUnit 테스트 코드 사용

### 2-1. 테스트 클래스 생성

다음 테스트 코드를 생성하거나 `MigrationJobTest.java` 파일을 사용:

```java
@RunWith(SpringRunner.class)
@SpringBootTest
public class MigrationJobTest {
    
    @Autowired
    private MigrationScheduler migrationScheduler;
    
    @Test
    public void testRunMigrationJob() {
        migrationScheduler.runMigrationJobManually();
    }
}
```

### 2-2. 실행 방법

1. 테스트 파일을 우클릭 → **Run As** → **JUnit Test**
2. 또는 **JUnit** 뷰에서 실행

---

## 📋 방법 3: Command Line Arguments 사용

### 3-1. Run Configuration 설정

1. **메인 클래스 선택**: `CrmMigrationApplication`
2. **우클릭** → **Run As** → **Run Configurations...**
3. **Arguments** 탭으로 이동
4. **Program arguments**에 다음 입력:
   ```
   --spring.batch.job.enabled=true --spring.batch.job.names=migrationJob
   ```

### 3-2. 실행

- **Run** 버튼 클릭
- 애플리케이션 시작 시 자동으로 Job 실행

---

## 📋 방법 4: application.yml 임시 수정

### 4-1. 설정 변경

`application.yml` 파일에서 다음 설정을 변경:

```yaml
spring:
  batch:
    job:
      enabled: true   # false → true로 변경
      names: migrationJob
```

### 4-2. 실행 후 원복

- 애플리케이션 시작 시 Job이 자동 실행됨
- 실행 후 다시 `enabled: false`로 변경

---

## 📋 방법 5: Debug 모드로 실행

### 5-1. Breakpoint 설정

1. `MigrationScheduler.runMigrationJobManually()` 메서드에 Breakpoint 설정
2. 또는 `ManualJobRunner.main()` 메서드에 Breakpoint 설정

### 5-2. Debug 실행

1. **Debug As** → **Java Application**
2. Breakpoint에서 멈춰서 변수 확인 가능
3. Step Over로 단계별 실행

---

## 🔍 실행 확인

### Console 로그 확인

성공적인 실행 시 다음과 같은 로그가 출력됩니다:

```
========================================
마이그레이션 Job 수동 실행 시작
========================================
INFO  - === 수동 마이그레이션 Job 시작 ===
INFO  - Creating migrationJob with ... table-specific steps
INFO  - [encryptionStep_테이블명] TableRecordReader initialized...
INFO  - Successfully updated ... records
INFO  - === 수동 마이그레이션 Job 완료 ===
========================================
마이그레이션 Job 수동 실행 완료
========================================
```

### 데이터베이스 확인

```sql
-- Job 실행 이력 확인
SELECT 
    ji.job_instance_id,
    ji.job_name,
    je.job_execution_id,
    je.status,
    je.start_time,
    je.end_time,
    je.exit_code
FROM batch_job_instance ji
LEFT JOIN batch_job_execution je ON ji.job_instance_id = je.job_instance_id
WHERE ji.job_name = 'migrationJob'
ORDER BY je.job_execution_id DESC
LIMIT 5;
```

---

## ⚠️ 주의사항

### 1. 중복 실행 방지

Spring Batch는 동일한 JobParameters로는 재실행이 불가능합니다.
현재 코드는 `timestamp`를 사용하므로 매번 다른 값이 생성되어 재실행이 가능합니다.

### 2. 애플리케이션 종료

`ManualJobRunner`는 실행 완료 후 자동으로 애플리케이션을 종료합니다.
계속 실행 상태를 유지하려면:

```java
// context.close() 주석 처리
// context.close();
```

### 3. 스케줄러 비활성화

수동 실행 중에는 자동 스케줄러 실행을 피하려면:

```java
// MigrationScheduler 클래스에서
@Scheduled(cron = "0 0 2 * * ?")
public void runMigrationJob() {
    // 임시로 주석 처리
}
```

---

## 📝 권장 방법

### 개발/테스트 환경
- ✅ **방법 1 (ManualJobRunner)**: 가장 간단하고 직관적
- ✅ **방법 2 (JUnit)**: 테스트 코드로 관리하고 싶을 때

### 프로덕션 환경
- ✅ **방법 3 (Command Line)**: 배포 시 스크립트로 실행
- ✅ **스케줄러**: 정기적으로 자동 실행

---

## 🎯 빠른 실행 체크리스트

- [ ] `ManualJobRunner.java` 파일 생성 확인
- [ ] 데이터베이스 연결 확인 (`application.yml`)
- [ ] `migration_config` 테이블에 데이터 확인
- [ ] Console 로그 레벨 확인 (DEBUG 권장)
- [ ] 실행 후 Job 상태 확인 (DB 쿼리)





