-- ============================================
-- CRM 마이그레이션 데이터베이스 초기 설정
-- ============================================

-- 1. 데이터베이스 생성 (postgres 사용자로 실행)
-- CREATE DATABASE migration_db;

-- 2. migration_db 데이터베이스로 연결 후 실행

-- 스키마 확인 (기본: public)
-- SELECT current_schema();

-- ============================================
-- migration_config 테이블 생성
-- ============================================

-- 기존 테이블이 있으면 삭제 (주의: 데이터가 모두 삭제됩니다)
DROP TABLE IF EXISTS migration_config;

-- 마이그레이션 설정 테이블 생성
CREATE TABLE migration_config (
  target_table_name VARCHAR(100) PRIMARY KEY,
  target_column_name VARCHAR(500) NOT NULL,  -- 쉼표로 구분하여 여러 컬럼 지정 가능
  where_condition VARCHAR(500),
  status VARCHAR(20) DEFAULT 'ACTIVE',
  priority INTEGER DEFAULT 0
);

-- 컬럼 설명 추가
COMMENT ON COLUMN migration_config.target_table_name IS '대상 테이블명 (PRIMARY KEY)';
COMMENT ON COLUMN migration_config.target_column_name IS '대상 컬럼명 (SafeDB 적용할 컬럼, 쉼표로 구분하여 여러 컬럼 지정 가능)';
COMMENT ON COLUMN migration_config.where_condition IS 'WHERE 조건 (선택사항)';
COMMENT ON COLUMN migration_config.status IS '처리 상태 (ACTIVE, INACTIVE, COMPLETE)';
COMMENT ON COLUMN migration_config.priority IS '처리 우선순위 (낮을수록 먼저 실행)';

-- ============================================
-- 샘플 데이터 삽입 (나중에 sample_data_setup.sql에서 생성된 테이블용)
-- ============================================

-- customer 테이블 설정 (phone 컬럼 암호화)
INSERT INTO migration_config 
  (target_table_name, target_column_name, where_condition, status, priority)
VALUES
  ('customer', 'phone', NULL, 'ACTIVE', 1)
ON CONFLICT (target_table_name) DO NOTHING;

-- order 테이블 설정 (recipient_phone, recipient_name 컬럼 암호화)
INSERT INTO migration_config 
  (target_table_name, target_column_name, where_condition, status, priority)
VALUES
  ('order', 'recipient_phone,recipient_name', NULL, 'ACTIVE', 2)
ON CONFLICT (target_table_name) DO NOTHING;

-- 확인
SELECT * FROM migration_config ORDER BY priority, target_table_name;
