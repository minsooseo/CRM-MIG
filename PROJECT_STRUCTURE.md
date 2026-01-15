# 프로젝트 구조

## 전체 디렉토리 구조

```
CRM-MIG/
├── pom.xml                                    # Maven 설정 파일
├── README.md                                  # 프로젝트 설명서
├── STS_IMPORT_GUIDE.md                        # STS 임포트 가이드
├── PROJECT_STRUCTURE.md                       # 프로젝트 구조 문서 (이 파일)
├── EXECUTION_FLOW.md                          # 실행 흐름 문서
├── MIGRATION_CONFIG_DDL.md                    # migration_config DDL 문서
├── UPDATE_METHODS_EXPLANATION.md              # UPDATE 메서드 설명
├── SIMULATION_SCENARIO.md                     # 시뮬레이션 시나리오
├── JOB_RERUN_GUIDE.md                         # Job 재실행 가이드
├── LINUX_EXECUTION_GUIDE.md                   # Linux 서버 실행 가이드
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── kt/
│   │   │           └── yaap/
│   │   │               └── mig_batch/        # ✅ 패키지명과 디렉토리 구조 일치
│   │   │               ├── CrmMigrationApplication.java
│   │   │               │
│   │   │               ├── batch/            # 배치 처리 컴포넌트
│   │   │               │   ├── TableRecordReader.java        # 실제 테이블 레코드 읽기
│   │   │               │   ├── EncryptionProcessor.java      # SafeDB 암호화 처리
│   │   │               │   └── EncryptionWriter.java         # UPDATE 수행 (status 업데이트)
│   │   │               │
│   │   │               ├── config/            # 설정 클래스
│   │   │               │   ├── BatchConfig.java                  # Step 설정
│   │   │               │   ├── MigrationJobConfig.java          # Job 설정 (테이블별 Step 동적 생성)
│   │   │               │   ├── MigrationProperties.java         # 설정 Properties (스키마명 등)
│   │   │               │   ├── DatabaseConfig.java              # 데이터소스 설정 (단일)
│   │   │               │   ├── MyBatisConfig.java               # MyBatis 설정
│   │   │               │   └── SafeDBConfig.java                # SafeDB 설정
│   │   │               │
│   │   │               ├── mapper/            # MyBatis Mapper 인터페이스
│   │   │               │   ├── MigrationConfigMapper.java   # 설정 테이블 Mapper
│   │   │               │   └── TargetTableMapper.java       # 대상 테이블 Mapper
│   │   │               │
│   │   │               ├── model/             # 엔티티 모델 (Lombok 적용)
│   │   │               │   ├── MigrationConfigEntity.java    # 마이그레이션 설정 (@Data, @NoArgsConstructor)
│   │   │               │   ├── TargetRecordEntity.java      # 레코드 엔티티 (@Data, 명시적 생성자)
│   │   │               │   ├── SourceEntity.java            # 소스 엔티티 (레거시)
│   │   │               │   └── TargetEntity.java            # 타겟 엔티티 (레거시)
│   │   │               │
│   │   │               ├── scheduler/         # 스케줄러
│   │   │               │   └── MigrationScheduler.java      # 배치 Job 스케줄링
│   │   │               │
│   │   │               ├── service/          # 서비스 레이어
│   │   │               │   └── BackupColumnService.java     # 백업 컬럼 자동 생성 서비스
│   │   │               │
│   │   │               └── util/               # 유틸리티
│   │   │                   └── SafeDBUtil.java               # SafeDB 암호화 유틸리티
│   │   │
│   │   └── resources/
│   │       ├── application.yml                # 애플리케이션 설정
│   │       └── mapper/                         # MyBatis Mapper XML
│   │           ├── MigrationConfigMapper.xml   # 설정 테이블 쿼리
│   │           └── TargetTableMapper.xml       # 대상 테이블 쿼리
│   │
│   └── test/                                   # 테스트 코드
│       └── java/com/kt/yaap/mig_batch/
│           ├── ManualJobRunner.java            # 수동 실행 테스트
│           └── ManualJobRerun.java             # 재실행 테스트
│
└── target/                                     # 빌드 결과물 (Maven 생성)
```

