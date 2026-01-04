# 마이그레이션 시뮬레이션 시나리오

## 시나리오 데이터

### migration_config 테이블
```
target_table_name: "TB_USER"
target_column_name: "name,addr,birth"
```

### 가정
- TB_USER 테이블의 PK 컬럼명: `user_id` (INFORMATION_SCHEMA에서 자동 조회)
- TB_USER 테이블에 3건의 레코드가 존재:
  - user_id=1: name="홍길동", addr="서울시 강남구", birth="1990-01-15"
  - user_id=2: name="김철수", addr="부산시 해운대구", birth="1985-05-20"
  - user_id=3: name="이영희", addr="인천시 연수구", birth="1992-08-10"

---

## 실행 흐름 상세

### Step 1: 백업 컬럼 자동 생성 (createBackupColumnStep)

#### 1.1 설정 조회
```sql
SELECT target_table_name, target_column_name
FROM migration_config
```
→ 결과: `{targetTableName: "TB_USER", targetColumnName: "name,addr,birth"}`

#### 1.2 컬럼명 분리 및 처리
- `target_column_name`을 쉼표로 분리: `["name", "addr", "birth"]`

#### 1.3 각 컬럼별 백업 컬럼 생성 확인

**1.3.1 name 컬럼**
1. 백업 컬럼명: `name_BAK`
2. 존재 여부 확인:
   ```sql
   SELECT COUNT(*)
   FROM information_schema.columns
   WHERE table_schema = 'public'
     AND table_name = 'TB_USER'
     AND column_name = 'name_BAK'
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
   ADD COLUMN name_BAK VARCHAR(100)
   ```

**1.3.2 addr 컬럼**
1. 백업 컬럼명: `addr_BAK`
2. 존재 여부 확인 → 없음
3. 원본 컬럼 타입 조회 → `VARCHAR(200)` (가정)
4. 백업 컬럼 생성:
   ```sql
   ALTER TABLE TB_USER
   ADD COLUMN addr_BAK VARCHAR(200)
   ```

**1.3.3 birth 컬럼**
1. 백업 컬럼명: `birth_BAK`
2. 존재 여부 확인 → 없음
3. 원본 컬럼 타입 조회 → `DATE` (가정)
4. 백업 컬럼 생성:
   ```sql
   ALTER TABLE TB_USER
   ADD COLUMN birth_BAK DATE
   ```

#### 1.4 결과
```
=== Step 1: 백업 컬럼 자동 생성 완료 ===
생성된 백업 컬럼:
- TB_USER.name_BAK (VARCHAR(100))
- TB_USER.addr_BAK (VARCHAR(200))
- TB_USER.birth_BAK (DATE)
```

---

### Step 2: 암호화 처리 (migrationStep)

#### 2.1 Reader: 설정 읽기
- `MigrationItemReader.read()` 호출
- 반환: `{targetTableName: "TB_USER", targetColumnName: "name,addr,birth"}`

#### 2.2 Processor: 데이터 처리

**2.2.1 PK 컬럼 조회**
```sql
SELECT kcu.column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu 
    ON tc.constraint_name = kcu.constraint_name
WHERE tc.table_schema = 'public'
  AND tc.table_name = 'TB_USER'
  AND tc.constraint_type = 'PRIMARY KEY'
LIMIT 1
```
→ 결과: `user_id`

**2.2.2 컬럼별 데이터 조회 및 암호화**

컬럼명 분리: `["name", "addr", "birth"]`

**컬럼 1: name**
```sql
SELECT 
    user_id AS pk_value,
    name AS original_value,
    'TB_USER' AS target_table_name,
    'name' AS target_column_name,
    'user_id' AS pk_column_name
FROM TB_USER
ORDER BY user_id
```
→ 조회 결과:
- user_id=1, original_value="홍길동"
- user_id=2, original_value="김철수"
- user_id=3, original_value="이영희"

암호화 처리:
- user_id=1: "홍길동" → SafeDB 암호화 → "encrypted_name_1"
- user_id=2: "김철수" → SafeDB 암호화 → "encrypted_name_2"
- user_id=3: "이영희" → SafeDB 암호화 → "encrypted_name_3"

**컬럼 2: addr**
```sql
SELECT 
    user_id AS pk_value,
    addr AS original_value,
    'TB_USER' AS target_table_name,
    'addr' AS target_column_name,
    'user_id' AS pk_column_name
FROM TB_USER
ORDER BY user_id
```
→ 조회 결과:
- user_id=1, original_value="서울시 강남구"
- user_id=2, original_value="부산시 해운대구"
- user_id=3, original_value="인천시 연수구"

암호화 처리:
- user_id=1: "서울시 강남구" → "encrypted_addr_1"
- user_id=2: "부산시 해운대구" → "encrypted_addr_2"
- user_id=3: "인천시 연수구" → "encrypted_addr_3"

**컬럼 3: birth**
```sql
SELECT 
    user_id AS pk_value,
    birth AS original_value,
    'TB_USER' AS target_table_name,
    'birth' AS target_column_name,
    'user_id' AS pk_column_name
FROM TB_USER
ORDER BY user_id
```
→ 조회 결과:
- user_id=1, original_value="1990-01-15"
- user_id=2, original_value="1985-05-20"
- user_id=3, original_value="1992-08-10"

