package mera.mera_v2.customer.Service;

import com.fasterxml.jackson.annotation.JsonProperty;
import mera.mera_v2.model.PosUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PosService {
  
  private static final Logger log = LoggerFactory.getLogger(PosService.class);
  private static final String POS_API_KEY = "2a6ed8b51a8d4ae49a851d5876b00018";
  private static final String POS_SHOP_ID = "1546758";
  
  private final RestTemplate restTemplate;
  
  public PosService() {
    this.restTemplate = new RestTemplate();
  }
  
  /**
   * Get list of users from POS API, filtered by department name equals "CSKH" or "NV CSKH" (case-insensitive)
   */
  public List<PosUser> getUsers() {
    String url = String.format(
        "https://pos.pages.fm/api/v1/shops/%s/users?api_key=%s",
        POS_SHOP_ID,
        POS_API_KEY
    );
    
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    
    HttpEntity<String> entity = new HttpEntity<>(headers);
    try {
      ResponseEntity<PosApiResponse> response = restTemplate.exchange(
          url != null ? url : "",
          HttpMethod.GET,
          entity,
          PosApiResponse.class
      );
      
      if (response.getStatusCode() == HttpStatus.OK) {
        PosApiResponse body = response.getBody();
        if (body != null) {
          List<PosUser> allUsers = body.getData();

          log.info("=== POS API Debug ===");
          log.info("Total users from POS: {}", allUsers.size());
          
          // Only keep users belonging to specific allowed departments (White List)
          List<PosUser> filteredUsers = allUsers.stream()
              .filter(u -> {
                  if (u.getDepartment() == null || u.getDepartment().getName() == null) {
                      return false; // Skip users without a clear department
                  }
                  String deptName = u.getDepartment().getName().trim();
                  // Check against allowed department list (Case-insensitive)
                  return deptName.equalsIgnoreCase("NV sale 2 NT") ||
                         deptName.equalsIgnoreCase("SALE 2 Nguyễn Trãi") ||
                         deptName.equalsIgnoreCase("SALE 2 OCP") ||
                         deptName.equalsIgnoreCase("NV SALE 2 OCP");
              })
              .collect(Collectors.toList());

          log.info("Total users from POS: {}, Active Allowed: {}", allUsers.size(), filteredUsers.size());
          
          // Log all unique surviving department names
          java.util.Set<String> departments = filteredUsers.stream()
              .filter(u -> u.getDepartment() != null && u.getDepartment().getName() != null)
              .map(u -> u.getDepartment().getName())
              .collect(java.util.stream.Collectors.toSet());
          log.info("Unique active departments: {}", departments);

          // Log first 5 filtered users for debugging
          for (int i = 0; i < Math.min(5, filteredUsers.size()); i++) {
            PosUser u = filteredUsers.get(i);
            String deptName = u.getDepartment() != null ? u.getDepartment().getName() : "null";
            String userName = u.getName();
            String phone = u.getUser() != null ? u.getUser().getPhoneNumber() : "null";
            log.info("  Active POS User[{}]: name='{}', dept='{}', phone='{}'", i, userName, deptName, phone);
          }

          log.info("Returning {} filtered users (excluded resigned/off departments)", filteredUsers.size());
          return filteredUsers;
        }
      }
      log.warn("Failed to get users: HTTP {}", response.getStatusCode());
      return Collections.emptyList();
    } catch (RestClientException e) {
      log.error("Error calling POS API: {}", e.getMessage(), e);
      return Collections.emptyList();
    }
  }
  
  /**
   * Response wrapper for POS API
   */
  private static class PosApiResponse {
    @JsonProperty("data")
    private List<PosUser> data;
    
    public List<PosUser> getData() {
      return data != null ? data : Collections.emptyList();
    }
    
    public void setData(List<PosUser> data) {
      this.data = data;
    }
  }
}
