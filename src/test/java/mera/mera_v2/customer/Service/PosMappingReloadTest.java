package mera.mera_v2.customer.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import mera.mera_v2.entity.SearchConfig;
import mera.mera_v2.lark.wiki.LarkWikiService;
import mera.mera_v2.model.*;
import mera.mera_v2.repository.SearchConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PosMappingReloadTest {
    private final PosService pos = new PosService();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(
            (RestTemplate) ReflectionTestUtils.getField(pos, "restTemplate")).build();

    private void respond(List<String> departments) throws Exception {
        var users = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < departments.size(); i++) {
            users.add(Map.of("id", "test-user-" + i,
                    "department", Map.of("name", departments.get(i)),
                    "user", Map.of("name", "1, Test Employee 0900000000")));
        }
        server.expect(request -> assertEquals("GET", request.getMethod().name()))
                .andRespond(withSuccess(new ObjectMapper().writeValueAsString(Map.of("data", users)),
                        MediaType.APPLICATION_JSON));
    }

    @Test void acceptsExistingDepartmentsAndNumberedGroupsOnly() throws Exception {
        respond(List.of("NV sale 2 NT", "SALE 2 Nguyễn Trãi", "SALE 2 OCP", "NV SALE 2 OCP",
                "Sale 2 OCP nhóm 1", " NV SALE 2 OCP  NHOM 2 ",
                "SALE 2 OCP nghỉ việc", "Sale 2 OCP nhóm 1 nghỉ việc", "SALE 2 OCP khác", "SALE 1 OCP"));
        var users = pos.getUsers();
        assertEquals(List.of("test-user-0", "test-user-1", "test-user-2", "test-user-3", "test-user-4", "test-user-5"),
                users.stream().map(PosUser::getId).toList());
        server.verify();
    }

    @Test void reloadLinksExistingBaseToGroupEmployeeByPhoneInName() throws Exception {
        respond(List.of("Sale 2 OCP nhóm 1"));
        var repo = mock(SearchConfigRepository.class);
        var wiki = mock(LarkWikiService.class);
        var tables = mock(BitableService.class);
        var config = new SearchConfig();
        config.setLarkBaseId("test-base");
        when(repo.findBySyncStatusOrderByUpdatedAtDesc(0)).thenReturn(List.of(config));
        var node = new LarkNode();
        node.setObjToken("test-base");
        node.setNodeToken("test-node");
        node.setTitle("Test Employee 0900000000");
        when(wiki.getAllNodes()).thenReturn(List.of(node));
        when(wiki.getChildNodesByNodeToken("test-node")).thenReturn(List.of());
        var table = new BitableTable();
        table.setName("Khách Hàng");
        table.setTableId("test-customers");
        when(tables.getTablesByBaseId(null, "test-base")).thenReturn(List.of(table));
        when(tables.getDefaultViewId(null, "test-base", "test-customers")).thenReturn("test-view");

        assertEquals(1, new SearchConfigService(repo, pos, wiki, tables).reloadAll());
        verify(repo).save(config);
        assertEquals("test-user-0", config.getPosUserId());
        assertEquals("1, Test Employee 0900000000", config.getPosName());
        assertEquals("0900000000", config.getPosPhone());
        assertEquals("Sale 2 OCP nhóm 1", config.getDepartmentName());
        assertEquals("test-customers", config.getKhachHangTableId());
        assertEquals("test-view", config.getKhachHangViewId());
        server.verify();
    }
}
