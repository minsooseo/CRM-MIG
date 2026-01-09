# 빠른 시작 가이드

이 가이드는 로컬 PC에 CRM 마이그레이션 환경을 빠르게 구성하는 방법을 안내합니다.

## 사전 요구사항 체크

다음 명령으로 설치 상태를 확인하세요:

```powershell
java -version      # Java 8 이상 필요
mvn -version       # Maven 필요
psql --version     # PostgreSQL 필요
```

## 단계별 설치 및 실행

### 1단계: Java 설정 (이미 설치됨)

Java는 이미 설치되어 있습니다. 환경 변수만 확인하세요.

```powershell
# Java 경로 확인
echo $env:JAVA_HOME

# JAVA_HOME이 비어있으면 설정 (관리자 권한 필요)
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-8.0.472.8-hotspot", [System.EnvironmentVariableTarget]::Machine)
```

### 2단계: Maven 설치

**방법 1: Chocolatey 사용 (권장, 관리자 권한 필요)**

```powershell
# Chocolatey 설치 (처음 사용하는 경우)
Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))

# Maven 설치
choco install maven -y
```

**방법 2: 수동 설치**

1. https://maven.apache.org/download.cgi 에서 최신 버전 다운로드
2. `C:\Program Files\Apache\maven`에 압축 해제
3. 시스템 환경 변수 설정:
   - `MAVEN_HOME` = `C:\Program Files\Apache\maven\apache-maven-3.9.x`
   - `Path`에 `%MAVEN_HOME%\bin` 추가

### 3단계: PostgreSQL 설치

**방법 1: Chocolatey 사용 (관리자 권한 필요)**

```powershell
choco install postgresql --params '/Password:postgres' -y
```

**방법 2: 수동 설치**

1. https://www.postgresql.org/download/windows/ 에서 다운로드
2. 설치 시:
   - 포트: `5432` (기본값)
   - 비밀번호: 원하는 비밀번호 설정 (예: `postgres`)
   - pgAdmin 4 설치 옵션 체크

3. 설치 후 서비스 확인:
```powershell
Get-Service postgresql*
```

### 4단계: 데이터베이스 초기화

**자동 스크립트 사용 (권장)**

```powershell
# 프로젝트 루트 디렉토리에서 실행
.\init_database.ps1

# 또는 비밀번호 직접 지정
.\init_database.ps1 -DbPassword "your_password"
```

**수동 실행**

```powershell
# 1. 데이터베이스 생성
psql -U postgres -c "CREATE DATABASE migration_db;"

# 2. migration_config 테이블 생성
psql -U postgres -d migration_db -f database_setup.sql

# 3. 샘플 데이터 생성
psql -U postgres -d migration_db -f sample_data_setup.sql
```

### 5단계: 애플리케이션 설정

`src/main/resources/application.yml` 파일을 열어 PostgreSQL 비밀번호를 수정하세요:

```yaml
spring:
  datasource:
    password: postgres  # PostgreSQL 설치 시 설정한 비밀번호로 변경
```

### 6단계: 프로젝트 빌드

```powershell
# 프로젝트 루트 디렉토리에서
mvn clean package -DskipTests
```

### 7단계: 배치 실행

```powershell
java -jar target/crm-mig-1.0.0.jar --spring.batch.job.names=migrationJob run.id=1
```

## 실행 결과 확인

### 데이터베이스에서 확인

```sql
-- 암호화된 데이터 확인
SELECT customer_id, phone, phone_bak FROM customer LIMIT 10;

-- 백업 컬럼 확인 (원본 데이터)
SELECT customer_id, phone_bak FROM customer WHERE phone_bak IS NOT NULL LIMIT 10;

-- migration_config 상태 확인
SELECT * FROM migration_config;

-- 배치 실행 이력 확인
SELECT * FROM batch_job_execution ORDER BY create_time DESC LIMIT 5;
```

### 예상 결과

- `phone` 컬럼: `[ENCRYPTED]010-1234-5678` (Mock 암호화)
- `phone_bak` 컬럼: `010-1234-5678` (원본 백업)
- `migration_config.status`: `COMPLETE` (처리 완료)

## 문제 해결

### Maven 명령을 찾을 수 없음

- 환경 변수 설정 후 **새 터미널**을 열어야 합니다
- 또는 `refreshenv` 명령 실행 (Chocolatey 사용 시)

### PostgreSQL 연결 실패

```powershell
# 서비스 확인 및 시작
Get-Service postgresql*
Start-Service postgresql-x64-16  # 버전에 따라 이름이 다를 수 있음

# 연결 테스트
psql -U postgres -d postgres
```

### 데이터베이스 초기화 실패

- PostgreSQL 서비스가 실행 중인지 확인
- 비밀번호가 올바른지 확인
- `init_database.ps1` 스크립트를 관리자 권한으로 실행

## 자세한 내용

- [`SETUP_GUIDE.md`](SETUP_GUIDE.md) - 상세한 설치 가이드
- [`README.md`](README.md) - 프로젝트 전체 문서
- [`LINUX_EXECUTION_GUIDE.md`](LINUX_EXECUTION_GUIDE.md) - 실행 가이드
