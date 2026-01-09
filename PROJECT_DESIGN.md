# 🏗️ CRM 마이그레이션 배치 프로젝트 설계서

## 📋 1. 프로젝트 개요

### 1.1 목적
기존 테이블의 개인정보 컬럼에 SafeDB 암호화를 적용하는 Spring Batch 기반 마이그레이션 시스템

### 1.2 주요 기능
- ✅ 설정 기반 자동 마이그레이션
- ✅ 백업 컬럼 자동 생성
- ✅ SafeDB 암호화 적용
- ✅ 복합키 지원
- ✅ 처리 상태 자동 관리

---

## 🏛️ 2. 시스템 아키텍처

### 2.1 전체 구조

```
┌─────────────────────────────────────────────────────────┐
│                  CRM Migration Batch                     │
│                  (Spring Boot 2.3.2)                     │
└─────────────────────────────────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        │                 │                 │
        ▼                 ▼                 ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Spring Batch │  │   MyBatis    │  │   SafeDB     │
│    4.2.x     │  │    1.3.5     │  │    Util      │
└──────────────┘  └──────────────┘  └──────────────┘
        │                 │                 │
        └─────────────────┼─────────────────┘
                          ▼
                ┌──────────────────┐
                │   PostgreSQL     │
                │   (단일 DB)      │
                └──────────────────┘
```

### 2.2 배치 실행 구조

```
[Job: migrationJob]
    │
    ├─ Step 1: createBackupColumnStep (Tasklet)
    │           └─ 백업 컬럼 자동 생성 (_bak)
    │
    ├─ Step 2: encryptionStep_TB_USER (Chunk)
    │           ├─ Reader:  TB_USER 레코드 읽기
    │           ├─ Processor: SafeDB 암호화
    │           └─ Writer: UPDATE 수행
    │
    ├─ Step 3: encryptionStep_TB_ORDER (Chunk)
    │           ├─ Reader:  TB_ORDER 레코드 읽기
    │           ├─ Processor: SafeDB 암호화
    │           └─ Writer: UPDATE 수행
    │
    └─ Step N: ...
            (테이블 수만큼 Step 동적 생성)
```

---

## 🎯 3. 핵심 설계 원칙

### 3.1 테이블별 Step 분리
- **1 테이블 = 1 Step**
- 각 Step은 독립적으로 실행 (순차)
- 장점: 정확한 read_count, 실패 시 테이블 단위 재처리

### 3.2 Reader/Processor/Writer 패턴

| 구성 요소 | 역할 | 입력 | 출력 |
|----------|------|------|------|
| **TableRecordReader** | 테이블 레코드 읽기 | - | TargetRecordEntity |
| **EncryptionProcessor** | SafeDB 암호화 | TargetRecordEntity | TargetRecordEntity |
| **EncryptionWriter** | DB 업데이트 | List&lt;TargetRecordEntity&gt; | - |

### 3.3 동적 Step 생성 방식

```java
// MigrationJobConfig.java
@Bean
public Job migrationJob(JobRepository jobRepository, Step createBackupColumnStep) {
    // 1. migration_config에서 설정 조회
    List<MigrationConfigEntity> configs = migrationConfigMapper.selectActiveConfigs();
    
    // 2. 테이블별로 그룹화
    Map<String, List<String>> tableColumnMap = groupByTable(configs);
    
    // 3. 각 테이블마다 Step 생성 및 연결
    SimpleJobBuilder jobBuilder = new JobBuilder("migrationJob")
        .repository(jobRepository)
        .start(createBackupColumnStep);
    
    for (Map.Entry<String, List<String>> entry : tableColumnMap.entrySet()) {
        Step step = batchConfig.createTableEncryptionStep(
            entry.getKey(),   // tableName
            entry.getValue(), // columns
            jobRepository, transactionManager);
        jobBuilder = jobBuilder.next(step);
    }
    
    return jobBuilder.build();
}
```

---

## 📊 4. 데이터베이스 설계

### 4.1 설정 테이블 (migration_config)

```sql
CREATE TABLE migration_config (
    target_table_name  VARCHAR(100) PRIMARY KEY,  -- 대상 테이블명
    target_column_name VARCHAR(500) NOT NULL,     -- 대상 컬럼 (쉼표 구분)
    where_condition    VARCHAR(500),              -- WHERE 조건 (선택)
    status             VARCHAR(20) DEFAULT 'ACTIVE', -- 처리 상태
    priority           INTEGER DEFAULT 0          -- 우선순위
);
```

