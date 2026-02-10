# 📋 마이그레이션 배치 프로그램 실행 흐름

## 🔄 전체 프로세스 개요

```
[애플리케이션 시작]
       ↓
[Job 트리거] (스케줄러 또는 수동 실행)
       ↓
[Step 1~N: 테이블별 암호화 (순차 실행)]
       - migration_config에서 테이블 목록 읽어 동적으로 Step 생성
       - 각 테이블마다 독립적인 Step 순차 실행
       - Step: Reader → Processor → Writer
       - 처리 완료 후 status를 'COMPLETE'로 업데이트
       ↓
[완료]
```

---

## 📍 Step 1~N: 테이블별 암호화 (`encryptionStep_테이블명`)

### 실행 구조
- **Step 타입**: `Chunk` (배치 처리)
- **순차 처리**: 각 테이블별로 독립적인 Step 생성 및 순차 실행
- **Step 개수**: 테이블 개수만큼
- **Chunk Size**: 기본 1000건 (설정 가능: `migration.chunk-size`)

### Step 생성 방식

```
1. MigrationJobConfig에서 migration_config 조회
       ↓
2. 테이블별로 그룹화 (같은 테이블의 여러 컬럼 합침)
   예: 
   - TB_USER: [name, email, mobile_no]
   - TB_ORDER: [receiver_name]
       ↓
3. 각 테이블별로 Step 동적 생성
   - encryptionStep_TB_USER
   - encryptionStep_TB_ORDER
       ↓
4. JobBuilder로 순차 연결
   .start(encryptionStep_TB_USER)
   .next(encryptionStep_TB_ORDER)
   .build()
```

### Reader → Processor → Writer 흐름

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐     ┌──────────────┐
│   Reader    │ --> │  Processor   │ --> │   Writer    │ --> │  Listener    │
│             │     │              │     │             │     │              │
│ 실제 테이블 │     │ SafeDB 암호화│     │ DB Update   │     │ status 업데이트│
│ 레코드 읽기 │     │ 처리         │     │             │     │ (Step 완료 시)│
│ (PK + 컬럼) │     │ (복합키 지원)│     │             │     │              │
└─────────────┘     └──────────────┘     └─────────────┘     └──────────────┘
```

---

### 🔍 Reader 단계 (`TableRecordReader`)

**역할**: 실제 테이블의 레코드를 직접 읽어서 Processor에 전달 ⭐

```
1. 초기화 (initialized = false일 때)
   a) INFORMATION_SCHEMA에서 PK 컬럼 조회 (복합키 지원)
      - table_constraints + key_column_usage 조인
      - constraint_type = 'PRIMARY KEY'
      - ordinal_position 순서대로 정렬
       ↓
   b) 대상 테이블에서 레코드 조회 (SqlSession 사용)
      SELECT 
        {pk_col1}, {pk_col2}, ...,  -- PK 컬럼들
        {target_col1}, {target_col2}, ...  -- 대상 컬럼들
      FROM {table_name}
      ORDER BY {pk_columns}
       ↓
   c) 조회 결과를 recordList에 저장
   d) iterator 생성
       ↓
2. read() 메서드 호출될 때마다:
   - iterator에서 다음 Map 객체 가져오기
   - Map을 TargetRecordEntity로 변환
   - 반환
       ↓
3. 모든 레코드를 읽으면 null 반환 (종료)
```

**출력**: `TargetRecordEntity` 객체
- `tableName`: 테이블명
- `targetColumns`: 암호화 대상 컬럼 리스트
- `pkColumns`: PK 컬럼 리스트
- `pkValues`: PK 값 Map (예: {user_id: 1})
- `columnValues`: 원본 컬럼 값 Map (예: {name: "홍길동", email: "test@example.com"})

**특징**:
- 실제 테이블 레코드를 직접 읽음 (migration_config가 아님)
- read_count가 실제 처리한 레코드 수를 정확하게 반영
- 예: TB_USER 150건 → Step의 read_count = 150 ✅

---

### ⚙️ Processor 단계 (`EncryptionProcessor`)

**역할**: `TargetRecordEntity`를 받아서 각 컬럼 값을 SafeDB 암호화

```
1. TargetRecordEntity 객체 받음
   - tableName: "TB_USER"
   - targetColumns: ["name", "email"]
   - pkValues: {user_id: 1}
   - columnValues: {name: "홍길동", email: "test@example.com"}
       ↓
2. 각 target 컬럼에 대해:
   a) columnValues에서 원본 값 가져오기
   b) 원본 값이 null이거나 빈 문자열이면 건너뜀
   c) SafeDBUtil.encrypt()로 암호화
   d) encryptedValues Map에 저장
       ↓
