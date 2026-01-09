# Linux 서버 수동 실행 가이드

본 문서는 CRM 마이그레이션 배치 프로그램을 Linux 서버에서 수동으로 실행하는 방법을 안내합니다.

## 1단계: JAR 빌드 (Windows에서)

```bash
# 프로젝트 루트 디렉토리에서 실행
mvn clean package -DskipTests

# 빌드 결과: target/crm-mig-1.0.0.jar
```

## 2단계: Linux 서버로 파일 업로드

### 업로드할 파일
- `target/crm-mig-1.0.0.jar` (실행 파일)
- `src/main/resources/application.yml` (선택사항, 외부 설정)
- SafeDB 라이브러리 JAR (있는 경우)

### SCP로 업로드 예시

```bash
scp target/crm-mig-1.0.0.jar user@linux-server:/opt/crm-mig/
scp src/main/resources/application.yml user@linux-server:/opt/crm-mig/config/
```

## 3단계: Linux 서버에서 실행

### 기본 실행 (내장된 application.yml 사용)

```bash
cd /opt/crm-mig

# 기본 실행 (JobParameters 자동 생성)
java -jar crm-mig-1.0.0.jar \
  --spring.batch.job.names=migrationJob \
  run.id=$(date +%s)
```

### 외부 설정 파일 사용

```bash
# 외부 application.yml 사용
java -jar crm-mig-1.0.0.jar \
  --spring.config.location=file:./config/application.yml \
  --spring.batch.job.names=migrationJob \
  run.id=$(date +%s)
```

### DB 연결 정보 직접 지정

```bash
java -jar crm-mig-1.0.0.jar \
  --spring.datasource.url=jdbc:postgresql://db-server:5432/migration_db \
  --spring.datasource.username=postgres \
  --spring.datasource.password=your_password \
  --spring.batch.job.names=migrationJob \
  run.id=$(date +%s)
```

## 4단계: 실행 스크립트 작성 (권장)

`/opt/crm-mig/run_migration.sh` 파일 생성:

```bash
#!/bin/bash

# 설정
APP_HOME="/opt/crm-mig"
JAR_FILE="crm-mig-1.0.0.jar"
CONFIG_FILE="${APP_HOME}/config/application.yml"
LOG_DIR="${APP_HOME}/logs"
LOG_FILE="${LOG_DIR}/migration_$(date +%Y%m%d_%H%M%S).log"

# 로그 디렉토리 생성
mkdir -p ${LOG_DIR}

# Java 실행
cd ${APP_HOME}

echo "=== CRM Migration Batch Start ===" | tee -a ${LOG_FILE}
echo "Start Time: $(date)" | tee -a ${LOG_FILE}

java -jar ${JAR_FILE} \
  --spring.config.location=file:${CONFIG_FILE} \
  --spring.batch.job.names=migrationJob \
  run.id=$(date +%s) \
  2>&1 | tee -a ${LOG_FILE}

EXIT_CODE=$?

echo "End Time: $(date)" | tee -a ${LOG_FILE}
echo "Exit Code: ${EXIT_CODE}" | tee -a ${LOG_FILE}
echo "=== CRM Migration Batch End ===" | tee -a ${LOG_FILE}

exit ${EXIT_CODE}
```

### 실행 권한 부여 및 실행

```bash
chmod +x /opt/crm-mig/run_migration.sh
/opt/crm-mig/run_migration.sh
```

## 5단계: 실행 확인

### 프로세스 확인

```bash
# 실행 중인 프로세스 확인
ps aux | grep crm-mig
```

### 로그 확인

```bash
# 실시간 로그 확인
tail -f /opt/crm-mig/logs/migration_*.log

# 최근 로그 파일 확인
ls -lt /opt/crm-mig/logs/
```

### 배치 메타데이터 확인 (DB)

```sql
-- 최근 Job 실행 이력 조회
SELECT * FROM batch_job_execution 
ORDER BY create_time DESC 
LIMIT 5;

-- 특정 Job의 Step 실행 상태 조회
SELECT 
    je.job_execution_id,
    je.status as job_status,
    je.start_time,
    je.end_time,
    se.step_name,
    se.status as step_status,
    se.read_count,
    se.write_count,
    se.commit_count
FROM batch_job_execution je
JOIN batch_step_execution se ON je.job_execution_id = se.job_execution_id
ORDER BY je.create_time DESC
LIMIT 10;
```

## 주의사항

### 1. JobParameters 필수
매번 실행 시 고유한 파라미터가 필요합니다.

```bash
# run.id에 현재 타임스탬프 사용
run.id=$(date +%s)

# 같은 파라미터로 재실행하면 오류 발생
# Error: A job instance already exists and is complete
```

### 2. DB 연결 확인
Linux 서버에서 PostgreSQL 접근 가능해야 합니다.

```bash
# DB 연결 테스트
psql -h db-server -U postgres -d migration_db

# 방화벽 확인
telnet db-server 5432
```

