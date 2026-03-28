package mera.mera_v2.model;

import java.time.LocalDateTime;

public class TokenInfo {
  private String accessToken;
  private String refreshToken;
  private int expiresIn; // seconds
  private LocalDateTime expiresAt;
  private String tokenType;
  private LocalDateTime lastUpdated; // Thá»i gian cáº­p nháº­t token
  
  public TokenInfo() {
  }
  
  public TokenInfo(String accessToken, String refreshToken, int expiresIn, String tokenType) {
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
    this.expiresIn = expiresIn;
    this.tokenType = tokenType;
    this.expiresAt = LocalDateTime.now().plusSeconds(expiresIn);
    this.lastUpdated = LocalDateTime.now();
  }
  
  public String getAccessToken() {
    return accessToken;
  }
  
  public void setAccessToken(String accessToken) {
    this.accessToken = accessToken;
  }
  
  public String getRefreshToken() {
    return refreshToken;
  }
  
  public void setRefreshToken(String refreshToken) {
    this.refreshToken = refreshToken;
  }
  
  public int getExpiresIn() {
    return expiresIn;
  }
  
  public void setExpiresIn(int expiresIn) {
    this.expiresIn = expiresIn;
    this.expiresAt = LocalDateTime.now().plusSeconds(expiresIn);
  }
  
  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }
  
  public void setExpiresAt(LocalDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }
  
  public String getTokenType() {
    return tokenType;
  }
  
  public void setTokenType(String tokenType) {
    this.tokenType = tokenType;
  }
  
  public boolean isExpired() {
    return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
  }
  
  public LocalDateTime getLastUpdated() {
    return lastUpdated;
  }
  
  public void setLastUpdated(LocalDateTime lastUpdated) {
    this.lastUpdated = lastUpdated;
  }
}
