# CRM 테이블 마이그레이션 프로젝트 (SafeDB 적용)

Spring Batch를 사용하여 테이블의 컬럼에 SafeDB 암호화를 적용하는 프로젝트입니다.

## 개요

소스 테이블에서 마이그레이션 설정을 읽어, 대상 테이블의 PK를 조회한 후 SafeDB를 적용하여 UPDATE하는 배치 프로그램입니다.

## 주요 구성 요소

### 1. 의존성 (pom.xml)
- Spring Boot 2.3.2
- Spring Batch
- MyBatis 1.3.5 (JDK 1.7 호환)
- PostgreSQL 9.4 (JDBC Driver 42.2.20)
- SafeDB (실제 라이브러리로 교체 필요)

### 2. 데이터베이스 설정 (application.yml)
- **소스 데이터베이스**: 마이그레이션 설정 테이블이 있는 PostgreSQL DB
- **타겟 데이터베이스**: 실제 업데이트 대상 테이블이 있는 PostgreSQL DB
- **배치 메타데이터 DB**: Spring Batch 실행 이력을 저장하는 PostgreSQL DB

### 3. 배치 구성 요소

#### ItemReader (MigrationItemReader)
- 마이그레이션 설정 테이블(`migration_config`)에서 설정 정보를 읽어옴
- 각 설정은 대상 테이블명, 컬럼명, PK 컬럼명 등을 포함
- MyBatis Mapper를 사용하여 설정 조회

#### ItemProcessor (MigrationItemProcessor)
- 설정 정보를 기반으로 대상 테이블에서 PK와 대상 컬럼 값을 조회 (MyBatis 사용)
- 각 값에 SafeDB 암호화를 적용
- `MigrationConfigEntity` → `List<TargetUpdateEntity>` 변환

#### ItemWriter (MigrationItemWriter)
- SafeDB가 적용된 값으로 대상 테이블을 UPDATE (MyBatis 사용)
- 테이블별, 컬럼별로 그룹화하여 배치 업데이트 수행

### 4. Job 구성
- **createBackupColumnStep**: 백업 컬럼 자동 생성 (Tasklet)
- **migrationStep**: 암호화 대상 처리 (Reader → Processor → Writer)
  - 설정 읽기 → PK 조회 → 데이터 조회 → SafeDB 적용 → 백업 → UPDATE
- **postMigrationStep**: 마이그레이션 후처리 (예: 검증, 통계)

## 설정 방법

### 1. 마이그레이션 설정 테이블 생성 (PostgreSQL)

```sql
CREATE TABLE migration_config (
  config_id BIGSERIAL PRIMARY KEY,
  target_table_name VARCHAR(100) NOT NULL,
  target_column_name VARCHAR(500) NOT NULL,  -- 쉼표로 구분하여 여러 컬럼 지정 가능
  where_condition VARCHAR(500),
  status VARCHAR(20) DEFAULT 'ACTIVE',
  priority INTEGER DEFAULT 0
);

COMMENT ON COLUMN migration_config.target_table_name IS '대상 테이블명';
COMMENT ON COLUMN migration_config.target_column_name IS '대상 컬럼명 (SafeDB 적용할 컬럼, 쉼표로 구분하여 여러 컬럼 지정 가능)';
COMMENT ON COLUMN migration_config.where_condition IS 'WHERE 조건 (선택사항)';
COMMENT ON COLUMN migration_config.status IS '처리 상태';
COMMENT ON COLUMN migration_config.priority IS '처리 우선순위';

-- 주의: pk_column_name은 저장하지 않습니다.
-- Primary Key는 INFORMATION_SCHEMA에서 자동으로 조회됩니다.

-- 예시 데이터
INSERT INTO migration_config 
  (target_table_name, target_column_name, where_condition, status, priority)
VALUES
  -- 단일 컬럼 처리
  ('customer', 'phone', NULL, 'ACTIVE', 1),
  -- 여러 컬럼 동시 처리 (쉼표로 구분)
  ('customer', 'email,phone,address', 'status = ''ACTIVE''', 'ACTIVE', 1),
  ('order', 'recipient_phone,recipient_name', NULL, 'ACTIVE', 2);
```

### 2. application.yml 설정

```yaml
spring:
  datasource:
    source:
      jdbc-url: jdbc:postgresql://localhost:5432/source_db
      username: postgres
      password: your_password
      driver-class-name: org.postgresql.Driver
    target:
      jdbc-url: jdbc:postgresql://localhost:5432/target_db
      username: postgres
      password: your_password
      driver-class-name: org.postgresql.Driver
    batch:
      jdbc-url: jdbc:postgresql://localhost:5432/batch_db
      username: postgres
      password: your_password
      driver-class-name: org.postgresql.Driver

  mybatis:
    mapper-locations: classpath:mapper/*.xml
    type-aliases-package: com.example.crmmig.model
    configuration:
      map-underscore-to-camel-case: true
      default-fetch-size: 1000
      default-statement-timeout: 30

migration:
  chunk-size: 1000  # 설정 테이블 읽기 기준 chunk 크기
  config-table: migration_config  # 마이그레이션 설정 테이블명
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

### 2. 애플리케이션 실행
```bash
java -jar target/crm-mig-1.0.0.jar
```

### 3. Job 실행 옵션

#### 방법 1: application.yml에서 자동 실행
```yaml
spring:
  batch:
    job:
      enabled: true
      names: migrationJob