PostgreSQL 설정 확인:
- `postgresql.conf`: `listen_addresses = '*'`
- `pg_hba.conf`: 해당 IP 허용 설정

### 3. Java 버전
JDK 1.7 이상 필요 (pom.xml 설정 기준)

```bash
# Java 버전 확인
java -version

# JAVA_HOME 설정 확인
echo $JAVA_HOME
```

### 4. 메모리 설정 (선택사항)
대용량 데이터 처리 시 힙 메모리 조정:

```bash
java -Xms512m -Xmx2048m -jar crm-mig-1.0.0.jar \
  --spring.batch.job.names=migrationJob \
  run.id=$(date +%s)
```

### 5. 재실행 시 동작
이미 'COMPLETE' 상태인 테이블은 자동으로 제외됩니다.

```sql
-- COMPLETE 상태 확인
SELECT target_table_name, status 
FROM migration_config 
WHERE status = 'COMPLETE';
```

## 트러블슈팅

### 문제: Job instance already exists

**증상**
```
A job instance already exists and is complete for parameters={run.id=1234567890}
```

**해결방법**
```bash
# 방법 1: run.id를 나노초로 변경
java -jar crm-mig-1.0.0.jar \
  --spring.batch.job.names=migrationJob \
  run.id=$(date +%s%N)

# 방법 2: 추가 파라미터 사용
java -jar crm-mig-1.0.0.jar \
  --spring.batch.job.names=migrationJob \
  run.id=$(date +%s) \
  execution.date=$(date +%Y%m%d%H%M%S)
```

### 문제: DB 연결 실패

**증상**
```
Connection refused: connect
```

**해결방법**
```bash
# 1. DB 서버 접근 가능 여부 확인
ping db-server

# 2. 포트 접근 가능 여부 확인
telnet db-server 5432

# 3. PostgreSQL 설정 확인
sudo vi /etc/postgresql/*/main/postgresql.conf
# listen_addresses = '*' 또는 특정 IP

sudo vi /etc/postgresql/*/main/pg_hba.conf
# host all all 0.0.0.0/0 md5 추가

# 4. PostgreSQL 재시작
sudo systemctl restart postgresql
```

### 문제: ClassNotFoundException

**증상**
```
java.lang.ClassNotFoundException: org.postgresql.Driver
```

**해결방법**
```bash
# JAR 파일에 의존성이 포함되어 있는지 확인
jar tf crm-mig-1.0.0.jar | grep postgresql

# spring-boot-maven-plugin이 정상 동작하는지 확인
mvn clean package -DskipTests
```

### 문제: OutOfMemoryError

**증상**
```
java.lang.OutOfMemoryError: Java heap space
```

**해결방법**
```bash
# 힙 메모리 증가
java -Xms1024m -Xmx4096m -jar crm-mig-1.0.0.jar \
  --spring.batch.job.names=migrationJob \
  run.id=$(date +%s)

# chunk-size 조정 (application.yml)
migration:
  chunk-size: 500  # 1000 → 500으로 감소
```

## 디렉토리 구조 예시

```
/opt/crm-mig/
├── crm-mig-1.0.0.jar           # 실행 JAR 파일
├── config/
│   └── application.yml         # 외부 설정 파일
├── logs/
│   ├── migration_20260108_140000.log
│   └── migration_20260108_150000.log
├── lib/                        # SafeDB 등 외부 라이브러리 (선택)
│   └── safedb-client.jar
└── run_migration.sh            # 실행 스크립트
```

## 배치 실행 플로우

```
1. createBackupColumnStep (Tasklet)
   ↓
   - migration_config에서 활성 설정 조회
   - 각 테이블/컬럼에 대해 _BAK 컬럼 생성
   - 이미 존재하는 경우 스킵

2. migrationStep (Chunk-oriented)
   ↓
   [Reader] MigrationItemReader
   - migration_config에서 status != 'COMPLETE' 조회
   
   [Processor] MigrationItemProcessor
   - 대상 테이블에서 데이터 조회
   - SafeDB 암호화 적용
   
   [Writer] MigrationItemWriter
   - 원본 데이터를 _BAK 컬럼에 백업
   - 암호화된 데이터를 원본 컬럼에 업데이트
   - migration_config의 status를 'COMPLETE'로 업데이트
```

## 참고사항

- 배치 실행 이력은 `batch_job_execution`, `batch_step_execution` 테이블에 저장됩니다.
- 실행 실패 시 재실행하면 마지막 실패 지점부터 재개됩니다 (Spring Batch의 Restart 기능).
- `migration_config`의 `status`가 'COMPLETE'인 항목은 자동으로 제외되어 중복 실행을 방지합니다.
- 복합키(Composite Key) 테이블도 자동으로 지원됩니다.
- 한 번에 여러 컬럼을 암호화하려면 `target_column_name`에 쉼표로 구분하여 입력하세요 (예: `phone,email,address`).
