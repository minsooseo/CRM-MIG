# 🔄 STS에서 성공한 Job 수동 재실행 가이드

## 🎯 핵심 내용

Spring Batch는 **동일한 JobParameters로는 Job을 재실행할 수 없습니다**.
하지만 현재 코드는 매번 **다른 timestamp**를 사용하므로 **자동으로 재실행이 가능**합니다.

---

## ✅ 방법 1: ManualJobRerun 클래스 사용 (가장 간단)

### 실행 방법

1. **프로젝트 탐색기**에서 다음 파일을 찾습니다:
   ```
   src/test/java/com/kt/yaap/mig_batch/ManualJobRerun.java
   ```

2. **파일을 우클릭** → **Run As** → **Java Application**

3. 실행하면 자동으로:
   - 새로운 timestamp 생성
   - 새로운 Job 인스턴스로 실행
   - 재실행 완료

### 특징
- ✅ 매번 다른 JobParameters 자동 생성
- ✅ 별도 설정 불필요
- ✅ 간단하게 재실행 가능

---

## ✅ 방법 2: ManualJobRunner 사용

이미 실행된 Job과 동일한 방식으로 실행 (timestamp 자동 생성):

1. **파일 찾기**: `src/test/java/com/kt/yaap/mig_batch/ManualJobRunner.java`
2. **우클릭** → **Run As** → **Java Application**

---

## ✅ 방법 3: Run Configuration으로 재실행

### 3-1. Run Configuration 생성/수정

1. **CrmMigrationApplication** 파일 선택
2. **우클릭** → **Run As** → **Run Configurations...**
3. **새로운 설정 생성** 또는 **기존 설정 선택**
4. **Arguments** 탭:
   ```
   --spring.batch.job.enabled=true --spring.batch.job.names=migrationJob
   ```
5. **Apply** → **Run**

### 3-2. 재실행 시
- 같은 설정으로 **Run** 버튼만 클릭하면 재실행됩니다
- Spring Boot가 내부적으로 다른 timestamp를 생성합니다

---

## ⚠️ 주의: 동일한 JobParameters로 재실행하려면

만약 정확히 **같은 조건**으로 재실행하려면 (timestamp 제외), 
DB에서 기존 Job 인스턴스를 먼저 삭제해야 합니다.

### SQL로 삭제 (PostgreSQL)

```sql
-- 1. Job 실행 컨텍스트 삭제
DELETE FROM batch_job_execution_context 
WHERE job_execution_id IN (
    SELECT job_execution_id 
    FROM batch_job_execution 
    WHERE job_instance_id IN (
        SELECT job_instance_id 
        FROM batch_job_instance 
        WHERE job_name = 'migrationJob'
    )
);

DELETE FROM batch_step_execution_context 
WHERE step_execution_id IN (
    SELECT step_execution_id 
    FROM batch_step_execution 
    WHERE job_execution_id IN (
        SELECT job_execution_id 
        FROM batch_job_execution 
        WHERE job_instance_id IN (
            SELECT job_instance_id 
            FROM batch_job_instance 
            WHERE job_name = 'migrationJob'
        )
    )
);

-- 2. Step 실행 삭제
DELETE FROM batch_step_execution 
WHERE job_execution_id IN (
    SELECT job_execution_id 
    FROM batch_job_execution 
    WHERE job_instance_id IN (
        SELECT job_instance_id 
        FROM batch_job_instance 
        WHERE job_name = 'migrationJob'
    )
);

-- 3. Job 실행 파라미터 삭제
DELETE FROM batch_job_execution_params 
WHERE job_execution_id IN (
    SELECT job_execution_id 
    FROM batch_job_execution 
    WHERE job_instance_id IN (
        SELECT job_instance_id 
        FROM batch_job_instance 
        WHERE job_name = 'migrationJob'
    )
);

-- 4. Job 실행 이력 삭제
DELETE FROM batch_job_execution 
WHERE job_instance_id IN (
    SELECT job_instance_id 
    FROM batch_job_instance 
    WHERE job_name = 'migrationJob'
);

-- 5. Job 인스턴스 삭제 (최종)
DELETE FROM batch_job_instance 
WHERE job_name = 'migrationJob';
```

