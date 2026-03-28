package mera.mera_v2.lark.token;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Service Ä‘á»ƒ lÆ°u trá»¯ token persistent (in-memory)
 * Äá»ƒ scheduler cÃ³ thá»ƒ truy cáº­p vÃ  lÃ m má»›i token
 */
@Service
@Slf4j
public class TokenStorageService {

    private volatile String userAccessToken;
    private volatile String refreshToken;

    private volatile String tenantAccessToken;

    private volatile Instant tokenExpireAt;
    private volatile Instant refreshTokenExpireAt;

    private volatile Instant tenantTokenExpireAt;

    public synchronized void save(mera.mera_v2.model.TokenInfo tokenInfo) {
        this.userAccessToken = tokenInfo.getAccessToken();
        this.refreshToken = tokenInfo.getRefreshToken();
        this.tokenExpireAt = Instant.now().plusSeconds(tokenInfo.getExpiresIn());
        this.refreshTokenExpireAt = Instant.now().plusSeconds(2592000L);
        log.info("Tokens saved from TokenInfo");
    }

    public synchronized boolean hasToken() {
        return userAccessToken != null && !userAccessToken.isEmpty();
    }

    public synchronized mera.mera_v2.model.TokenInfo get() {
        if (!hasToken()) return null;
        mera.mera_v2.model.TokenInfo info = new mera.mera_v2.model.TokenInfo();
        info.setAccessToken(this.userAccessToken);
        info.setRefreshToken(this.refreshToken);
        int expiresIn = 0;
        if (this.tokenExpireAt != null) {
            long remaining = this.tokenExpireAt.getEpochSecond() - Instant.now().getEpochSecond();
            expiresIn = remaining > 0 ? (int)remaining : 0;
        }
        info.setExpiresIn(expiresIn);
        return info;
    }

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * LÆ°u token má»›i (user OAuth)
     */
    public synchronized void saveTokens(
            String userAccessToken,
            String refreshToken,
            long expiresIn,
            long refreshExpiresIn
    ) {
        this.userAccessToken = userAccessToken;
        this.refreshToken = refreshToken;
        this.tokenExpireAt = Instant.now().plusSeconds(expiresIn);
        this.refreshTokenExpireAt = Instant.now().plusSeconds(refreshExpiresIn);

        log.info("====================================");
        log.info("TOKEN SAVED AT: {}", formatTime(Instant.now()));
        log.info("User Access Token: {}", maskToken(userAccessToken));
        log.info("Token expires at: {}", formatTime(tokenExpireAt));
        log.info("Refresh Token expires at: {}", formatTime(refreshTokenExpireAt));
        log.info("====================================");
    }

    /**
     * Cáº­p nháº­t token sau khi refresh
     * synchronized Ä‘á»ƒ trÃ¡nh race condition khi scheduler refresh token
     */
    public synchronized void updateTokens(
            String newUserAccessToken,
            String newRefreshToken,
            long expiresIn,
            long refreshExpiresIn
    ) {
        this.userAccessToken = newUserAccessToken;
        this.refreshToken = newRefreshToken;
        this.tokenExpireAt = Instant.now().plusSeconds(expiresIn);
        this.refreshTokenExpireAt = Instant.now().plusSeconds(refreshExpiresIn);

        log.info("====================================");
        log.info("TOKEN REFRESHED AT: {}", formatTime(Instant.now()));
        log.info("New User Access Token: {}", maskToken(newUserAccessToken));
        log.info("Token expires at: {}", formatTime(tokenExpireAt));
        log.info("Refresh Token expires at: {}", formatTime(refreshTokenExpireAt));
        log.info("====================================");
    }

