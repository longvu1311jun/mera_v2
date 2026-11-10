package mera.mera_v2.lark.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import mera.mera_v2.customer.Service.SearchConfigService;
import mera.mera_v2.entity.SearchConfig;
import mera.mera_v2.lark.token.TokenStorageService;
import mera.mera_v2.lark.webhook.dto.BitableRecordResponse;
import mera.mera_v2.lark.webhook.service.*;
import mera.mera_v2.repository.SearchConfigRepository;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class CskhWebhookMappingTest {
    private final ObjectMapper json = new ObjectMapper();
    private final LarkBitableService bitable = mock(LarkBitableService.class);
    private final SearchConfigRepository configs = mock(SearchConfigRepository.class);
    private final LarkWebhookController controller = new LarkWebhookController(
            bitable, new PosToBitableMapper(mock(SellerBaseMappingService.class)));

    @BeforeEach void setup() throws Exception {
        ReflectionTestUtils.setField(controller, "searchConfigService",
                new SearchConfigService(configs, null, null, null));
        var tokens = mock(TokenStorageService.class);
        when(tokens.getUserAccessToken()).thenReturn("test-token");
        ReflectionTestUtils.setField(controller, "tokenStorageService", tokens);
        var config = new SearchConfig();
        config.setLarkBaseId("test-base");
        config.setKhachHangTableId("test-table");
        when(configs.findByPosPhoneAndSyncStatusOrderByUpdatedAtDesc("0900000000", 2))
                .thenReturn(List.of(config));
        when(bitable.createRecord(anyString(), anyString(), anyString(), anyMap())).thenReturn(
                json.readValue("{\"code\":0,\"data\":{\"record\":{\"record_id\":\"test-record\"}}}", BitableRecordResponse.class));
    }

    @AfterEach void shutdown() { controller.shutdownExecutors(); }

    private void create(String payload) throws Exception {
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(payload);
        root.put("phone_number", "0900000001");
        root.set("histories", json.readTree("[{\"status\":{\"old\":0,\"new\":1},\"updated_at\":\"2026-10-03T10:15:00+07:00\"}]"));
        ReflectionTestUtils.invokeMethod(controller, "createBitableRecord", root, WebhookCreationLogService.Entry.of(root));
    }

    private void verifyDestination() throws Exception {
        verify(configs).findByPosPhoneAndSyncStatusOrderByUpdatedAtDesc("0900000000", 2);
        verify(bitable).createRecord(eq("test-base"), eq("test-table"), eq("test-token"), anyMap());
    }

    @Test void explicitPhoneOverridesOutdatedPhoneInNameAndNormalizesCountryCode() throws Exception {
        create("{\"assigning_care\":{\"name\":\"TEST 0900000002\",\"phone_number\":\"+84 900 000 000\"}}");
        verifyDestination();
        verify(configs, never()).findByPosPhoneAndSyncStatusOrderByUpdatedAtDesc("0900000002", 2);
    }

    @Test void phoneWithoutNameResolvesTable() throws Exception {
        create("{\"assigning_care\":{\"phone_number\":\"0900000000\"}}");
        verifyDestination();
    }

    @Test void nestedCarePhoneIsUsedWhenRootCareIsEmpty() throws Exception {
        create("{\"assigning_care\":{},\"data\":{\"assigning_care\":{\"phone_number\":\"0900000000\"}}}");
        verifyDestination();
    }

    @Test void legacyNameStillResolvesWhenPhoneIsBlank() throws Exception {
        create("{\"assigning_care\":{\"name\":\"TEST 0900000000\",\"phone_number\":\"  \"}}");
        verifyDestination();
    }

    @Test void missingPhoneDoesNotRouteByCustomerOrSellerPhone() throws Exception {
        create("{\"assigning_care\":{\"name\":\"TEST\"},\"assigning_seller\":{\"phone_number\":\"0900000000\"},\"phone_number\":\"0900000000\"}");
        verifyNoInteractions(configs, bitable);
    }

    // SĐT tài khoản POS chưa có mapping -> dùng SĐT trong tên (mapping CSKH lưu theo SĐT trong tên)
    @Test void unmappedAccountPhoneFallsBackToPhoneInName() throws Exception {
        create("{\"assigning_care\":{\"name\":\"TEST 0900000000\",\"phone_number\":\"0900000002\"}}");
        verifyDestination();
    }

    private WebhookCreationLogService.Entry process(int status, boolean tagged, boolean history) throws Exception {
        return process(status, tagged, history, json.createObjectNode());
    }

    private WebhookCreationLogService.Entry process(int status, boolean tagged, boolean history,
            com.fasterxml.jackson.databind.node.ObjectNode root) throws Exception {
        root.put("status", status);
        root.put("phone_number", "0900000001");
        root.set("assigning_care", json.readTree("{\"phone_number\":\"0900000000\"}"));
        if (tagged) root.set("tags", json.readTree("[{\"id\":32}]"));
        if (history) root.set("histories", json.readTree("[{\"status\":{\"old\":0,\"new\":1},\"updated_at\":\"2026-10-03T10:15:00+07:00\"}]"));
        ReflectionTestUtils.setField(controller, "autoCreateRecord", true);
        var entry = WebhookCreationLogService.Entry.of(root);
        ReflectionTestUtils.invokeMethod(controller, "processLarkFlow", root,
                json.treeToValue(root, mera.mera_v2.lark.webhook.dto.PosOrderWebhook.class), entry);
        return entry;
    }

    @Test void statusOneAndTagCreatesExactlyOnceWithHistoryDate() throws Exception {
        assertEquals("CREATED", process(1, true, true).getStatus());
        verify(bitable, times(1)).createRecord(eq("test-base"), eq("test-table"), eq("test-token"),
                argThat(fields -> Long.valueOf(java.time.Instant.parse("2026-10-03T03:15:00Z").toEpochMilli()).equals(fields.get("Ngày tạo"))));
    }

    @Test void tagCreatesRegardlessOfCurrentStatusOrSource() throws Exception {
        assertEquals("CREATED", process(5, true, true).getStatus());
        verifyDestination();
    }

    @Test void statusOneFacebookCreatesWithoutTag() throws Exception {
        ReflectionTestUtils.setField(controller, "webhookConfigService",
                new mera.mera_v2.lark.webhook.service.WebhookConfigService());
        var root = json.createObjectNode();
        root.put("page_id", "pzl_123");
        assertEquals("CREATED", process(1, false, true, root).getStatus());
        verifyDestination();
    }

    @Test void statusOneNonFacebookWithoutTagSkips() throws Exception {
        ReflectionTestUtils.setField(controller, "webhookConfigService",
                new mera.mera_v2.lark.webhook.service.WebhookConfigService());
        assertEquals("SKIPPED", process(1, false, true).getStatus());
        verifyNoInteractions(bitable);
    }

    @Test void missingHistoryDoesNotInventCurrentDate() throws Exception {
        assertEquals("WAITING_DATE", process(1, true, false).getStatus());
        verify(bitable, never()).createRecord(anyString(), anyString(), anyString(), anyMap());
    }

    @Test void readonlyDateFieldIsVisibleInLogAndDoesNotCreate() throws Exception {
        when(bitable.checkWritableDateField(anyString(), anyString(), anyString(), anyString())).thenReturn("Created time is readonly");
        var entry = process(1, true, true);
        assertEquals("WAITING_FIELD", entry.getStatus());
        assertEquals("Created time is readonly", entry.getReason());
        verify(bitable, never()).createRecord(anyString(), anyString(), anyString(), anyMap());
    }

    @Test void failedDuplicateLookupDoesNotCreateAndIsLogged() throws Exception {
        when(bitable.checkPhoneExistsWithFilter(anyString(), anyString(), anyString(), anyString(), isNull()))
                .thenThrow(new IllegalStateException("secret-test-token"));
        var entry = process(1, true, true);
        assertEquals("FAILED", entry.getStatus());
        assertFalse(entry.getReason().contains("secret-test-token"));
        verify(bitable, never()).createRecord(anyString(), anyString(), anyString(), anyMap());
    }
}
