package mera.mera_v2.lark.sync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.LarkDepartment;
import mera.mera_v2.entity.LarkEmployee;
import mera.mera_v2.lark.sync.client.LarkEmployeeClient;
import mera.mera_v2.lark.sync.client.LarkEmployeeClient.DepartmentOrder;
import mera.mera_v2.lark.sync.client.LarkEmployeeClient.EmployeeItem;
import mera.mera_v2.lark.sync.client.LarkEmployeeClient.EmployeeListResponse;
import mera.mera_v2.lark.sync.client.LarkEmployeeClient.EmployeeStatus;
import mera.mera_v2.repository.LarkDepartmentRepository;
import mera.mera_v2.repository.LarkEmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LarkEmployeeSyncService {

    private final LarkEmployeeClient employeeClient;
    private final LarkEmployeeRepository employeeRepository;
    private final LarkDepartmentRepository departmentRepository;

    @Transactional
    public LarkEmployeeSyncResult syncAllEmployees(String userToken) {
        log.info("[lark-sync] start sync employees");

        List<LarkDepartment> allDepts = departmentRepository.findAll();
        Map<String, LarkDepartment> deptMap = new HashMap<>();
        for (LarkDepartment dept : allDepts) {
            if (dept.getOpenId() != null) {
                deptMap.put(dept.getOpenId(), dept);
            }
        }

        int totalFromApi = 0;
        int inserted = 0, updated = 0;
        Set<String> processedOpenIds = new HashSet<>();

        for (LarkDepartment dept : allDepts) {
            try {
                String pageToken = null;
                do {
                    EmployeeListResponse response = employeeClient.findUsersByDepartment(
                            dept.getOpenId(), pageToken, userToken);

                    for (EmployeeItem item : response.getItems()) {
                        totalFromApi++;
                        String primaryDeptOpenId = getPrimaryDepartmentOpenId(item);
                        String departmentDbId = null;

                        if (primaryDeptOpenId != null && deptMap.containsKey(primaryDeptOpenId)) {
                            departmentDbId = deptMap.get(primaryDeptOpenId).getId();
                        } else if (dept.getId() != null) {
                            departmentDbId = dept.getId();
                        }

                        boolean isNew = !processedOpenIds.contains(item.getOpenId());
                        upsertEmployee(item, departmentDbId);
                        processedOpenIds.add(item.getOpenId());

                        if (isNew) {
                            inserted++;
                        } else {
                            updated++;
                        }
                    }

                    pageToken = response.hasMore() ? response.getPageToken() : null;

                } while (pageToken != null);

            } catch (Exception e) {
                log.warn("[lark-sync] error endpoint=/contact/v3/users/find_by_department " +
                        "params=dept={} code={} msg={}",
                        dept.getOpenId(), getErrorCode(e), e.getMessage());
                if (e.getMessage() != null && e.getMessage().contains("99991400")) {
                    log.info("[lark-sync] Dept {} has no users or not accessible", dept.getOpenId());
                }
            }
        }

        String message = String.format("employees inserted=%d updated=%d", inserted, updated);
        log.info("[lark-sync] {}", message);

        return LarkEmployeeSyncResult.builder()
                .totalFromApi(totalFromApi)
                .inserted(inserted)
                .updated(updated)
                .message(message)
                .build();
    }

    private String getPrimaryDepartmentOpenId(EmployeeItem user) {
        if (user.getOrders() != null) {
            for (DepartmentOrder order : user.getOrders()) {
                if (order.isPrimaryDept()) {
                    return order.getDepartmentId();
                }
            }
        }

        if (user.getDepartmentIds() != null && !user.getDepartmentIds().isEmpty()) {
            return user.getDepartmentIds().get(0);
        }

        return null;
    }

    private LarkEmployee upsertEmployee(EmployeeItem item, String departmentDbId) {
        Optional<LarkEmployee> existing = employeeRepository.findByOpenId(item.getOpenId());

        LocalDateTime now = LocalDateTime.now();

        if (existing.isPresent()) {
            LarkEmployee emp = existing.get();
            emp.setId(item.getUserId());
            emp.setUnionId(nullIfEmpty(item.getUnionId()));
            emp.setName(item.getName());
            emp.setEmail(nullIfEmpty(item.getEmail()));
            emp.setPhoneNumber(nullIfEmpty(item.getMobile()));
            emp.setEmployeeNo(nullIfEmpty(item.getEmployeeNo()));
            emp.setDepartmentId(departmentDbId);
            emp.setJobTitle(nullIfEmpty(item.getJobTitle()));
            emp.setAvatarUrl(nullIfEmpty(item.getAvatarUrl()));
            emp.setStatus(calculateEmployeeStatus(item.getStatus()));
            emp.setUpdatedAt(now);
            return employeeRepository.save(emp);
        } else {
            LarkEmployee emp = new LarkEmployee();
            emp.setId(item.getUserId());
            emp.setOpenId(item.getOpenId());
            emp.setUnionId(nullIfEmpty(item.getUnionId()));
            emp.setName(item.getName());
            emp.setEmail(nullIfEmpty(item.getEmail()));
            emp.setPhoneNumber(nullIfEmpty(item.getMobile()));
            emp.setEmployeeNo(nullIfEmpty(item.getEmployeeNo()));
            emp.setDepartmentId(departmentDbId);
            emp.setJobTitle(nullIfEmpty(item.getJobTitle()));
            emp.setAvatarUrl(nullIfEmpty(item.getAvatarUrl()));
            emp.setStatus(calculateEmployeeStatus(item.getStatus()));
            emp.setCreatedAt(now);
            emp.setUpdatedAt(now);
            return employeeRepository.save(emp);
        }
    }

    private int calculateEmployeeStatus(EmployeeStatus status) {
        if (status == null) return 0;

        boolean isActivated = status.isActivated();
        boolean isExited = status.isExited();
        boolean isFrozen = status.isFrozen();
        boolean isResigned = status.isResigned();
        boolean isUnjoin = status.isUnjoin();

        if (isActivated && !isExited && !isFrozen && !isResigned && !isUnjoin) {
            return 1;
        }
        return 0;
    }

    private String nullIfEmpty(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value;
    }

    private int getErrorCode(Exception e) {
        return -1;
    }
}