### 또는 간단하게 (주의: 모든 배치 히스토리 삭제)

```sql
-- 주의: migrationJob만 삭제
DELETE FROM batch_job_instance WHERE job_name = 'migrationJob';
```

나머지 관련 테이블은 외래 키 제약조건에 의해 자동으로 삭제될 수 있습니다.
(제약조건이 CASCADE DELETE로 설정된 경우)

---

## 🔍 재실행 확인

### 1. Console 로그 확인

성공적인 재실행 시:

```
========================================
성공한 Job 재실행 시작
========================================
JobParameters: timestamp=1234567890123
Job 실행 중...
INFO  - === 수동 마이그레이션 Job 시작 ===
INFO  - === Step 1: 백업 컬럼 자동 생성 시작 ===
...
INFO  - === 수동 마이그레이션 Job 완료 ===
========================================
Job 재실행 완료
========================================
```

### 2. 데이터베이스 확인

```sql
-- 최근 실행된 Job 목록 확인
SELECT 
    ji.job_instance_id,
    ji.job_name,
    je.job_execution_id,
    je.status,
    je.start_time,
    je.end_time,
    je.exit_code,
    jep.parameter_name,
    jep.parameter_value
FROM batch_job_instance ji
LEFT JOIN batch_job_execution je ON ji.job_instance_id = je.job_instance_id
LEFT JOIN batch_job_execution_params jep ON je.job_execution_id = jep.job_execution_id
WHERE ji.job_name = 'migrationJob'
ORDER BY je.job_execution_id DESC
LIMIT 10;
```

**확인 포인트**:
- `job_execution_id`가 증가했는지 (새로운 실행)
- `timestamp` 파라미터 값이 다른지
- `status`가 `COMPLETED`인지

---

## 🎯 빠른 재실행 체크리스트

- [ ] `ManualJobRerun.java` 파일 확인
- [ ] 데이터베이스 연결 확인
- [ ] `migration_config` 테이블에 활성 데이터 확인
- [ ] 이전 Job 실행 상태 확인 (COMPLETED)
- [ ] Console 로그 확인 (DEBUG 레벨 권장)

---

## 💡 자주 묻는 질문

### Q1: 같은 데이터를 다시 암호화하면 문제가 없나요?

**A**: 현재 코드는 백업 컬럼(`_BAK`)에 원본 값을 저장하고, 
암호화된 값으로 업데이트합니다. 
다시 실행하면 이미 암호화된 값이 다시 암호화될 수 있으므로, 
`migration_config` 테이블의 `where_condition`을 사용하여 
처리 대상 데이터를 제한하는 것을 권장합니다.

### Q2: 재실행 시 중복 실행 오류가 발생하면?

**A**: `JobInstanceAlreadyCompleteException` 오류가 발생하면:
1. `ManualJobRerun`을 다시 실행 (자동으로 다른 timestamp 생성)
2. 또는 위의 SQL로 기존 Job 인스턴스 삭제

### Q3: 재실행 시 기존 데이터에 영향이 있나요?

**A**: 
- 백업 컬럼(`_BAK`): 이미 값이 있으면 UPDATE
- 대상 컬럼: 이미 암호화된 값이 있어도 다시 암호화됨
- **권장**: `where_condition`으로 처리 대상 제한

---

## 📝 권장 재실행 시나리오

### 시나리오 1: 정상 재실행 (가장 일반적)
→ **방법 1 (ManualJobRerun)** 사용

### 시나리오 2: 같은 조건으로 정확히 재실행
→ **DB 삭제 + 방법 1** 사용

### 시나리오 3: 특정 데이터만 재처리
→ `migration_config` 테이블의 `where_condition` 수정 후 **방법 1** 사용

---

## 🎯 결론

**가장 간단한 재실행 방법**:
1. `ManualJobRerun.java` 파일 찾기
2. **우클릭** → **Run As** → **Java Application**
3. 완료!

현재 코드는 매번 다른 timestamp를 생성하므로, 
별도 작업 없이 바로 재실행이 가능합니다! ✅





