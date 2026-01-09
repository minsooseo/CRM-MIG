# ============================================
# Windows 환경 자동 설정 스크립트
# ============================================
# 관리자 권한으로 실행 필요
# PowerShell 실행 정책 변경: Set-ExecutionPolicy RemoteSigned -Scope CurrentUser

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "CRM 마이그레이션 환경 설정 시작" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 관리자 권한 확인
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "경고: 관리자 권한이 필요합니다. 일부 작업은 수동으로 수행해야 할 수 있습니다." -ForegroundColor Yellow
}

# ============================================
# 1. Java 환경 변수 설정
# ============================================

Write-Host "`n[1/5] Java 환경 변수 설정 중..." -ForegroundColor Green

$javaPath = "C:\Program Files\Eclipse Adoptium\jdk-8.0.472.8-hotspot"
if (Test-Path $javaPath) {
    try {
        [System.Environment]::SetEnvironmentVariable("JAVA_HOME", $javaPath, [System.EnvironmentVariableTarget]::Machine)
        Write-Host "✓ JAVA_HOME 설정 완료: $javaPath" -ForegroundColor Green
    } catch {
        Write-Host "✗ JAVA_HOME 설정 실패 (관리자 권한 필요): $_" -ForegroundColor Red
        Write-Host "  수동 설정 필요: 시스템 속성 → 환경 변수 → JAVA_HOME = $javaPath" -ForegroundColor Yellow
    }
} else {
    Write-Host "✗ Java 경로를 찾을 수 없습니다: $javaPath" -ForegroundColor Red
    Write-Host "  Java가 다른 위치에 설치되어 있는지 확인하세요." -ForegroundColor Yellow
}

# Java 버전 확인
try {
    $javaVersion = java -version 2>&1 | Select-Object -First 1
    Write-Host "✓ Java 버전 확인: $javaVersion" -ForegroundColor Green
} catch {
    Write-Host "✗ Java 명령을 찾을 수 없습니다." -ForegroundColor Red
}

# ============================================
# 2. Maven 설치 확인 및 안내
# ============================================

Write-Host "`n[2/5] Maven 설치 확인 중..." -ForegroundColor Green

try {
    $mavenVersion = mvn -version 2>&1 | Select-Object -First 1
    Write-Host "✓ Maven이 이미 설치되어 있습니다: $mavenVersion" -ForegroundColor Green
} catch {
    Write-Host "✗ Maven이 설치되어 있지 않습니다." -ForegroundColor Red
    Write-Host "`nMaven 설치 방법:" -ForegroundColor Yellow
    Write-Host "1. https://maven.apache.org/download.cgi 에서 다운로드" -ForegroundColor Yellow
    Write-Host "2. C:\Program Files\Apache\maven 에 압축 해제" -ForegroundColor Yellow
    Write-Host "3. 시스템 환경 변수 설정:" -ForegroundColor Yellow
    Write-Host "   - MAVEN_HOME = C:\Program Files\Apache\maven\apache-maven-3.9.x" -ForegroundColor Yellow
    Write-Host "   - Path에 %MAVEN_HOME%\bin 추가" -ForegroundColor Yellow
    Write-Host "`n또는 Chocolatey 사용:" -ForegroundColor Yellow
    Write-Host "   choco install maven -y" -ForegroundColor Cyan
}

# ============================================
# 3. PostgreSQL 설치 확인 및 안내
# ============================================

Write-Host "`n[3/5] PostgreSQL 설치 확인 중..." -ForegroundColor Green

try {
    $pgVersion = psql --version 2>&1
    Write-Host "✓ PostgreSQL이 이미 설치되어 있습니다: $pgVersion" -ForegroundColor Green
} catch {
    Write-Host "✗ PostgreSQL이 설치되어 있지 않습니다." -ForegroundColor Red
    Write-Host "`nPostgreSQL 설치 방법:" -ForegroundColor Yellow
    Write-Host "1. https://www.postgresql.org/download/windows/ 에서 다운로드" -ForegroundColor Yellow
    Write-Host "2. 설치 시 포트: 5432 (기본값)" -ForegroundColor Yellow
    Write-Host "3. 슈퍼유저 비밀번호 설정 (기억하세요!)" -ForegroundColor Yellow
    Write-Host "4. pgAdmin 4 설치 옵션 체크" -ForegroundColor Yellow
    Write-Host "`n또는 Chocolatey 사용:" -ForegroundColor Yellow
    Write-Host "   choco install postgresql --params '/Password:postgres' -y" -ForegroundColor Cyan
}

# PostgreSQL 서비스 확인
$pgServices = Get-Service | Where-Object { $_.Name -like "postgresql*" }
if ($pgServices) {
    Write-Host "✓ PostgreSQL 서비스 발견:" -ForegroundColor Green
    foreach ($service in $pgServices) {
        $status = if ($service.Status -eq "Running") { "실행 중" } else { "중지됨" }
        Write-Host "  - $($service.Name): $status" -ForegroundColor $(if ($service.Status -eq "Running") { "Green" } else { "Yellow" })
        if ($service.Status -ne "Running") {
            Write-Host "    시작하려면: Start-Service $($service.Name)" -ForegroundColor Cyan
        }
    }
} else {
    Write-Host "✗ PostgreSQL 서비스를 찾을 수 없습니다." -ForegroundColor Yellow
}

# ============================================
# 4. 프로젝트 경로 확인
# ============================================

Write-Host "`n[4/5] 프로젝트 경로 확인 중..." -ForegroundColor Green

$projectPath = Split-Path -Parent $MyInvocation.MyCommand.Path
if (Test-Path (Join-Path $projectPath "pom.xml")) {
    Write-Host "✓ 프로젝트 경로: $projectPath" -ForegroundColor Green
} else {
    Write-Host "✗ pom.xml을 찾을 수 없습니다. 스크립트를 프로젝트 루트에서 실행하세요." -ForegroundColor Red
    exit 1
}

# ============================================
# 5. 다음 단계 안내
# ============================================

Write-Host "`n[5/5] 설정 완료!" -ForegroundColor Green
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "다음 단계:" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "1. PostgreSQL이 설치되어 있다면:" -ForegroundColor Yellow
Write-Host "   - PostgreSQL 서비스 시작 확인" -ForegroundColor Yellow
Write-Host "   - psql -U postgres 로 접속 테스트" -ForegroundColor Cyan
Write-Host "`n2. 데이터베이스 생성 및 초기화:" -ForegroundColor Yellow
Write-Host "   - database_setup.sql 실행" -ForegroundColor Cyan
Write-Host "   - sample_data_setup.sql 실행" -ForegroundColor Cyan
Write-Host "`n3. application.yml 수정:" -ForegroundColor Yellow
Write-Host "   - PostgreSQL 비밀번호 설정" -ForegroundColor Cyan
Write-Host "`n4. 프로젝트 빌드:" -ForegroundColor Yellow
Write-Host "   cd '$projectPath'" -ForegroundColor Cyan
Write-Host "   mvn clean package -DskipTests" -ForegroundColor Cyan
Write-Host "`n5. 실행:" -ForegroundColor Yellow
Write-Host "   java -jar target/crm-mig-1.0.0.jar --spring.batch.job.names=migrationJob run.id=1" -ForegroundColor Cyan
Write-Host "`n자세한 내용은 SETUP_GUIDE.md를 참조하세요." -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
