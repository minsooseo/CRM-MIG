# 🔍 updateTargetRecord vs batchUpdateTargetRecords 사용 현황

## 📊 현재 상태 요약

| 메서드 | 사용 여부 | 사용 위치 | 용도 |
|--------|----------|----------|------|
| `updateTargetRecord` | ✅ **사용 중** | `MigrationItemWriter.write()` | 단일 레코드 업데이트 |
| `batchUpdateTargetRecords` | ❌ **사용 안 함** | 없음 | 배치 업데이트 (미사용) |

---

## 🔧 updateTargetRecord (현재 사용 중)

### 사용 시점
- **위치**: `MigrationItemWriter.write()` 메서드 내부
- **라인**: 91번 라인
- **실행 순서**: 
  1. 백업 컬럼에 원본 값 저장 (`backupOriginalValue`)
  2. 암호화된 값으로 대상 컬럼 업데이트 (`updateTargetRecord`) ← **여기서 호출**

### 처리 방식
```java
// MigrationItemWriter.java (64-91번 라인)
for (TargetUpdateEntity item : columnUpdates) {
    // 1. 먼저 원본 값을 백업 컬럼에 저장
    mapper.backupOriginalValue(backupParams);
    
    // 2. 암호화된 값으로 대상 컬럼 업데이트
    mapper.updateTargetRecord(updateParams);  // ← 단일 레코드씩 처리
}
```

### SQL 실행
```sql
-- 각 레코드마다 개별 실행
UPDATE customer
SET phone = 'encrypted_value_1'
WHERE customer_id = 1;

UPDATE customer
SET phone = 'encrypted_value_2'
WHERE customer_id = 2;

UPDATE customer
SET phone = 'encrypted_value_3'
WHERE customer_id = 3;
...
```

### 특징
- ✅ **백업과 업데이트를 함께 처리** (백업 먼저 → 암호화 업데이트)
- ✅ **트랜잭션 안전성**: 각 레코드 처리 후 에러 발생 시 롤백 가능
- ❌ **성능**: 레코드마다 개별 SQL 실행 → 대용량 데이터 처리 시 느림

---

## 🔧 batchUpdateTargetRecords (미사용)

### 정의 위치
- **Mapper 인터페이스**: `TargetTableMapper.java` (52번 라인)
- **SQL 매퍼**: `TargetTableMapper.xml` (61-67번 라인)

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
2. **구현되지 않음**: `MigrationItemWriter`에서 호출하는 코드가 없음
3. **PostgreSQL 9.4 제약**: 여러 UPDATE를 세미콜론으로 구분한 배치 실행이 제한적일 수 있음

---

## 📈 성능 비교

### 현재 방식 (updateTargetRecord - 개별 처리)
```
레코드 1000건 처리 시:
- SQL 실행 횟수: 2000번 (백업 1000번 + 업데이트 1000번)
- 네트워크 왕복: 2000번
- 트랜잭션: Chunk 단위로 커밋
```

### 배치 방식 (batchUpdateTargetRecords - 가정)
```
레코드 1000건 처리 시:
- SQL 실행 횟수: 1번 (하나의 배치 쿼리)
- 네트워크 왕복: 1번
- 트랜잭션: 한 번에 커밋
```

---

## 💡 개선 방안

### 옵션 1: 현재 방식 유지 (권장)
**이유**:
- 백업과 업데이트가 함께 처리되어 안전함
- 에러 발생 시 롤백 범위가 명확함
- 코드가 단순하고 이해하기 쉬움

**단점**:
- 대용량 데이터 처리 시 성능 저하 가능

### 옵션 2: 배치 처리로 개선
배치 처리를 사용하려면 `MigrationItemWriter`를 다음과 같이 수정:

```java
// 개선 예시 (현재는 구현되지 않음)
private void updateBatch(List<TargetUpdateEntity> items) {
    // 1단계: 모든 백업 처리
    for (TargetUpdateEntity item : items) {
        mapper.backupOriginalValue(backupParams);
    }
    
    // 2단계: 배치 업데이트 (한 번에)
    mapper.batchUpdateTargetRecords(items);
}
```

**주의사항**:
- PostgreSQL 9.4에서 세미콜론 구분 배치 실행 지원 여부 확인 필요
- 백업 실패 시 처리 로직 필요
- 트랜잭션 범위 관리 필요

---

## 🎯 결론

### 현재 실제 사용
- ✅ **`updateTargetRecord`**: 매 레코드마다 개별 호출
- ❌ **`batchUpdateTargetRecords`**: 정의만 있고 사용 안 함

### 추천
현재 상황에서는 `updateTargetRecord`를 계속 사용하는 것이 안전합니다:
1. 백업 처리가 함께 이루어짐
2. 에러 처리 및 롤백이 명확함
3. 코드 가독성이 좋음

만약 성능 개선이 필요하다면 `batchUpdateTargetRecords`를 활용하되, 백업 로직도 함께 배치 처리하도록 개선해야 합니다.





