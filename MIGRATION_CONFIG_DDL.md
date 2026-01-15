# migration_config 테이블 DDL

## 테이블 생성 스크립트 (PostgreSQL)

```sql
-- 마이그레이션 설정 테이블 생성
CREATE TABLE migration_config (
  target_table_name VARCHAR(100) PRIMARY KEY,
  target_column_name VARCHAR(500) NOT NULL,  -- 쉼표로 구분하여 여러 컬럼 지정 가능
  status VARCHAR(20) DEFAULT 'ACTIVE',
  priority INTEGER DEFAULT 0
);

-- 컬럼 설명
COMMENT ON COLUMN migration_config.target_table_name IS '대상 테이블명 (PRIMARY KEY)';
COMMENT ON COLUMN migration_config.target_column_name IS '대상 컬럼명 (SafeDB 적용할 컬럼, 쉼표로 구분하여 여러 컬럼 지정 가능)';
COMMENT ON COLUMN migration_config.status IS '처리 상태 (ACTIVE, INACTIVE, COMPLETE) - COMPLETE는 자동 업데이트됨';
COMMENT ON COLUMN migration_config.priority IS '처리 우선순위 (낮을수록 먼저 실행)';
```

## 테이블 구조

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| `target_table_name` | VARCHAR(100) | PRIMARY KEY | 대상 테이블명 (유니크) |
| `target_column_name` | VARCHAR(500) | NOT NULL | 대상 컬럼명 (쉼표로 구분 가능) |
| `status` | VARCHAR(20) | DEFAULT 'ACTIVE' | 처리 상태 (ACTIVE, INACTIVE, COMPLETE) |
| `priority` | INTEGER | DEFAULT 0 | 처리 우선순위 |

## 예시 데이터

```sql
-- 예시 데이터 삽입
INSERT INTO migration_config 
  (target_table_name, target_column_name, status, priority)
VALUES
  -- 단일 컬럼 처리
  ('customer', 'phone', 'ACTIVE', 1),
  
  -- 여러 컬럼 동시 처리 (쉼표로 구분)
  ('order', 'recipient_phone,recipient_name', 'ACTIVE', 2);
```

## 주요 특징

1. **target_table_name이 PRIMARY KEY**: 하나의 테이블당 하나의 설정만 가능
2. **config_id 제거**: target_table_name 자체가 무결성을 보장
3. **다중 컬럼 지원**: target_column_name에 쉼표로 구분하여 여러 컬럼 지정 가능
   - 예: `'phone,email,address'`
4. **PK 자동 조회**: pk_column_name은 저장하지 않고 INFORMATION_SCHEMA에서 자동 조회 (복합키 지원)
5. **자동 상태 관리**: 처리 완료 후 status가 'COMPLETE'로 자동 업데이트되어 재실행 시 제외됨

## 주의사항

1. **target_table_name이 PRIMARY KEY**이므로 하나의 테이블당 하나의 설정만 가능합니다.
2. **pk_column_name은 저장하지 않습니다**. Primary Key는 INFORMATION_SCHEMA에서 자동으로 조회됩니다 (단일키 및 복합키 지원).
3. **target_column_name은 쉼표(,)로 구분**하여 여러 컬럼을 한 번에 처리할 수 있습니다.
4. **status**는 'ACTIVE' 또는 NULL인 설정만 처리됩니다. 'COMPLETE' 상태는 제외됩니다.
5. **처리 완료 후** status가 'COMPLETE'로 자동 업데이트되어 재실행 시 중복 처리를 방지합니다.

## 테이블 삭제 (필요시)

```sql
-- 테이블 삭제
DROP TABLE IF EXISTS migration_config;
```

## 데이터 조회 예시

```sql
-- 활성화된 모든 설정 조회 (COMPLETE 제외)
SELECT * FROM migration_config 
WHERE status IS NULL OR status = 'ACTIVE'
ORDER BY priority, target_table_name;

-- 특정 테이블 설정 조회
SELECT * FROM migration_config 
WHERE target_table_name = 'customer';

-- 설정 비활성화
UPDATE migration_config 
SET status = 'INACTIVE' 
WHERE target_table_name = 'customer';

-- 처리 완료 상태 확인
SELECT * FROM migration_config 
WHERE status = 'COMPLETE';

-- COMPLETE 상태를 다시 ACTIVE로 변경 (재처리 필요 시)
UPDATE migration_config 
SET status = 'ACTIVE' 
WHERE target_table_name = 'customer' AND status = 'COMPLETE';
```

