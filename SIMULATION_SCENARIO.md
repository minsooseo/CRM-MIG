# 마이그레이션 시뮬레이션 시나리오

## 시나리오 데이터

### migration_config 테이블
```
target_table_name | target_column_name | status  | priority
-----------------|--------------------|---------|----------
TB_USER          | name,mobile_no     | ACTIVE  | 1
```

### 가정
- TB_USER 테이블의 PK 컬럼명: `user_id` (INFORMATION_SCHEMA에서 자동 조회)
- TB_USER 테이블에 3건의 레코드가 존재:
  - user_id=1: name="홍길동", mobile_no="010-1234-5678"
  - user_id=2: name="김철수", mobile_no="010-9876-5432"
  - user_id=3: name="이영희", mobile_no="010-5555-6666"

---

## 실행 흐름 상세

### Step 1: 백업 컬럼 자동 생성 (createBackupColumnStep)

#### 1.1 설정 조회
```sql
SELECT target_table_name, target_column_name
FROM migration_config
WHERE status IS NULL OR status = 'ACTIVE'
ORDER BY priority, target_table_name
```
→ 결과: `{targetTableName: "TB_USER", targetColumnName: "name,mobile_no"}`

#### 1.2 컬럼명 분리 및 처리
- `target_column_name`을 쉼표로 분리: `["name", "mobile_no"]`

#### 1.3 각 컬럼별 백업 컬럼 생성 확인

**1.3.1 name 컬럼**
1. 백업 컬럼명: `name_bak` (소문자)
2. 존재 여부 확인:
   ```sql
   SELECT COUNT(*)
   FROM information_schema.columns
   WHERE table_schema = 'public'
     AND table_name = 'TB_USER'
     AND column_name = 'name_bak'
   ```
3. 원본 컬럼 타입 조회:
   ```sql
   SELECT data_type
   FROM information_schema.columns
   WHERE table_schema = 'public'
     AND table_name = 'TB_USER'
     AND column_name = 'name'
   ```
   → 결과: `VARCHAR(100)` (가정)
4. 백업 컬럼 생성:
   ```sql
   ALTER TABLE TB_USER
   ADD COLUMN name_bak VARCHAR(100)
   ```

**1.3.2 mobile_no 컬럼**
1. 백업 컬럼명: `mobile_no_bak` (소문자)
2. 존재 여부 확인 → 없음
3. 원본 컬럼 타입 조회 → `VARCHAR(20)` (가정)
4. 백업 컬럼 생성:
   ```sql
   ALTER TABLE TB_USER
   ADD COLUMN mobile_no_bak VARCHAR(20)
   ```

#### 1.4 결과
```
=== Step 1: 백업 컬럼 자동 생성 완료 ===
생성된 백업 컬럼:
- TB_USER.name_bak (VARCHAR(100), 소문자)
- TB_USER.mobile_no_bak (VARCHAR(20), 소문자)
```

---

### Step 2: 암호화 처리 (encryptionStep_TB_USER)

#### 2.1 Reader: 실제 테이블 레코드 읽기 (TableRecordReader)

**2.1.1 PK 컬럼 조회**
```sql
SELECT kcu.column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu 
    ON tc.constraint_name = kcu.constraint_name
WHERE tc.table_schema = 'public'
  AND tc.table_name = 'TB_USER'
  AND tc.constraint_type = 'PRIMARY KEY'
ORDER BY kcu.ordinal_position
```
→ 결과: `user_id`

**2.1.2 실제 테이블 레코드 조회**
```sql
SELECT 
    user_id,
    name,
    mobile_no
FROM TB_USER
ORDER BY user_id
```
→ 조회 결과:
```
[
  {user_id: 1, name: "홍길동", mobile_no: "010-1234-5678"},
  {user_id: 2, name: "김철수", mobile_no: "010-9876-5432"},
  {user_id: 3, name: "이영희", mobile_no: "010-5555-6666"}
]
```

**2.1.3 Map을 TargetRecordEntity로 변환**

레코드 1:
```java
{
  tableName: "TB_USER",
  targetColumns: ["name", "mobile_no"],
  pkColumns: ["user_id"],
  pkValues: {user_id: 1},
  columnValues: {name: "홍길동", mobile_no: "010-1234-5678"}
}
```

레코드 2:
```java
{
  tableName: "TB_USER",
  targetColumns: ["name", "mobile_no"],
  pkColumns: ["user_id"],
  pkValues: {user_id: 2},
  columnValues: {name: "김철수", mobile_no: "010-9876-5432"}
}
```