```

#### 방법 2: Command Line에서 실행
```bash
java -jar target/crm-mig-1.0.0.jar --spring.batch.job.enabled=true --spring.batch.job.names=migrationJob
```

#### 방법 3: 스케줄러 사용
- `MigrationScheduler`의 cron 표현식 수정하여 원하는 시간에 실행

## 처리 흐름

1. **설정 읽기**: `migration_config` 테이블에서 활성화된 설정 조회 (target_table_name, target_column_name)
2. **PK 동적 조회**: INFORMATION_SCHEMA에서 대상 테이블의 Primary Key 컬럼명 자동 조회
3. **데이터 조회**: 대상 테이블에서 PK와 대상 컬럼 값 조회
4. **SafeDB 적용**: 각 값에 SafeDB 암호화 적용
5. **백업 수행**: 원본 값을 대상컬럼명_BAK 컬럼에 저장 (예: phone → phone_BAK)
6. **UPDATE 수행**: 암호화된 값으로 대상 테이블 업데이트

## 주의사항

1. **대용량 데이터 처리**
   - `chunk-size`를 적절히 조정
   - 대상 테이블이 큰 경우 WHERE 조건으로 분할 처리 고려

2. **트랜잭션 관리**
   - 각 설정별로 트랜잭션이 분리됨
   - 실패 시 해당 설정만 롤백

3. **백업 컬럼 자동 생성**
   - 마이그레이션 전처리 단계에서 백업 컬럼(컬럼명_BAK)을 자동으로 생성
   - 원본 컬럼과 동일한 데이터 타입으로 생성
   - 이미 존재하는 경우 건너뜀
   - 백업 컬럼이 없으면 자동 생성되므로 수동 생성 불필요

4. **에러 처리**
   - SafeDB 적용 실패 시 로깅 및 별도 처리
   - 실패한 레코드는 재처리 가능하도록 설계

5. **성능 최적화**
   - 대상 테이블에 적절한 인덱스 설정
   - 병렬 처리 설정 조정

## 파일 구조

```
src/
├── main/
│   ├── java/com/example/crmmig/
│   │   ├── CrmMigrationApplication.java
│   │   ├── config/
│   │   │   ├── DatabaseConfig.java
│   │   │   ├── MyBatisConfig.java            # MyBatis 설정
│   │   │   ├── MigrationReaderConfig.java    # Reader Bean 설정
│   │   │   └── BatchConfig.java
│   │   ├── mapper/
│   │   │   ├── MigrationConfigMapper.java    # 설정 테이블 Mapper
│   │   │   └── TargetTableMapper.java        # 대상 테이블 Mapper
│   │   ├── batch/
│   │   │   ├── MigrationItemReader.java      # 설정 테이블 읽기 (MyBatis)
│   │   │   ├── MigrationItemProcessor.java   # PK 조회 및 SafeDB 적용 (MyBatis)
│   │   │   └── MigrationItemWriter.java      # UPDATE 수행 (MyBatis)
│   │   ├── model/
│   │   │   ├── MigrationConfigEntity.java    # 설정 엔티티
│   │   │   └── TargetUpdateEntity.java       # 업데이트 엔티티
│   │   ├── util/
│   │   │   └── SafeDBUtil.java               # SafeDB 유틸리티
│   │   ├── scheduler/
│   │   │   └── MigrationScheduler.java
│   │   └── controller/
│   │       └── MigrationController.java
│   └── resources/
│       ├── mapper/
│       │   ├── MigrationConfigMapper.xml     # 설정 테이블 쿼리
│       │   └── TargetTableMapper.xml         # 대상 테이블 쿼리
│       └── application.yml
└── test/
```

## 주요 모델 설명

### MigrationConfigEntity
- 마이그레이션 설정 정보
- `target_table_name`: 업데이트할 테이블명
- `target_column_name`: SafeDB 적용할 컬럼명
- `pk_column_name`: Primary Key 컬럼명 (동적으로 조회됨, DB에 저장 안함)
- `where_condition`: 선택적 WHERE 조건

### TargetUpdateEntity
- 업데이트 대상 레코드 정보
- `pkValue`: Primary Key 값
- `originalValue`: 원본 값
- `encryptedValue`: SafeDB 암호화된 값

## 참고 자료

- [Spring Batch Documentation](https://docs.spring.io/spring-batch/docs/current/reference/html/)
- [Spring Boot Batch](https://spring.io/guides/gs/batch-processing/)
