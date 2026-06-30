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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LarkAttendanceClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${lark.base-url:https://open.larksuite.com}")
    private String baseUrl;

    @Value("${lark.attendance.operator-user-id:}")
    private String operatorUserId;

    @Value("${lark.attendance.locale:zh}")
    private String locale;

    @Value("${lark.attendance.stats-type:daily}")
    private String statsType;

    @Value("${lark.attendance.current-group-only:true}")
    private boolean currentGroupOnly;

    @Value("${lark.attendance.need-history:true}")
    private boolean needHistory;

    private static final int MAX_USERS_PER_REQUEST = 20;

    /**
     * Query user attendance stats data.
     *
     * @param userIds List of Lark user IDs (max 20)
     * @param startDate Start date in YYYYMMDD format
     * @param endDate End date in YYYYMMDD format
     * @param tenantToken Tenant access token
     * @return AttendanceStatsResponse containing attendance data
     */
    public AttendanceStatsResponse queryUserStatsData(List<String> userIds, int startDate, int endDate, String tenantToken) throws Exception {
        if (userIds.size() > MAX_USERS_PER_REQUEST) {
            throw new IllegalArgumentException("Maximum " + MAX_USERS_PER_REQUEST + " users per request");
        }

        String url = baseUrl + "/open-apis/attendance/v1/user_stats_datas/query?employee_type=employee_id";

        Map<String, Object> body = new HashMap<>();
        body.put("current_group_only", currentGroupOnly);
        body.put("end_date", endDate);
        body.put("locale", locale);
        body.put("need_history", needHistory);
        body.put("start_date", startDate);
        body.put("stats_type", statsType);
        body.put("user_id", operatorUserId);
        body.put("user_ids", userIds);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(tenantToken);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            return parseAttendanceStatsResponse(response.getBody());
        } catch (Exception e) {
            log.error("[lark-attendance] Failed to query attendance: startDate={}, endDate={}, users={}, error={}",
                    startDate, endDate, userIds.size(), e.getMessage());
            throw e;
        }
    }

    private AttendanceStatsResponse parseAttendanceStatsResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        int code = root.path("code").asInt(-1);
        if (code != 0) {
            throw new RuntimeException("Lark API error: code=" + code + ", msg=" + root.path("msg").asText());
        }

        JsonNode data = root.path("data");

        List<String> invalidUsers = new ArrayList<>();
        JsonNode invalidList = data.path("invalid_user_list");
        if (invalidList.isArray()) {
            for (JsonNode userId : invalidList) {
                invalidUsers.add(userId.asText());
            }
        }

        List<UserAttendanceData> userDatas = new ArrayList<>();
        JsonNode userDataList = data.path("user_datas");
        if (userDataList.isArray()) {
            for (JsonNode userData : userDataList) {
                UserAttendanceData ud = new UserAttendanceData();
                ud.setUserId(userData.path("user_id").asText());
                ud.setName(userData.path("name").asText());

                List<AttendanceDataItem> datas = new ArrayList<>();
                JsonNode dataItems = userData.path("datas");
                if (dataItems.isArray()) {
                    for (JsonNode item : dataItems) {
                        AttendanceDataItem di = new AttendanceDataItem();
                        di.setCode(item.path("code").asText());
                        di.setTitle(item.path("title").asText());
                        di.setValue(item.path("value").asText());

                        Map<String, String> features = new HashMap<>();
                        JsonNode featuresNode = item.path("features");
                        if (featuresNode.isArray()) {
                            for (JsonNode f : featuresNode) {
                                String key = f.path("key").asText();
                                String value = f.path("value").asText();
                                features.put(key, value);
                            }
                        }
                        di.setFeatures(features);
                        datas.add(di);
                    }
                }
                ud.setDatas(datas);
                userDatas.add(ud);
            }
        }

        return new AttendanceStatsResponse(invalidUsers, userDatas);
    }

    public static class AttendanceStatsResponse {
        private final List<String> invalidUserList;
        private final List<UserAttendanceData> userDatas;

        public AttendanceStatsResponse(List<String> invalidUserList, List<UserAttendanceData> userDatas) {
            this.invalidUserList = invalidUserList;
            this.userDatas = userDatas;
        }

        public List<String> getInvalidUserList() { return invalidUserList; }
        public List<UserAttendanceData> getUserDatas() { return userDatas; }
    }

    public static class UserAttendanceData {
        private String userId;
        private String name;
        private List<AttendanceDataItem> datas;

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<AttendanceDataItem> getDatas() { return datas; }
        public void setDatas(List<AttendanceDataItem> datas) { this.datas = datas; }
    }

    public static class AttendanceDataItem {
        private String code;
        private String title;
        private String value;
        private Map<String, String> features;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public Map<String, String> getFeatures() { return features; }
        public void setFeatures(Map<String, String> features) { this.features = features; }
    }
}
