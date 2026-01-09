# 환경 구성 요약

로컬 PC에 CRM 마이그레이션 배치 프로그램 실행 환경을 구성하기 위한 모든 준비가 완료되었습니다.

## ✅ 완료된 작업

### 1. 소프트웨어 확인 및 설정
- ✅ Java 설치 확인 (OpenJDK 1.8.0_472)
- ✅ pom.xml Java 버전 1.8로 업데이트
- ⚠️ Maven 설치 필요 (설치 가이드 제공)
- ⚠️ PostgreSQL 설치 필요 (설치 가이드 제공)

### 2. 애플리케이션 코드 수정
- ✅ SafeDBUtil Mock 암호화 개선 (`[ENCRYPTED]` 접두사 추가)
- ✅ application.yml에 비밀번호 설정 안내 주석 추가
- ✅ Java 버전 호환성 문제 해결

### 3. 데이터베이스 스크립트 생성
- ✅ `database_setup.sql` - migration_config 테이블 생성 및 샘플 설정
- ✅ `sample_data_setup.sql` - customer, order 테이블 및 샘플 데이터 (약 350건)
- ✅ `init_database.ps1` - 데이터베이스 자동 초기화 스크립트

### 4. 설치 및 실행 가이드 작성
- ✅ `SETUP_GUIDE.md` - 상세한 설치 가이드
- ✅ `QUICK_START.md` - 빠른 시작 가이드
- ✅ `setup_windows.ps1` - 환경 설정 자동화 스크립트

## 📋 다음 단계 (사용자 작업 필요)

### 1단계: Maven 설치

**Chocolatey 사용 (권장):**
```powershell
# Chocolatey 설치 (처음 사용하는 경우)
Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))

# Maven 설치
choco install maven -y
```

**또는 수동 설치:** [`SETUP_GUIDE.md`](SETUP_GUIDE.md) 참조

### 2단계: PostgreSQL 설치

**Chocolatey 사용:**
```powershell
choco install postgresql --params '/Password:postgres' -y
```

**또는 수동 설치:** [`SETUP_GUIDE.md`](SETUP_GUIDE.md) 참조

### 3단계: 데이터베이스 초기화

```powershell
# 프로젝트 루트에서 실행
.\init_database.ps1
```

또는 수동 실행:
```powershell
psql -U postgres -c "CREATE DATABASE migration_db;"
psql -U postgres -d migration_db -f database_setup.sql
psql -U postgres -d migration_db -f sample_data_setup.sql
```

### 4단계: 애플리케이션 설정

`src/main/resources/application.yml` 파일을 열어 PostgreSQL 비밀번호를 수정:
```yaml
spring:
  datasource:
    password: postgres  # PostgreSQL 설치 시 설정한 비밀번호로 변경
```

### 5단계: 프로젝트 빌드

```powershell
mvn clean package -DskipTests
```

### 6단계: 배치 실행

```powershell
java -jar target/crm-mig-1.0.0.jar --spring.batch.job.names=migrationJob run.id=1
```

## 📁 생성된 파일 목록

### 데이터베이스 스크립트
- `database_setup.sql` - migration_config 테이블 생성
- `sample_data_setup.sql` - 샘플 테이블 및 데이터 생성

### 자동화 스크립트
- `init_database.ps1` - 데이터베이스 자동 초기화
- `setup_windows.ps1` - 환경 설정 확인 및 안내

### 가이드 문서
- `SETUP_GUIDE.md` - 상세 설치 가이드
- `QUICK_START.md` - 빠른 시작 가이드
- `ENVIRONMENT_SETUP_SUMMARY.md` - 이 파일 (환경 구성 요약)

## 🔍 확인 사항

### 설치 확인 명령
```powershell
java -version      # Java 8 이상
mvn -version       # Maven
psql --version     # PostgreSQL
```

### 데이터베이스 연결 테스트
```powershell
psql -U postgres -d migration_db -c "SELECT version();"
```

### 데이터 확인
```sql
-- 샘플 데이터 확인
SELECT COUNT(*) FROM customer;  -- 약 150건
SELECT COUNT(*) FROM order;     -- 약 200건
SELECT * FROM migration_config; -- 2건 (customer, order)
```

## 🎯 실행 결과 예상

배치 실행 후:

1. **customer 테이블**
   - `phone` 컬럼: `[ENCRYPTED]010-1234-5678` (암호화됨)
   - `phone_bak` 컬럼: `010-1234-5678` (원본 백업)

2. **order 테이블**
   - `recipient_phone` 컬럼: `[ENCRYPTED]010-2345-6789` (암호화됨)
   - `recipient_phone_bak` 컬럼: `010-2345-6789` (원본 백업)
   - `recipient_name` 컬럼: `[ENCRYPTED]수령인1` (암호화됨)
   - `recipient_name_bak` 컬럼: `수령인1` (원본 백업)

3. **migration_config 테이블**
   - `status` 컬럼: `COMPLETE` (처리 완료)

4. **배치 메타데이터**
   - `batch_job_execution`: Job 실행 이력
   - `batch_step_execution`: Step 실행 이력 (read_count, write_count 확인 가능)

## 📚 참고 문서

- [`README.md`](README.md) - 프로젝트 전체 문서
- [`SETUP_GUIDE.md`](SETUP_GUIDE.md) - 상세 설치 가이드
- [`QUICK_START.md`](QUICK_START.md) - 빠른 시작 가이드
- [`LINUX_EXECUTION_GUIDE.md`](LINUX_EXECUTION_GUIDE.md) - Linux 실행 가이드

## ⚠️ 주의사항

1. **PostgreSQL 비밀번호**: 설치 시 설정한 비밀번호를 `application.yml`에 반드시 반영해야 합니다.
2. **환경 변수**: Maven 설치 후 새 터미널을 열어야 PATH가 적용됩니다.
3. **PostgreSQL 서비스**: 데이터베이스 작업 전에 PostgreSQL 서비스가 실행 중이어야 합니다.
4. **SafeDB Mock**: 현재는 테스트용 Mock 암호화(`[ENCRYPTED]` 접두사)를 사용합니다. 실제 운영 환경에서는 실제 SafeDB 라이브러리로 교체해야 합니다.

## 🆘 문제 해결

문제가 발생하면:
1. [`QUICK_START.md`](QUICK_START.md)의 "문제 해결" 섹션 참조
2. [`SETUP_GUIDE.md`](SETUP_GUIDE.md)의 "트러블슈팅" 섹션 참조
3. 콘솔 로그 확인 (DEBUG 레벨로 상세 로그 출력)

---

**환경 구성 준비 완료!** 위의 단계를 순서대로 따라하시면 회사와 동일한 환경을 로컬 PC에 구성할 수 있습니다.
