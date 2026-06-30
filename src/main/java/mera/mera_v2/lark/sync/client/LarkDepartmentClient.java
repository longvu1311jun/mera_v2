package mera.mera_v2.lark.sync.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.lark.token.LarkTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LarkDepartmentClient {

    private final RestTemplate restTemplate;
    private final LarkTokenService larkTokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${lark.base-url:https://open.larksuite.com}")
    private String baseUrl;

    private static final int PAGE_SIZE = 50;

    /**
     * Get children departments of a parent department.
     *
     * @param parentDepartmentId The parent department ID (use "0" for root)
     * @param pageToken Optional page token for pagination
     * @param userToken User access token
     * @return DepartmentChildrenResponse containing list of departments and pagination info
     */
    public DepartmentChildrenResponse getDepartmentChildren(String parentDepartmentId, String pageToken, String userToken) throws Exception {
        StringBuilder urlBuilder = new StringBuilder(baseUrl)
                .append("/open-apis/contact/v3/departments/")
                .append(parentDepartmentId)
                .append("/children")
                .append("?department_id_type=open_department_id")
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

            return parseDepartmentChildrenResponse(response.getBody());
        } catch (Exception e) {
            log.error("[lark-sync] error endpoint=/contact/v3/departments/... parent={} code={} msg={}",
                    parentDepartmentId, getErrorCode(e), e.getMessage());
            throw e;
        }
    }

    private DepartmentChildrenResponse parseDepartmentChildrenResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        int code = root.path("code").asInt(-1);
        if (code != 0) {
            throw new RuntimeException("Lark API error: code=" + code + ", msg=" + root.path("msg").asText());
        }

        JsonNode data = root.path("data");
        boolean hasMore = data.path("has_more").asBoolean(false);
        String nextPageToken = data.path("page_token").asText(null);

        List<DepartmentItem> items = new ArrayList<>();
        JsonNode itemsNode = data.path("items");
        if (itemsNode.isArray()) {
            for (JsonNode item : itemsNode) {
                DepartmentItem dept = new DepartmentItem();
                dept.setDepartmentId(item.path("department_id").asText());
                dept.setName(item.path("name").asText());
                dept.setOpenDepartmentId(item.path("open_department_id").asText());
                dept.setParentDepartmentId(item.path("parent_department_id").asText());
                dept.setDeleted(item.path("status").path("is_deleted").asBoolean(false));
                items.add(dept);
            }
        }

        return new DepartmentChildrenResponse(items, hasMore, nextPageToken);
    }

    private int getErrorCode(Exception e) {
        return -1;
    }

    public static class DepartmentChildrenResponse {
        private final List<DepartmentItem> items;
        private final boolean hasMore;
        private final String pageToken;

        public DepartmentChildrenResponse(List<DepartmentItem> items, boolean hasMore, String pageToken) {
            this.items = items;
            this.hasMore = hasMore;
            this.pageToken = pageToken;
        }

        public List<DepartmentItem> getItems() { return items; }
        public boolean hasMore() { return hasMore; }
        public String getPageToken() { return pageToken; }
    }

    public static class DepartmentItem {
        private String departmentId;
        private String name;
        private String openDepartmentId;
        private String parentDepartmentId;
        private boolean isDeleted;

        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getOpenDepartmentId() { return openDepartmentId; }
        public void setOpenDepartmentId(String openDepartmentId) { this.openDepartmentId = openDepartmentId; }
        public String getParentDepartmentId() { return parentDepartmentId; }
        public void setParentDepartmentId(String parentDepartmentId) { this.parentDepartmentId = parentDepartmentId; }
        public boolean isDeleted() { return isDeleted; }
        public void setDeleted(boolean deleted) { isDeleted = deleted; }
    }
}