3. encryptedValues가 채워진 TargetRecordEntity 반환
```

**입력**: `TargetRecordEntity` (1개)
**출력**: `TargetRecordEntity` (암호화된 값 포함)

**예시**:
```java
// 입력
{
  tableName: "TB_USER",
  pkValues: {user_id: 1},
  columnValues: {name: "홍길동", email: "test@example.com"},
  encryptedValues: {}  // 비어있음
}

// 출력
{
  tableName: "TB_USER",
  pkValues: {user_id: 1},
  columnValues: {name: "홍길동", email: "test@example.com"},
  encryptedValues: {name: "encrypted_name_1", email: "encrypted_email_1"}
}
```

---

### ✍️ Writer 단계 (`EncryptionWriter`)

**역할**: 암호화된 값을 대상 테이블에 업데이트

```
1. Chunk 단위로 여러 TargetRecordEntity 받음
      ↓
2. ExecutorType.BATCH로 SqlSession 열기
      ↓
3. 각 레코드에 대해:
  a) columnUpdates 리스트 구성 (columnName, encryptedValue)
  b) updateTargetRecordWithMultipleColumns 호출
     UPDATE TB_USER
     SET name = 'encrypted_name_1', email = 'encrypted_email_1'
     WHERE user_id = 1
      ↓
4. sqlSession.commit()
```

**주의사항**:
- status 업데이트는 **MigrationStatusListener**에서 처리 (Step 완료 시)

---

### 🎧 Listener 단계 (`MigrationStatusListener`)

**역할**: Step 완료 시 migration_config 상태를 'COMPLETE'로 업데이트 (한 번만!)

```
Step 시작 (beforeStep):
  - 로그 출력: "Starting encryption step for table: {tableName}"
      ↓
Reader → Processor → Writer 실행 (청크별 반복)
      ↓
Step 완료 (afterStep):
  1. Step 실행 결과 확인
     - ExitStatus == COMPLETED인 경우만 진행
      ↓
  2. migration_config status 업데이트 (한 번만!)
     UPDATE migration_config
     SET status = 'COMPLETE'
     WHERE target_table_name = {tableName}
      ↓
  3. 업데이트 결과 로그 출력
     - 성공: "✅ Updated migration_config status to COMPLETE"
     - 실패: "⚠️ No migration_config record found" (테스트 테이블 등)
      ↓
  4. ExitStatus 반환
```

**주요 특징**:
- ✅ **Step 완료 시 한 번만** status 업데이트 (Writer는 청크마다 실행되므로 비효율적)
- ✅ Step 실패 시 status 업데이트 안 함 → 재실행 가능
- ✅ status 업데이트 실패해도 Step은 성공으로 처리 (데이터는 이미 처리됨)
- ✅ 단일 책임 원칙: Writer는 데이터 업데이트, Listener는 상태 관리

---

## ✅ 처리 완료 후 자동 상태 업데이트

### 실행 위치
- **클래스**: `MigrationStatusListener.afterStep()`
- **시점**: 각 테이블별 Step 완료 후 (한 번만!)

### 처리 내용
```
1. Step 실행 결과 확인
  - ExitStatus == COMPLETED인 경우만 진행
      ↓
2. migration_config.status를 'COMPLETE'로 업데이트
  UPDATE migration_config
  SET status = 'COMPLETE'
  WHERE target_table_name = {tableName}
      ↓
3. 업데이트 결과 확인
  - 성공: 로그 출력 + 처리된 레코드 수 표시
  - 실패: 워닝 로그 (테스트 테이블 등)
      ↓
4. ExitStatus 반환
  - status 업데이트 실패해도 Step은 성공으로 처리
  - (데이터는 이미 정상 처리되었으므로)
```

### 효과
- ✅ 재실행 시 'COMPLETE' 상태인 테이블은 자동 제외
- ✅ 중복 처리 방지
- ✅ 성능 최적화: Writer는 청크마다 실행되지만 status는 Step당 1회만 업데이트
- ✅ 단일 책임 원칙: Writer(데이터 처리) / Listener(상태 관리) 분리

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

### 단일 데이터소스 (마이그레이션 설정 + 대상 테이블 + 배치 메타데이터)
```sql
-- migration_config 테이블
CREATE TABLE migration_config (
    target_table_name VARCHAR(100) PRIMARY KEY,
    target_column_name VARCHAR(500) NOT NULL,  -- 쉼표로 구분: "phone,email"
    status VARCHAR(20) DEFAULT 'ACTIVE',       -- 'ACTIVE', 'INACTIVE', 'COMPLETE'
    priority INTEGER DEFAULT 0                 -- 우선순위 (낮을수록 먼저 실행)
);
```

### 대상 테이블 (실제 데이터 테이블)
```
예시: customer 테이블

