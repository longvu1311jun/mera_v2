package mera.mera_v2.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@EqualsAndHashCode
public class PosSaleGroupMemberId implements Serializable {

    private static final long serialVersionUID = 1L;

    private String shopUserId;
    private Integer saleGroupId;
}

