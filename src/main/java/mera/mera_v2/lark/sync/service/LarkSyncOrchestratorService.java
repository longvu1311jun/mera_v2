package mera.mera_v2.lark.sync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LarkSyncOrchestratorService {

    private final LarkDepartmentSyncService departmentSyncService;
    private final LarkEmployeeSyncService employeeSyncService;

    public SyncResult runFullSync(String userToken) {
        log.info("[lark-sync] Starting full sync (departments + employees)");

        DepartmentSyncResult deptResult = departmentSyncService.syncAllDepartments(userToken);
        LarkEmployeeSyncResult empResult = employeeSyncService.syncAllEmployees(userToken);

        log.info("[lark-sync] done. dept: {}, emp: {}", deptResult.getMessage(), empResult.getMessage());

        return SyncResult.builder()
                .departmentResult(deptResult)
                .larkEmployeeResult(empResult)
                .build();
    }

    public DepartmentSyncResult syncDepartments(String userToken) {
        return departmentSyncService.syncAllDepartments(userToken);
    }

    public LarkEmployeeSyncResult syncEmployees(String userToken) {
        return employeeSyncService.syncAllEmployees(userToken);
    }
}
