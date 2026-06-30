package mera.mera_v2.lark.sync.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LarkEmployeeClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${lark.base-url:https://open.larksuite.com}")
    private String baseUrl;

    private static final int PAGE_SIZE = 50;

    /**
     * Find users by department.
     *
     * @param departmentId The open department ID
     * @param pageToken Optional page token for pagination
     * @param userToken User access token
     * @return EmployeeListResponse containing list of employees and pagination info
     */
    public EmployeeListResponse findUsersByDepartment(String departmentId, String pageToken, String userToken) throws Exception {
        StringBuilder urlBuilder = new StringBuilder(baseUrl)
                .append("/open-apis/contact/v3/users/find_by_department")
                .append("?department_id=").append(departmentId)
                .append("&department_id_type=open_department_id")
                .append("&page_size=").append(PAGE_SIZE)
                .append("&user_id_type=open_id");

        if (pageToken != null && !pageToken.isEmpty()) {
            urlBuilder.append("&page_token=").append(pageToken);
        }

        String url = urlBuilder.toString();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(userToken);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            return parseEmployeeListResponse(response.getBody());
        } catch (Exception e) {
            log.error("[lark-sync] error endpoint=/contact/v3/users/find_by_department dept={} code={} msg={}",
                    departmentId, getErrorCode(e), e.getMessage());
            throw e;
        }
    }

    private EmployeeListResponse parseEmployeeListResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        int code = root.path("code").asInt(-1);
        if (code != 0) {
            throw new RuntimeException("Lark API error: code=" + code + ", msg=" + root.path("msg").asText());
        }

        JsonNode data = root.path("data");
        boolean hasMore = data.path("has_more").asBoolean(false);
        String nextPageToken = data.path("page_token").asText(null);

        List<EmployeeItem> items = new ArrayList<>();
        JsonNode itemsNode = data.path("items");
        if (itemsNode.isArray()) {
            for (JsonNode item : itemsNode) {
                EmployeeItem emp = parseEmployeeItem(item);
                items.add(emp);
            }
        }

        return new EmployeeListResponse(items, hasMore, nextPageToken);
    }

    private EmployeeItem parseEmployeeItem(JsonNode item) {
        EmployeeItem emp = new EmployeeItem();
        emp.setUserId(item.path("user_id").asText());
        emp.setOpenId(item.path("open_id").asText());
        emp.setUnionId(item.path("union_id").asText(null));
        emp.setName(item.path("name").asText());
        emp.setEmail(item.path("email").asText(null));
        emp.setMobile(item.path("mobile").asText(null));
        emp.setEmployeeNo(item.path("employee_no").asText(null));
        emp.setJobTitle(item.path("job_title").asText(null));

        JsonNode avatar = item.path("avatar");
        if (!avatar.isMissingNode()) {
            emp.setAvatarUrl(avatar.path("avatar_origin").asText(null));
            if (emp.getAvatarUrl() == null) {
                emp.setAvatarUrl(avatar.path("avatar_640").asText(null));
            }
            if (emp.getAvatarUrl() == null) {
                emp.setAvatarUrl(avatar.path("avatar_240").asText(null));
            }
            if (emp.getAvatarUrl() == null) {
                emp.setAvatarUrl(avatar.path("avatar_72").asText(null));
            }
        }

        List<String> departmentIds = new ArrayList<>();
        JsonNode deptIds = item.path("department_ids");
        if (deptIds.isArray()) {
            for (JsonNode deptId : deptIds) {
                departmentIds.add(deptId.asText());
            }
        }
        emp.setDepartmentIds(departmentIds);

        JsonNode orders = item.path("orders");
        List<DepartmentOrder> orderList = new ArrayList<>();
        if (orders.isArray()) {
            for (JsonNode order : orders) {
                DepartmentOrder o = new DepartmentOrder();
                o.setDepartmentId(order.path("department_id").asText());
                o.setPrimaryDept(order.path("is_primary_dept").asBoolean(false));
                orderList.add(o);
            }
        }
        emp.setOrders(orderList);

        JsonNode status = item.path("status");
        if (!status.isMissingNode()) {
            EmployeeStatus empStatus = new EmployeeStatus();
            empStatus.setActivated(status.path("is_activated").asBoolean(false));
            empStatus.setExited(status.path("is_exited").asBoolean(false));
            empStatus.setFrozen(status.path("is_frozen").asBoolean(false));
            empStatus.setResigned(status.path("is_resigned").asBoolean(false));
            empStatus.setUnjoin(status.path("is_unjoin").asBoolean(false));
            emp.setStatus(empStatus);
        }

        return emp;
    }

    private int getErrorCode(Exception e) {
        return -1;
    }

    public static class EmployeeListResponse {
        private final List<EmployeeItem> items;
        private final boolean hasMore;
        private final String pageToken;

        public EmployeeListResponse(List<EmployeeItem> items, boolean hasMore, String pageToken) {
            this.items = items;
            this.hasMore = hasMore;
            this.pageToken = pageToken;
        }

        public List<EmployeeItem> getItems() { return items; }
        public boolean hasMore() { return hasMore; }
        public String getPageToken() { return pageToken; }
    }

    public static class EmployeeItem {
        private String userId;
        private String openId;
        private String unionId;
        private String name;
        private String email;
        private String mobile;
        private String employeeNo;
        private String jobTitle;
        private String avatarUrl;
        private List<String> departmentIds;
        private List<DepartmentOrder> orders;
        private EmployeeStatus status;

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getOpenId() { return openId; }
        public void setOpenId(String openId) { this.openId = openId; }
        public String getUnionId() { return unionId; }
        public void setUnionId(String unionId) { this.unionId = unionId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getMobile() { return mobile; }
        public void setMobile(String mobile) { this.mobile = mobile; }
        public String getEmployeeNo() { return employeeNo; }
        public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }
        public String getJobTitle() { return jobTitle; }
        public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
        public List<String> getDepartmentIds() { return departmentIds; }
        public void setDepartmentIds(List<String> departmentIds) { this.departmentIds = departmentIds; }
        public List<DepartmentOrder> getOrders() { return orders; }
        public void setOrders(List<DepartmentOrder> orders) { this.orders = orders; }
        public EmployeeStatus getStatus() { return status; }
        public void setStatus(EmployeeStatus status) { this.status = status; }
    }

    public static class DepartmentOrder {
        private String departmentId;
        private boolean isPrimaryDept;

        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
        public boolean isPrimaryDept() { return isPrimaryDept; }
        public void setPrimaryDept(boolean primaryDept) { isPrimaryDept = primaryDept; }
    }

    public static class EmployeeStatus {
        private boolean isActivated;
        private boolean isExited;
        private boolean isFrozen;
        private boolean isResigned;
        private boolean isUnjoin;

        public boolean isActivated() { return isActivated; }
        public void setActivated(boolean activated) { isActivated = activated; }
        public boolean isExited() { return isExited; }
        public void setExited(boolean exited) { isExited = exited; }
        public boolean isFrozen() { return isFrozen; }
        public void setFrozen(boolean frozen) { isFrozen = frozen; }
        public boolean isResigned() { return isResigned; }
        public void setResigned(boolean resigned) { isResigned = resigned; }
        public boolean isUnjoin() { return isUnjoin; }
        public void setUnjoin(boolean unjoin) { isUnjoin = unjoin; }
    }
}
