# 📋 마이그레이션 배치 프로그램 실행 흐름

## 🔄 전체 프로세스 개요

```
[애플리케이션 시작]
       ↓
[Job 트리거] (스케줄러 또는 수동 실행)
       ↓
[Step 1: 백업 컬럼 생성]
       ↓
[Step 2: 암호화 처리 (Reader → Processor → Writer)]
       ↓
[Step 3: 후처리]
       ↓
[완료]
```

---

## 📍 Step 1: 백업 컬럼 자동 생성 (`createBackupColumnStep`)

### 실행 위치
- **클래스**: `BackupColumnService`
- **Step 타입**: `Tasklet` (단일 작업)

### 처리 흐름

```
1. migration_config 테이블에서 활성화된 설정 조회
   (status = 'ACTIVE' 또는 NULL)
       ↓
2. 각 설정별로 target_column_name을 쉼표(,)로 분리
   예: "phone,email" → ["phone", "email"]
       ↓
3. 각 컬럼에 대해:
   a) 백업 컬럼명 생성: {column_name}_BAK
   b) INFORMATION_SCHEMA에서 백업 컬럼 존재 여부 확인
   c) 존재하지 않으면:
      - 원본 컬럼의 데이터 타입 조회
      - 백업 컬럼 생성 (ALTER TABLE ... ADD COLUMN)
   d) 이미 존재하면 건너뜀
       ↓
4. 트랜잭션 커밋
```

### 주요 SQL 쿼리
- `MigrationConfigMapper.selectActiveConfigs()`: 활성 설정 조회
- `TargetTableMapper.checkColumnExists()`: 컬럼 존재 확인
- `TargetTableMapper.selectColumnDataType()`: 데이터 타입 조회
- `TargetTableMapper.createBackupColumn()`: 백업 컬럼 생성

### 예시
```sql
-- migration_config 데이터
config_id: 1
target_table_name: "customer"
target_column_name: "phone,email"

-- 실행 결과
✓ customer.phone_BAK 생성 (VARCHAR 타입)
✓ customer.email_BAK 생성 (VARCHAR 타입)
```

---

## 📍 Step 2: 암호화 처리 (`migrationStep`)

### 실행 구조
- **Step 타입**: `Chunk` 기반 (배치 처리)
- **Chunk Size**: 기본 1000건 (설정 가능: `migration.chunk-size`)

### Reader → Processor → Writer 흐름

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│   Reader    │ --> │  Processor   │ --> │   Writer    │
│             │     │              │     │             │
│ Config 1개  │     │ Encrypted    │     │ DB Update   │
│ 읽기        │     │ Record 리스트│     │ (백업+암호화)│
└─────────────┘     └──────────────┘     └─────────────┘
```

---

### 🔍 Reader 단계 (`MigrationItemReader`)

**역할**: 마이그레이션 설정을 하나씩 읽어서 Processor에 전달

```
1. migration_config 테이블에서 활성 설정 목록 조회
   - status = 'ACTIVE' 또는 NULL
   - priority, config_id 순으로 정렬
       ↓
2. Iterator로 변환하여 하나씩 반환
       ↓
3. 모든 설정을 읽으면 null 반환 (종료)
```

**출력**: `MigrationConfigEntity` 객체
- `targetTableName`: 대상 테이블명
- `targetColumnName`: 대상 컬럼명 (쉼표로 구분 가능)
- `whereCondition`: 조건절 (선택사항)
- `configId`: 설정 ID

---

### ⚙️ Processor 단계 (`MigrationItemProcessor`)

**역할**: 설정을 기반으로 대상 테이블의 데이터를 조회하고 SafeDB 암호화 적용

```
1. INFORMATION_SCHEMA에서 Primary Key 컬럼명 조회
   - table_constraints + key_column_usage 조인
   - constraint_type = 'PRIMARY KEY'
       ↓
2. target_column_name을 쉼표(,)로 분리
   예: "phone,email" → ["phone", "email"]
       ↓
3. 각 컬럼에 대해:
   a) 대상 테이블에서 레코드 조회
      - SELECT pk, {column_name} FROM {table_name}
      - WHERE 조건 적용 (있는 경우)
      - ORDER BY pk
       ↓
   b) 각 레코드에 대해:
      - 원본 값이 있으면 SafeDB.encrypt() 적용
      - TargetUpdateEntity 객체 생성
      - List에 추가
       ↓
4. 모든 컬럼 처리 완료 후 List 반환
```

**입력**: `MigrationConfigEntity` (1개)
**출력**: `List<TargetUpdateEntity>` (여러 건)

**TargetUpdateEntity 구조**:
```java
{
  pkValue: 123,
  targetTableName: "customer",
  targetColumnName: "phone",
  pkColumnName: "customer_id",
  originalValue: "010-1234-5678",
  encryptedValue: "encrypted_value_here",
  configId: 1
}
```

**병렬 처리**: `TaskExecutor`를 통한 멀티스레드 처리 (최대 5개 스레드)

---

### ✍️ Writer 단계 (`MigrationItemWriter`)

**역할**: 암호화된 값을 대상 테이블에 업데이트 (원본 값 백업 포함)

```
1. Processor에서 받은 List<List<TargetUpdateEntity>> 처리
   (Chunk 단위로 묶인 데이터)
       ↓
2. 테이블별 → 컬럼별로 그룹화
   예: customer 테이블의 phone, email 컬럼
       ↓
