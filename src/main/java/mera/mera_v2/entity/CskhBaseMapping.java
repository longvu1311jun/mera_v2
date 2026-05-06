package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cskh_base_mapping",
       indexes = {
           @Index(name = "idx_pos_phone", columnList = "pos_phone"),
           @Index(name = "idx_lark_base_id", columnList = "lark_base_id")
       },
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_pos_phone", columnNames = "pos_phone")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CskhBaseMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pos_user_id")
    private String posUserId;

    @Column(name = "pos_name", nullable = false)
    private String posName;

    @Column(name = "pos_phone")
    private String posPhone;

    @Column(name = "lark_base_name")
    private String larkBaseName;

    @Column(name = "lark_base_id")
    private String larkBaseId;

    @Column(name = "khach_hang_table_id")
    private String khachHangTableId;

    @Column(name = "trao_doi_table_id")
    private String traoDoiTableId;

    @Column(name = "lich_hen_table_id")
    private String lichHenTableId;

    @Column(name = "view_id")
    private String viewId;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "department_name")
    private String departmentName;
}
