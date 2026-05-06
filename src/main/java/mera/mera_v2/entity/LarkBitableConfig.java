package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "lark_bitable_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LarkBitableConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_name", nullable = false)
    private String configName;

    @Column(name = "base_name")
    private String baseName;

    @Column(name = "base_id")
    private String baseId;

    @Column(name = "table_name")
    private String tableName;

    @Column(name = "table_id")
    private String tableId;

    @Column(name = "view_id")
    private String viewId;

    @Column(name = "user_access_token")
    private String userAccessToken;

    @Column(name = "is_default")
    private Boolean isDefault;

    @Column(name = "shop_id")
    private Long shopId;
}
