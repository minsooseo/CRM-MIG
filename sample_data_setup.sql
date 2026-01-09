-- ============================================
-- 테스트용 샘플 테이블 및 데이터 생성
-- ============================================

-- migration_db 데이터베이스에 연결 후 실행하세요.

-- ============================================
-- 1. customer 테이블 생성
-- ============================================

DROP TABLE IF EXISTS customer CASCADE;

CREATE TABLE customer (
    customer_id SERIAL PRIMARY KEY,
    customer_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(100),
    address VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE customer IS '고객 테이블 (암호화 대상: phone)';
COMMENT ON COLUMN customer.customer_id IS '고객 ID (PK)';
COMMENT ON COLUMN customer.phone IS '전화번호 (암호화 대상)';

-- 샘플 데이터 삽입 (약 150건)
INSERT INTO customer (customer_name, phone, email, address) VALUES
('홍길동', '010-1234-5678', 'hong@example.com', '서울시 강남구 테헤란로 123'),
('김철수', '010-2345-6789', 'kim@example.com', '서울시 서초구 서초대로 456'),
('이영희', '010-3456-7890', 'lee@example.com', '서울시 송파구 올림픽로 789'),
('박민수', '010-4567-8901', 'park@example.com', '서울시 강동구 천호대로 321'),
('정수진', '010-5678-9012', 'jung@example.com', '서울시 마포구 홍대로 654'),
('최영호', '010-6789-0123', 'choi@example.com', '서울시 영등포구 여의대로 987'),
('한지은', '010-7890-1234', 'han@example.com', '서울시 종로구 세종대로 147'),
('윤동현', '010-8901-2345', 'yoon@example.com', '서울시 중구 명동길 258'),
('임소연', '010-9012-3456', 'lim@example.com', '서울시 용산구 한강대로 369'),
('강태우', '010-0123-4567', 'kang@example.com', '서울시 성동구 왕십리로 741'),
('송미라', '010-1357-9246', 'song@example.com', '경기도 성남시 분당구 정자로 852'),
('오세훈', '010-2468-1357', 'oh@example.com', '경기도 수원시 영통구 광교로 963'),
('유나영', '010-3691-4825', 'yu@example.com', '인천시 연수구 송도과학로 159'),
('조현우', '010-4826-1593', 'jo@example.com', '부산시 해운대구 해운대해변로 357'),
('신예진', '010-5937-2604', 'shin@example.com', '대전시 유성구 대학로 468'),
('류성호', '010-6048-3715', 'ryu@example.com', '광주시 북구 첨단과기로 579'),
('문혜진', '010-7159-4826', 'moon@example.com', '대구시 수성구 범어천로 680'),
('남도현', '010-8260-5937', 'nam@example.com', '울산시 남구 삼산로 791'),
('배수지', '010-9371-6048', 'bae@example.com', '세종시 조치원읍 세종로 802'),
('하준호', '010-0482-7159', 'ha@example.com', '강원도 춘천시 중앙로 913');

-- 추가 데이터 생성 (반복)
INSERT INTO customer (customer_name, phone, email, address)
SELECT 
    '고객' || generate_series(21, 150)::text,
    '010-' || LPAD(FLOOR(RANDOM() * 10000)::text, 4, '0') || '-' || LPAD(FLOOR(RANDOM() * 10000)::text, 4, '0'),
    'customer' || generate_series(21, 150)::text || '@example.com',
    '주소 ' || generate_series(21, 150)::text || '번지'
FROM generate_series(21, 150);

-- 데이터 확인
SELECT COUNT(*) as customer_count FROM customer;
SELECT * FROM customer LIMIT 10;

-- ============================================
-- 2. order 테이블 생성
-- ============================================

DROP TABLE IF EXISTS order CASCADE;

CREATE TABLE order (
    order_id SERIAL PRIMARY KEY,
    customer_id INTEGER,
    order_date DATE NOT NULL,
    recipient_name VARCHAR(100),
    recipient_phone VARCHAR(20),
    delivery_address VARCHAR(200),
    total_amount DECIMAL(10, 2),
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id)
);

COMMENT ON TABLE order IS '주문 테이블 (암호화 대상: recipient_phone, recipient_name)';
COMMENT ON COLUMN order.order_id IS '주문 ID (PK)';
COMMENT ON COLUMN order.recipient_name IS '수령인 이름 (암호화 대상)';
COMMENT ON COLUMN order.recipient_phone IS '수령인 전화번호 (암호화 대상)';

-- 샘플 데이터 삽입 (약 200건)
INSERT INTO order (customer_id, order_date, recipient_name, recipient_phone, delivery_address, total_amount, status)
SELECT 
    (RANDOM() * 149 + 1)::INTEGER,
    CURRENT_DATE - (RANDOM() * 90)::INTEGER,
    '수령인' || generate_series(1, 200)::text,
    '010-' || LPAD(FLOOR(RANDOM() * 10000)::text, 4, '0') || '-' || LPAD(FLOOR(RANDOM() * 10000)::text, 4, '0'),
    '배송주소 ' || generate_series(1, 200)::text || '번지',
    (RANDOM() * 100000 + 10000)::DECIMAL(10, 2),
    (ARRAY['PENDING', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED'])[FLOOR(RANDOM() * 5 + 1)::INTEGER]
FROM generate_series(1, 200);

-- 데이터 확인
SELECT COUNT(*) as order_count FROM order;
SELECT * FROM order LIMIT 10;

-- ============================================
-- 3. 인덱스 생성 (성능 최적화)
-- ============================================

CREATE INDEX idx_customer_phone ON customer(phone);
CREATE INDEX idx_order_recipient_phone ON order(recipient_phone);
CREATE INDEX idx_order_customer_id ON order(customer_id);

-- ============================================
-- 4. 통계 확인
-- ============================================

SELECT 
    'customer' as table_name,
    COUNT(*) as row_count
FROM customer
UNION ALL
SELECT 
    'order' as table_name,
    COUNT(*) as row_count
FROM order;
