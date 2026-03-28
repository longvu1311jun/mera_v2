package mera.mera_v2.lark.webhook.scheduler2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.lark.auth.larkAuthService;
import mera.mera_v2.lark.auth.larkTokenResponse;
import mera.mera_v2.lark.token.TokenStorageService;
import mera.mera_v2.lark.webhook.service.TenantTokenService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Scheduler tự động làm mới token mỗi 1 giờ
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenRefreshScheduler {

  private final TokenStorageService tokenStorageService;
  private final larkAuthService authService;
  private final TenantTokenService tenantTokenService;

  private static final DateTimeFormatter FORMATTER = 
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  /**
   * Chạy mỗi 1 giờ (3600000 ms)
   * initialDelay = 60000 ms (1 phút) để chờ app khởi động xong
   */
  @Scheduled(fixedRate = 3600000, initialDelay = 60000)
  public void refreshTokenPeriodically() {
    log.info("========================================");
    log.info("SCHEDULED TOKEN REFRESH - {}", LocalDateTime.now().format(FORMATTER));
    log.info("========================================");

    try {
      String currentRefreshToken = tokenStorageService.getRefreshToken();
      
      if (currentRefreshToken == null || currentRefreshToken.isEmpty()) {
        log.warn("No refresh token available. User needs to login first.");
        log.info("Waiting for user to login via OAuth...");
        return;
      }

      if (!tokenStorageService.hasValidRefreshToken()) {
        log.warn("Refresh token has expired. User needs to re-login.");
        return;
      }

      // Gọi API làm mới token
      larkTokenResponse response = authService.refreshUserAccessToken(currentRefreshToken);
      
      if (response != null && response.getData() != null) {
        larkTokenResponse.TokenData data = response.getData();
        
        // Cập nhật token mới vào storage
        tokenStorageService.updateTokens(
            data.getAccessToken(),
            data.getRefreshToken(),
            data.getExpiresIn(),
            data.getRefreshExpiresIn()
        );

        // In ra token mới
        printNewToken(data.getAccessToken());
      }

      // Refresh tenant token nếu cần
      refreshTenantTokenIfNeeded();

    } catch (Exception e) {
      log.error("Failed to refresh token: {}", e.getMessage(), e);
    }
  }

  /**
   * Refresh tenant token nếu còn < 30 phút
   */
  private void refreshTenantTokenIfNeeded() {
    try {
      if (tenantTokenService == null) {
        log.debug("TenantTokenService not available, skipping tenant token refresh");
        return;
      }

      long remainingSeconds = tenantTokenService.getTokenRemainingSeconds();
      if (remainingSeconds > 1800) { // Còn hơn 30 phút
        log.debug("Tenant token còn {} giây, không cần refresh", remainingSeconds);
        return;
      }

      log.info("⚠️ Tenant token còn < 30 phút hoặc đã hết hạn, đang refresh...");
      String newTenantToken = tenantTokenService.getTenantAccessToken();

      if (newTenantToken != null && !newTenantToken.isBlank()) {
        // Lưu tenant token mới vào storage (giả sử 2 giờ = 7200 giây)
        tokenStorageService.updateTenantAccessToken(newTenantToken, 7200);
        log.info("✅ Tenant token đã được refresh thành công");
      }
    } catch (Exception e) {
      log.error("❌ Failed to refresh tenant token: {}", e.getMessage());
    }
  }

  /**
   * In token mới ra console
   */
  private void printNewToken(String token) {
    log.info("");
    log.info("╔══════════════════════════════════════════════════════════════╗");
    log.info("║              NEW USER ACCESS TOKEN                           ║");
    log.info("╠══════════════════════════════════════════════════════════════╣");
    log.info("║ Time: {}                              ║", LocalDateTime.now().format(FORMATTER));
    log.info("╠══════════════════════════════════════════════════════════════╣");
    log.info("║ Token:                                                       ║");
    log.info("╚══════════════════════════════════════════════════════════════╝");
    log.info("");
    log.info(token);
    log.info("");
    log.info("================================================================");
  }

  /**
   * Làm mới token thủ công (có thể gọi từ controller)
   */
  public boolean manualRefresh() {
    log.info("Manual token refresh triggered");
    try {
      refreshTokenPeriodically();
      return true;
    } catch (Exception e) {
      log.error("Manual refresh failed: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Kiểm tra và refresh token nếu còn < 30 phút
   * @return true nếu token đã được refresh hoặc không cần refresh, false nếu refresh thất bại
   */
  public synchronized boolean refreshTokenIfNeeded() {
    // Kiểm tra xem token còn hơn 30 phút không
    if (tokenStorageService.isTokenValidFor30Minutes()) {
      long remainingMinutes = tokenStorageService.getTokenRemainingSeconds() / 60;
      log.debug("Token còn {} phút, không cần refresh", remainingMinutes);
      return true;
    }

    log.info("⚠️ Token còn < 30 phút hoặc đã hết hạn, đang refresh token...");
    long remainingSeconds = tokenStorageService.getTokenRemainingSeconds();
    if (remainingSeconds > 0) {
      log.info("Token còn {} giây ({} phút), đang refresh...", remainingSeconds, remainingSeconds / 60);
    } else {
      log.warn("Token đã hết hạn, đang refresh...");
    }

    try {
      String currentRefreshToken = tokenStorageService.getRefreshToken();
      
      if (currentRefreshToken == null || currentRefreshToken.isEmpty()) {
        log.error("❌ No refresh token available. Cannot refresh.");
        return false;
      }

      if (!tokenStorageService.hasValidRefreshToken()) {
        log.error("❌ Refresh token has expired. User needs to re-login.");
        return false;
      }

      // Gọi API làm mới token
      larkTokenResponse response = authService.refreshUserAccessToken(currentRefreshToken);
      
      if (response != null && response.getData() != null) {
        larkTokenResponse.TokenData data = response.getData();
        
        // Cập nhật token mới vào storage
        tokenStorageService.updateTokens(
            data.getAccessToken(),
            data.getRefreshToken(),
            data.getExpiresIn(),
            data.getRefreshExpiresIn()
        );

        log.info("✅ Token đã được refresh thành công. Token mới sẽ hết hạn sau {} giây", data.getExpiresIn());
        return true;
      }

      log.error("❌ Failed to refresh token: response is null or invalid");
      return false;

    } catch (Exception e) {
      log.error("❌ Failed to refresh token: {}", e.getMessage(), e);
      return false;
    }
  }
}
