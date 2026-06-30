package mera.mera_v2.lark.sync.service;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SyncResult {
    private DepartmentSyncResult departmentResult;
    private LarkEmployeeSyncResult larkEmployeeResult;
}