**설정 예시:**
```sql
INSERT INTO migration_config 
    (target_table_name, target_column_name, status)
VALUES
    ('tb_user', 'phone,email', 'ACTIVE'),
    ('tb_order', 'mobile_no,address', 'ACTIVE');
```

### 4.2 대상 테이블 구조 변경

**Before:**
```sql
CREATE TABLE tb_user (
    user_id   SERIAL PRIMARY KEY,
    user_name VARCHAR(100),
    phone     VARCHAR(20),      -- 평문 데이터
    email     VARCHAR(100)      -- 평문 데이터
);
```

**After (자동 생성):**
```sql
CREATE TABLE tb_user (
    user_id   SERIAL PRIMARY KEY,
    user_name VARCHAR(100),
    phone     VARCHAR(20),      -- 암호화된 데이터
    email     VARCHAR(100),     -- 암호화된 데이터
    phone_bak VARCHAR(20),      -- 백업 (원본 평문)
    email_bak VARCHAR(100)      -- 백업 (원본 평문)
);
```

### 4.3 Spring Batch 메타데이터

- `batch_job_instance`
- `batch_job_execution`
- `batch_step_execution` ⭐ **read_count 확인**
- `batch_job_execution_params`
- `batch_step_execution_context`

---

## 🔄 5. 처리 흐름

### 5.1 전체 프로세스

```
[시작]
  │
  ▼
┌────────────────────────────────┐
│ Step 1: 백업 컬럼 생성          │
│ - migration_config 조회        │
│ - 각 컬럼에 _bak 컬럼 생성     │
└────────────────────────────────┘
  │
  ▼
┌────────────────────────────────┐
│ Step 2: encryptionStep_테이블1 │
│                                │
│ [Reader]                       │
│ - PK 조회 (INFORMATION_SCHEMA) │
│ - 레코드 읽기 (PK + 컬럼값)    │
│                                │
│ [Processor]                    │
│ - SafeDB 암호화 적용           │
│                                │
│ [Writer]                       │
│ - 백업 컬럼에 원본 저장        │
│ - 원본 컬럼에 암호화 값 저장   │
│ - status = 'COMPLETE' 업데이트 │
└────────────────────────────────┘
  │
  ▼
┌────────────────────────────────┐
│ Step 3: encryptionStep_테이블2 │
│ (동일한 처리)                  │
└────────────────────────────────┘
  │
  ▼
[종료]
```

### 5.2 Chunk 처리 상세

```
[Chunk Size = 1000]

┌─────────────────────────────────────┐
│ Reader: 1000개 레코드 읽기          │
│ ┌─────────────────────────────┐    │
│ │ Record 1: {pk=1, phone=...} │    │
│ │ Record 2: {pk=2, phone=...} │    │
│ │ ...                         │    │
│ │ Record 1000: {...}          │    │
│ └─────────────────────────────┘    │
└─────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│ Processor: 각각 암호화              │
│ ┌─────────────────────────────┐    │
│ │ Record 1: encrypted         │    │
│ │ Record 2: encrypted         │    │
│ │ ...                         │    │
│ └─────────────────────────────┘    │
└─────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│ Writer: 1000개 일괄 UPDATE          │
│ - Transaction Commit               │
└─────────────────────────────────────┘
```

---

## 🛠️ 6. 주요 구성 요소

### 6.1 Configuration

| 클래스 | 역할 |
|--------|------|
| `MigrationJobConfig` | Job 정의, 테이블별 Step 동적 생성 |
| `BatchConfig` | Step 생성 팩토리, Tasklet 정의 |
| `DatabaseConfig` | DataSource 설정 |
| `MyBatisConfig` | MyBatis SqlSessionFactory 설정 |
| `MigrationProperties` | 설정 Properties (chunk-size, schema-name) |

### 6.2 Batch Components

| 클래스 | 타입 | 역할 |
|--------|------|------|
| `TableRecordReader` | ItemReader | 테이블 레코드 직접 읽기 (PK + 컬럼값) |
| `EncryptionProcessor` | ItemProcessor | SafeDB 암호화 적용 |
| `EncryptionWriter` | ItemWriter | UPDATE 수행 + status 업데이트 |

### 6.3 Model

| 클래스 | 설명 |
|--------|------|
| `MigrationConfigEntity` | migration_config 테이블 매핑 |
| `TargetRecordEntity` | 대상 테이블의 레코드 (PK + 컬럼값 + 암호화값) |

