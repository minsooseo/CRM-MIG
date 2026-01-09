# 🔍 UPDATE 메서드 사용 현황

## 📊 현재 상태 요약

| 메서드 | 사용 여부 | 사용 위치 | 용도 |
|--------|----------|----------|------|
| `updateTargetRecordWithMultipleColumns` | ✅ **사용 중** | `EncryptionWriter.write()` | 여러 컬럼을 한 번에 업데이트 (복합키 지원) |
| `batchUpdateTargetRecords` | ❌ **사용 안 함** | 없음 | 배치 업데이트 (미사용) |
| `updateStatus` | ✅ **사용 중** | `EncryptionWriter.write()` | migration_config의 status를 'COMPLETE'로 업데이트 |

---

## 🔧 updateTargetRecordWithMultipleColumns (현재 사용 중)

### 사용 시점
- **위치**: `EncryptionWriter.write()` 메서드 내부
- **실행 순서**: 
  1. Chunk 단위로 TargetRecordEntity 리스트 받음
  2. 테이블별로 그룹화
  3. 각 레코드에 대해 여러 컬럼을 한 번에 업데이트 (`updateTargetRecordWithMultipleColumns`) ← **여기서 호출**
  4. status를 'COMPLETE'로 업데이트 (`updateStatus`)

### 처리 방식
```java
// EncryptionWriter.java
// 각 레코드별로 여러 컬럼을 한 번에 처리
for (TargetRecordEntity entity : tableEntities) {
    // 여러 컬럼 정보를 리스트로 구성
    List<Map<String, Object>> columnUpdates = new ArrayList<>();
    
    for (String columnName : entity.getTargetColumns()) {
        Map<String, Object> columnInfo = new HashMap<>();
        columnInfo.put("columnName", columnName);
        columnInfo.put("backupColumnName", (columnName + "_bak").toLowerCase());
        columnInfo.put("originalValue", entity.getColumnValues().get(columnName));
        columnInfo.put("encryptedValue", entity.getEncryptedValues().get(columnName));
        columnUpdates.add(columnInfo);
    }
    
    // 한 번에 여러 컬럼 업데이트 (백업 + 암호화)
    mapper.updateTargetRecordWithMultipleColumns(updateParams);
}
```

### SQL 실행 (단일키 예시)
```sql
-- 각 PK마다 한 번씩 실행 (여러 컬럼 포함)
UPDATE customer
SET 
    phone_bak = '010-1234-5678',
    phone = 'encrypted_phone_1',
    email_bak = 'test@example.com',
    email = 'encrypted_email_1'
WHERE customer_id = 1;

UPDATE customer
SET 
    phone_bak = '010-9876-5432',
    phone = 'encrypted_phone_2',
    email_bak = 'test2@example.com',
    email = 'encrypted_email_2'
WHERE customer_id = 2;
...
```

### SQL 실행 (복합키 예시)
```sql
-- 복합키인 경우
UPDATE order_item
SET 
    price_bak = '10000',
    price = 'encrypted_price_1'
WHERE order_id = 1 AND product_id = 100;
```

### 특징
- ✅ **백업과 업데이트를 한 번에 처리**: 여러 컬럼을 하나의 UPDATE로 처리
- ✅ **복합키 지원**: 단일키 및 복합키 모두 처리 가능
- ✅ **트랜잭션 안전성**: Chunk 단위로 커밋/롤백
- ✅ **성능 최적화**: PK별로 여러 컬럼을 한 번에 업데이트하여 SQL 실행 횟수 감소
- ✅ **자동 상태 관리**: 처리 완료 후 status를 'COMPLETE'로 업데이트
- ✅ **소문자 백업 컬럼**: PostgreSQL 호환을 위해 _bak 소문자 사용

---

## 🔧 batchUpdateTargetRecords (미사용)

### 정의 위치
- **Mapper 인터페이스**: `TargetTableMapper.java`
- **SQL 매퍼**: `TargetTableMapper.xml`

### SQL 구조
```xml
<update id="batchUpdateTargetRecords">
    <foreach collection="list" item="item" separator=";">
        UPDATE ${item.targetTableName}
        SET ${item.targetColumnName} = #{item.encryptedValue}
        WHERE ${item.pkColumnName} = #{item.pkValue}
    </foreach>
</update>
```

### 실행될 SQL (가정)
```sql
-- 한 번에 여러 레코드 처리
UPDATE customer SET phone = 'encrypted_value_1' WHERE customer_id = 1;
UPDATE customer SET phone = 'encrypted_value_2' WHERE customer_id = 2;
UPDATE customer SET phone = 'encrypted_value_3' WHERE customer_id = 3;
UPDATE customer SET phone = 'encrypted_value_4' WHERE customer_id = 4;
...
```

### 현재 미사용인 이유
1. **백업 처리 로직 부재**: 백업 컬럼에 원본 값을 저장하는 로직이 없음
2. **구현되지 않음**: `EncryptionWriter`에서 호출하는 코드가 없음
3. **새로운 구조**: `TargetRecordEntity`를 사용하는 새로운 구조로 변경됨
4. **PostgreSQL 9.4 제약**: 여러 UPDATE를 세미콜론으로 구분한 배치 실행이 제한적일 수 있음

