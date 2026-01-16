# 🎯 Spring Profile 사용 가이드

## 📋 Profile 개요

프로젝트는 환경별로 최적화된 4개의 Profile을 제공합니다.

| Profile | 환경 | DB | Chunk | 로그 | 성능 | 용도 |
|---------|------|----|----|------|------|------|
| **local** | 개발자 PC | localhost | 1,000 | INFO/WARN | ⭐⭐⭐⭐ | 일상 개발/테스트 |
| **dev** | 개발 서버 | dev-db | 3,000 | DEBUG/INFO | ⭐⭐⭐ | 통합 테스트 |
| **prod** | 운영 서버 | prod-db | 5,000 | INFO/WARN | ⭐⭐⭐⭐⭐ | 실제 운영 |
| **debug** | 문제 해결 | localhost | 100 | DEBUG | ⭐ | 버그 추적 |

---

## 🚀 실행 방법

### Local (기본값)

```bash
# Profile 미지정 시 자동으로 local 사용
mvn spring-boot:run

# 또는 JAR 실행
java -jar crm-mig-1.0.0.jar

# 명시적 지정
java -jar crm-mig-1.0.0.jar --spring.profiles.active=local
```

**특징:**
- localhost PostgreSQL 사용
- 빠른 성능 (로그 최소화)
- chunk-size: 1000
- 로그 파일: `logs/crm-migration-local.log`

---

### Dev (개발 서버)

```bash
# 환경변수 설정 (선택사항)
export DB_PASSWORD=dev_password

java -jar crm-mig-1.0.0.jar \
  --spring.profiles.active=dev \
  --spring.batch.job.enabled=true \
  --spring.batch.job.names=migrationJob
```

**특징:**
- 개발 서버 DB 연결
- DEBUG 로그로 개발 중 문제 파악
- chunk-size: 3000
- SQL 쿼리 확인 가능
- 로그 파일: `/opt/crm-mig/logs/crm-migration-dev.log`

---

### Prod (운영 서버)

```bash
# 환경변수 설정 (필수!)
export DB_PASSWORD=your_production_password
# export SAFEDB_URL=https://safedb.prod.com
# export SAFEDB_API_KEY=your_api_key

java -jar crm-mig-1.0.0.jar \
  --spring.profiles.active=prod \
  --spring.batch.job.enabled=true \
  --spring.batch.job.names=migrationJob \
  run.id=$(date +%s)
```

**특징:**
- 운영 DB 연결 (환경변수로 비밀번호 주입)
- 최고 성능 (로그 최소화)
- chunk-size: 5000
- Connection Pool 최대
- 로그 파일: `/opt/crm-mig/logs/crm-migration-prod.log` (90일 보관)

**보안:**
- 비밀번호는 절대 코드에 하드코딩 금지
- 환경변수 또는 Secret Manager 사용

---

### Debug (디버깅)

```bash
# 주의: 매우 느립니다! 문제 해결용으로만 사용
java -jar crm-mig-1.0.0.jar --spring.profiles.active=debug
```

**특징:**
- 모든 DEBUG 로그 활성화
- SQL 쿼리 + 파라미터 전부 출력
- chunk-size: 100 (작은 단위로 상세 확인)
- 로그 파일: `logs/crm-migration-debug.log`

**사용 시나리오:**
- NPE 발생 원인 파악
- SQL 쿼리 문제 추적
- 데이터 흐름 확인

---

## 🖥️ STS에서 Profile 설정

### 방법 1: Run Configuration

```
1. Run → Run Configurations...
2. Spring Boot App → CRM Migration 선택
3. Profile 탭:
   - Profile: local  (또는 dev, prod, debug)
4. Arguments 탭:
   --spring.batch.job.enabled=true
   --spring.batch.job.names=migrationJob
5. Apply → Run
```

### 방법 2: application.yml 수정

```yaml
spring:
  profiles:
    active: local  # ← 이 값 변경
```

---

## 📊 성능 비교 (3,000건 기준)

| Profile | 예상 시간 | 로그 양 | 추천 용도 |
|---------|-----------|---------|-----------|
| **local** | 2-4초 | 10줄 | ✅ 일상 개발 |
| **dev** | 3-6초 | 100줄 | 테스트 서버 |
| **prod** | 1-3초 | 5줄 | ✅ 운영 환경 |
| **debug** | 30-40초 | 10,000줄 | ⚠️ 디버깅만 |

**200만건 예상:**
- local: 20-30분
- dev: 30-40분
- prod: 15-25분 ⭐
- debug: 5-7시간 (사용 금지!)

---

## 🔧 환경변수 설정

### Linux/Mac

```bash
# .bashrc 또는 .zshrc
export DB_PASSWORD=your_password
export SAFEDB_URL=https://safedb.example.com
export SAFEDB_API_KEY=your_api_key

# 적용
source ~/.bashrc
```

### Windows

```cmd
# 환경변수 설정
setx DB_PASSWORD "your_password"
setx SAFEDB_URL "https://safedb.example.com"

# PowerShell
$env:DB_PASSWORD="your_password"
```

### Docker

```bash
docker run -e DB_PASSWORD=your_password \
           -e SAFEDB_URL=https://safedb.example.com \
           crm-migration:latest
```

---

## 📝 설정 파일 위치

```
src/main/resources/
├── application.yml              # 기본 설정 (공통)
├── application-local.yml        # 로컬 환경
├── application-dev.yml          # 개발 서버
├── application-prod.yml         # 운영 서버
└── application-debug.yml        # 디버깅
```

---

## ⚠️ 주의사항

### Local Profile
- ✅ 기본값이므로 특별한 설정 불필요
- ✅ 빠른 성능으로 일상 개발에 최적

### Dev Profile
- ⚠️ DEBUG 로그로 인해 local보다 약간 느림
- ✅ SQL 확인 필요 시 유용

### Prod Profile
- ⚠️ 환경변수 DB_PASSWORD 필수!
- ⚠️ 운영 DB 연결 정보 확인 필수
- ✅ 최고 성능, 최소 로그

### Debug Profile
- 🚨 매우 느림! 절대 운영 환경에서 사용 금지
- ✅ 문제 해결 시에만 사용
- ✅ 사용 후 즉시 다른 Profile로 전환

---

## 🎯 권장 사용 패턴

### 일상 개발
```bash
# Local profile (기본값)
mvn spring-boot:run
```

### 통합 테스트
```bash
# Dev profile
java -jar app.jar --spring.profiles.active=dev
```

### 운영 배포
```bash
# Prod profile + 환경변수
export DB_PASSWORD=xxx
java -jar app.jar --spring.profiles.active=prod
```

### 버그 추적
```bash
# Debug profile (임시)
java -jar app.jar --spring.profiles.active=debug

# 문제 해결 후 즉시 local로 변경!
java -jar app.jar --spring.profiles.active=local
```

---

## 📚 추가 참고

- [EXECUTION_GUIDE.md](EXECUTION_GUIDE.md) - 실행 가이드
- [README.md](README.md) - 프로젝트 개요
- [Spring Boot Profiles 공식 문서](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.profiles)

---

**작성일:** 2026-01-15  
**버전:** 1.0