## 패키지 구조 (코드상)

```
com.kt.yaap.mig_batch
├── CrmMigrationApplication                     # 메인 애플리케이션 클래스
│
├── batch                                       # 배치 처리 컴포넌트
│   ├── TableRecordReader                      # 실제 테이블 레코드를 직접 읽음
│   ├── EncryptionProcessor                     # SafeDB 암호화 처리
│   └── EncryptionWriter                        # UPDATE 수행 (status 업데이트 포함)
│
├── config                                      # 설정 클래스
│   ├── BatchConfig                             # Step 설정
│   ├── MigrationJobConfig                      # Job 설정 (테이블별 Step 동적 생성)
│   ├── MigrationProperties                     # 설정 Properties (스키마명 등)
│   ├── DatabaseConfig                          # 데이터소스 설정 (단일)
│   ├── MyBatisConfig                           # MyBatis SqlSessionFactory 설정
│   └── SafeDBConfig                            # SafeDB 설정
│
├── mapper                                      # MyBatis Mapper 인터페이스
│   ├── MigrationConfigMapper                   # migration_config 테이블 조회
│   └── TargetTableMapper                       # 대상 테이블 조회/업데이트
│
├── model                                       # 엔티티 모델 (Lombok 적용)
│   ├── MigrationConfigEntity                   # 마이그레이션 설정 (@Data, @NoArgsConstructor)
│   ├── TargetRecordEntity                      # 레코드 엔티티 (@Data, 명시적 생성자)
│   ├── SourceEntity                            # 소스 엔티티 (레거시)
│   └── TargetEntity                            # 타겟 엔티티 (레거시)
│
├── scheduler                                   # 스케줄러
│   └── MigrationScheduler                      # 배치 Job 스케줄링
│
├── service                                     # 서비스 레이어
│   └── BackupColumnService                     # 백업 컬럼 자동 생성 (_bak 소문자)
│
└── util                                        # 유틸리티
    └── SafeDBUtil                              # SafeDB 암호화/복호화
```

## 주요 파일 설명

### 배치 컴포넌트 (batch/)
- **TableRecordReader**: 실제 테이블의 레코드를 직접 읽음 ⭐
  - 각 Step마다 특정 테이블의 레코드를 직접 조회
  - INFORMATION_SCHEMA에서 PK 조회 (복합키 지원)
  - 여러 컬럼의 값을 한 번에 읽어옴
  - `TargetRecordEntity` 객체로 반환

- **EncryptionProcessor**: 
  - `TargetRecordEntity`를 받아서 각 컬럼 값을 SafeDB 암호화
  - 원본 값과 암호화된 값을 `encryptedValues` Map에 저장
  - 복합키 지원

- **EncryptionWriter**: 
  - 원본 값을 `_bak` 컬럼에 백업 (소문자)
  - 암호화된 값으로 UPDATE (여러 컬럼을 한 번에)
  - 처리 완료 후 status를 'COMPLETE'로 업데이트

### 설정 (config/)
- **BatchConfig**: 
  - createBackupColumnStep (백업 컬럼 생성)
  - createTableEncryptionStep (테이블별 Step 생성 메서드)

- **MigrationJobConfig**: 
  - migrationJob (테이블별 Step 동적 생성 및 순차 연결)
  - migration_config에서 설정을 읽어 테이블별로 Step 생성

- **MigrationProperties**:
  - application.yml의 migration 설정을 Properties로 매핑
  - chunk-size, config-table, schema-name 설정

- **DatabaseConfig**: 단일 데이터소스 설정
- **MyBatisConfig**: MyBatis SqlSessionFactory 설정
- **SafeDBConfig**: SafeDB 설정

