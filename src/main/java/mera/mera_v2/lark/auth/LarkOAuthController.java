package mera.mera_v2.lark.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.lark.token.TokenStorageService;
import mera.mera_v2.lark.webhook.service.TenantTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Dang nhap Lark de lay user_access_token (luu in-memory trong TokenStorageService).
 *
 * - GET /lark        : trang co nut dang nhap Lark
 * - GET /lark/oauth/callback : Lark goi ve sau khi user authorize
 * - GET /lark/token  : xem trang thai token hien tai
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class LarkOAuthController {

  @Value("${lark.app-id}")
  private String appId;

  @Value("${lark.redirect-uri}")
  private String redirectUri;

  private final larkAuthService authService;
  private final TokenStorageService tokenStorageService;
  private final TenantTokenService tenantTokenService;

  @GetMapping(value = {"/lark", "/"}, produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> index() {
    String authUrl = "https://open.larksuite.com/open-apis/authen/v1/index"
        + "?app_id=" + URLEncoder.encode(appId, StandardCharsets.UTF_8)
        + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
        + "&state=" + URLEncoder.encode("mera-lark-only", StandardCharsets.UTF_8);

    String html = """
        <!DOCTYPE html>
        <html lang="vi"><head><meta charset="utf-8"><title>Đăng nhập Lark</title>
        <style>body{font-family:system-ui,sans-serif;margin:60px auto;max-width:640px;line-height:1.6}
        a.btn{display:inline-block;padding:12px 24px;background:#3370ff;color:#fff;border-radius:6px;text-decoration:none}
        code{background:#f4f4f5;padding:2px 6px;border-radius:4px}</style></head>
        <body>
        <h2>Đăng nhập Lark để lấy user access token</h2>
        <p>Token được giữ trong bộ nhớ ứng dụng và tự làm mới mỗi giờ. Sau khi app khởi động lại phải đăng nhập lại.</p>
        <p><a class="btn" href="%s">Đăng nhập Lark</a></p>
        <p>Kiểm tra trạng thái: <code>/lark/token</code> — webhook POS: <code>/api/lark/orders</code></p>
        </body></html>
        """.formatted(authUrl);

    return ResponseEntity.ok(html);
  }

  @GetMapping({"/oauth/callback", "/lark/oauth/callback"})
  public ResponseEntity<String> callback(@RequestParam(value = "code", required = false) String code) {
    if (code == null || code.isBlank()) {
      return ResponseEntity.badRequest().body("Khong nhan duoc authorization code. Vui long dang nhap lai tai /lark");
    }

    try {
      larkTokenResponse tokenResp = authService.exchangeCodeForUserToken(code);
      larkTokenResponse.TokenData data = tokenResp.getData();

      tokenStorageService.saveTokens(
          data.getAccessToken(),
          data.getRefreshToken(),
          data.getExpiresIn(),
          data.getRefreshExpiresIn()
      );
      log.info("User access token da duoc luu, het han sau {}s", data.getExpiresIn());

      try {
        String tenantToken = tenantTokenService.getTenantAccessToken();
        tokenStorageService.saveTenantAccessToken(tenantToken, 7200);
      } catch (Exception e) {
        log.warn("Khong lay duoc tenant token: {}", e.getMessage());
      }

      return ResponseEntity.ok("Dang nhap thanh cong. Xem trang thai token tai /lark/token");
    } catch (Exception e) {
      log.error("OAuth callback that bai: {}", e.getMessage(), e);
      return ResponseEntity.status(500).body("Dang nhap that bai: " + e.getMessage());
    }
  }

  @GetMapping({"/lark/token", "/token"})
  public ResponseEntity<java.util.Map<String, Object>> tokenStatus() {
    java.util.Map<String, Object> body = new java.util.HashMap<>();
    body.put("hasUserToken", tokenStorageService.hasToken());
    body.put("userTokenRemainingSeconds", tokenStorageService.getTokenRemainingSeconds());
    body.put("hasValidRefreshToken", tokenStorageService.hasValidRefreshToken());
    body.put("refreshTokenRemainingSeconds", tokenStorageService.getRefreshTokenRemainingSeconds());
    body.put("hasTenantToken", tokenStorageService.isTenantTokenValid());
    body.put("loginUrl", "/lark");
    return ResponseEntity.ok(body);
  }
}
