# ============================================
# 데이터베이스 초기화 스크립트 (PostgreSQL)
# ============================================
# PostgreSQL이 설치되어 있고 서비스가 실행 중이어야 합니다.

param(
    [string]$DbPassword = "",
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432,
    [string]$DbUser = "postgres",
    [string]$DbName = "migration_db"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "PostgreSQL 데이터베이스 초기화" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# PostgreSQL 설치 확인
try {
    $pgVersion = psql --version 2>&1
    Write-Host "✓ PostgreSQL 확인: $pgVersion" -ForegroundColor Green
} catch {
    Write-Host "✗ PostgreSQL이 설치되어 있지 않거나 PATH에 없습니다." -ForegroundColor Red
    Write-Host "  SETUP_GUIDE.md를 참조하여 PostgreSQL을 설치하세요." -ForegroundColor Yellow
    exit 1
}

# 비밀번호 확인
if ([string]::IsNullOrWhiteSpace($DbPassword)) {
    $securePassword = Read-Host "PostgreSQL '$DbUser' 사용자 비밀번호를 입력하세요" -AsSecureString
    $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    $DbPassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
}

# PGPASSWORD 환경 변수 설정 (psql이 비밀번호를 묻지 않도록)
$env:PGPASSWORD = $DbPassword

# PostgreSQL 서비스 확인
$pgServices = Get-Service | Where-Object { $_.Name -like "postgresql*" -and $_.Status -eq "Running" }
if (-not $pgServices) {
    Write-Host "경고: PostgreSQL 서비스가 실행 중이 아닙니다." -ForegroundColor Yellow
    Write-Host "  PostgreSQL 서비스를 시작하세요." -ForegroundColor Yellow
    $pgServices = Get-Service | Where-Object { $_.Name -like "postgresql*" }
    if ($pgServices) {
        Write-Host "  사용 가능한 서비스:" -ForegroundColor Yellow
        foreach ($service in $pgServices) {
            Write-Host "    - $($service.Name) ($($service.Status))" -ForegroundColor Yellow
            Write-Host "      시작하려면: Start-Service $($service.Name)" -ForegroundColor Cyan
        }
    }
    exit 1
}

Write-Host "✓ PostgreSQL 서비스 실행 중" -ForegroundColor Green

# 연결 테스트
Write-Host "`n[1/4] PostgreSQL 연결 테스트 중..." -ForegroundColor Green
try {
    $testResult = psql -h $DbHost -p $DbPort -U $DbUser -d postgres -c "SELECT version();" 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ 연결 성공" -ForegroundColor Green
    } else {
        Write-Host "✗ 연결 실패: $testResult" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "✗ 연결 실패: $_" -ForegroundColor Red
    exit 1
}

# 데이터베이스 생성
Write-Host "`n[2/4] 데이터베이스 생성 중..." -ForegroundColor Green
$createDbQuery = "SELECT 1 FROM pg_database WHERE datname = '$DbName'"
$dbExists = psql -h $DbHost -p $DbPort -U $DbUser -d postgres -t -c $createDbQuery 2>&1

if ($dbExists -match "1") {
    Write-Host "  데이터베이스 '$DbName'가 이미 존재합니다." -ForegroundColor Yellow
    $overwrite = Read-Host "  기존 데이터베이스를 삭제하고 다시 생성하시겠습니까? (y/N)"
    if ($overwrite -eq "y" -or $overwrite -eq "Y") {
        Write-Host "  기존 데이터베이스 삭제 중..." -ForegroundColor Yellow
        psql -h $DbHost -p $DbPort -U $DbUser -d postgres -c "DROP DATABASE IF EXISTS $DbName;" 2>&1 | Out-Null
        psql -h $DbHost -p $DbPort -U $DbUser -d postgres -c "CREATE DATABASE $DbName;" 2>&1 | Out-Null
        Write-Host "✓ 데이터베이스 재생성 완료" -ForegroundColor Green
    } else {
        Write-Host "  기존 데이터베이스를 사용합니다." -ForegroundColor Yellow
    }
} else {
    psql -h $DbHost -p $DbPort -U $DbUser -d postgres -c "CREATE DATABASE $DbName;" 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ 데이터베이스 생성 완료" -ForegroundColor Green
    } else {
        Write-Host "✗ 데이터베이스 생성 실패" -ForegroundColor Red
        exit 1
    }
}

# migration_config 테이블 생성
Write-Host "`n[3/4] migration_config 테이블 생성 중..." -ForegroundColor Green
$dbSetupScript = Join-Path $PSScriptRoot "database_setup.sql"
if (Test-Path $dbSetupScript) {
    psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -f $dbSetupScript 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ migration_config 테이블 생성 완료" -ForegroundColor Green
    } else {
        Write-Host "✗ migration_config 테이블 생성 실패" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "✗ database_setup.sql 파일을 찾을 수 없습니다: $dbSetupScript" -ForegroundColor Red
    exit 1
}

# 샘플 데이터 생성
Write-Host "`n[4/4] 샘플 테이블 및 데이터 생성 중..." -ForegroundColor Green
$sampleDataScript = Join-Path $PSScriptRoot "sample_data_setup.sql"
if (Test-Path $sampleDataScript) {
    psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -f $sampleDataScript 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ 샘플 데이터 생성 완료" -ForegroundColor Green
    } else {
        Write-Host "✗ 샘플 데이터 생성 실패" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "✗ sample_data_setup.sql 파일을 찾을 수 없습니다: $sampleDataScript" -ForegroundColor Red
    exit 1
}

# 결과 확인
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "초기화 완료!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan

Write-Host "`n데이터베이스 상태 확인:" -ForegroundColor Yellow
$tableCheck = psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -t -c @"
SELECT 
    'customer' as table_name, COUNT(*) as row_count FROM customer
UNION ALL
SELECT 
    'order' as table_name, COUNT(*) as row_count FROM order
UNION ALL
SELECT 
    'migration_config' as table_name, COUNT(*) as row_count FROM migration_config;
"@ 2>&1

Write-Host $tableCheck

Write-Host "`n다음 단계:" -ForegroundColor Cyan
Write-Host "1. application.yml에서 PostgreSQL 비밀번호 확인/수정" -ForegroundColor Yellow
Write-Host "2. 프로젝트 빌드: mvn clean package -DskipTests" -ForegroundColor Yellow
Write-Host "3. 배치 실행: java -jar target/crm-mig-1.0.0.jar --spring.batch.job.names=migrationJob run.id=1" -ForegroundColor Yellow

# PGPASSWORD 환경 변수 제거 (보안)
Remove-Item Env:\PGPASSWORD