레코드 3:
```java
{
  tableName: "TB_USER",
  targetColumns: ["name", "mobile_no"],
  pkColumns: ["user_id"],
  pkValues: {user_id: 3},
  columnValues: {name: "이영희", mobile_no: "010-5555-6666"}
}
```

#### 2.2 Processor: SafeDB 암호화 (EncryptionProcessor)

**레코드 1 처리:**
```java
// 입력
{
  pkValues: {user_id: 1},
  columnValues: {name: "홍길동", mobile_no: "010-1234-5678"},
  encryptedValues: {}
}

// 암호화 처리
name: "홍길동" → SafeDB.encrypt() → "encrypted_name_1"
mobile_no: "010-1234-5678" → SafeDB.encrypt() → "encrypted_mobile_1"

// 출력
{
  pkValues: {user_id: 1},
  columnValues: {name: "홍길동", mobile_no: "010-1234-5678"},
  encryptedValues: {name: "encrypted_name_1", mobile_no: "encrypted_mobile_1"}
}
```

**레코드 2 처리:**
```java
{
  pkValues: {user_id: 2},
  columnValues: {name: "김철수", mobile_no: "010-9876-5432"},
  encryptedValues: {name: "encrypted_name_2", mobile_no: "encrypted_mobile_2"}
}
```

**레코드 3 처리:**
```java
{
  pkValues: {user_id: 3},
  columnValues: {name: "이영희", mobile_no: "010-5555-6666"},
  encryptedValues: {name: "encrypted_name_3", mobile_no: "encrypted_mobile_3"}
}
```

#### 2.3 Writer: 데이터베이스 업데이트 (EncryptionWriter)

**2.3.1 user_id=1 업데이트 (여러 컬럼을 한 번에)**
```sql
UPDATE TB_USER
SET 
    name_bak = '홍길동',
    name = 'encrypted_name_1',
    mobile_no_bak = '010-1234-5678',
    mobile_no = 'encrypted_mobile_1'
WHERE user_id = 1
```

**2.3.2 user_id=2 업데이트**
```sql
UPDATE TB_USER
SET 
    name_bak = '김철수',
    name = 'encrypted_name_2',
    mobile_no_bak = '010-9876-5432',
    mobile_no = 'encrypted_mobile_2'
WHERE user_id = 2
```

**2.3.3 user_id=3 업데이트**
```sql
UPDATE TB_USER
SET 
    name_bak = '이영희',
    name = 'encrypted_name_3',
    mobile_no_bak = '010-5555-6666',
    mobile_no = 'encrypted_mobile_3'
WHERE user_id = 3
```

**2.3.4 status 업데이트**
```sql
UPDATE migration_config
SET status = 'COMPLETE'
WHERE target_table_name = 'TB_USER'
```

---

## 최종 결과

### TB_USER 테이블 상태

| user_id | name | name_bak | mobile_no | mobile_no_bak |
|---------|------|----------|-----------|---------------|
| 1 | encrypted_name_1 | 홍길동 | encrypted_mobile_1 | 010-1234-5678 |
| 2 | encrypted_name_2 | 김철수 | encrypted_mobile_2 | 010-9876-5432 |
| 3 | encrypted_name_3 | 이영희 | encrypted_mobile_3 | 010-5555-6666 |

### migration_config 테이블 상태

| target_table_name | target_column_name | status  | priority |
|-------------------|--------------------|---------|----------|
| TB_USER           | name,mobile_no     | COMPLETE| 1        |

### 처리 통계
- 처리된 레코드 수: 3건
- 처리된 컬럼 수: 6개 (3건 × 2컬럼)
- UPDATE 문 실행 횟수: 3번 (PK별로 1번씩)
- 백업 컬럼 생성: 2개 (name_bak, mobile_no_bak, 소문자)
- **read_count**: 3 (실제 처리한 레코드 수) ✅

---

## 성능 특징

### 최적화 포인트
1. **여러 컬럼을 한 번의 UPDATE로 처리**: 같은 PK의 여러 컬럼을 한 번에 업데이트
   - 기존 방식: 3건 × 2컬럼 × 2(백업+암호화) = 12번의 UPDATE
   - 현재 방식: 3건 = 3번의 UPDATE (75% 감소)

