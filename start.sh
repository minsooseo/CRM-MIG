#!/bin/bash

###############################################################################
# CRM 마이그레이션 배치 - 수동 실행 스크립트
###############################################################################
# 사용법: ./start.sh
# 
# 특징:
# - 웹 서버 비활성화 (포트 점유 없음)
# - 스케줄러 비활성화 (수동 실행만)
# - 배치 작업 완료 후 자동 종료
###############################################################################

# 설정
APP_HOME="/opt/crm-mig"
JAR_FILE="crm-mig-1.0.0.jar"
LOG_DIR="${APP_HOME}/logs"
LOG_FILE="${LOG_DIR}/migration_$(date +%Y%m%d_%H%M%S).log"

# 로그 디렉토리 생성
mkdir -p ${LOG_DIR}

# 작업 디렉토리로 이동
cd ${APP_HOME}

# 이전 프로세스 확인 및 종료
echo "=== 기존 프로세스 확인 ===" | tee -a ${LOG_FILE}
EXISTING_PID=$(pgrep -f "crm-mig.*jar")
if [ -n "$EXISTING_PID" ]; then
    echo "⚠️  실행 중인 프로세스 발견 (PID: $EXISTING_PID)" | tee -a ${LOG_FILE}
    echo "기존 프로세스를 종료합니다..." | tee -a ${LOG_FILE}
    kill -9 $EXISTING_PID 2>/dev/null
    sleep 2
    
    # 종료 확인
    if pgrep -f "crm-mig.*jar" > /dev/null; then
        echo "❌ 프로세스 종료 실패. 수동으로 종료해주세요." | tee -a ${LOG_FILE}
        exit 1
    else
        echo "✅ 기존 프로세스 종료 완료" | tee -a ${LOG_FILE}
    fi
else
    echo "✅ 실행 중인 프로세스 없음" | tee -a ${LOG_FILE}
fi

echo "" | tee -a ${LOG_FILE}
echo "=== CRM Migration Batch Start ===" | tee -a ${LOG_FILE}
echo "Start Time: $(date '+%Y-%m-%d %H:%M:%S')" | tee -a ${LOG_FILE}
echo "JAR File: ${APP_HOME}/${JAR_FILE}" | tee -a ${LOG_FILE}
echo "Log File: ${LOG_FILE}" | tee -a ${LOG_FILE}
echo "" | tee -a ${LOG_FILE}

# JAR 파일 존재 확인
if [ ! -f "${APP_HOME}/${JAR_FILE}" ]; then
    echo "❌ JAR 파일을 찾을 수 없습니다: ${APP_HOME}/${JAR_FILE}" | tee -a ${LOG_FILE}
    exit 1
fi

# Java 실행
# - 웹 서버 비활성화: --spring.main.web-application-type=none
# - 스케줄러 비활성화: --spring.task.scheduling.enabled=false
# - 배치 작업 실행: --spring.batch.job.names=migrationJob
# - 고유 Job 파라미터: run.id=$(date +%s)
java -jar ${APP_HOME}/${JAR_FILE} \
  --spring.main.web-application-type=none \
  --spring.task.scheduling.enabled=false \
  --spring.batch.job.names=migrationJob \
  run.id=$(date +%s) \
  2>&1 | tee -a ${LOG_FILE}

EXIT_CODE=$?

echo "" | tee -a ${LOG_FILE}
echo "End Time: $(date '+%Y-%m-%d %H:%M:%S')" | tee -a ${LOG_FILE}
echo "Exit Code: ${EXIT_CODE}" | tee -a ${LOG_FILE}

if [ ${EXIT_CODE} -eq 0 ]; then
    echo "=== CRM Migration Batch End (Success) ===" | tee -a ${LOG_FILE}
else
    echo "=== CRM Migration Batch End (Failed) ===" | tee -a ${LOG_FILE}
fi

exit ${EXIT_CODE}
