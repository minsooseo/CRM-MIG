# CRM 테이블 마이그레이션 프로젝트 (SafeDB 적용)

Spring Batch를 사용하여 테이블의 컬럼에 SafeDB 암호화를 적용하는 프로젝트입니다.

## 🚀 빠른 시작 (로컬 환경 구성)

로컬 PC에 개발 환경을 구성하려면 다음 문서를 참조하세요:

- **[QUICK_START.md](QUICK_START.md)** - 빠른 시작 가이드 (권장)
- **[SETUP_GUIDE.md](SETUP_GUIDE.md)** - 상세한 설치 가이드
- **[ENVIRONMENT_SETUP_SUMMARY.md](ENVIRONMENT_SETUP_SUMMARY.md)** - 환경 구성 요약

## 🎯 실행 가이드

프로젝트 실행 방법은 다음 문서를 참조하세요:

- **[EXECUTION_GUIDE.md](EXECUTION_GUIDE.md)** - 통합 실행 가이드 (STS + Linux) ⭐ 권장
- **[PROFILE_GUIDE.md](PROFILE_GUIDE.md)** - Profile 설정 가이드 (local/dev/prod) ⭐ 필독
- **[STS_MANUAL_EXECUTION_GUIDE.md](STS_MANUAL_EXECUTION_GUIDE.md)** - STS 수동 실행
- **[STS_JOB_RERUN_GUIDE.md](STS_JOB_RERUN_GUIDE.md)** - STS Job 재실행
- **[LINUX_EXECUTION_GUIDE.md](LINUX_EXECUTION_GUIDE.md)** - Linux 서버 실행

## 개요

`migration_config` 테이블에서 마이그레이션 설정을 읽어, 대상 테이블의 PK를 조회한 후 SafeDB를 적용하여 UPDATE하는 배치 프로그램입니다.

**주요 특징:**
- ✅ 테이블별 Step 동적 생성: 각 테이블마다 독립적인 Step 생성 (순차 실행)
- ✅ 정확한 read_count: Step별로 실제 처리한 레코드 수를 정확하게 집계
- ✅ 복합키 지원: 단일키 및 복합키 모두 지원
- ✅ 자동 상태 관리: 처리 완료 후 `status`를 'COMPLETE'로 자동 업데이트
- ✅ 백업 컬럼 자동 생성: 원본 데이터 손실 방지 (_bak 소문자)
- ✅ 스키마 설정: application.yml에서 스키마명 설정 가능

## 주요 구성 요소

### 1. 의존성 (pom.xml)
- Spring Boot 2.3.2
- Spring Batch 4.2.x
- MyBatis 1.3.5
- PostgreSQL (JDBC Driver)
- Lombok (보일러플레이트 코드 제거)
- SafeDB (실제 라이브러리로 교체 필요)

### 2. 데이터베이스 설정 (application.yml)
- **단일 데이터베이스**: 마이그레이션 설정, 대상 테이블, 배치 메타데이터가 모두 같은 DB에 있음
- **데이터소스**: 단일 PostgreSQL 데이터소스 사용
- **스키마 설정**: `migration.schema-name`으로 스키마명 지정

### 3. 배치 구성 요소

#### ItemReader (TableRecordReader)
- **실제 테이블 레코드를 직접 읽음** ⭐
- 각 Step마다 특정 테이블의 레코드를 직접 조회
- 동적 PK 조회 및 여러 컬럼의 값을 한 번에 읽어옴
- `TargetRecordEntity` 객체로 반환 (PK + 여러 컬럼 값 포함)

#### ItemProcessor (EncryptionProcessor)
- `TargetRecordEntity`를 받아서 각 컬럼 값을 SafeDB 암호화
- 원본 값과 암호화된 값을 `encryptedValues` Map에 저장
- 복합키 지원

#### ItemWriter (EncryptionWriter)
- SafeDB가 적용된 값으로 대상 테이블을 UPDATE
- 원본 값은 `_bak` 컬럼에 백업 (소문자)
- 여러 컬럼을 한 번의 UPDATE로 처리
- **MyBatis BATCH 모드**: DB 왕복 횟수 대폭 감소 (1000건당 10~50회) ⚡

#### StepExecutionListener (MigrationStatusListener)
- Step 완료 시 `migration_config`의 `status`를 'COMPLETE'로 업데이트 (한 번만!)
- Step 실패 시 status 업데이트 안 함 → 재실행 가능