### 6.4 Service

| 클래스 | 역할 |
|--------|------|
| `BackupColumnService` | 백업 컬럼 자동 생성 (_bak) |

### 6.5 Mapper

| 인터페이스 | 역할 |
|----------|------|
| `MigrationConfigMapper` | migration_config CRUD |
| `TargetTableMapper` | 대상 테이블 조회/업데이트, PK 조회 |

---

## 💡 7. 핵심 설계 결정 사항

### 7.1 왜 테이블별 Step인가?

#### ❌ 기존 방식 (설정 기반 Step)
```
Read: migration_config (3건)
  → read_count = 3 (❌ 부정확)
  → 실제 처리: 500건
```

#### ✅ 현재 방식 (테이블별 Step)
```
Step: encryptionStep_TB_USER
  Read: TB_USER (150건)
  → read_count = 150 (✅ 정확)
  
Step: encryptionStep_TB_ORDER
  Read: TB_ORDER (350건)
  → read_count = 350 (✅ 정확)
```

### 7.2 복합키 처리

```java
// TargetRecordEntity
private List<String> pkColumnNames;    // ["user_id", "sub_id"]
private Map<String, Object> pkValues;  // {user_id=1, sub_id=2}

// 동적 WHERE 절 생성
WHERE user_id = ? AND sub_id = ?
```

### 7.3 백업 컬럼 네이밍 (_bak 소문자)

PostgreSQL은 컬럼명을 소문자로 저장하므로 `_bak` 사용

```sql
-- ✅ 올바른 방식
ALTER TABLE tb_user ADD COLUMN phone_bak VARCHAR(20);

-- ❌ 잘못된 방식
ALTER TABLE tb_user ADD COLUMN phone_BAK VARCHAR(20);
```

---

## 📐 8. 기술 스택

| 분류 | 기술 | 버전 |
|------|------|------|
| **Language** | Java | 1.8 |
| **Framework** | Spring Boot | 2.3.2 |
| **Batch** | Spring Batch | 4.2.x |
| **ORM** | MyBatis | 1.3.5 |
| **Database** | PostgreSQL | 9.6+ |
| **Build** | Maven | 3.6+ |
| **암호화** | SafeDB | (외부 라이브러리) |

---

## 🔒 9. 보안 설계

### 9.1 백업 전략
- 원본 데이터를 `_bak` 컬럼에 자동 백업
- 롤백 가능 구조

### 9.2 트랜잭션 관리
- Chunk 단위 트랜잭션 (기본 1000건)
- 실패 시 해당 Chunk만 롤백

### 9.3 재실행 전략
```sql
-- 재실행 시 COMPLETE 상태 제외
SELECT * FROM migration_config 
WHERE status = 'ACTIVE' OR status IS NULL;
```

---

## 📈 10. 성능 설계

### 10.1 Chunk Size 조정
```yaml
migration:
  chunk-size: 1000  # 환경에 따라 조정
```

**권장값:**
- 로컬: 100
- 개발: 1,000
- 운영: 5,000

### 10.2 Connection Pool
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10  # 환경에 따라 조정
```

### 10.3 병렬 처리 고려사항
현재는 **순차 처리** 방식 채택
- 이유: 정확한 read_count 보장
- 향후 필요 시 테이블별 병렬 처리 가능 (Flow + Split)

---

## 🎯 11. 설계 목표 달성

| 목표 | 구현 | 상태 |
|------|------|------|
| 설정 기반 동적 처리 | migration_config 테이블 | ✅ |
| 백업 자동화 | BackupColumnService | ✅ |
| 복합키 지원 | INFORMATION_SCHEMA 조회 | ✅ |
| 정확한 모니터링 | 테이블별 Step, read_count | ✅ |
| 재실행 가능성 | status 관리 | ✅ |
| 확장성 | 동적 Step 생성 | ✅ |

---

## 📚 12. 참고 문서

- [README.md](README.md) - 프로젝트 개요
- [EXECUTION_FLOW.md](EXECUTION_FLOW.md) - 실행 흐름 상세
- [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) - 프로젝트 구조
- [MIGRATION_CONFIG_DDL.md](MIGRATION_CONFIG_DDL.md) - DDL 가이드
- [LINUX_EXECUTION_GUIDE.md](LINUX_EXECUTION_GUIDE.md) - 리눅스 실행 가이드

---

**작성일:** 2025-01-09  
**버전:** 1.0  
**담당:** CRM Migration Team
