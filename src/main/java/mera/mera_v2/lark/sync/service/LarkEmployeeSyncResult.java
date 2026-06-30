package mera.mera_v2.lark.sync.service;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LarkEmployeeSyncResult {
    private int totalFromApi;
    private int inserted;
    private int updated;
    private String message;
}