### 4. Job 구성 (테이블별 Step 동적 생성)
- **createBackupColumnStep**: 백업 컬럼 자동 생성 (Tasklet)
  - `migration_config`에서 활성 설정 조회
  - 각 컬럼에 대해 `_bak` 백업 컬럼 생성 (소문자)
- **encryptionStep_테이블명**: 테이블별 암호화 처리 Step
  - `migration_config`에서 테이블 목록을 읽어 동적으로 Step 생성
  - 같은 테이블의 여러 컬럼을 하나의 Step에서 함께 처리
  - 순차 실행 (Step 개수 = 테이블 개수)

## 설정 방법

### 1. 마이그레이션 설정 테이블 생성 (PostgreSQL)

```sql
CREATE TABLE migration_config (
  target_table_name VARCHAR(100) PRIMARY KEY,
  target_column_name VARCHAR(500) NOT NULL,  -- 쉼표로 구분하여 여러 컬럼 지정 가능
  status VARCHAR(20) DEFAULT 'ACTIVE',
  priority INTEGER DEFAULT 0
);

COMMENT ON COLUMN migration_config.target_table_name IS '대상 테이블명 (PRIMARY KEY)';
COMMENT ON COLUMN migration_config.target_column_name IS '대상 컬럼명 (SafeDB 적용할 컬럼, 쉼표로 구분하여 여러 컬럼 지정 가능)';
COMMENT ON COLUMN migration_config.status IS '처리 상태 (ACTIVE, INACTIVE, COMPLETE)';
COMMENT ON COLUMN migration_config.priority IS '처리 우선순위 (낮을수록 먼저 실행)';

-- 주의: pk_column_name은 저장하지 않습니다.
-- Primary Key는 INFORMATION_SCHEMA에서 자동으로 조회됩니다.

-- 예시 데이터
INSERT INTO migration_config 
  (target_table_name, target_column_name, status, priority)
VALUES
  -- 단일 컬럼 처리
  ('customer', 'phone', 'ACTIVE', 1),
  -- 여러 컬럼 동시 처리 (쉼표로 구분)
  ('order', 'recipient_phone,recipient_name', 'ACTIVE', 2);
  
-- 주의: 
-- 1. target_table_name이 PRIMARY KEY이므로 하나의 테이블당 하나의 설정만 가능합니다.
-- 2. 처리 완료 후 status가 'COMPLETE'로 자동 업데이트되어 재실행 시 제외됩니다.
```

### 2. application.yml 설정

```yaml
spring:
  # 단일 데이터소스 설정
  datasource:
    url: jdbc:postgresql://localhost:5432/migration_db
    username: postgres
    password: your_password
    driver-class-name: org.postgresql.Driver

  mybatis:
    mapper-locations: classpath:mapper/*.xml
    type-aliases-package: com.kt.yaap.mig_batch.model
    configuration:
      map-underscore-to-camel-case: true
      default-fetch-size: 1000
      default-statement-timeout: 30
      log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl  # MyBatis 쿼리 로그

# 로깅 설정
logging:
  level:
    root: INFO
    com.kt.yaap.mig_batch: DEBUG
    org.springframework.batch: DEBUG
    com.kt.yaap.mig_batch.mapper: DEBUG  # MyBatis SQL 로그
    org.apache.ibatis: TRACE  # 바인딩 파라미터 로그

migration:
  chunk-size: 1000  # Chunk 처리 단위
  config-table: migration_config  # 마이그레이션 설정 테이블명
  schema-name: public  # 데이터베이스 스키마명
```

### 3. SafeDB 설정

`SafeDBUtil.java` 파일에서 실제 SafeDB 라이브러리를 사용하도록 수정:

```java
public static String encrypt(String plainText) {
    // 실제 SafeDB 라이브러리 사용
    SafeDB sdb = SafeDBFactory.getInstance();
    return sdb.encrypt(plainText);
}
```

`pom.xml`에 SafeDB 의존성 추가:

```xml
<dependency>
    <groupId>com.safedb</groupId>
    <artifactId>safedb-client</artifactId>
    <version>X.X.X</version>
</dependency>
```

## 실행 방법

### 1. Maven 빌드
```bash
mvn clean package
```

### 2. 환경별 실행

