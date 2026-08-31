package mera.mera_v2.lark.webhook.mapping;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Mapping CSKH -> Lark Base/Table, doc tu file JSON (thay cho bang search_config trong DB).
 *
 * Dung cho ca hai luong:
 *   - Webhook POS -> tao ban ghi: can posPhone + baseId + khachHangTableId
 *   - Trang /search-info: quet tat ca base, ke ca base he thong khong co CSKH/SDT
 *     (TU CHOI CHAM, Don hoan, Huy...) — khi do de trong cskhName/posPhone.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CskhMapping {

    /** Ten CSKH tren POS. De trong = base he thong (search-info se tu liet ke bang) */
    private String cskhName;

    /** So dien thoai CSKH tren POS - khoa tra cuu cua webhook */
    private String posPhone;

    /** Base ID (app_token) cua Lark Base */
    private String baseId;

    /** Ten Base, dung de hien thi trong ket qua search-info */
    private String baseName;

    /** Table ID bang Khach hang / Lieu trinh */
    private String khachHangTableId;

    /** View ID dung khi search trung SDT (mac dinh vew5Ou4Kee neu bo trong) */
    private String khachHangViewId;

    /** Table ID bang Trao doi - search-info doc lich su trao doi tu day */
    private String traoDoiTableId;

    private String traoDoiViewId;

    /** Table ID bang Lich hen */
    private String lichHenTableId;

    private String lichHenViewId;

    /**
     * Neu co gia tri: search-info hien canh bao "Khach hang nam trong bang &lt;ten&gt;"
     * thay vi liet ke trao doi. Dung cho cac base canh bao nhu TU CHOI CHAM, Don hoan, Huy.
     */
    private String specialWarningName;

    /** true khi entry khong gan voi CSKH nao (base he thong dung chung) */
    public boolean isSystemBase() {
        return posPhone == null || posPhone.isBlank();
    }
}
