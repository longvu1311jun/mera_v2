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
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    /** key = ten CSKH da chuan hoa (bo dau, bo so, chi giu chu cai) */
    private volatile Map<String, CskhMapping> byName = Collections.emptyMap();

    /** Toan bo entry co baseId, ke ca base he thong khong gan CSKH — dung cho /search-info */
    private volatile List<CskhMapping> all = Collections.emptyList();

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
        List<CskhMapping> all = new ArrayList<>();
        Map<String, CskhMapping> phoneIndex = new LinkedHashMap<>();
        Map<String, CskhMapping> nameIndex = new LinkedHashMap<>();
        Set<String> ambiguousNames = new HashSet<>();
        int systemBases = 0;

        for (CskhMapping m : mappings) {
            if (m.getBaseId() == null || m.getBaseId().isBlank()) {
                log.warn("[CskhMapping] Bo qua entry khong co baseId: cskhName={}, baseName={}",
                        m.getCskhName(), m.getBaseName());
                continue;
            }

            // Entry khong co SDT (base he thong) van duoc giu cho trang /search-info,
            // chi khong tra cuu duoc tu webhook.
            all.add(m);

            String key = normalizePhone(m.getPosPhone());
            if (key == null) {
                systemBases++;
                continue;
            }
            if (m.getKhachHangTableId() == null || m.getKhachHangTableId().isBlank()) {
                log.warn("[CskhMapping] Entry posPhone={} thieu khachHangTableId, webhook se khong tao duoc ban ghi",
                        m.getPosPhone());
                continue;
            }
            CskhMapping old = phoneIndex.put(key, m);
            if (old != null) {
                log.warn("[CskhMapping] Trung posPhone={}, dung entry cuoi cung (baseId={})", m.getPosPhone(), m.getBaseId());
            }

            String nameKey = normalizeName(m.getCskhName());
            if (nameKey != null) {
                CskhMapping oldByName = nameIndex.put(nameKey, m);
                if (oldByName != null && !oldByName.getBaseId().equals(m.getBaseId())) {
                    // Hai CSKH trung ten nhung khac Base -> khong dung ten de tra cuu nua
                    ambiguousNames.add(nameKey);
                    log.warn("[CskhMapping] Trung ten CSKH '{}' o 2 Base khac nhau, se khong tra cuu theo ten cho ten nay",
                            m.getCskhName());
                }
            }
        }
        ambiguousNames.forEach(nameIndex::remove);

        this.all = all;
        this.byPhone = phoneIndex;
        this.byName = nameIndex;
        log.info("[CskhMapping] Da nap {} entry tu {} ({} tra duoc theo SDT, {} theo ten, {} base he thong)",
                all.size(), loadedFrom, phoneIndex.size(), nameIndex.size(), systemBases);
        return phoneIndex.size();
    }

    /**
     * Tim mapping theo SDT CSKH (so sanh theo 9 so cuoi de bo qua khac biet 0/+84).
     */
    public Optional<CskhMapping> findByPhone(String phone) {
        String key = normalizePhone(phone);
        if (key == null) return Optional.empty();
        return Optional.ofNullable(byPhone.get(key));
    }

    /**
     * Tim mapping theo ten CSKH (bo dau, bo so, bo ky tu dac biet).
     * Dung lam phuong an cuoi khi khong tra duoc theo SDT.
     */
    public Optional<CskhMapping> findByName(String name) {
        String key = normalizeName(name);
        if (key == null) return Optional.empty();
        return Optional.ofNullable(byName.get(key));
    }

    /** Cac mapping tra cuu duoc theo SDT (dung cho webhook) */
    public List<CskhMapping> getAll() {
        return new ArrayList<>(byPhone.values());
    }

    /** Toan bo entry, ke ca base he thong khong co SDT (dung cho /search-info) */
    public List<CskhMapping> getAllIncludingSystemBases() {
        return new ArrayList<>(all);
    }

    public int size() {
        return byPhone.size();
    }

    public int totalEntries() {
        return all.size();
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

    /**
     * Chuan hoa ten CSKH de so khop: bo dau tieng Viet, bo so va ky tu dac biet, ve chu thuong.
     * "Nguyễn Thị Hiền - 0362205714" -> "nguyenthihien"
     */
    static String normalizeName(String name) {
        if (name == null) return null;
        String s = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase()
                .replaceAll("[^a-z]", "");
        return s.isBlank() ? null : s;
    }
}