### 서비스 (service/)
- **BackupColumnService**: 
  - 백업 컬럼 존재 여부 확인
  - 원본 컬럼 타입 조회
  - 백업 컬럼 자동 생성 (_bak 소문자)
  - 중복 컬럼 오류 처리 강화

### Mapper (mapper/)
- **MigrationConfigMapper**: 
  - 활성화된 설정 조회 (status != 'COMPLETE')
  - status 업데이트

- **TargetTableMapper**: 
  - PK 조회 (복합키 지원)
  - 데이터 조회 (복합키 지원)
  - 백업 컬럼 생성
  - UPDATE 수행 (복합키 지원, 여러 컬럼 한 번에)

## 리소스 파일

### application.yml
- 데이터소스 설정 (단일)
- MyBatis 설정 (쿼리 로그 포함)
- Spring Batch 설정
- 로깅 설정 (DEBUG, TRACE 레벨)
- 마이그레이션 설정 (chunk-size, config-table, schema-name)

### Mapper XML
- **MigrationConfigMapper.xml**: 설정 테이블 쿼리
- **TargetTableMapper.xml**: 대상 테이블 쿼리 (PK 조회, 데이터 조회, 컬럼 생성, UPDATE)

## ⚠️ 주의사항

**✅ 디렉토리 구조와 패키지명 일치:**
- 실제 디렉토리: `src/main/java/com/kt/yaap/mig_batch/`
- 코드상 패키지명: `com.kt.yaap.mig_batch`

**✅ 현재 상태:**
- 패키지 구조 정리 완료
- 디렉토리와 패키지명 일치
- Reader가 실제 테이블 레코드를 직접 읽음
- read_count가 실제 처리한 레코드 수를 정확하게 반영

## Job 실행 흐름

```
migrationJob
├── createBackupColumnStep (Tasklet)
│   └── BackupColumnService.createBackupColumns()
│       └── _bak 컬럼 자동 생성 (소문자)
│
├── encryptionStep_TB_USER (Chunk, 순차 실행)
│   ├── Reader: TableRecordReader (TB_USER 테이블의 레코드 직접 읽기)
│   ├── Processor: EncryptionProcessor
│   └── Writer: EncryptionWriter
│       └── status를 'COMPLETE'로 업데이트
│
├── encryptionStep_TB_ORDER (Chunk, 순차 실행)
│   ├── Reader: TableRecordReader (TB_ORDER 테이블의 레코드 직접 읽기)
│   ├── Processor: EncryptionProcessor
│   └── Writer: EncryptionWriter
│       └── status를 'COMPLETE'로 업데이트
│
└── ... (테이블 개수만큼 순차 실행)
```

## 핵심 변경사항

### 이전 구조와의 차이점

1. **Reader 구조 완전 변경**
   - 이전: `MigrationItemReader` (migration_config만 읽음)
   - 현재: `TableRecordReader` (실제 테이블 레코드를 직접 읽음) ⭐

2. **Processor/Writer 변경**
   - 이전: `MigrationItemProcessor`, `MigrationItemWriter`
   - 현재: `EncryptionProcessor`, `EncryptionWriter`

3. **모델 변경**
   - 이전: `TargetUpdateEntity`
   - 현재: `TargetRecordEntity`

4. **Job 구조 변경**
   - 이전: 병렬 처리, JobExecutionContext 사용
   - 현재: 순차 처리, 테이블별 Step 동적 생성

5. **백업 컬럼**
   - 이전: `_BAK` (대문자)
   - 현재: `_bak` (소문자, PostgreSQL 호환)

6. **Properties 추가**
   - `MigrationProperties` 클래스 추가
   - `schema-name` 설정 추가

7. **read_count 정확성**
   - Step별 read_count가 실제 처리한 레코드 수를 정확하게 반영
   - 예: TB_USER 150건 → encryptionStep_TB_USER의 read_count = 150

8. **MyBatis 쿼리 로깅**
   - application.yml에 로그 설정 추가
   - DEBUG, TRACE 레벨로 SQL 및 파라미터 출력
