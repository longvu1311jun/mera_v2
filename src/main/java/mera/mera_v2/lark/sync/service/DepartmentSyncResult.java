package mera.mera_v2.lark.sync.service;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DepartmentSyncResult {
    private int inserted;
    private int updated;
    private int skippedDeleted;
    private String message;
}
