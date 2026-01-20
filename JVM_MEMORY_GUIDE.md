# JVM 메모리 설정 가이드

로컬 실행 시 Chunk Size에 따른 JVM 메모리 설정 방법을 안내합니다.

---

## 📋 목차

1. [개요](#개요)
2. [Chunk Size와 메모리 관계](#chunk-size와-메모리-관계)
3. [메모리 설정 방법](#메모리-설정-방법)
4. [권장 메모리 설정](#권장-메모리-설정)
5. [메모리 부족 증상 및 해결](#메모리-부족-증상-및-해결)
6. [추가 최적화 옵션](#추가-최적화-옵션)

---

## 개요

Spring Batch에서 Chunk Size를 크게 설정하면 메모리 사용량이 증가합니다. Chunk Size 5000 사용 시 권장 힙 메모리는 **최소 2GB**입니다.

**메모리 부족 시 증상:**
- `OutOfMemoryError: Java heap space`
- GC가 자주 발생하면서 성능 저하
- 애플리케이션이 느려지거나 멈춤

---

## Chunk Size와 메모리 관계

| Chunk Size | 최소 메모리 | 권장 메모리 | VM Arguments |
|------------|------------|------------|--------------|
| **1000** | 512MB | 1GB | `-Xms512m -Xmx1g` |
| **3000** | 1GB | 2GB | `-Xms1g -Xmx2g` |
| **5000** | 2GB | 4GB | `-Xms1g -Xmx4g` |
| **10000** | 4GB | 8GB | `-Xms2g -Xmx8g` |

**메모리 사용량 계산:**
- Chunk Size × (레코드 크기 + 암호화 데이터) = 한 번에 메모리에 올라가는 데이터
- 예: Chunk Size 5000, 레코드당 1KB → 약 5MB (압축 전)
- 실제로는 암호화 과정, Spring Batch 오버헤드 등을 고려해 **4~8배 더 필요**

---

## 메모리 설정 방법

### 방법 1: STS (Eclipse/Spring Tools Suite) - 권장

#### 1. Run Configuration 설정

1. **Run** → **Run Configurations...**
2. **Java Application** 또는 **Spring Boot App** 선택 (또는 새로 생성)
3. **Arguments** 탭 선택
4. **VM arguments**에 다음 추가:

```
-Xms1g -Xmx4g
```

#### 2. Spring Boot App 실행 시 상세 설정

1. **Run** → **Run Configurations...**
2. **Spring Boot App** 선택 (또는 새로 생성)
3. **Arguments** 탭:
   - **Program arguments**에 Profile 지정:
     ```
     --spring.profiles.active=local
     ```
   - **VM arguments**에 메모리 설정:
     ```
     -Xms1g -Xmx4g -XX:+UseG1GC
     ```

#### 3. Run Configuration 화면 예시

```
Name: CrmMigrationApplication (local)
Main class: com.kt.yaap.mig_batch.CrmMigrationApplication

Arguments 탭:
  Program arguments:
    --spring.profiles.active=local
  
  VM arguments:
    -Xms1g -Xmx4g -XX:+UseG1GC
```

---

### 방법 2: Maven으로 실행

#### Windows PowerShell

```powershell
# 기본 실행 (메모리 기본값)
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 메모리 지정 실행
mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.jvmArguments="-Xms1g -Xmx4g"

# 또는 MAVEN_OPTS 환경변수 사용 (영구 설정)
$env:MAVEN_OPTS="-Xms1g -Xmx4g"
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

#### Linux/Mac

```bash
# 기본 실행
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 메모리 지정 실행
mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.jvmArguments="-Xms1g -Xmx4g"

# 또는 MAVEN_OPTS 환경변수 사용
export MAVEN_OPTS="-Xms1g -Xmx4g"
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

---

### 방법 3: JAR 파일 실행

#### 1. JAR 파일 생성

```bash
mvn clean package
```

#### 2. 실행 시 메모리 지정

**Windows PowerShell:**
```powershell
java -Xms1g -Xmx4g -jar target/crm-mig-1.0.0.jar --spring.profiles.active=local
```

**Linux/Mac:**
```bash
java -Xms1g -Xmx4g -jar target/crm-mig-1.0.0.jar --spring.profiles.active=local
```

#### 3. 추가 옵션 포함 실행

```bash
java -Xms1g -Xmx4g -XX:+UseG1GC -Xlog:gc*:file=gc.log -jar target/crm-mig-1.0.0.jar --spring.profiles.active=local
```

---

### 방법 4: application-local.yml에 Chunk Size 설정

메모리 설정 전에 먼저 Chunk Size를 설정하세요:

```yaml
# src/main/resources/application-local.yml
migration:
  chunk-size: 5000  # 1000 → 5000으로 변경
```

---

## 권장 메모리 설정

### Chunk Size 5000 사용 시 (권장)

**VM Arguments:**
```
-Xms1g -Xmx4g
```

**설명:**
- `-Xms1g`: 초기 힙 메모리 1GB
- `-Xmx4g`: 최대 힙 메모리 4GB

### Chunk Size 10000 사용 시 (대용량 처리)

**VM Arguments:**
```
-Xms2g -Xmx8g
```

**주의:** 시스템 메모리가 충분한 경우에만 사용 (최소 16GB RAM 권장)

---

## 메모리 부족 증상 및 해결

### 증상

1. **OutOfMemoryError 발생**
   ```
   java.lang.OutOfMemoryError: Java heap space
   ```

2. **GC가 자주 발생**
   - 로그에 GC 관련 메시지가 빈번하게 나타남
   - 애플리케이션 성능 저하

3. **애플리케이션 멈춤**
   - 일시적으로 응답 없음
   - 처리 속도 급격히 감소

### 해결 방법

#### 1. 메모리 증가 (가장 간단)

**현재 설정:**
```
-Xms512m -Xmx1g
```

**변경 후:**
```
-Xms1g -Xmx4g
```

#### 2. Chunk Size 감소 (임시 해결)

```yaml
# application-local.yml
migration:
  chunk-size: 3000  # 5000 → 3000으로 감소
```

#### 3. 시스템 메모리 확인

**Windows:**
```
작업 관리자 → 성능 → 메모리
```

**필요한 메모리:**
- JVM 힙 메모리 (4GB) + 시스템 여유 메모리 (2GB) = 최소 6GB 이상 권장

---

## 추가 최적화 옵션

### 1. G1 GC 사용 (대용량 힙에 유리)

**VM Arguments:**
```
-Xms1g -Xmx4g -XX:+UseG1GC
```

**효과:**
- 대용량 힙 메모리에서 GC 성능 향상
- 일시 중지 시간 감소

### 2. GC 로그 활성화 (디버깅용)

**VM Arguments:**
```
-Xms1g -Xmx4g -XX:+UseG1GC -Xlog:gc*:file=gc.log:time
```

**로그 확인:**
- `gc.log` 파일에서 GC 발생 빈도 및 시간 확인
- 메모리 부족 원인 분석 가능

### 3. 메타스페이스 크기 조정 (필요시)

**VM Arguments:**
```
-Xms1g -Xmx4g -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m
```

**설명:**
- Java 8+: PermGen 대신 Metaspace 사용
- 기본값으로도 충분하지만, 클래스 로딩이 많은 경우 조정 가능

### 4. 전체 최적화 예시

**Chunk Size 5000, 대용량 처리 최적화:**
```
-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:InitiatingHeapOccupancyPercent=45
```

---

## 메모리 사용량 확인 방법

### 1. STS 내장 모니터 (권장)

1. **Window** → **Show View** → **Memory**
2. **Monitor** 뷰에서 실시간 메모리 사용량 확인
3. 힙 메모리, GC 발생 빈도 확인 가능

### 2. 코드에서 메모리 정보 출력

```java
// EncryptionWriter나 Processor에 추가 가능
Runtime runtime = Runtime.getRuntime();
long totalMemory = runtime.totalMemory();
long freeMemory = runtime.freeMemory();
long usedMemory = totalMemory - freeMemory;

log.info("Memory Usage - Used: {} MB / Total: {} MB / Max: {} MB",
    usedMemory / 1024 / 1024,
    totalMemory / 1024 / 1024,
    runtime.maxMemory() / 1024 / 1024);
```

### 3. Windows 작업 관리자

- **작업 관리자** → **성능** → **메모리**
- Java 프로세스의 메모리 사용량 확인

### 4. jstat 명령어 (고급)

```bash
# Java 프로세스 ID 확인
jps

# 힙 메모리 통계 확인 (1초마다, 10회)
jstat -gc <PID> 1000 10
```

---

## 실행 순서 정리

### Chunk Size 5000으로 설정하고 실행하는 전체 과정

1. **application-local.yml 수정**
   ```yaml
   migration:
     chunk-size: 5000
   ```

2. **STS Run Configuration 설정**
   - **VM arguments**: `-Xms1g -Xmx4g`
   - **Program arguments**: `--spring.profiles.active=local`

3. **실행 및 모니터링**
   - Memory Monitor로 메모리 사용량 확인
   - 로그에서 OutOfMemoryError 발생 여부 확인

4. **문제 발생 시**
   - 메모리 증가: `-Xmx4g` → `-Xmx8g`
   - 또는 Chunk Size 감소: `5000` → `3000`

---

## 주의사항

1. **시스템 메모리 고려**
   - JVM 힙 메모리는 시스템 메모리의 50% 이하로 설정 권장
   - 예: 16GB RAM → 최대 8GB 힙 메모리

2. **32비트 JVM 제한**
   - 32비트 JVM은 최대 4GB 힙 메모리 제한
   - 대용량 처리 시 **64비트 JVM 필수**

3. **Chunk Size와 메모리의 균형**
   - Chunk Size가 클수록 성능 향상, 하지만 메모리 부담 증가
   - 적절한 균형점 찾기 중요

4. **로컬 vs 운영 환경**
   - 로컬: 테스트용이므로 적당한 메모리 설정 (2~4GB)
   - 운영: 실제 처리량에 맞춰 충분한 메모리 설정 (4~8GB)

---

## 참고 문서

- [PROFILE_GUIDE.md](PROFILE_GUIDE.md) - Profile 설정 가이드
- [EXECUTION_GUIDE.md](EXECUTION_GUIDE.md) - 통합 실행 가이드
- [STS_MANUAL_EXECUTION_GUIDE.md](STS_MANUAL_EXECUTION_GUIDE.md) - STS 실행 가이드

---

**작성일:** 2026-01-15  
**버전:** 1.0  
**최종 업데이트:** Chunk Size 5000 기준 메모리 설정 가이드 추가