2. **백업과 암호화를 한 번에 처리**: 백업 컬럼 저장과 암호화된 값 업데이트를 동시에 수행

3. **실제 테이블 레코드 읽기**: Reader가 실제 테이블 레코드를 직접 읽어 정확한 read_count 집계

4. **PK별 처리**: 각 레코드의 PK를 기반으로 효율적인 WHERE 절 생성

---

## 로그 예시

```
INFO  - === 마이그레이션 Job 시작 ===

INFO  - === Step 1: 백업 컬럼 자동 생성 시작 ===
INFO  - 백업 컬럼 생성 완료: TB_USER.name_bak (VARCHAR(100), 소문자)
INFO  - 백업 컬럼 생성 완료: TB_USER.mobile_no_bak (VARCHAR(20), 소문자)
INFO  - === Step 1: 백업 컬럼 자동 생성 완료 ===

INFO  - Creating migrationJob with 1 table-specific steps
INFO  -   - Table: TB_USER, Columns: [name, mobile_no]
INFO  - Added encryption step for table: TB_USER, columns: [name, mobile_no]

INFO  - [encryptionStep_TB_USER] TableRecordReader initialized for table: TB_USER
INFO  - [encryptionStep_TB_USER] Loaded 3 records from table: TB_USER
INFO  - [encryptionStep_TB_USER] Read record: user_id=1, name=홍길동, mobile_no=010-1234-5678
INFO  - [encryptionStep_TB_USER] Encrypted name: 홍길동 → encrypted_name_1
INFO  - [encryptionStep_TB_USER] Encrypted mobile_no: 010-1234-5678 → encrypted_mobile_1
INFO  - [encryptionStep_TB_USER] Read record: user_id=2, name=김철수, mobile_no=010-9876-5432
INFO  - [encryptionStep_TB_USER] Encrypted name: 김철수 → encrypted_name_2
INFO  - [encryptionStep_TB_USER] Encrypted mobile_no: 010-9876-5432 → encrypted_mobile_2
INFO  - [encryptionStep_TB_USER] Read record: user_id=3, name=이영희, mobile_no=010-5555-6666
INFO  - [encryptionStep_TB_USER] Encrypted name: 이영희 → encrypted_name_3
INFO  - [encryptionStep_TB_USER] Encrypted mobile_no: 010-5555-6666 → encrypted_mobile_3

INFO  - [encryptionStep_TB_USER] Completed updating table: TB_USER, total records: 3
INFO  - [encryptionStep_TB_USER] Updated migration_config status to 'COMPLETE' for table: TB_USER

INFO  - === 마이그레이션 Job 완료 ===
INFO  - Job execution completed. Total records processed: 3
```

---

## batch_step_execution 결과

```
step_name                    | read_count | write_count | status
----------------------------|------------|-------------|----------
createBackupColumnStep      | 0          | 0           | COMPLETED
encryptionStep_TB_USER      | 3          | 3           | COMPLETED
```

**✅ read_count = 3**: 실제 처리한 레코드 수를 정확하게 반영!

---

## 참고사항

1. **PK 동적 조회**: `INFORMATION_SCHEMA`에서 자동으로 PK 컬럼명을 조회하므로, migration_config 테이블에 PK 정보를 저장할 필요가 없습니다. 복합키도 자동으로 지원됩니다.

2. **여러 컬럼 처리**: `target_column_name`에 쉼표로 구분된 여러 컬럼을 지정하면, 모두 자동으로 처리됩니다. 같은 PK의 여러 컬럼은 한 번의 UPDATE로 처리됩니다.

3. **백업 컬럼**: 원본 데이터는 `{컬럼명}_bak` 형식의 백업 컬럼에 저장되므로 (소문자), 필요 시 원복이 가능합니다.

4. **빈 값 처리**: NULL이거나 빈 문자열인 값은 암호화하지 않고 건너뜁니다.

5. **순차 처리**: 각 테이블별로 독립적인 Step이 생성되어 순차로 실행됩니다.

6. **자동 상태 관리**: 처리 완료 후 `migration_config`의 `status`가 'COMPLETE'로 자동 업데이트되어 재실행 시 중복 처리를 방지합니다.

7. **실제 레코드 읽기**: Reader가 실제 테이블 레코드를 직접 읽어 정확한 read_count를 집계합니다. ⭐

8. **PostgreSQL 호환**: 백업 컬럼명은 소문자 (_bak)로 생성되어 PostgreSQL 호환성을 확보합니다.