3. 각 항목에 대해:
   a) 원본 값을 백업 컬럼에 저장
      UPDATE {table} SET {column}_BAK = {original_value}
      WHERE {pk_column} = {pk_value}
       ↓
   b) 암호화된 값으로 대상 컬럼 업데이트
      UPDATE {table} SET {column} = {encrypted_value}
      WHERE {pk_column} = {pk_value}
       ↓
4. Chunk 단위로 트랜잭션 커밋
   (에러 발생 시 롤백)
```

**주의사항**:
- 백업 컬럼이 없으면 경고 로그만 출력하고 계속 진행
- 각 레코드는 개별 UPDATE로 처리 (트랜잭션 안전성)

---

## 📍 Step 3: 후처리 (`postMigrationStep`)

### 실행 위치
- **클래스**: `BatchConfig.postMigrationTasklet()`
- **Step 타입**: `Tasklet` (단일 작업)

### 현재 구현
- 기본 로그 출력만 수행
- 향후 추가 가능한 작업:
  - 암호화 처리 결과 검증
  - 통계 수집 및 리포트 생성
  - 알림 발송
  - 이력 저장

---

## 🚀 Job 실행 방법

### 1. 스케줄러를 통한 자동 실행
- **클래스**: `MigrationScheduler`
- **스케줄**: 매일 새벽 2시 (`@Scheduled(cron = "0 0 2 * * ?")`)
- **설정**: `application.yml`에서 스케줄 변경 가능

### 2. 수동 실행
- **메서드**: `MigrationScheduler.runMigrationJobManually()`
- **용도**: 테스트, 즉시 실행 필요 시

---

## 🗄️ 데이터베이스 구조

### Source DB (마이그레이션 설정)
```sql
-- migration_config 테이블
CREATE TABLE migration_config (
    config_id BIGSERIAL PRIMARY KEY,
    target_table_name VARCHAR(100) NOT NULL,
    target_column_name VARCHAR(500) NOT NULL,  -- 쉼표로 구분: "phone,email"
    where_condition VARCHAR(500),              -- 선택사항
    status VARCHAR(20) DEFAULT 'ACTIVE',       -- 'ACTIVE', 'INACTIVE'
    priority INTEGER DEFAULT 0                 -- 우선순위 (낮을수록 먼저 실행)
);
```

### Target DB (실제 데이터 테이블)
```
예시: customer 테이블

Before:
- customer_id (PK)
- phone
- email
- name

After Step 1 (백업 컬럼 생성):
- customer_id (PK)
- phone
- email
- name
- phone_BAK      ← 새로 생성
- email_BAK      ← 새로 생성

After Step 2 (암호화 처리):
- phone_BAK = "010-1234-5678" (원본)
- phone = "encrypted_value" (암호화됨)
- email_BAK = "test@example.com" (원본)
- email = "encrypted_value" (암호화됨)
```

---

## 🔑 주요 특징

### 1. 동적 PK 조회
- `migration_config` 테이블에 PK 정보 저장 불필요
- 런타임에 `INFORMATION_SCHEMA`에서 자동 조회

### 2. 다중 컬럼 처리
- 하나의 설정에서 여러 컬럼 처리 가능
- 예: `target_column_name = "phone,email,address"`

### 3. 자동 백업
- 원본 데이터 손실 방지
- 백업 컬럼 자동 생성 및 데이터 저장

### 4. 트랜잭션 관리
- Chunk 단위로 커밋/롤백
- 에러 발생 시 안전하게 롤백

### 5. 병렬 처리
- `TaskExecutor`를 통한 멀티스레드 처리
- 대용량 데이터 처리 성능 향상

---

## 📊 실행 예시 로그

```
=== Step 1: 백업 컬럼 자동 생성 시작 ===
INFO  - 백업 컬럼 생성 완료: customer.phone_BAK (타입: VARCHAR(50))
INFO  - 백업 컬럼 생성 완료: customer.email_BAK (타입: VARCHAR(100))
INFO  - === 백업 컬럼 자동 생성 완료: 생성=2, 건너뜀=0 ===
=== Step 1: 백업 컬럼 자동 생성 완료 ===

INFO  - Processing migration config: Table=customer, Columns=phone,email
INFO  - Found Primary Key column: customer_id for table: customer
INFO  - Processing column: phone for table: customer
INFO  - Processed 100 records for config ID: 1
INFO  - Processing column: email for table: customer
INFO  - Processed 100 records for config ID: 1
INFO  - Updating table: customer, column: phone, count: 100
INFO  - Backed up original value to phone_BAK column: PK=1
INFO  - Successfully updated 200 records

=== Step 3: 마이그레이션 후처리 시작 ===
=== Step 3: 마이그레이션 후처리 완료 ===
```

---

## ⚠️ 주의사항

1. **SafeDB 라이브러리**: `SafeDBUtil`은 현재 플레이스홀더 구현입니다. 실제 SafeDB 라이브러리로 교체 필요

2. **PostgreSQL 9.4 호환성**: 
   - `IF NOT EXISTS` 구문 미지원 → Java에서 존재 여부 확인 후 처리

3. **JDK 1.7 호환성**: 
   - Lambda, Stream API, Optional 등 Java 8+ 기능 사용 불가
   - 모든 코드가 JDK 1.7 문법으로 작성됨

4. **데이터 소스 분리**:
   - Source DB: `migration_config` 테이블
   - Target DB: 실제 마이그레이션 대상 테이블
   - Batch DB: Spring Batch 메타데이터