#### 로컬 환경 (기본값)
```bash
# Profile 미지정 시 자동으로 local 사용
java -jar target/crm-mig-1.0.0.jar

# 또는 명시적으로 지정
java -jar target/crm-mig-1.0.0.jar --spring.profiles.active=local
```

#### 개발 서버
```bash
java -jar target/crm-mig-1.0.0.jar \
  --spring.profiles.active=dev \
  --spring.batch.job.enabled=true \
  --spring.batch.job.names=migrationJob
```

#### 운영 서버
```bash
# 환경변수 설정
export DB_PASSWORD=prod_password

java -jar target/crm-mig-1.0.0.jar \
  --spring.profiles.active=prod \
  --spring.batch.job.enabled=true \
  --spring.batch.job.names=migrationJob
```

#### 디버깅 모드 (문제 해결용)
```bash
# 주의: 성능이 매우 느려집니다!
java -jar target/crm-mig-1.0.0.jar --spring.profiles.active=debug
```

### 3. Profile별 특징

| Profile | 용도 | Chunk Size | 로그 레벨 | 성능 |
|---------|------|------------|-----------|------|
| **local** | 로컬 개발 | 1,000 | INFO/WARN | ⭐⭐⭐⭐ 빠름 |
| **dev** | 개발 서버 | 3,000 | DEBUG/INFO | ⭐⭐⭐ 보통 |
| **prod** | 운영 서버 | 5,000 | INFO/WARN | ⭐⭐⭐⭐⭐ 최고 |
| **debug** | 디버깅 | 100 | DEBUG | ⭐ 매우 느림 |

### 4. STS에서 Profile 설정

```
Run → Run Configurations...
→ Spring Boot App
→ Profile: local (또는 dev, prod, debug)
→ Apply → Run
```

## 처리 흐름

1. **Step 1: 백업 컬럼 생성**
   - `migration_config` 테이블에서 활성화된 설정 조회 (status = 'ACTIVE' 또는 NULL)
   - 각 컬럼에 대해 백업 컬럼 자동 생성 (`{컬럼명}_bak`, 소문자)

2. **Step 2~N: 테이블별 암호화 (순차 실행)**
   - 각 테이블별로 독립적인 Step 실행 (encryptionStep_테이블명)
   - **PK 동적 조회**: INFORMATION_SCHEMA에서 대상 테이블의 Primary Key 자동 조회 (복합키 지원)
   - **데이터 읽기 (Reader)**: 대상 테이블에서 PK와 모든 대상 컬럼 값을 레코드 단위로 조회
   - **SafeDB 적용 (Processor)**: 각 컬럼 값에 SafeDB 암호화 적용
   - **UPDATE 수행 (Writer)**: 백업 컬럼에 원본 저장 + 암호화된 값으로 업데이트 (MyBatis BATCH 모드)
   - **상태 업데이트 (Listener)**: Step 완료 시 `status`를 'COMPLETE'로 업데이트 (한 번만!)

## 주의사항

1. **대용량 데이터 처리**
   - `chunk-size`를 적절히 조정
   - 대상 테이블이 큰 경우 WHERE 조건으로 분할 처리 고려

2. **트랜잭션 관리**
   - 각 Step별로 Chunk 단위 트랜잭션 분리
   - 실패 시 해당 Chunk만 롤백

3. **백업 컬럼 자동 생성**
   - 마이그레이션 전처리 단계에서 백업 컬럼(`컬럼명_bak`, 소문자)을 자동으로 생성
   - 원본 컬럼과 동일한 데이터 타입으로 생성
   - 이미 존재하는 경우 건너뜀
   - PostgreSQL은 컬럼명을 소문자로 저장하므로 `_bak` 소문자 사용

4. **에러 처리**
   - SafeDB 적용 실패 시 로깅 및 별도 처리
   - 실패한 레코드는 재처리 가능하도록 설계
   - status 업데이트 실패해도 데이터 처리는 성공 처리 (이미 암호화 완료)

5. **성능 최적화**
   - **MyBatis BATCH 모드**: Writer에서 DB 왕복 횟수 대폭 감소
   - 1000건 처리 시: 1000번 왕복 → 10~50번 왕복 (약 50배 빠름!) ⚡
   - 대상 테이블에 적절한 인덱스 설정
   - 테이블별 Step 순차 실행
   - 여러 컬럼을 한 번의 UPDATE로 처리

