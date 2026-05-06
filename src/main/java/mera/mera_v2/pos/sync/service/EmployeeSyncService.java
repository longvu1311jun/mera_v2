package mera.mera_v2.pos.sync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.*;
import mera.mera_v2.pos.sync.client.EmployeeApiClient;
import mera.mera_v2.pos.sync.dto.EmployeeApiResponse;
import mera.mera_v2.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeSyncService {

    private final EmployeeApiClient employeeApiClient;
    private final PosUserRepository posUserRepository;
    private final PosShopUserRepository posShopUserRepository;
    private final PosDepartmentRepository posDepartmentRepository;
    private final PosSaleGroupRepository posSaleGroupRepository;
    private final PosSaleGroupMemberRepository posSaleGroupMemberRepository;

    public EmployeeSyncResult syncAllEmployees() {
        log.info("Starting employee sync...");

        try {
            EmployeeApiResponse response = employeeApiClient.fetchAllUsers();
            
            if (response == null || response.getData() == null || response.getData().isEmpty()) {
                log.warn("No employees returned from API");
                return EmployeeSyncResult.builder()
                    .totalFromApi(0)
                    .message("No employees found from API")
                    .build();
            }

            List<EmployeeApiResponse.EmployeeDto> employees = response.getData();
            log.info("Processing {} employees...", employees.size());

            return syncEmployeesBatch(employees);

        } catch (Exception e) {
            log.error("Employee sync failed: {}", e.getMessage(), e);
            return EmployeeSyncResult.builder()
                .message("Sync failed: " + e.getMessage())
                .build();
        }
    }

    @Transactional
    public EmployeeSyncResult syncEmployeesBatch(List<EmployeeApiResponse.EmployeeDto> employees) {
        int totalFromApi = employees.size();
        int insertedUsers = 0, updatedUsers = 0;
        int insertedShopUsers = 0, updatedShopUsers = 0;
        int insertedDepartments = 0, updatedDepartments = 0;
        int insertedSaleGroups = 0, updatedSaleGroups = 0;
        int insertedSaleGroupMembers = 0;
        int skippedCount = 0;

        Set<String> userIds = new HashSet<>();
        Set<String> shopUserIds = new HashSet<>();
        Set<Long> departmentIds = new HashSet<>();
        Set<Integer> saleGroupIds = new HashSet<>();

        for (EmployeeApiResponse.EmployeeDto emp : employees) {
            if (emp.getUser() != null && emp.getUser().getId() != null) {
                userIds.add(emp.getUser().getId());
            }
            if (emp.getShopUserId() != null) {
                shopUserIds.add(emp.getShopUserId());
            }
            if (emp.getDepartmentId() != null) {
                departmentIds.add(emp.getDepartmentId());
            }
            if (emp.getSale_group() != null && emp.getSale_group().getId() != null) {
                saleGroupIds.add(emp.getSale_group().getId());
            }
        }

        Map<String, PosUser> existingUsers = new HashMap<>();
        for (PosUser u : posUserRepository.findAllById(userIds)) {
            existingUsers.put(u.getId(), u);
        }

        Map<String, PosShopUser> existingShopUsers = new HashMap<>();
        for (PosShopUser su : posShopUserRepository.findAllById(shopUserIds)) {
            existingShopUsers.put(su.getId(), su);
        }

        Map<Long, PosDepartment> existingDepartments = new HashMap<>();
        for (PosDepartment d : posDepartmentRepository.findAllById(departmentIds)) {
            existingDepartments.put(d.getId(), d);
        }

        Map<Integer, PosSaleGroup> existingSaleGroups = new HashMap<>();
        for (PosSaleGroup sg : posSaleGroupRepository.findAllById(saleGroupIds)) {
            existingSaleGroups.put(sg.getId(), sg);
        }

        Set<String> existingMemberKeys = new HashSet<>();
        for (PosSaleGroupMember m : posSaleGroupMemberRepository.findAll()) {
            String key = m.getShopUserId() + "_" + m.getSaleGroupId();
            existingMemberKeys.add(key);
        }

        List<PosUser> usersToSave = new ArrayList<>();
        List<PosShopUser> shopUsersToSave = new ArrayList<>();
        List<PosDepartment> departmentsToSave = new ArrayList<>();
        List<PosSaleGroup> saleGroupsToSave = new ArrayList<>();
        List<PosSaleGroupMember> membersToSave = new ArrayList<>();

        for (EmployeeApiResponse.EmployeeDto emp : employees) {
            try {
                if (emp.getUser() != null && emp.getUser().getId() != null) {
                    EmployeeApiResponse.EmployeeDto.UserDto userDto = emp.getUser();
                    PosUser posUser = existingUsers.get(userDto.getId());
                    boolean isNewUser = (posUser == null);
                    
                    if (isNewUser) {
                        posUser = new PosUser();
                        posUser.setId(userDto.getId());
                        insertedUsers++;
                    } else {
                        updatedUsers++;
                    }
                    
                    posUser.setName(userDto.getName() != null ? userDto.getName() : "");
                    posUser.setEmail(userDto.getEmail());
                    posUser.setFbId(userDto.getFbId());
                    posUser.setPhoneNumber(userDto.getPhoneNumber());
                    posUser.setAvatarUrl(userDto.getAvatarUrl());
                    posUser.setCreatedAt(LocalDateTime.now());
                    posUser.setUpdatedAt(LocalDateTime.now());
                    
                    usersToSave.add(posUser);
                }

                if (emp.getShopUserId() != null) {
                    PosShopUser shopUser = existingShopUsers.get(emp.getShopUserId());
                    boolean isNewShopUser = (shopUser == null);
                    
                    if (isNewShopUser) {
                        shopUser = new PosShopUser();
                        shopUser.setId(emp.getShopUserId());
                        insertedShopUsers++;
                    } else {
                        updatedShopUsers++;
                    }
                    
                    shopUser.setShopId(emp.getShopId() != null ? emp.getShopId() : 1546758L);
                    shopUser.setUserId(emp.getUserId());
                    // Chỉ set department_id nếu department tồn tại trong DB hoặc có thông tin để insert
                    boolean deptExistsInDb = existingDepartments.containsKey(emp.getDepartmentId());
                    boolean deptWillBeInserted = emp.getDepartment() != null;
                    // Kiểm tra thêm: department.id phải match với department_id
                    boolean deptIdMatches = emp.getDepartment() != null 
                        && emp.getDepartment().getId() != null 
                        && emp.getDepartment().getId().equals(emp.getDepartmentId());
                    
                    if (emp.getDepartmentId() != null && (deptExistsInDb || deptIdMatches)) {
                        shopUser.setDepartmentId(emp.getDepartmentId());
                    } else {
                        log.debug("ShopUser {} has invalid department_id {}, setting to null", 
                            emp.getShopUserId(), emp.getDepartmentId());
                        shopUser.setDepartmentId(null);
                    }
                    shopUser.setRole(emp.getRole());
                    shopUser.setPermissionInSaleGroup(emp.getPermissionInSaleGroup());
                    shopUser.setIsAssigned(emp.getIsAssigned() != null ? emp.getIsAssigned() : false);
                    shopUser.setEnableApi(emp.getEnableApi() != null ? emp.getEnableApi() : false);
                    shopUser.setApiKey(emp.getApiKey());
                    shopUser.setNoteApiKey(emp.getNoteApiKey());
                    shopUser.setIsApiKey(emp.getIsApiKey() != null ? emp.getIsApiKey() : false);
                    
                    Integer poc = emp.getPendingOrderCount();
                    shopUser.setPendingOrderCount(poc != null ? poc : 0);
                    
                    shopUser.setPreferredShop(emp.getPreferredShop());
                    shopUser.setAppWarehouse(emp.getAppWarehouse());
                    shopUser.setCreatorId(emp.getCreatorId());
                    shopUser.setProfileId(emp.getProfileId());
                    shopUser.setInsertedAt(parseDateTime(emp.getInsertedAt()));
                    shopUser.setUpdatedAt(LocalDateTime.now());
                    
                    shopUsersToSave.add(shopUser);
                }

                if (emp.getDepartmentId() != null && emp.getDepartment() != null) {
                    Long deptId = emp.getDepartment().getId();
                    PosDepartment dept = existingDepartments.get(deptId);
                    boolean isNewDept = (dept == null);
                    
                    if (isNewDept) {
                        dept = new PosDepartment();
                        dept.setId(deptId);
                        insertedDepartments++;
                    } else {
                        updatedDepartments++;
                    }
                    
                    dept.setName(emp.getDepartment().getName());
                    dept.setShopId(emp.getShopId() != null ? emp.getShopId() : 1546758L);
                    dept.setCreatedAt(LocalDateTime.now());
                    
                    departmentsToSave.add(dept);
                }

                if (emp.getSale_group() != null && emp.getSale_group().getId() != null) {
                    Integer sgId = emp.getSale_group().getId();
                    PosSaleGroup saleGroup = existingSaleGroups.get(sgId);
                    boolean isNewSG = (saleGroup == null);
                    
                    if (isNewSG) {
                        saleGroup = new PosSaleGroup();
                        saleGroup.setId(sgId);
                        insertedSaleGroups++;
                    } else {
                        updatedSaleGroups++;
                    }
                    
                    saleGroup.setName(emp.getSale_group().getName());
                    saleGroup.setShopId(emp.getShopId() != null ? emp.getShopId() : 1546758L);
                    
                    saleGroupsToSave.add(saleGroup);
                }

                if (emp.getSale_group() != null && emp.getSale_group().getId() != null 
                    && emp.getShopUserId() != null) {
                    String memberKey = emp.getShopUserId() + "_" + emp.getSale_group().getId();
                    
                    if (!existingMemberKeys.contains(memberKey)) {
                        PosSaleGroupMember member = new PosSaleGroupMember();
                        member.setShopUserId(emp.getShopUserId());
                        member.setSaleGroupId(emp.getSale_group().getId());
                        member.setPermission(emp.getPermissionInSaleGroup());
                        
                        membersToSave.add(member);
                        existingMemberKeys.add(memberKey);
                        insertedSaleGroupMembers++;
                    }
                }

            } catch (Exception e) {
                log.error("Failed to process employee {}: {}", emp.getShopUserId(), e.getMessage());
                skippedCount++;
            }
        }

        saveWithRetry(() -> {
            // Lưu các bảng không có dependency trước
            if (!usersToSave.isEmpty()) {
                posUserRepository.saveAll(usersToSave);
                log.debug("Saved {} pos_users", usersToSave.size());
            }
            if (!departmentsToSave.isEmpty()) {
                posDepartmentRepository.saveAll(departmentsToSave);
                log.debug("Saved {} pos_departments", departmentsToSave.size());
            }
            if (!saleGroupsToSave.isEmpty()) {
                posSaleGroupRepository.saveAll(saleGroupsToSave);
                log.debug("Saved {} pos_sale_groups", saleGroupsToSave.size());
            }
            // Lưu bảng có dependency sau
            if (!shopUsersToSave.isEmpty()) {
                posShopUserRepository.saveAll(shopUsersToSave);
                log.debug("Saved {} pos_shop_users", shopUsersToSave.size());
            }
            if (!membersToSave.isEmpty()) {
                posSaleGroupMemberRepository.saveAll(membersToSave);
                log.debug("Saved {} pos_sale_group_members", membersToSave.size());
            }
        });

        String message = String.format(
            "Synced %d employees: %d users, %d shop_users, %d departments, %d sale_groups, %d group_members",
            totalFromApi, usersToSave.size(), shopUsersToSave.size(), 
            departmentsToSave.size(), saleGroupsToSave.size(), membersToSave.size());

        log.info("Employee sync completed: {}", message);

        return EmployeeSyncResult.builder()
            .totalFromApi(totalFromApi)
            .insertedUsers(insertedUsers)
            .updatedUsers(updatedUsers)
            .insertedShopUsers(insertedShopUsers)
            .updatedShopUsers(updatedShopUsers)
            .insertedDepartments(insertedDepartments)
            .updatedDepartments(updatedDepartments)
            .insertedSaleGroups(insertedSaleGroups)
            .updatedSaleGroups(updatedSaleGroups)
            .insertedSaleGroupMembers(insertedSaleGroupMembers)
            .skippedCount(skippedCount)
            .message(message)
            .build();
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(value.replace("Z", "").substring(0, 19));
        } catch (Exception e) {
            log.warn("Cannot parse datetime '{}', using now", value);
            return LocalDateTime.now();
        }
    }

    @FunctionalInterface
    private interface RetryableOperation {
        void execute() throws Exception;
    }

    private void saveWithRetry(RetryableOperation op) {
        int retries = 0;
        while (retries < 3) {
            try {
                op.execute();
                return;
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("Deadlock")) {
                    retries++;
                    log.warn("Deadlock on save attempt {}, retrying...", retries);
                    if (retries >= 3) {
                        throw new RuntimeException("Deadlock persisted after 3 retries", e);
                    }
                    try { Thread.sleep(200L * retries); } 
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                } else {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
