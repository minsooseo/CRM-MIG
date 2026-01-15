# 🚀 CRM 마이그레이션 배치 실행 가이드

**최종 업데이트:** 2026-01-15  
**버전:** 1.0 (Lombok 적용, NPE 수정 반영)

이 문서는 현재 프로젝트 구조를 기반으로 STS와 Linux 서버에서 배치를 실행하는 방법을 안내합니다.

---

## 📑 목차

1. [STS에서 실행하기](#1-sts에서-실행하기)
2. [STS에서 재실행하기](#2-sts에서-재실행하기)
3. [Linux 서버에서 실행하기](#3-linux-서버에서-실행하기)
4. [실행 전 체크리스트](#4-실행-전-체크리스트)
5. [문제 해결](#5-문제-해결)

---

## 1. STS에서 실행하기

### 1.1 사전 준비

#### ✅ 필수 확인 사항

```yaml
# application.yml 확인
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/migration_db  # DB 연결 정보
    username: postgres
    password: your_password
    
migration:
  chunk-size: 1000
  schema-name: public  # 스키마명
```

#### ✅ 데이터베이스 준비

```sql
-- 1. migration_config 테이블 생성 (where_condition 제거됨)
CREATE TABLE migration_config (
  target_table_name VARCHAR(100) PRIMARY KEY,
  target_column_name VARCHAR(500) NOT NULL,
  status VARCHAR(20) DEFAULT 'ACTIVE',
  priority INTEGER DEFAULT 0
);

-- 2. 샘플 데이터 삽입
INSERT INTO migration_config 
  (target_table_name, target_column_name, status, priority)
VALUES
  ('customer', 'phone', 'ACTIVE', 1),
  ('order', 'recipient_phone,recipient_name', 'ACTIVE', 2);
```

### 1.2 실행 방법

#### 방법 1: Spring Boot App으로 실행 (권장)

```
1. 프로젝트 우클릭
2. Run As → Spring Boot App
3. Console에서 실행 로그 확인
```

**특징:**
- ✅ Spring Boot 자동 설정
- ✅ 로깅 자동 구성
- ✅ 재실행 시 간편

#### 방법 2: Java Application으로 실행

```
1. CrmMigrationApplication.java 파일 열기
2. 우클릭 → Run As → Java Application
3. Console에서 실행 로그 확인
```

#### 방법 3: Maven으로 실행

```bash
# STS 터미널 또는 외부 터미널
cd C:\CRM\workspace\CRM-MIG
mvn spring-boot:run
```

### 1.3 실행 옵션 설정

#### Run Configuration 설정

```
1. Run → Run Configurations...
2. Spring Boot App → 새로 만들기
3. Name: CRM Migration Batch
4. Project: CRM-MIG
5. Main type: com.kt.yaap.mig_batch.CrmMigrationApplication
6. Profile: (비워둠)
7. Arguments 탭:
   --spring.batch.job.enabled=true
   --spring.batch.job.names=migrationJob
```

### 1.4 실행 로그 확인

```
=== 수동 마이그레이션 Job 시작 ===
Creating migrationJob with 2 table-specific steps
  - Table: customer, Columns: [phone]
  - Table: order, Columns: [recipient_phone, recipient_name]

=== Step 1: encryptionStep_customer 시작 ===
Table: customer, PK columns: [customer_id], Target columns: [phone]
Loaded 100 records from table: customer with 1 columns
Encrypted: table=customer, column=phone, pk={customer_id=1}
...
Updated record: table=customer, pk={customer_id=1}, columns=1
Successfully updated 100 records for table: customer
Updated migration_config status to COMPLETE for table: customer

=== Step 2: encryptionStep_order 시작 ===
...

=== 수동 마이그레이션 Job 완료 ===
```

---

## 2. STS에서 재실행하기

### 2.1 자동 재실행 (JobParameters 자동 생성)

Spring Batch는 매번 다른 timestamp를 생성하므로 **별도 작업 없이 재실행 가능**합니다.

```
1. Run 버튼 클릭 또는 Ctrl+F11
2. 자동으로 새로운 Job Instance 생성
3. 실행 완료
```

### 2.2 수동 재실행 (ManualJobRerun 사용)

```
1. src/test/java/com/kt/yaap/mig_batch/ManualJobRerun.java 열기
2. 우클릭 → Run As → Java Application
3. 실행 로그 확인
```

**ManualJobRerun.java 코드:**
```java
@SpringBootApplication
public class ManualJobRerun {
    public static void main(String[] args) {
        System.out.println("=== 재실행 시작 ===");
        
        String[] newArgs = new String[]{
            "--spring.batch.job.enabled=true",
            "--spring.batch.job.names=migrationJob",
            "run.id=" + System.currentTimeMillis()  // 자동으로 다른 timestamp
        };
        
        SpringApplication.run(ManualJobRerun.class, newArgs);
    }
}
```

### 2.3 특정 테이블만 재실행

```sql
-- migration_config에서 status를 다시 ACTIVE로 변경
UPDATE migration_config 
SET status = 'ACTIVE' 
WHERE target_table_name = 'customer' AND status = 'COMPLETE';

-- 재실행
-- (STS에서 Run 버튼 클릭)
```

### 2.4 배치 메타데이터 초기화 후 재실행

완전히 새로 시작하려면 Spring Batch 메타데이터를 삭제:

```sql
-- Spring Batch 메타데이터 삭제
DELETE FROM batch_step_execution_context;
DELETE FROM batch_job_execution_context;
DELETE FROM batch_step_execution;
DELETE FROM batch_job_execution_params;
DELETE FROM batch_job_execution;
DELETE FROM batch_job_instance WHERE job_name = 'migrationJob';

-- migration_config 상태 초기화
UPDATE migration_config SET status = 'ACTIVE' WHERE status = 'COMPLETE';
```

---

## 3. Linux 서버에서 실행하기

### 3.1 빌드 (Windows/STS에서)

```bash
# 프로젝트 루트 디렉토리에서
cd C:\CRM\workspace\CRM-MIG

# Maven 빌드
mvn clean package -DskipTests

# 결과물 확인
dir target\crm-mig-1.0.0.jar
```

### 3.2 서버로 업로드

#### SCP로 업로드

```bash
# JAR 파일 업로드
scp target/crm-mig-1.0.0.jar user@linux-server:/opt/crm-mig/

# 설정 파일 업로드 (선택사항)
scp src/main/resources/application.yml user@linux-server:/opt/crm-mig/config/
```

#### WinSCP 사용 (Windows)

```
1. WinSCP 실행
2. 서버 접속
3. target/crm-mig-1.0.0.jar → /opt/crm-mig/ 드래그
```

### 3.3 Linux 서버에서 실행

#### 기본 실행

```bash
# 서버 접속
ssh user@linux-server

# 실행 디렉토리로 이동
cd /opt/crm-mig

# 실행
java -jar crm-mig-1.0.0.jar \
  --spring.batch.job.enabled=true \
  --spring.batch.job.names=migrationJob \
  run.id=$(date +%s)
```

#### 외부 설정 파일 사용

```bash
java -jar crm-mig-1.0.0.jar \
  --spring.config.location=file:./config/application.yml \
  --spring.batch.job.enabled=true \
  --spring.batch.job.names=migrationJob \
  run.id=$(date +%s)
```

#### 데이터베이스 연결 정보 오버라이드

```bash
java -jar crm-mig-1.0.0.jar \
  --spring.datasource.url=jdbc:postgresql://prod-db-server:5432/migration_db \
  --spring.datasource.username=prod_user \
  --spring.datasource.password=prod_password \
  --spring.batch.job.enabled=true \
  --spring.batch.job.names=migrationJob \
  run.id=$(date +%s)
```

### 3.4 백그라운드 실행

```bash
# nohup으로 백그라운드 실행
nohup java -jar crm-mig-1.0.0.jar \
  --spring.batch.job.enabled=true \
  --spring.batch.job.names=migrationJob \
  run.id=$(date +%s) > migration.log 2>&1 &

# 프로세스 확인
ps -ef | grep crm-mig

# 로그 확인
tail -f migration.log
```

### 3.5 스크립트 작성

**run-migration.sh 생성:**

```bash
#!/bin/bash

# 환경 변수 설정
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk
export PATH=$JAVA_HOME/bin:$PATH

# 실행 디렉토리
CD_DIR=/opt/crm-mig
JAR_FILE=crm-mig-1.0.0.jar
LOG_FILE=migration_$(date +%Y%m%d_%H%M%S).log

# 디렉토리 이동
cd $CD_DIR

# 실행
echo "========================================" | tee -a $LOG_FILE
echo "CRM Migration Batch 시작: $(date)" | tee -a $LOG_FILE
echo "========================================" | tee -a $LOG_FILE

java -jar $JAR_FILE \
  --spring.batch.job.enabled=true \
  --spring.batch.job.names=migrationJob \
  run.id=$(date +%s) 2>&1 | tee -a $LOG_FILE

EXIT_CODE=$?

echo "========================================" | tee -a $LOG_FILE
echo "CRM Migration Batch 종료: $(date)" | tee -a $LOG_FILE
echo "Exit Code: $EXIT_CODE" | tee -a $LOG_FILE
echo "========================================" | tee -a $LOG_FILE

exit $EXIT_CODE
```

**실행 권한 부여 및 실행:**

```bash
chmod +x run-migration.sh
./run-migration.sh
```

### 3.6 Cron으로 스케줄링

```bash
# crontab 편집
crontab -e

# 매일 새벽 2시 실행
0 2 * * * /opt/crm-mig/run-migration.sh

# cron 확인
crontab -l
```

---

## 4. 실행 전 체크리스트

### 4.1 데이터베이스

- [ ] PostgreSQL 서버 실행 확인
- [ ] migration_db 데이터베이스 생성
- [ ] migration_config 테이블 생성 및 데이터 입력
- [ ] 대상 테이블에 백업 컬럼(`_bak`) 생성
- [ ] Spring Batch 메타데이터 테이블 자동 생성 확인

### 4.2 설정 파일

- [ ] application.yml 데이터베이스 연결 정보 확인
- [ ] chunk-size 적절히 설정 (기본 1000)
- [ ] schema-name 확인 (기본 public)
- [ ] 로그 레벨 확인 (DEBUG 권장)

### 4.3 소스 코드

- [ ] Lombok 의존성 확인 (pom.xml)
- [ ] TargetRecordEntity 생성자 확인 (NPE 수정 반영)
- [ ] SafeDBUtil 구현 확인
- [ ] Maven 빌드 성공 확인

### 4.4 실행 환경

**STS:**
- [ ] Java 1.8 이상
- [ ] Maven 설정 확인
- [ ] Lombok 플러그인 설치

**Linux:**
- [ ] Java 1.8 이상 설치
- [ ] 네트워크: DB 서버 접근 가능
- [ ] 디스크: 충분한 공간
- [ ] 메모리: 최소 512MB (권장 1GB)

---

## 5. 문제 해결

### 5.1 NPE (NullPointerException) 발생

**증상:**
```
entity.getOriginalValues().put(columnName, originalValue)
NullPointerException
```

**원인:** TargetRecordEntity의 Map 필드가 초기화되지 않음

**해결:**
```java
// TargetRecordEntity에 명시적 생성자 확인
public TargetRecordEntity() {
    this.pkValues = new HashMap<>();
    this.originalValues = new HashMap<>();  // ← 필수!
    this.encryptedValues = new HashMap<>();
}
```

### 5.2 Lombok 관련 오류

**증상:**
```
cannot find symbol: method getOriginalValues()
```

**해결:**
```
1. STS에서: Help → Install New Software
2. Lombok 플러그인 설치
3. 또는 STS 재시작
4. Maven → Update Project
```

### 5.3 데이터베이스 연결 오류

**증상:**
```
Connection refused: connect
```

**해결:**
```
1. PostgreSQL 서버 실행 확인
2. 포트 확인 (기본 5432)
3. 방화벽 설정 확인
4. application.yml 연결 정보 확인
```

### 5.4 Job 중복 실행 오류

**증상:**
```
JobInstanceAlreadyCompleteException
```

**해결:**
```
1. run.id를 다른 값으로 변경
2. 또는 ManualJobRerun 사용 (자동으로 다른 timestamp)
3. 또는 배치 메타데이터 삭제 (위 2.4 참고)
```

### 5.5 백업 컬럼 없음 오류

**증상:**
```
ERROR: column "phone_bak" of relation "customer" does not exist
```

**해결:**
```sql
-- 백업 컬럼 생성 (소문자)
ALTER TABLE customer ADD COLUMN phone_bak VARCHAR(20);
```

---

## 6. 실행 결과 확인

### 6.1 Spring Batch 메타데이터

```sql
-- Job 실행 이력
SELECT 
    ji.job_instance_id,
    ji.job_name,
    je.status,
    je.start_time,
    je.end_time,
    je.exit_code
FROM batch_job_instance ji
LEFT JOIN batch_job_execution je ON ji.job_instance_id = je.job_instance_id
WHERE ji.job_name = 'migrationJob'
ORDER BY je.job_execution_id DESC
LIMIT 10;

-- Step별 실행 결과 (read_count 확인)
SELECT 
    step_name,
    status,
    read_count,
    write_count,
    commit_count,
    rollback_count,
    start_time,
    end_time
FROM batch_step_execution
WHERE job_execution_id = (
    SELECT MAX(job_execution_id) FROM batch_job_execution
)
ORDER BY step_execution_id;
```

### 6.2 migration_config 상태

```sql
-- 처리 상태 확인
SELECT 
    target_table_name,
    target_column_name,
    status,
    priority
FROM migration_config
ORDER BY priority, target_table_name;
```

### 6.3 대상 테이블 확인

```sql
-- 백업 및 암호화 확인
SELECT 
    customer_id,
    phone_bak,      -- 원본 백업
    phone,          -- 암호화된 값
    LENGTH(phone) as encrypted_length
FROM customer
LIMIT 10;
```

---

## 7. 성능 튜닝

### 7.1 Chunk Size 조정

```yaml
migration:
  chunk-size: 5000  # 환경에 따라 조정 (기본 1000)
```

**권장값:**
- 로컬/개발: 100 ~ 1,000
- 운영: 1,000 ~ 5,000

### 7.2 JVM 메모리 설정

```bash
java -Xms512m -Xmx2048m -jar crm-mig-1.0.0.jar ...
```

### 7.3 Connection Pool 설정

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
```

---

## 📚 추가 참고 자료

- [README.md](README.md) - 프로젝트 전체 개요
- [EXECUTION_FLOW.md](EXECUTION_FLOW.md) - 상세 실행 흐름
- [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) - 프로젝트 구조
- [STS_IMPORT_GUIDE.md](STS_IMPORT_GUIDE.md) - STS 임포트 가이드

---

**작성일:** 2026-01-15  
**버전:** 1.0 (NPE 수정 및 Lombok 적용 반영)
