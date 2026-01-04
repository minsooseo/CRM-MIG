# 프로젝트 구조

## 전체 디렉토리 구조

```
CRM-MIG/
├── pom.xml                                    # Maven 설정 파일
├── README.md                                  # 프로젝트 설명서
├── STS_IMPORT_GUIDE.md                        # STS 임포트 가이드
├── PROJECT_STRUCTURE.md                       # 프로젝트 구조 문서 (이 파일)
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── example/
│   │   │           └── crmmig/               # ⚠️ 실제 디렉토리는 com.example.crmmig
│   │   │                                       #    코드상 패키지명: com.kt.yaap.mig_batch
│   │   │               ├── CrmMigrationApplication.java
│   │   │               │
│   │   │               ├── batch/            # 배치 처리 컴포넌트
│   │   │               │   ├── MigrationItemReader.java      # 설정 테이블 읽기
│   │   │               │   ├── MigrationItemProcessor.java    # PK 조회 및 SafeDB 적용
│   │   │               │   └── MigrationItemWriter.java      # 백업 및 UPDATE 수행
│   │   │               │
│   │   │               ├── config/            # 설정 클래스
│   │   │               ├── BatchConfig.java                  # 배치 Job/Step 설정
│   │   │               ├── DatabaseConfig.java              # 데이터소스 설정
│   │   │               ├── MyBatisConfig.java               # MyBatis 설정
│   │   │               └── MigrationReaderConfig.java       # Reader Bean 설정
│   │   │               │
│   │   │               ├── mapper/            # MyBatis Mapper 인터페이스
│   │   │               │   ├── MigrationConfigMapper.java   # 설정 테이블 Mapper
│   │   │               │   └── TargetTableMapper.java       # 대상 테이블 Mapper
│   │   │               │
│   │   │               ├── model/             # 엔티티 모델
│   │   │               │   ├── MigrationConfigEntity.java    # 마이그레이션 설정 엔티티
│   │   │               │   ├── TargetUpdateEntity.java      # 업데이트 대상 엔티티
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
│   └── test/                                   # 테스트 코드 (미구현)
│
└── target/                                     # 빌드 결과물 (Maven 생성)
```

## 패키지 구조 (코드상)

```
com.kt.yaap.mig_batch
├── CrmMigrationApplication                     # 메인 애플리케이션 클래스
│
├── batch                                       # 배치 처리 컴포넌트
│   ├── MigrationItemReader                    # 설정 테이블 읽기 (MyBatis)
│   ├── MigrationItemProcessor                  # PK 조회 및 SafeDB 적용
│   └── MigrationItemWriter                     # 백업 및 UPDATE 수행
│
├── config                                      # 설정 클래스
│   ├── BatchConfig                             # Job/Step 설정
│   ├── DatabaseConfig                          # 데이터소스 설정 (소스/타겟/배치)
│   ├── MyBatisConfig                           # MyBatis SqlSessionFactory 설정
│   └── MigrationReaderConfig                   # Reader Bean 설정
│
├── mapper                                      # MyBatis Mapper 인터페이스
│   ├── MigrationConfigMapper                   # migration_config 테이블 조회
│   └── TargetTableMapper                       # 대상 테이블 조회/업데이트
│
├── model                                       # 엔티티 모델
│   ├── MigrationConfigEntity                   # 마이그레이션 설정
│   ├── TargetUpdateEntity                      # 업데이트 대상 데이터
│   ├── SourceEntity                            # 소스 엔티티 (레거시)
│   └── TargetEntity                            # 타겟 엔티티 (레거시)
│
├── scheduler                                   # 스케줄러
│   └── MigrationScheduler                      # 배치 Job 스케줄링
│
├── service                                     # 서비스 레이어
│   └── BackupColumnService                     # 백업 컬럼 자동 생성
│
└── util                                        # 유틸리티
    └── SafeDBUtil                              # SafeDB 암호화/복호화
```

## 주요 파일 설명

### 배치 컴포넌트 (batch/)
- **MigrationItemReader**: migration_config 테이블에서 설정 읽기
- **MigrationItemProcessor**: 
  - INFORMATION_SCHEMA에서 PK 조회
  - 대상 테이블 데이터 조회
  - SafeDB 암호화 적용
- **MigrationItemWriter**: 
  - 원본 값을 `_BAK` 컬럼에 백업
  - 암호화된 값으로 UPDATE

### 설정 (config/)
- **BatchConfig**: 
  - createBackupColumnStep (백업 컬럼 생성)
  - migrationStep (암호화 처리)
  - postMigrationStep (후처리)
- **DatabaseConfig**: 소스/타겟/배치 DB 데이터소스 설정
- **MyBatisConfig**: MyBatis SqlSessionFactory 설정
- **MigrationReaderConfig**: Reader Bean 설정

### 서비스 (service/)
- **BackupColumnService**: 
  - 백업 컬럼 존재 여부 확인
  - 원본 컬럼 타입 조회
  - 백업 컬럼 자동 생성

### Mapper (mapper/)
- **MigrationConfigMapper**: 활성화된 설정 조회
- **TargetTableMapper**: 
  - PK 조회
  - 데이터 조회
  - 백업 컬럼 생성
  - UPDATE 수행

## 리소스 파일

### application.yml
- 데이터소스 설정 (소스/타겟/배치)
- MyBatis 설정
- Spring Batch 설정
- 로깅 설정
- 마이그레이션 설정

### Mapper XML
- **MigrationConfigMapper.xml**: 설정 테이블 쿼리
- **TargetTableMapper.xml**: 대상 테이블 쿼리 (PK 조회, 데이터 조회, 컬럼 생성, UPDATE)

## ⚠️ 주의사항

**디렉토리 구조와 패키지명 불일치:**
- 실제 디렉토리: `src/main/java/com/example/crmmig/`
- 코드상 패키지명: `com.kt.yaap.mig_batch`

**해결 방법:**
1. STS에서 Import 후 Maven Update 실행
2. IDE가 자동으로 디렉토리 구조 조정
3. 또는 수동으로 디렉토리 이동:
   - `com/example/crmmig/` → `com/kt/yaap/mig_batch/`

## Job 실행 흐름

```
migrationJob
├── createBackupColumnStep (Tasklet)
│   └── BackupColumnService.createBackupColumns()
│
├── migrationStep (Chunk)
│   ├── Reader: MigrationItemReader
│   ├── Processor: MigrationItemProcessor
│   └── Writer: MigrationItemWriter
│
└── postMigrationStep (Tasklet)
    └── 검증, 통계 등
```


