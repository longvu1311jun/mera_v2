package mera.mera_v2.pos.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmployeeApiResponse {
    
    private List<EmployeeDto> data;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmployeeDto {
        
        @JsonProperty("id")
        private String shopUserId;  // pos_shop_users.id
        
        @JsonProperty("shop_id")
        private Long shopId;
        
        @JsonProperty("user_id")
        private String userId;  // pos_users.id
        
        @JsonProperty("department_id")
        private Long departmentId;
        
        @JsonProperty("role")
        private String role;
        
        @JsonProperty("permission_in_sale_group")
        private String permissionInSaleGroup;
        
        @JsonProperty("is_assigned")
        private Boolean isAssigned;
        
        @JsonProperty("enable_api")
        private Boolean enableApi;
        
        @JsonProperty("api_key")
        private String apiKey;
        
        @JsonProperty("note_api_key")
        private String noteApiKey;
        
        @JsonProperty("is_api_key")
        private Boolean isApiKey;
        
        @JsonProperty("pending_order_count")
        private Integer pendingOrderCount;
        
        @JsonProperty("preferred_shop")
        private Integer preferredShop;
        
        @JsonProperty("app_warehouse")
        private String appWarehouse;
        
        @JsonProperty("creator_id")
        private String creatorId;
        
        @JsonProperty("profile_id")
        private String profileId;
        
        @JsonProperty("inserted_at")
        private String insertedAt;
        
        // Nested objects
        private DepartmentDto department;
        
        private SaleGroupDto sale_group;
        
        private UserDto user;
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class DepartmentDto {
            private Long id;
            private String name;
        }
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class SaleGroupDto {
            private Integer id;
            private String name;
        }
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class UserDto {
            private String id;
            private String name;
            private String email;
            private String fbId;
            private String phoneNumber;
            private String avatarUrl;
        }
    }
}
