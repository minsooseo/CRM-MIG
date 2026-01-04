# STS 임포트 및 실행 가이드

## ⚠️ 주의사항

이 프로젝트를 STS에서 바로 실행하려면 다음 사항들을 확인하고 설정해야 합니다.

## 1. 디렉토리 구조 변경 필요

현재 패키지명은 `com.kt.yaap.mig_batch`로 변경되었지만, 실제 디렉토리 구조는 아직 `com/example/crmmig`로 되어 있습니다.

**해결 방법:**
- STS에서 프로젝트를 Import한 후
- 모든 Java 파일을 `src/main/java/com/kt/yaap/mig_batch/` 경로로 이동
- 또는 IDE에서 "Move" 기능을 사용하여 패키지 구조 자동 조정

## 2. 필수 사전 준비사항

### 2.1 PostgreSQL 데이터베이스 설정
```sql
-- 소스 DB, 타겟 DB, 배치 DB 생성
CREATE DATABASE source_db;
CREATE DATABASE target_db;
CREATE DATABASE batch_db;
```

### 2.2 마이그레이션 설정 테이블 생성
```sql
-- source_db에 생성
CREATE TABLE migration_config (
  config_id BIGSERIAL PRIMARY KEY,
  target_table_name VARCHAR(100) NOT NULL,
  target_column_name VARCHAR(500) NOT NULL,
  where_condition VARCHAR(500),
  status VARCHAR(20) DEFAULT 'ACTIVE',
  priority INTEGER DEFAULT 0
);
```

### 2.3 application.yml 설정
`src/main/resources/application.yml` 파일에서 다음을 실제 환경에 맞게 수정:

```yaml
spring:
  datasource:
    source:
      jdbc-url: jdbc:postgresql://localhost:5432/source_db  # 실제 DB 주소
      username: postgres                                     # 실제 사용자명
      password: password                                     # 실제 비밀번호
    target:
      jdbc-url: jdbc:postgresql://localhost:5432/target_db
      username: postgres
      password: password
    batch:
      jdbc-url: jdbc:postgresql://localhost:5432/batch_db
      username: postgres
      password: password
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

### 3.3 패키지 구조 정리
패키지명과 디렉토리 구조가 일치하도록 조정:
- `src/main/java/com/example/crmmig/` → `src/main/java/com/kt/yaap/mig_batch/`

## 4. 실행 전 체크리스트

- [ ] PostgreSQL 데이터베이스 접속 가능
- [ ] migration_config 테이블 생성 완료
- [ ] application.yml 데이터베이스 연결 정보 설정
- [ ] 패키지 구조가 `com.kt.yaap.mig_batch`로 정리됨
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
- application.yml 패키지 설정 변경 완료
- MyBatis 매퍼 설정 변경 완료

⚠️ 수동 작업 필요:
- 디렉토리 구조 변경 (`com/example/crmmig` → `com/kt/yaap/mig_batch`)
- 데이터베이스 연결 정보 설정
- migration_config 테이블 생성


