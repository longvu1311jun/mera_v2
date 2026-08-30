package mera.mera_v2.lark.webhook.mapping;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Mapping CSKH -> Lark Base/Table, doc tu file JSON (thay cho bang search_config trong DB).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CskhMapping {

    /** Ten CSKH tren POS, chi dung de hien thi/doi soat */
    private String cskhName;

    /** So dien thoai CSKH tren POS - khoa de tra cuu */
    private String posPhone;

    /** Base ID (app_token) cua Lark Base */
    private String baseId;

    /** Ten Base, chi dung de hien thi/doi soat */
    private String baseName;

    /** Table ID bang Khach hang / Lieu trinh */
    private String khachHangTableId;

    /** View ID dung khi search trung SDT (mac dinh vew5Ou4Kee neu bo trong) */
    private String khachHangViewId;
}