---

## 📈 성능 비교

### 현재 방식 (updateTargetRecordWithMultipleColumns - PK별 그룹화)
```
테이블: customer, 컬럼: phone, email
레코드 1000건 처리 시:
- SQL 실행 횟수: 1000번 (PK별로 1번씩, 여러 컬럼 포함)
- 네트워크 왕복: 1000번
- 트랜잭션: Chunk 단위로 커밋
- status 업데이트: 테이블당 1번
```

### 기존 방식 (개별 컬럼 업데이트 - 비교용)
```
테이블: customer, 컬럼: phone, email
레코드 1000건 처리 시:
- SQL 실행 횟수: 4000번 (백업 2000번 + 업데이트 2000번)
- 네트워크 왕복: 4000번
- 트랜잭션: Chunk 단위로 커밋
```

### 성능 개선
- **75% 감소**: 4000번 → 1000번 (2개 컬럼 기준)
- **여러 컬럼 처리**: 같은 PK의 여러 컬럼을 한 번의 UPDATE로 처리

---

## 💡 현재 구조의 장점

### 옵션: 현재 방식 유지 (권장) ✅

**이유**:
- 백업과 업데이트가 한 번에 처리되어 안전함
- 여러 컬럼을 PK별로 그룹화하여 성능 최적화
- 복합키 지원
- 자동 상태 관리 (COMPLETE 업데이트)
- 에러 발생 시 롤백 범위가 명확함
- _bak 소문자로 PostgreSQL 호환성 확보

**성능**:
- PK별로 여러 컬럼을 한 번에 처리하여 SQL 실행 횟수 75% 감소 (2개 컬럼 기준)
- 순차 처리로 안정성 확보
- Chunk 단위 트랜잭션으로 메모리 효율성

---

## 🎯 데이터 흐름

### 전체 흐름
```
TableRecordReader (실제 테이블 레코드 읽기)
       ↓
TargetRecordEntity (PK + 여러 컬럼)
  {
    tableName: "TB_USER",
    pkValues: {user_id: 1},
    columnValues: {name: "홍길동", email: "test@example.com"}
  }
       ↓
EncryptionProcessor (SafeDB 암호화)
       ↓
TargetRecordEntity (암호화된 값 포함)
  {
    tableName: "TB_USER",
    pkValues: {user_id: 1},
    columnValues: {name: "홍길동", email: "test@example.com"},
    encryptedValues: {name: "encrypted_1", email: "encrypted_2"}
  }
       ↓
EncryptionWriter (UPDATE + status 업데이트)
       ↓
UPDATE TB_USER
SET 
    name_bak = '홍길동',
    name = 'encrypted_1',
    email_bak = 'test@example.com',
    email = 'encrypted_2'
WHERE user_id = 1;
```

---

## 🔄 주요 변경사항

### 이전 구조와의 차이

| 항목 | 이전 | 현재 |
|------|------|------|
| Reader | `MigrationItemReader` (migration_config만 읽음) | `TableRecordReader` (실제 테이블 레코드 읽음) ⭐ |
| Processor | `MigrationItemProcessor` | `EncryptionProcessor` |
| Writer | `MigrationItemWriter` | `EncryptionWriter` |
| 모델 | `TargetUpdateEntity` | `TargetRecordEntity` |
| 백업 컬럼 | `_BAK` (대문자) | `_bak` (소문자) |
| 처리 방식 | 병렬 처리 | 순차 처리 |
| read_count | migration_config 개수 | 실제 처리한 레코드 수 ✅ |

---

## 🎯 결론

### 현재 실제 사용
- ✅ **`updateTargetRecordWithMultipleColumns`**: PK별로 여러 컬럼을 한 번에 업데이트 (복합키 지원)
- ✅ **`updateStatus`**: 처리 완료 후 status를 'COMPLETE'로 업데이트
- ❌ **`batchUpdateTargetRecords`**: 정의만 있고 사용 안 함

### 추천
현재 방식 (`updateTargetRecordWithMultipleColumns`)을 계속 사용하는 것을 권장합니다:
1. 백업과 업데이트가 한 번에 처리되어 안전함
2. 여러 컬럼을 PK별로 그룹화하여 성능 최적화
3. 복합키 지원으로 유연성 확보
4. 자동 상태 관리로 데이터 무결성 보장
5. 순차 처리로 안정성 확보
6. _bak 소문자로 PostgreSQL 호환성 확보

### 성능 특징
- PK별로 여러 컬럼을 한 번에 처리: SQL 실행 횟수 75% 감소 (2개 컬럼 기준)
- 순차 처리: 안정성 우선
- Chunk 단위 트랜잭션: 안전한 롤백 보장
- Reader가 실제 테이블 레코드를 읽어 정확한 read_count 집계
