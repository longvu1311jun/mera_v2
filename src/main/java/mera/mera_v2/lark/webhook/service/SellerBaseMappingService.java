package mera.mera_v2.lark.webhook.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class SellerBaseMappingService {

    private static final Map<String, String> SELLER_BASE_MAP = new HashMap<>();

    static {
        SELLER_BASE_MAP.put("Nguyen Van Anh - 0369895624", "FtrzbnSfkajsSfsjHMXlGdlpgLf");
        SELLER_BASE_MAP.put("Hương Hiền - 0362205714", "FwTzb7ueqaVkaKsMj4hlxrW1gnI");
        SELLER_BASE_MAP.put("Nguyễn Thị Quyên 0862048268", "KmGibOundaMONHsmGqkl7cgqgie");
        SELLER_BASE_MAP.put("Trần Thị Nhung 0369890462", "D1IjbkcbLacyPPsXnmhlFv5xgNb");
        SELLER_BASE_MAP.put("Mai Thị Dung - 0868450031", "JGd7bDH2la0nuRsf8IqlKUidguy");
        SELLER_BASE_MAP.put("Nguyễn Vũ Huyền Thanh - 0814747251", "STQsbnjzsaexUasFjU0lsCQ2gze");
        SELLER_BASE_MAP.put("Phạm Thị Tố Nga - 0337787629", "XcmybVZTFaOciZsnDtGlzZbIg3g");
        SELLER_BASE_MAP.put("Đỗ Bình Hương Trang 0346910370", "NmEMbTewiaEsyvsWvBOlJIZQgmf");
        SELLER_BASE_MAP.put("Nguyễn Quỳnh Phương 0346174490", "WUF4bilKGazcpBsRhVcluF6Dgff");
        SELLER_BASE_MAP.put("Ngô Thùy Linh 0868842293", "OqrVbWFywaZmYQsXkAglPRxDgnc");
        SELLER_BASE_MAP.put("Trần Thị Phượng 0344168646", "AukubVHSza40fRsYMUolD3MYg9c");
        SELLER_BASE_MAP.put("Vũ Thị Yên 0344937572", "FgP4bEUJhagI3LsIvCKlfcvYgYf");
        SELLER_BASE_MAP.put("Nguyễn Thu Ngân 0345283365", "Ax8obcuTEayWwfs1u6qly5rsgFd");
        SELLER_BASE_MAP.put("Bùi Thị Ngọc 0362259761", "AKjBbtC5PaAGYksbzSjlG3vigdd");
        SELLER_BASE_MAP.put("Ngô Ngọc Hà - 0362246873", "CbArb49N6aQSrOsACZ0lJInSggg");
        SELLER_BASE_MAP.put("Đinh Thị Xuân 0366544903", "LJPvbBNGfaqrdfsjGColQeLsgYc");
        SELLER_BASE_MAP.put("Nguyễn Vân Anh - 0369895624", "FtrzbnSfkajsSfsjHMXlGdlpgLf");
        SELLER_BASE_MAP.put("Bùi Minh Thúy 0363004833", "R9uzbayXJaCuPMswMEOltsD2gXd");
        SELLER_BASE_MAP.put("Nguyễn Thị Xuân 0387754441", "T7Qvb1DLvayaLpskQdAl8Ungg3e");
        SELLER_BASE_MAP.put("Ngô Thị Hương 0988495310", "H5ZMbVG2zaJ79xs3iYulBdL6gSd");
        SELLER_BASE_MAP.put("Dương Minh Giang- 0352843650", "CKDnbOlfmahxxSsAzGllFHz5gog");
        SELLER_BASE_MAP.put("Nguyễn Thị Thùy Linh 0343912253", "ZBp6bWxEMaPTVZsnI2plN2pvg1g");
        SELLER_BASE_MAP.put("Hà Phương Anh 0385084441", "Ss25bicA9aSZmKs6HcRlvZKAgqf");
        SELLER_BASE_MAP.put("Nguyễn Thị Phương 0365943745", "QkiRbMWgcaeGt0sz38Kllkavgbc");
        SELLER_BASE_MAP.put("Vũ Thị Thanh 0362270564", "X1Dcbrws0a2RWZsWmFWldWZlgqf");
        SELLER_BASE_MAP.put("Phạm Thu Trang 0363009938", "GYQRbAjpgaYReSstkB9lMP1jgcg");
        SELLER_BASE_MAP.put("Vũ Thị Lan Anh - 0327564834", "MhTgbDBe3aSquvsBp3al26tvgwf");
        SELLER_BASE_MAP.put("Đỗ Trà My 0362320627", "Sn6LbiAexaeg1MsPx0LlJRfxghd");
        SELLER_BASE_MAP.put("Nguyễn Thị Bích Ngọc 0363026833", "QPKYbdcCnanQomsKvuclo6xwgLg");
        SELLER_BASE_MAP.put("Lê Thúy Kiều - 0333074239", "SJ2RbTyZdawBhbsMtOclNdDZgNh");
        SELLER_BASE_MAP.put("Nguyễn Thị Lan Anh 0333058439", "HSiybdWcVaGJUnsvyuzljICfgWb");
        SELLER_BASE_MAP.put("Tô Thị Dung - 0356722165", "D8evbwClVa08SksjtyCl4sZzgRb");
        SELLER_BASE_MAP.put("Nguyễn Ngọc Quỳnh Anh 0356338575", "Xa7Vbd7nMaHBjds9WDYlvcWkgzd");
        SELLER_BASE_MAP.put("Trần Thị Lý 0325597280", "EX7FbIanJaMAYIsiOfvlPCZ0gqb");
        SELLER_BASE_MAP.put("Đỗ Thị Phi Quỳnh 0394407732", "S3nubTMdkaw13Js60LMly78Ggoe");
        SELLER_BASE_MAP.put("Trần Lan Anh 0333478469", "O8ywbzfkXaszhPsJTTglWOBogpg");
    }

    /**
     * Extract the first 10-digit Vietnamese phone number from a string.
     * Supports formats like: "Nguyễn Vân Anh - 0369895624", "0369895624", "+84369895624"
     */
    private String extractPhoneNumber(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        // Pattern: match 0xxx... or +84xxx... with optional separators, total 10-11 digits
        Pattern pattern = Pattern.compile("(?:\\+84|0)[\\s\\.\\-]*[35789][0-9\\s\\.\\-]{7,10}");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String raw = matcher.group();
            String phone = raw.replaceAll("[^0-9]", "");
            // Normalize to 0... format (strip +84 → 0)
            if (phone.startsWith("84") && phone.length() > 9) {
                phone = "0" + phone.substring(2);
            } else if (!phone.startsWith("0") && phone.length() == 9) {
                phone = "0" + phone;
            }
            // Valid VN mobile: 10 digits starting with 0[35789]
            if (phone.length() == 10 && phone.matches("0[35789].*")) {
                return phone;
            }
        }
        // Fallback: search for any 10-digit sequence
        Pattern simplePattern = Pattern.compile("[0-9]{10}");
        Matcher simpleMatcher = simplePattern.matcher(text.replaceAll("[^0-9]", ""));
        if (simpleMatcher.find()) {
            return simpleMatcher.group();
        }
        return null;
    }

    public Optional<String> findBaseIdBySellerName(String sellerName) {
        if (sellerName == null || sellerName.isBlank()) {
            return Optional.empty();
        }

        String trimmedName = sellerName.trim();

        // Strategy 1: Exact name match (existing behavior)
        String baseId = SELLER_BASE_MAP.get(trimmedName);
        if (baseId != null) {
            log.info("✅ [SellerBaseMapping] Exact name match for '{}': baseId={}", trimmedName, baseId);
            return Optional.of(baseId);
        }

        // Strategy 2: Case-insensitive match
        for (Map.Entry<String, String> entry : SELLER_BASE_MAP.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(trimmedName)) {
                log.info("✅ [SellerBaseMapping] Case-insensitive match for '{}': baseId={}", trimmedName, entry.getValue());
                return Optional.of(entry.getValue());
            }
        }

        // Strategy 3: Phone number match (same approach as /search-info)
        String phoneFromInput = extractPhoneNumber(trimmedName);
        if (phoneFromInput != null) {
            log.info("🔍 [SellerBaseMapping] Extracted phone '{}' from seller name '{}'", phoneFromInput, trimmedName);
            for (Map.Entry<String, String> entry : SELLER_BASE_MAP.entrySet()) {
                String phoneFromMap = extractPhoneNumber(entry.getKey());
                if (phoneFromMap != null && phoneFromMap.equals(phoneFromInput)) {
                    log.info("✅ [SellerBaseMapping] Phone match for '{}' (input phone: '{}'): baseId={} (from '{}')",
                            trimmedName, phoneFromInput, entry.getValue(), entry.getKey());
                    return Optional.of(entry.getValue());
                }
            }
            log.warn("⚠️ [SellerBaseMapping] Phone '{}' extracted but no mapping found for seller '{}'",
                    phoneFromInput, trimmedName);
        } else {
            log.warn("⚠️ [SellerBaseMapping] Cannot extract phone from seller name: '{}'", trimmedName);
        }

        return Optional.empty();
    }

    public Map<String, String> getAllMappings() {
        return new HashMap<>(SELLER_BASE_MAP);
    }
}
