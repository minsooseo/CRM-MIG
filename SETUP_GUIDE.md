# 로컬 개발 환경 설정 가이드 (Windows)

이 가이드는 Windows PC에 CRM 마이그레이션 배치 프로그램 실행 환경을 구축하는 방법을 안내합니다.

## 현재 시스템 상태

- ✅ Java: 설치됨 (OpenJDK 1.8.0_472)
  - 경로: `C:\Program Files\Eclipse Adoptium\jdk-8.0.472.8-hotspot`
- ❌ Maven: 설치 필요
- ❌ PostgreSQL: 설치 필요

## 1. Java 환경 변수 설정

Java는 이미 설치되어 있지만 `JAVA_HOME` 환경 변수를 설정해야 합니다.

### 수동 설정 방법

1. **시스템 속성 열기**
   - `Win + R` → `sysdm.cpl` 입력 → Enter
   - 또는 제어판 → 시스템 → 고급 시스템 설정

2. **환경 변수 설정**
   - "환경 변수" 버튼 클릭
   - "시스템 변수" 섹션에서 "새로 만들기" 클릭
   - 변수 이름: `JAVA_HOME`
   - 변수 값: `C:\Program Files\Eclipse Adoptium\jdk-8.0.472.8-hotspot`
   - 확인 클릭

3. **PATH 확인/추가**
   - 시스템 변수에서 `Path` 선택 → 편집
   - 다음 경로가 있는지 확인 (없으면 추가):
     - `%JAVA_HOME%\bin`
   - 확인 클릭

4. **새 터미널에서 확인**
   ```powershell
   java -version
   echo $env:JAVA_HOME
   ```

### PowerShell 스크립트로 설정 (관리자 권한 필요)

```powershell
# 관리자 권한으로 PowerShell 실행 후 실행
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-8.0.472.8-hotspot", [System.EnvironmentVariableTarget]::Machine)
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
```

## 2. Maven 설치

### 방법 1: 수동 설치 (권장)

1. **Maven 다운로드**
   - https://maven.apache.org/download.cgi 에서 최신 버전 다운로드
   - `apache-maven-3.9.x-bin.zip` 다운로드

2. **압축 해제**
   - `C:\Program Files\Apache\maven` 또는 원하는 위치에 압축 해제

3. **환경 변수 설정**
   - 시스템 변수에 `MAVEN_HOME` 추가
     - 변수 이름: `MAVEN_HOME`
     - 변수 값: `C:\Program Files\Apache\maven\apache-maven-3.9.x`
   - `Path`에 추가: `%MAVEN_HOME%\bin`

4. **설치 확인**
   ```powershell
   mvn -version
   ```

### 방법 2: Chocolatey 사용 (관리자 권한 필요)

```powershell
# Chocolatey 설치 (처음 사용하는 경우)
Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))

# Maven 설치
choco install maven -y
```

### 방법 3: Scoop 사용

```powershell
# Scoop 설치 (처음 사용하는 경우)
Set-ExecutionPolicy RemoteSigned -Scope CurrentUser
irm get.scoop.sh | iex

# Maven 설치
scoop install maven
```

## 3. PostgreSQL 설치

### 방법 1: 공식 설치 프로그램 (권장)

1. **PostgreSQL 다운로드**
   - https://www.postgresql.org/download/windows/
   - PostgreSQL 14 이상 버전 다운로드 (예: PostgreSQL 16)

2. **설치 실행**
   - 설치 프로그램 실행
   - 설치 경로: 기본값 유지 (일반적으로 `C:\Program Files\PostgreSQL\16`)
   - 포트: `5432` (기본값 유지)
   - 슈퍼유저 비밀번호: **기억할 비밀번호 설정** (예: `postgres` 또는 원하는 비밀번호)
   - 로캘: 기본값 유지 또는 한국어
   - pgAdmin 4 설치 옵션: 체크 (DB 관리 도구)

3. **설치 확인**
   ```powershell
   # PostgreSQL이 PATH에 추가되었는지 확인
   psql --version
   
   # 서비스 상태 확인
   Get-Service postgresql*
   ```

4. **환경 변수 확인**
   - PostgreSQL 설치 시 자동으로 PATH에 추가됩니다
   - 수동으로 추가해야 하는 경우:
     - `Path`에 `C:\Program Files\PostgreSQL\16\bin` 추가

### 방법 2: Chocolatey 사용 (관리자 권한 필요)

```powershell
choco install postgresql --params '/Password:postgres' -y
```

### 설치 후 확인

```powershell
# PostgreSQL 서비스 시작 (자동 시작 설정됨)
Start-Service postgresql-x64-16  # 버전에 따라 이름이 다를 수 있음

# psql로 접속 테스트
psql -U postgres -d postgres
# 비밀번호 입력 후 다음 명령으로 확인:
# SELECT version();
# \q
```

## 4. 데이터베이스 초기 설정

설치 완료 후 아래 SQL 스크립트를 실행하세요.

### 4.1 데이터베이스 생성

pgAdmin 4를 사용하거나 명령줄에서 실행:

```powershell
# 명령줄에서 실행
psql -U postgres -c "CREATE DATABASE migration_db;"
```

또는 `database_setup.sql` 스크립트 참조 (별도 생성 예정)

### 4.2 샘플 테이블 및 데이터 생성

`sample_data_setup.sql` 스크립트 실행 (별도 생성 예정)

### 4.3 마이그레이션 설정 테이블 생성

`migration_config_setup.sql` 스크립트 실행 (별도 생성 예정)

## 5. 애플리케이션 설정

### application.yml 수정

`src/main/resources/application.yml` 파일에서 다음 설정을 수정하세요:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/migration_db
    username: postgres
    password: [설정한_비밀번호]  # PostgreSQL 설치 시 설정한 비밀번호
```

## 6. 프로젝트 빌드 및 실행

### 빌드

```powershell
cd C:\Users\Administrator\Downloads\CRM-MIG-main\CRM-MIG-main
mvn clean package -DskipTests
```

### 실행

```powershell
java -jar target/crm-mig-1.0.0.jar --spring.batch.job.names=migrationJob run.id=1
```

## 문제 해결

### Maven 명령을 찾을 수 없음

- 환경 변수 설정 후 새 터미널을 열어야 합니다
- PATH 확인: `echo $env:Path`
- Maven 경로 확인: `where.exe mvn`

### PostgreSQL 연결 실패

- PostgreSQL 서비스가 실행 중인지 확인:
  ```powershell
  Get-Service postgresql*
  ```
- 방화벽 설정 확인
- 비밀번호 확인 (대소문자 구분)

### Java 버전 문제

- Java 8 이상인지 확인: `java -version`
- JAVA_HOME 설정 확인: `echo $env:JAVA_HOME`

## 다음 단계

설치가 완료되면:
1. `database_setup.sql` 실행하여 데이터베이스 생성
2. `sample_data_setup.sql` 실행하여 샘플 데이터 생성
3. `application.yml`에서 DB 비밀번호 설정
4. 프로젝트 빌드 및 실행