    /**
     * LÆ°u tenant access token (náº¿u báº¡n cÃ³ dÃ¹ng)
     */
    public synchronized void saveTenantAccessToken(String tenantAccessToken, long expiresIn) {
        this.tenantAccessToken = tenantAccessToken;
        this.tenantTokenExpireAt = Instant.now().plusSeconds(expiresIn);

        log.info("====================================");
        log.info("TENANT TOKEN SAVED AT: {}", formatTime(Instant.now()));
        log.info("Tenant Access Token: {}", maskToken(tenantAccessToken));
        log.info("Tenant Token expires at: {}", formatTime(tenantTokenExpireAt));
        log.info("====================================");
    }

    /**
     * Cáº­p nháº­t tenant token sau khi refresh
     */
    public synchronized void updateTenantAccessToken(String newTenantAccessToken, long expiresIn) {
        this.tenantAccessToken = newTenantAccessToken;
        this.tenantTokenExpireAt = Instant.now().plusSeconds(expiresIn);

        log.info("====================================");
        log.info("TENANT TOKEN REFRESHED AT: {}", formatTime(Instant.now()));
        log.info("New Tenant Access Token: {}", maskToken(newTenantAccessToken));
        log.info("Tenant Token expires at: {}", formatTime(tenantTokenExpireAt));
        log.info("====================================");
    }

    // ===================== GETTERS =====================

    public synchronized String getUserAccessToken() {
        return userAccessToken;
    }

    public synchronized String getRefreshToken() {
        return refreshToken;
    }

    public synchronized String getTenantAccessToken() {
        return tenantAccessToken;
    }

    public synchronized Instant getTokenExpireAt() {
        return tokenExpireAt;
    }

    public synchronized Instant getRefreshTokenExpireAt() {
        return refreshTokenExpireAt;
    }

    public synchronized Instant getTenantTokenExpireAt() {
        return tenantTokenExpireAt;
    }

    // ===================== VALIDATION / TIME LEFT =====================

    public boolean hasValidRefreshToken() {
        return refreshToken != null && !refreshToken.isEmpty()
                && (refreshTokenExpireAt == null || Instant.now().isBefore(refreshTokenExpireAt));
    }

    public boolean isTenantTokenValid() {
        return tenantAccessToken != null && !tenantAccessToken.isEmpty()
                && (tenantTokenExpireAt == null || Instant.now().isBefore(tenantTokenExpireAt));
    }

    /** Token (user access token) sáº¯p háº¿t háº¡n trong 10 phÃºt tá»›i */
    public boolean isTokenExpiringSoon() {
        if (tokenExpireAt == null) return true;
        return Instant.now().isAfter(tokenExpireAt.minusSeconds(600));
    }

    /** Token cÃ²n hÆ¡n 30 phÃºt */
    public boolean isTokenValidFor30Minutes() {
        if (tokenExpireAt == null) return false;
        Instant thirtyMinutesFromNow = Instant.now().plusSeconds(1800);
        return tokenExpireAt.isAfter(thirtyMinutesFromNow);
    }

    /** Sá»‘ giÃ¢y cÃ²n láº¡i cá»§a user token */
    public long getTokenRemainingSeconds() {
        if (tokenExpireAt == null) return 0;
        long remaining = tokenExpireAt.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }

    /** Sá»‘ giÃ¢y cÃ²n láº¡i cá»§a refresh token */
    public long getRefreshTokenRemainingSeconds() {
        if (refreshTokenExpireAt == null) return 0;
        long remaining = refreshTokenExpireAt.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }

    /** Sá»‘ giÃ¢y cÃ²n láº¡i cá»§a tenant token */
    public long getTenantTokenRemainingSeconds() {
        if (tenantTokenExpireAt == null) return 0;
        long remaining = tenantTokenExpireAt.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }

    // ===================== HELPERS =====================

    private String formatTime(Instant instant) {
        if (instant == null) return "(null)";
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(FORMATTER);
    }

    /** Mask token Ä‘á»ƒ trÃ¡nh lá»™ ra log/UI */
    private String maskToken(String token) {
        if (token == null || token.isBlank()) return "(null)";
        int len = token.length();
        if (len <= 16) return "***";
        return token.substring(0, 8) + "..." + token.substring(len - 6);
    }
}