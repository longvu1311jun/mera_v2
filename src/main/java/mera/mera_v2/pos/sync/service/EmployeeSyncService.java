package mera.mera_v2.pos.sync.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.*;
import mera.mera_v2.pos.sync.client.EmployeeApiClient;
import mera.mera_v2.pos.sync.dto.EmployeeApiResponse;
import mera.mera_v2.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeSyncService {

    @PersistenceContext
    private EntityManager entityManager;
    
    private final EmployeeApiClient employeeApiClient;
    private final PosUserRepository posUserRepository;
    private final PosShopUserRepository posShopUserRepository;
    private final PosDepartmentRepository posDepartmentRepository;
    private final PosSaleGroupRepository posSaleGroupRepository;
    private final PosSaleGroupMemberRepository posSaleGroupMemberRepository;
    private final TransactionTemplate transactionTemplate;

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

        // ========================================
        // PHASE 1: Collect all IDs from API response
        // ========================================
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

        // ========================================
        // PHASE 2: Load existing entities into in-memory cache
        // ========================================
        Map<String, PosUser> userCache = new HashMap<>();
        for (PosUser u : posUserRepository.findAllById(userIds)) {
            userCache.put(u.getId(), u);
        }

        Map<String, PosShopUser> shopUserCache = new HashMap<>();
        for (PosShopUser su : posShopUserRepository.findAllById(shopUserIds)) {
            shopUserCache.put(su.getId(), su);
        }

        Map<Long, PosDepartment> departmentCache = new HashMap<>();
        for (PosDepartment d : posDepartmentRepository.findAllById(departmentIds)) {
            departmentCache.put(d.getId(), d);
        }

        Map<Integer, PosSaleGroup> saleGroupCache = new HashMap<>();
        for (PosSaleGroup sg : posSaleGroupRepository.findAllById(saleGroupIds)) {
            saleGroupCache.put(sg.getId(), sg);
        }

        Set<String> existingMemberKeys = new HashSet<>();
        for (PosSaleGroupMember m : posSaleGroupMemberRepository.findAll()) {
            existingMemberKeys.add(m.getShopUserId() + "_" + m.getSaleGroupId());
        }

        // ========================================
        // PHASE 3: Process each employee
        // ========================================
        List<PosUser> usersToSave = new ArrayList<>();
        List<PosDepartment> departmentsToSave = new ArrayList<>();
        List<PosSaleGroup> saleGroupsToSave = new ArrayList<>();
        List<PosShopUser> shopUsersToSave = new ArrayList<>();
        List<PosSaleGroupMember> membersToSave = new ArrayList<>();

        for (EmployeeApiResponse.EmployeeDto emp : employees) {
            try {
                // --- Process PosUser ---
                PosUser posUser = null;
                if (emp.getUser() != null && emp.getUser().getId() != null) {
                    EmployeeApiResponse.EmployeeDto.UserDto userDto = emp.getUser();
                    posUser = userCache.get(userDto.getId());

                    if (posUser == null) {
                        posUser = new PosUser();
                        posUser.setId(userDto.getId());
                        userCache.put(userDto.getId(), posUser); // Add to cache
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

                // --- SAFE PATTERN: Department - create if not exists ---
                PosDepartment department = null;
                if (emp.getDepartment() != null && emp.getDepartmentId() != null) {
                    Long deptId = emp.getDepartmentId();
                    department = departmentCache.get(deptId);

                    if (department == null) {
                        department = new PosDepartment();
                        department.setId(deptId);
                        departmentCache.put(deptId, department); // Add to cache BEFORE save
                        insertedDepartments++;
                    } else {
                        updatedDepartments++;
                    }

                    department.setName(emp.getDepartment().getName());
                    department.setShopId(emp.getShopId() != null ? emp.getShopId() : 1546758L);
                    department.setCreatedAt(LocalDateTime.now());

                    departmentsToSave.add(department);
                }

                // --- SAFE PATTERN: SaleGroup - create if not exists ---
                PosSaleGroup saleGroup = null;
                if (emp.getSale_group() != null && emp.getSale_group().getId() != null) {
                    Integer sgId = emp.getSale_group().getId();
                    saleGroup = saleGroupCache.get(sgId);

                    if (saleGroup == null) {
                        saleGroup = new PosSaleGroup();
                        saleGroup.setId(sgId);
                        saleGroupCache.put(sgId, saleGroup); // Add to cache BEFORE save
                        insertedSaleGroups++;
                    } else {
                        updatedSaleGroups++;
                    }

                    saleGroup.setName(emp.getSale_group().getName());
                    saleGroup.setShopId(emp.getShopId() != null ? emp.getShopId() : 1546758L);

                    saleGroupsToSave.add(saleGroup);
                }

                // --- Process PosShopUser with JPA relationship ---
                if (emp.getShopUserId() != null) {
                    PosShopUser shopUser = shopUserCache.get(emp.getShopUserId());

                    if (shopUser == null) {
                        shopUser = new PosShopUser();
                        shopUser.setId(emp.getShopUserId());
                        shopUserCache.put(emp.getShopUserId(), shopUser); // Add to cache
                        insertedShopUsers++;
                    } else {
                        updatedShopUsers++;
                    }

                    shopUser.setShopId(emp.getShopId() != null ? emp.getShopId() : 1546758L);
                    shopUser.setUserId(emp.getUserId());

                    // Use JPA relationship - department already in cache and will be saved
                    shopUser.setDepartment(department);

                    shopUser.setRole(emp.getRole());
                    shopUser.setPermissionInSaleGroup(emp.getPermissionInSaleGroup());
                    shopUser.setIsAssigned(emp.getIsAssigned() != null ? emp.getIsAssigned() : false);
                    shopUser.setEnableApi(emp.getEnableApi() != null ? emp.getEnableApi() : false);
                    shopUser.setApiKey(emp.getApiKey());
                    shopUser.setNoteApiKey(emp.getNoteApiKey());
                    shopUser.setIsApiKey(emp.getIsApiKey() != null ? emp.getIsApiKey() : false);
                    shopUser.setPendingOrderCount(emp.getPendingOrderCount() != null ? emp.getPendingOrderCount() : 0);
                    shopUser.setPreferredShop(emp.getPreferredShop());
                    shopUser.setAppWarehouse(emp.getAppWarehouse());
                    shopUser.setCreatorId(emp.getCreatorId());
                    shopUser.setProfileId(emp.getProfileId());
                    shopUser.setInsertedAt(parseDateTime(emp.getInsertedAt()));
                    shopUser.setUpdatedAt(LocalDateTime.now());

                    shopUsersToSave.add(shopUser);
                }

                // --- Process PosSaleGroupMember ---
                if (saleGroup != null && emp.getShopUserId() != null) {
                    String memberKey = emp.getShopUserId() + "_" + saleGroup.getId();

                    if (!existingMemberKeys.contains(memberKey)) {
                        PosSaleGroupMember member = new PosSaleGroupMember();
                        member.setShopUserId(emp.getShopUserId());
                        member.setSaleGroupId(saleGroup.getId());
                        member.setPermission(emp.getPermissionInSaleGroup());

                        membersToSave.add(member);
                        existingMemberKeys.add(memberKey);
                        insertedSaleGroupMembers++;
                    }
                }

            } catch (Exception e) {
                log.error("Failed to process employee {}: {}", emp.getShopUserId(), e.getMessage(), e);
                skippedCount++;
            }
        }

        // ========================================
        // PHASE 4: Save in correct order (parents before children)
        // ========================================
        saveWithRetry(() -> {
            // 1. Save PosUser (root entity)
            if (!usersToSave.isEmpty()) {
                for (PosUser user : usersToSave) {
                    posUserRepository.save(user);
                }
                entityManager.flush();
                log.debug("Saved {} pos_users", usersToSave.size());
            }

            // 2. Save PosDepartment (root entity)
            if (!departmentsToSave.isEmpty()) {
                for (PosDepartment dept : departmentsToSave) {
                    posDepartmentRepository.save(dept);
                }
                entityManager.flush();
                log.debug("Saved {} pos_departments", departmentsToSave.size());
            }

            // 3. Save PosSaleGroup (root entity)
            if (!saleGroupsToSave.isEmpty()) {
                for (PosSaleGroup sg : saleGroupsToSave) {
                    posSaleGroupRepository.save(sg);
                }
                entityManager.flush();
                log.debug("Saved {} pos_sale_groups", saleGroupsToSave.size());
            }

            // 4. Save PosShopUser (depends on PosUser, PosDepartment)
            if (!shopUsersToSave.isEmpty()) {
                for (PosShopUser shopUser : shopUsersToSave) {
                    posShopUserRepository.save(shopUser);
                }
                entityManager.flush();
                log.debug("Saved {} pos_shop_users", shopUsersToSave.size());
            }

            // 5. Save PosSaleGroupMember (depends on PosShopUser, PosSaleGroup)
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
        transactionTemplate.executeWithoutResult(status -> {
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
        });
    }
}
