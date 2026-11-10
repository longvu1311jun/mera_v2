package mera.mera_v2.lark.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import mera.mera_v2.lark.webhook.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WebhookLogAndDateTest {
    @TempDir Path directory;
    private final ObjectMapper json = new ObjectMapper();
    private final ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");

    @Test void resolvesLatestTransitionAcrossPayloadShapesAndIgnoresUnchangedStatus() throws Exception {
        var root = json.readTree("""
                {"histories":[
                  {"status":{"old":0,"new":1},"updated_at":"2026-10-05T08:00:00+07:00"},
                  {"status":{"old":1,"new":1},"updated_at":"2026-11-10T08:00:00+07:00"}],
                 "data":{"histories":[
                  {"status":{"old":0,"new":"1"},"updated_at":"2026-10-06T08:00:00.123456"},
                  {"status":{"old":0,"new":1},"updated_at":"2026-10-02T08:00:00Z"}]}}
                """);
        assertEquals(Instant.parse("2026-10-06T01:00:00.123456Z"), WebhookStatusDate.latestStatusOne(root, zone));
        assertThrows(DateTimeException.class, () -> WebhookStatusDate.latestStatusOne(json.readTree(
                "{\"histories\":[{\"status\":{\"new\":1},\"updated_at\":\"bad-date\"}]}"), zone));
    }

    @Test void persistsAcrossRestartAndShowsLatestOutcomeWithFilters() throws Exception {
        var logs = new WebhookCreationLogService(directory.toString());
        var entry = WebhookCreationLogService.Entry.of(json.readTree("{\"data\":{\"id\":123,\"shop_id\":456}}"));
        logs.append(entry);
        entry.finish("CREATED", "Created");
        logs.append(entry);
        var restarted = new WebhookCreationLogService(directory.toString());
        LocalDate today = LocalDate.now(zone);
        var entries = restarted.read(today, today, "123", "CREATED");
        assertEquals(1, entries.size());
        assertEquals("456", entries.get(0).getShopId());
        assertTrue(restarted.read(today, today, "123", "QUEUED").isEmpty());
        assertTrue(restarted.read(today, today, "999", "").isEmpty());
    }

    @Test void logTemplateRendersAndEscapesUntrustedValues() {
        var resolver = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new org.thymeleaf.spring6.SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var context = new org.thymeleaf.context.Context();
        var entry = new WebhookCreationLogService.Entry();
        entry.setCskh("<script>bad()</script>");
        entry.finish("CREATED", "Created");
        context.setVariable("entries", List.of(entry));
        context.setVariable("status", "");
        context.setVariable("order", "");
        context.setVariable("from", LocalDate.now(zone));
        context.setVariable("to", LocalDate.now(zone));
        String html = engine.process("webhook-lark-logs", context);
        assertTrue(html.contains("Log tạo khách Lark"));
        assertTrue(html.contains("&lt;script&gt;bad()&lt;/script&gt;"));
        assertFalse(html.contains("<script>bad()</script>"));
    }

    @Test void pageAcceptsHtmlDateFiltersAndRejectsUnboundedRanges() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new WebhookCreationLogController(new WebhookCreationLogService(directory.toString())))
                .setSingleView(new org.springframework.web.servlet.view.InternalResourceView("/test-view")).build();
        var valid = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/webhook-lark-logs")
                .param("from", "2026-11-01").param("to", "2026-11-10")).andReturn();
        assertEquals(200, valid.getResponse().getStatus());
        assertEquals(LocalDate.of(2026, 11, 1), valid.getModelAndView().getModel().get("from"));
        var invalid = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/webhook-lark-logs")
                .param("from", "2026-01-01").param("to", "2026-11-10")).andReturn();
        assertTrue(invalid.getModelAndView().getModel().containsKey("error"));
    }
}
