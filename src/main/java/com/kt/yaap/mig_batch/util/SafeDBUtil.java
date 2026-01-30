package com.kt.yaap.mig_batch.util;

import com.kt.yaap.mig_batch.config.SafeDBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * SafeDB 유틸리티 클래스
 * 
 * 실제 SafeDB 라이브러리를 사용하려면:
 * 1. pom.xml에 SafeDB 의존성 추가
 * 2. 아래 주석 처리된 실제 SafeDB 코드를 활성화하고 임시 구현 부분 제거
 * 3. SafeDBConfig에 필요한 설정 추가
 */
@Component
public class SafeDBUtil {

    private static final Logger log = LoggerFactory.getLogger(SafeDBUtil.class);

    @Autowired
    private SafeDBConfig safeDBConfig;

    // 실제 SafeDB 인스턴스 (싱글톤)
    // private SafeDB safeDBInstance;

    @PostConstruct
    public void init() {
        if (!safeDBConfig.isEnabled()) {
            log.warn("SafeDB가 비활성화되어 있습니다. 암호화가 수행되지 않습니다.");
            return;
        }

        try {
            // TODO: 실제 SafeDB 초기화 로직
            // 예시:
            // if (safeDBConfig.getConfigFile() != null) {
            //     safeDBInstance = SafeDBFactory.getInstance(safeDBConfig.getConfigFile());
            // } else if (safeDBConfig.getServerUrl() != null) {
            //     safeDBInstance = SafeDBFactory.getInstance(
            //         safeDBConfig.getServerUrl(),
            //         safeDBConfig.getApiKey(),
            //         safeDBConfig.getTimeout()
            //     );
            // } else {
            //     safeDBInstance = SafeDBFactory.getInstance();
            // }
            
            log.info("SafeDB 초기화 완료 (현재는 임시 구현 모드)");
            
        } catch (Exception e) {
            log.error("SafeDB 초기화 실패", e);
            throw new RuntimeException("SafeDB 초기화 실패", e);
        }
    }

    /**
     * SafeDB 암호화 적용
     * 
     * @param plainText 평문 텍스트
     * @return 암호화된 텍스트
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.trim().isEmpty()) {
            return plainText;
        }

        if (!safeDBConfig.isEnabled()) {
            log.debug("SafeDB가 비활성화되어 원본 값 반환: {}", plainText);
            return plainText;
        }

        try {
            // TODO: 실제 SafeDB 암호화 로직으로 교체 필요
            // 실제 SafeDB 사용 예시:
            /*
            if (safeDBInstance == null) {
                throw new IllegalStateException("SafeDB 인스턴스가 초기화되지 않았습니다.");
            }
            String encrypted = safeDBInstance.encrypt(plainText);
            log.debug("Encrypted: {} -> {}", plainText, encrypted);
            return encrypted;
            */
            
            // 임시 구현 (실제 SafeDB 라이브러리로 교체 필요)
            // 테스트용: "[ENCRYPTED]" 접두사를 추가하여 암호화된 것을 시각적으로 확인 가능
            log.debug("Encrypting value (Mock 모드): {} -> [ENCRYPTED]{}", plainText, plainText);
            
            // 주의: 실제 운영 환경에서는 반드시 실제 SafeDB 라이브러리를 사용해야 합니다!
            return "[ENCRYPTED]" + plainText;
            
        } catch (Exception e) {
            log.error("SafeDB 암호화 실패: {}", plainText, e);
            throw new RuntimeException("SafeDB 암호화 실패: " + e.getMessage(), e);
        }
    }

    /**
     * AES128 암호화 후 Base64 인코딩된 값인지 체크
     * 
     * 성능 최적화: 복호화 시도 없이 패턴 기반으로 빠르게 판단
     * - Base64 문자만 포함 (A-Za-z0-9+/=)
     * - 최소 24자 이상 (AES128 블록 크기 고려)
     * - Base64 길이 규칙 준수 (4의 배수 또는 =로 끝남)
     * 
     * @param value 체크할 값
     * @return 암호화된 값이면 true, 평문이면 false
     */
    public boolean isEncrypted(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        
        // Base64 패턴 체크: Base64 문자만 포함하고 24자 이상
        // AES128 암호화 후 Base64 인코딩된 값은 최소 24자 이상
        boolean matchesBase64Pattern = value.matches("^[A-Za-z0-9+/=]{24,}$");
        
        // Base64 길이 규칙 체크: 4의 배수이거나 =로 끝남
        boolean validBase64Length = (value.length() % 4 == 0 || value.endsWith("="));
        
        return matchesBase64Pattern && validBase64Length;
    }

    /**
     * SafeDB 복호화
     * 
     * @param encryptedText 암호화된 텍스트
     * @return 복호화된 텍스트
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.trim().isEmpty()) {
            return encryptedText;
        }

        if (!safeDBConfig.isEnabled()) {
            log.debug("SafeDB가 비활성화되어 원본 값 반환: {}", encryptedText);
            return encryptedText;
        }

        try {
            // TODO: 실제 SafeDB 복호화 로직으로 교체 필요
            // 실제 SafeDB 사용 예시:
            /*
            if (safeDBInstance == null) {
                throw new IllegalStateException("SafeDB 인스턴스가 초기화되지 않았습니다.");
            }
            String decrypted = safeDBInstance.decrypt(encryptedText);
            log.debug("Decrypted: {} -> {}", encryptedText, decrypted);
            return decrypted;
            */
            
            // 임시 구현 (Mock 모드)
            // "[ENCRYPTED]" 접두사가 있으면 제거하여 복호화된 것처럼 처리
            log.debug("Decrypting value (Mock 모드): {}", encryptedText);
            if (encryptedText.startsWith("[ENCRYPTED]")) {
                return encryptedText.substring("[ENCRYPTED]".length());
            }
            return encryptedText;
            
        } catch (Exception e) {
            log.error("SafeDB 복호화 실패: {}", encryptedText, e);
            throw new RuntimeException("SafeDB 복호화 실패: " + e.getMessage(), e);
        }
    }
}


