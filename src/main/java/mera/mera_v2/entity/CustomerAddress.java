package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "customer_addresses")
public class CustomerAddress {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "customer_id", length = 64, nullable = false)
    private String customerId;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "full_address", columnDefinition = "TEXT")
    private String fullAddress;

    @Column(name = "province_id", length = 16)
    private String provinceId;

    @Column(name = "province_name", length = 128)
    private String provinceName;

    @Column(name = "district_id", length = 16)
    private String districtId;

    @Column(name = "district_name", length = 128)
    private String districtName;

    @Column(name = "commune_id", length = 16)
    private String communeId;

    @Column(name = "commune_name", length = 128)
    private String communeName;

    @Column(name = "country_code", length = 8)
    private String countryCode;

    @Column(name = "post_code", length = 16)
    private String postCode;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Customer customer;
}