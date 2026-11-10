package mera.mera_v2.lark.webhook;

import mera.mera_v2.lark.webhook.service.LarkBitableService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class LarkBitableCreationGuardTest {
    private final RestTemplate http = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
    private final LarkBitableService service = new LarkBitableService(http);

    @Test void findsWritableDateOnSecondPage() throws Exception {
        server.expect(r -> assertEquals("GET", r.getMethod().name())).andRespond(withSuccess(
                "{\"code\":0,\"data\":{\"items\":[],\"has_more\":true,\"page_token\":\"next\"}}", MediaType.APPLICATION_JSON));
        server.expect(r -> assertTrue(r.getURI().getQuery().contains("page_token=next"))).andRespond(withSuccess(
                "{\"code\":0,\"data\":{\"items\":[{\"field_name\":\"Ngày tạo\",\"type\":5}]}}", MediaType.APPLICATION_JSON));
        assertNull(service.checkWritableDateField("base", "table", "fake-token", "Ngày tạo"));
        server.verify();
    }

    @Test void explainsReadonlyCreatedTime() throws Exception {
        server.expect(r -> {}).andRespond(withSuccess(
                "{\"code\":0,\"data\":{\"items\":[{\"field_name\":\"Ngày tạo\",\"type\":1001}]}}", MediaType.APPLICATION_JSON));
        assertTrue(service.checkWritableDateField("base", "table", "fake-token", "Ngày tạo").contains("1001"));
        server.verify();
    }

    @Test void duplicateItemsWorkWithoutTotal() {
        server.expect(r -> {}).andRespond(withSuccess(
                "{\"code\":0,\"data\":{\"items\":[{\"record_id\":\"existing\"}]}}", MediaType.APPLICATION_JSON));
        assertTrue(service.checkPhoneExistsWithFilter("base", "table", "fake-token", "0900000001", null));
        server.verify();
    }

    @Test void searchApiErrorIsNotTreatedAsAbsent() {
        server.expect(r -> {}).andRespond(withSuccess("{\"code\":999,\"msg\":\"test-error\"}", MediaType.APPLICATION_JSON));
        assertThrows(IllegalStateException.class,
                () -> service.checkPhoneExistsWithFilter("base", "table", "fake-token", "0900000001", null));
        server.verify();
    }
}
