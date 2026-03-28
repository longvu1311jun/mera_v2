package mera.mera_v2.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@EqualsAndHashCode
public class OrderTagId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long orderId;
    private Long tagId;
}