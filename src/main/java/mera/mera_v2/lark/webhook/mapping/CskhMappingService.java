package mera.mera_v2.lark.webhook.mapping;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Nguon mapping CSKH -> Base/Table cua nhanh chi-Lark: doc tu file JSON, KHONG dung database.
 *
 * Thu tu tim file:
 *   1. Duong dan cau hinh o cskh.mapping.file (mac dinh: ./config/cskh-mapping.json)
 *   2. File cskh-mapping.json trong classpath (src/main/resources)
 *
 * Cau truc file: xem src/main/resources/cskh-mapping.json
 */
@Slf4j
@Service
public class CskhMappingService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${cskh.mapping.file:config/cskh-mapping.json}")
    private String mappingFilePath;

    /** key = 9 so cuoi cua SDT CSKH */
    private volatile Map<String, CskhMapping> byPhone = Collections.emptyMap();

    private volatile String loadedFrom = "(chua load)";

    @PostConstruct
    public void init() {
        reload();
    }

    /**
     * Nap lai mapping tu file. An toan khi goi luc runtime (webhook van doc duoc map cu cho den khi xong).
     *
     * @return so luong mapping da nap
     */
    public synchronized int reload() {
        List<CskhMapping> mappings = readMappings();
        Map<String, CskhMapping> index = new LinkedHashMap<>();

        for (CskhMapping m : mappings) {
            String key = normalizePhone(m.getPosPhone());
            if (key == null) {
                log.warn("[CskhMapping] Bo qua entry khong co posPhone hop le: cskhName={}, baseId={}",
                        m.getCskhName(), m.getBaseId());
                continue;
            }
            if (m.getBaseId() == null || m.getBaseId().isBlank()
                    || m.getKhachHangTableId() == null || m.getKhachHangTableId().isBlank()) {
                log.warn("[CskhMapping] Bo qua entry thieu baseId/khachHangTableId: posPhone={}", m.getPosPhone());
                continue;
            }
            CskhMapping old = index.put(key, m);
            if (old != null) {
                log.warn("[CskhMapping] Trung posPhone={}, dung entry cuoi cung (baseId={})", m.getPosPhone(), m.getBaseId());
            }
        }

        this.byPhone = index;
        log.info("[CskhMapping] Da nap {} mapping tu {}", index.size(), loadedFrom);
        return index.size();
    }

    /**
     * Tim mapping theo SDT CSKH (so sanh theo 9 so cuoi de bo qua khac biet 0/+84).
     */
    public Optional<CskhMapping> findByPhone(String phone) {
        String key = normalizePhone(phone);
        if (key == null) return Optional.empty();
        return Optional.ofNullable(byPhone.get(key));
    }

    public List<CskhMapping> getAll() {
        return new ArrayList<>(byPhone.values());
    }

    public int size() {
        return byPhone.size();
    }

    public String getLoadedFrom() {
        return loadedFrom;
    }

    private List<CskhMapping> readMappings() {
        // 1. File ngoai (uu tien) - sua duoc ma khong can build lai
        try {
            Path path = Paths.get(mappingFilePath);
            if (Files.isRegularFile(path)) {
                try (InputStream in = Files.newInputStream(path)) {
                    List<CskhMapping> result = parse(in);
                    loadedFrom = path.toAbsolutePath().toString();
                    return result;
                }
            }
        } catch (Exception e) {
            log.error("[CskhMapping] Loi doc file {}: {}", mappingFilePath, e.getMessage(), e);
        }

        // 2. Classpath fallback
        try {
            ClassPathResource resource = new ClassPathResource("cskh-mapping.json");
            if (resource.exists()) {
                try (InputStream in = resource.getInputStream()) {
                    List<CskhMapping> result = parse(in);
                    loadedFrom = "classpath:cskh-mapping.json";
                    return result;
                }
            }
        } catch (Exception e) {
            log.error("[CskhMapping] Loi doc classpath:cskh-mapping.json: {}", e.getMessage(), e);
        }

        loadedFrom = "(khong tim thay file)";
        log.error("[CskhMapping] Khong tim thay file mapping ({} hoac classpath:cskh-mapping.json). "
                + "Webhook se khong tao duoc ban ghi nao.", mappingFilePath);
        return List.of();
    }

    /** Chap nhan ca dang {"mappings": [...]} lan mang JSON tran [...] */
    private List<CskhMapping> parse(InputStream in) throws Exception {
        var root = objectMapper.readTree(in);
        var node = root.isArray() ? root : root.get("mappings");
        if (node == null || !node.isArray()) {
            throw new IllegalArgumentException("File mapping phai la mang JSON hoac object co truong \"mappings\"");
        }
        return objectMapper.convertValue(node, new TypeReference<List<CskhMapping>>() {});
    }

    /** Lay 9 so cuoi de so khop SDT (0968420624 / +84968420624 / 968420624 -> 968420624) */
    static String normalizePhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 9) return null;
        return digits.substring(digits.length() - 9);
    }
}