6. **상태 관리**
   - 처리 완료된 테이블의 `status`를 'COMPLETE'로 자동 업데이트
   - 'COMPLETE' 상태인 설정은 다음 실행 시 자동 제외
   - 데이터 무결성 보장

7. **read_count 정확성**
   - 각 Step의 read_count는 실제 처리한 레코드 수를 정확하게 반영
   - 예: TB_USER 테이블 150건 → encryptionStep_TB_USER의 read_count = 150

## 파일 구조

```
src/
├── main/
│   ├── java/com/kt/yaap/mig_batch/
│   │   ├── CrmMigrationApplication.java
│   │   ├── config/
│   │   │   ├── BatchConfig.java              # Step 설정
│   │   │   ├── MigrationJobConfig.java       # Job 설정 (테이블별 Step 동적 생성)
│   │   │   ├── MigrationProperties.java      # 설정 Properties
│   │   │   ├── DatabaseConfig.java           # 데이터소스 설정
│   │   │   ├── MyBatisConfig.java            # MyBatis 설정
│   │   │   └── SafeDBConfig.java             # SafeDB 설정
│   │   ├── mapper/
│   │   │   ├── MigrationConfigMapper.java    # 설정 테이블 Mapper
│   │   │   └── TargetTableMapper.java        # 대상 테이블 Mapper
│   │   ├── batch/
│   │   │   ├── TableRecordReader.java        # 실제 테이블 레코드 읽기
│   │   │   ├── EncryptionProcessor.java      # SafeDB 암호화 처리
│   │   │   └── EncryptionWriter.java         # UPDATE 수행 (MyBatis BATCH 모드)
│   │   ├── listener/
│   │   │   └── MigrationStatusListener.java  # Step 완료 시 status 업데이트
│   │   ├── model/
│   │   │   ├── MigrationConfigEntity.java    # 설정 엔티티
│   │   │   ├── TargetRecordEntity.java       # 레코드 엔티티 (PK + 여러 컬럼)
│   │   │   ├── SourceEntity.java             # 소스 엔티티 (레거시)
│   │   │   └── TargetEntity.java             # 타겟 엔티티 (레거시)
│   │   ├── service/
│   │   │   └── BackupColumnService.java      # 백업 컬럼 자동 생성 서비스
│   │   ├── util/
│   │   │   └── SafeDBUtil.java               # SafeDB 유틸리티
│   │   └── scheduler/
│   │       └── MigrationScheduler.java       # 배치 스케줄러
│   └── resources/
│       ├── mapper/
│       │   ├── MigrationConfigMapper.xml     # 설정 테이블 쿼리
│       │   └── TargetTableMapper.xml         # 대상 테이블 쿼리
│       └── application.yml
└── test/
    └── java/com/kt/yaap/mig_batch/
        ├── ManualJobRunner.java              # 수동 실행 테스트
        └── ManualJobRerun.java               # 재실행 테스트
```

## 주요 모델 설명

### MigrationConfigEntity
- **마이그레이션 설정 정보** (Lombok `@Data`, `@NoArgsConstructor` 적용)
- `targetTableName`: 업데이트할 테이블명 (PRIMARY KEY)
- `targetColumnName`: SafeDB 적용할 컬럼명 (쉼표로 구분 가능)
- `pkColumnName`: Primary Key 컬럼명 (동적 조회용, DB 저장 안함)
- PK는 INFORMATION_SCHEMA에서 동적으로 조회

### TargetRecordEntity
- **대상 테이블의 실제 레코드 정보** (Lombok `@Data` 적용)
- `tableName`: 테이블명
- `pkColumnNames`: PK 컬럼명 리스트 (복합키 지원)
- `pkValues`: PK 값 Map
- `targetColumnNames`: 암호화 대상 컬럼명 리스트
- `originalValues`: 원본 컬럼 값 Map
- `encryptedValues`: 암호화된 값 Map
- 명시적 생성자로 모든 Map 필드 초기화 (NPE 방지)
- `getPkDisplay()`: PK 값을 문자열로 표시 (로깅용)

## 참고 자료

- [Spring Batch Documentation](https://docs.spring.io/spring-batch/docs/current/reference/html/)
- [Spring Boot Batch](https://spring.io/guides/gs/batch-processing/)
- [LINUX_EXECUTION_GUIDE.md](LINUX_EXECUTION_GUIDE.md) - Linux 서버에서 수동 실행 가이드