# STS 임포트 및 실행 가이드

## ⚠️ 주의사항

이 프로젝트를 STS에서 바로 실행하려면 다음 사항들을 확인하고 설정해야 합니다.

## 1. 디렉토리 구조 변경 필요

**✅ 현재 상태:**
- 패키지명: `com.kt.yaap.mig_batch`
- 디렉토리 구조: `src/main/java/com/kt/yaap/mig_batch/`
- 패키지명과 디렉토리 구조가 일치합니다.

## 2. 필수 사전 준비사항

### 2.1 PostgreSQL 데이터베이스 설정
```sql
-- 단일 데이터베이스 생성 (설정, 대상 테이블, 배치 메타데이터 모두 포함)
CREATE DATABASE migration_db;
```

### 2.2 마이그레이션 설정 테이블 생성
```sql
-- migration_db에 생성
CREATE TABLE migration_config (
  target_table_name VARCHAR(100) PRIMARY KEY,
  target_column_name VARCHAR(500) NOT NULL,
  status VARCHAR(20) DEFAULT 'ACTIVE',
  priority INTEGER DEFAULT 0
);

-- 컬럼 설명
COMMENT ON COLUMN migration_config.status IS '처리 상태 (ACTIVE, INACTIVE, COMPLETE)';
```

### 2.3 application.yml 설정
`src/main/resources/application.yml` 파일에서 다음을 실제 환경에 맞게 수정:

```yaml
spring:
  # 단일 데이터소스 설정
  datasource:
    url: jdbc:postgresql://localhost:5432/migration_db  # 실제 DB 주소
    username: postgres                                   # 실제 사용자명
    password: password                                   # 실제 비밀번호
    driver-class-name: org.postgresql.Driver
  
  mybatis:
    mapper-locations: classpath:mapper/*.xml
    type-aliases-package: com.kt.yaap.mig_batch.model
    configuration:
      map-underscore-to-camel-case: true
      default-fetch-size: 1000
      default-statement-timeout: 30
      log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl  # MyBatis 쿼리 로그

# 로깅 설정
logging:
  level:
    root: INFO
    com.kt.yaap.mig_batch: DEBUG
    org.springframework.batch: DEBUG
    com.kt.yaap.mig_batch.mapper: DEBUG  # MyBatis SQL 로그
    org.apache.ibatis: TRACE  # 바인딩 파라미터 로그

migration:
  chunk-size: 1000
  config-table: migration_config
  schema-name: public  # 데이터베이스 스키마명
```

## 3. STS에서 Import 방법

### 3.1 프로젝트 Import
1. STS 실행
2. File → Import
3. Maven → Existing Maven Projects
4. 프로젝트 루트 디렉토리 선택
5. Finish

### 3.2 Maven Update
1. 프로젝트 우클릭
2. Maven → Update Project...
3. Force Update of Snapshots/Releases 체크
4. OK

### 3.3 패키지 구조 확인
✅ 패키지명과 디렉토리 구조가 이미 일치합니다:
- `src/main/java/com/kt/yaap/mig_batch/`

## 4. 실행 전 체크리스트

- [ ] PostgreSQL 데이터베이스 접속 가능
- [ ] migration_db 데이터베이스 생성 완료
- [ ] migration_config 테이블 생성 완료
- [ ] application.yml 데이터베이스 연결 정보 설정 (단일 데이터소스)
- [ ] 패키지 구조 확인 (`com.kt.yaap.mig_batch`)
- [ ] Maven 빌드 성공 (프로젝트 우클릭 → Maven → Update Project)
- [ ] SafeDB 라이브러리 추가 (필요시)

## 5. 실행 방법

### 방법 1: STS에서 직접 실행
1. `CrmMigrationApplication.java` 파일 열기
2. Run As → Java Application

### 방법 2: Spring Boot App으로 실행
1. 프로젝트 우클릭
2. Run As → Spring Boot App

### 방법 3: Maven으로 실행
```bash
mvn spring-boot:run
```

## 6. 실행 시 주의사항

### 6.1 배치 Job 자동 실행 방지
기본적으로 `application.yml`에서 `spring.batch.job.enabled=false`로 설정되어 있어
애플리케이션 시작 시 자동으로 실행되지 않습니다.

### 6.2 배치 실행 방법
- Command Line: `--spring.batch.job.enabled=true --spring.batch.job.names=migrationJob`
- 스케줄러: `MigrationScheduler`의 cron 설정에 따라 자동 실행

## 7. 문제 해결

### 7.1 컴파일 오류
- Maven → Update Project 실행
- Project → Clean → Build

### 7.2 데이터베이스 연결 오류
- PostgreSQL 서버 실행 확인
- application.yml의 연결 정보 확인
- 방화벽 설정 확인

### 7.3 패키지 관련 오류
- 패키지 구조와 디렉토리 구조 일치 확인
- Project → Clean → Build

## 8. 현재 상태

✅ 완료된 사항:
- 모든 Java 파일의 패키지명 변경 완료
- 디렉토리 구조 정리 완료 (`com/kt/yaap/mig_batch`)
- application.yml 설정 완료 (단일 데이터소스)
- MyBatis 매퍼 설정 완료
- 테이블별 Step 동적 생성 구현 완료 (순차 실행)
- 복합키 지원 구현 완료
- 자동 상태 관리 구현 완료 (COMPLETE)
- Reader가 실제 테이블 레코드를 직접 읽음
- read_count가 실제 처리한 레코드 수를 정확하게 반영
- 백업 컬럼 소문자 (_bak) 사용 (PostgreSQL 호환)
- MyBatis 쿼리 로깅 설정 추가

⚠️ 수동 작업 필요:
- 데이터베이스 연결 정보 설정 (application.yml)
- migration_db 데이터베이스 생성
- migration_config 테이블 생성