암호화 처리:
- user_id=1: "1990-01-15" → "encrypted_birth_1"
- user_id=2: "1985-05-20" → "encrypted_birth_2"
- user_id=3: "1992-08-10" → "encrypted_birth_3"

**2.2.3 Processor 반환 데이터**
총 9개의 `TargetUpdateEntity` 객체:
- user_id=1: name, addr, birth (3개)
- user_id=2: name, addr, birth (3개)
- user_id=3: name, addr, birth (3개)

#### 2.3 Writer: 데이터베이스 업데이트

**2.3.1 PK별로 그룹화**
- user_id=1: [name, addr, birth]
- user_id=2: [name, addr, birth]
- user_id=3: [name, addr, birth]

**2.3.2 user_id=1 업데이트 (여러 컬럼을 한 번에)**
```sql
UPDATE TB_USER
SET 
    name_BAK = '홍길동',
    name = 'encrypted_name_1',
    addr_BAK = '서울시 강남구',
    addr = 'encrypted_addr_1',
    birth_BAK = '1990-01-15',
    birth = 'encrypted_birth_1'
WHERE user_id = 1
```

**2.3.3 user_id=2 업데이트**
```sql
UPDATE TB_USER
SET 
    name_BAK = '김철수',
    name = 'encrypted_name_2',
    addr_BAK = '부산시 해운대구',
    addr = 'encrypted_addr_2',
    birth_BAK = '1985-05-20',
    birth = 'encrypted_birth_2'
WHERE user_id = 2
```

**2.3.4 user_id=3 업데이트**
```sql
UPDATE TB_USER
SET 
    name_BAK = '이영희',
    name = 'encrypted_name_3',
    addr_BAK = '인천시 연수구',
    addr = 'encrypted_addr_3',
    birth_BAK = '1992-08-10',
    birth = 'encrypted_birth_3'
WHERE user_id = 3
```

---

## 최종 결과

### TB_USER 테이블 상태

| user_id | name | name_BAK | addr | addr_BAK | birth | birth_BAK |
|---------|------|----------|------|----------|-------|-----------|
| 1 | encrypted_name_1 | 홍길동 | encrypted_addr_1 | 서울시 강남구 | encrypted_birth_1 | 1990-01-15 |
| 2 | encrypted_name_2 | 김철수 | encrypted_addr_2 | 부산시 해운대구 | encrypted_birth_2 | 1985-05-20 |
| 3 | encrypted_name_3 | 이영희 | encrypted_addr_3 | 인천시 연수구 | encrypted_birth_3 | 1992-08-10 |

### 처리 통계
- 처리된 레코드 수: 3건
- 처리된 컬럼 수: 9개 (3건 × 3컬럼)
- UPDATE 문 실행 횟수: 3번 (PK별로 1번씩)
- 백업 컬럼 생성: 3개 (name_BAK, addr_BAK, birth_BAK)

---

## 성능 특징

### 최적화 포인트
1. **여러 컬럼을 한 번의 UPDATE로 처리**: 같은 PK의 여러 컬럼을 한 번에 업데이트
   - 기존 방식: 3건 × 3컬럼 = 9번의 UPDATE
   - 현재 방식: 3건 = 3번의 UPDATE (67% 감소)

2. **백업과 암호화를 한 번에 처리**: 백업 컬럼 저장과 암호화된 값 업데이트를 동시에 수행

3. **PK별 그룹화**: 같은 PK의 여러 컬럼을 메모리에 모아서 배치 처리

---

## 로그 예시

```
=== Step 1: 백업 컬럼 자동 생성 시작 ===
백업 컬럼 생성 완료: TB_USER.name_BAK
백업 컬럼 생성 완료: TB_USER.addr_BAK
백업 컬럼 생성 완료: TB_USER.birth_BAK
=== Step 1: 백업 컬럼 자동 생성 완료: 생성=3, 건너뜀=0 ===

Processing migration config: Table=TB_USER, Columns=name,addr,birth
Found Primary Key column: user_id for table: TB_USER
Processing column: name for table: TB_USER
Processing column: addr for table: TB_USER
Processing column: birth for table: TB_USER
Processed 9 records for table: TB_USER, columns: name,addr,birth

Updating table: TB_USER, PK: 1, columns count: 3
Updated multiple columns: PK=1, Columns=3
Updating table: TB_USER, PK: 2, columns count: 3
Updated multiple columns: PK=2, Columns=3
Updating table: TB_USER, PK: 3, columns count: 3
Updated multiple columns: PK=3, Columns=3
Completed updating table: TB_USER, total records: 9
Successfully updated 9 records
```

---

## 참고사항

1. **PK 동적 조회**: `INFORMATION_SCHEMA`에서 자동으로 PK 컬럼명을 조회하므로, migration_config 테이블에 PK 정보를 저장할 필요가 없습니다.

2. **여러 컬럼 처리**: `target_column_name`에 쉼표로 구분된 여러 컬럼을 지정하면, 모두 자동으로 처리됩니다.

3. **백업 컬럼**: 원본 데이터는 `{컬럼명}_BAK` 형식의 백업 컬럼에 저장되므로, 필요 시 원복이 가능합니다.

4. **빈 값 처리**: NULL이거나 빈 문자열인 값은 암호화하지 않고 건너뜁니다.