Before:
- customer_id (PK)
- phone
- email
- name

After 암호화 처리:
- phone = "encrypted_value" (암호화됨)
- email = "encrypted_value" (암호화됨)
```

---

## 🔑 주요 특징

### 1. 동적 PK 조회
- `migration_config` 테이블에 PK 정보 저장 불필요
- 런타임에 `INFORMATION_SCHEMA`에서 자동 조회
- 복합키 자동 지원

### 2. 다중 컬럼 처리
- 하나의 설정에서 여러 컬럼 처리 가능
- 예: `target_column_name = "phone,email,address"`
- 여러 컬럼을 한 번의 UPDATE로 처리하여 성능 최적화

### 3. 트랜잭션 관리
- Chunk 단위로 커밋/롤백
- 데이터 업데이트와 status 업데이트가 같은 트랜잭션으로 처리
- 에러 발생 시 안전하게 롤백

### 4. 순차 처리
- 테이블별로 독립적인 Step 생성
- 순차 실행 (안정성 우선)
- Step 개수 = 테이블 개수

### 5. 복합키 지원
- 단일키 및 복합키 모두 지원
- INFORMATION_SCHEMA에서 자동으로 모든 PK 컬럼 조회
- 동적 WHERE 절 생성

### 6. 자동 상태 관리
- 처리 완료 후 status를 'COMPLETE'로 자동 업데이트
- 재실행 시 'COMPLETE' 상태인 테이블은 자동 제외

### 7. 정확한 read_count
- Reader가 실제 테이블 레코드를 직접 읽음
- Step별 read_count가 실제 처리한 레코드 수를 정확하게 반영
- 예: TB_USER 150건 → encryptionStep_TB_USER의 read_count = 150 ✅

---

## 📊 실행 예시 로그

```
INFO  - Creating migrationJob with 2 table-specific steps
INFO  -   - Table: customer, Columns: [phone, email]
INFO  -   - Table: order, Columns: [receiver_name]

INFO  - Starting with encryption step for table: customer, columns: [phone, email]
INFO  - Added encryption step for table: order, columns: [receiver_name]

INFO  - [encryptionStep_customer] TableRecordReader initialized for table: customer
INFO  - [encryptionStep_customer] Loaded 100 records from table: customer
INFO  - [encryptionStep_customer] Processing encryption for columns: [phone, email]
INFO  - [encryptionStep_customer] Completed updating table: customer, total records: 100
INFO  - [encryptionStep_customer] Updated migration_config status to 'COMPLETE' for table: customer

INFO  - [encryptionStep_order] TableRecordReader initialized for table: order
INFO  - [encryptionStep_order] Loaded 50 records from table: order
INFO  - [encryptionStep_order] Processing encryption for columns: [receiver_name]
INFO  - [encryptionStep_order] Completed updating table: order, total records: 50
INFO  - [encryptionStep_order] Updated migration_config status to 'COMPLETE' for table: order

INFO  - Job 'migrationJob' completed successfully
```

---

## ⚠️ 주의사항

1. **SafeDB 라이브러리**: `SafeDBUtil`은 현재 플레이스홀더 구현입니다. 실제 SafeDB 라이브러리로 교체 필요

2. **PostgreSQL 호환성**: 
   - 예약어 테이블명은 큰따옴표로 감싸기 (예: "order")

3. **단일 데이터소스**:
   - `migration_config` 테이블, 대상 테이블, 배치 메타데이터가 모두 같은 DB에 있음

4. **순차 처리**:
   - 각 테이블별로 독립적인 Step 순차 실행
   - 안정성 우선

5. **read_count 정확성**:
   - Reader가 실제 테이블 레코드를 직접 읽어 정확한 집계 가능
   - migration_config 개수가 아닌 실제 처리 레코드 수 반영

---

## 🎯 성능 특징

### 최적화 포인트
1. **여러 컬럼을 한 번의 UPDATE로 처리**: 같은 PK의 여러 컬럼을 한 번에 업데이트

2. **Chunk 처리**: 대용량 데이터를 chunk 단위로 나눠서 처리 (기본 1000건)

3. **동적 쿼리 최적화**: PK 기반 효율적인 WHERE 절 생성

4. **트랜잭션 최적화**: Chunk 단위 커밋으로 메모리 효율성 확보
