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

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeSyncService {

    private static final long DEFAULT_SHOP_ID = 1546758L;

    @PersistenceContext
    private EntityManager entityManager;
    
    private final EmployeeApiClient employeeApiClient;
    private final PosUserRepository posUserRepository;
    private final PosProfileRepository posProfileRepository;
    private final PosShopUserRepository posShopUserRepository;
    private final PosDepartmentRepository posDepartmentRepository;
    private final PosSaleGroupRepository posSaleGroupRepository;
    private final PosSaleGroupMemberRepository posSaleGroupMemberRepository;
    private final PosShopRepository posShopRepository;

    @Transactional
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
        int insertedProfiles = 0, updatedProfiles = 0;
        int insertedShopUsers = 0, updatedShopUsers = 0;
        int insertedDepartments = 0, updatedDepartments = 0;
        int insertedSaleGroups = 0, updatedSaleGroups = 0;
        int insertedSaleGroupMembers = 0;
        int skippedCount = 0;

        // ========================================
        // PHASE 1: Collect all IDs from API response
        // ========================================
        Set<String> userIds = new HashSet<>();
        Set<String> profileIds = new HashSet<>();
        Set<String> shopUserIds = new HashSet<>();
        Set<Long> departmentIds = new HashSet<>();
        Set<Integer> saleGroupIds = new HashSet<>();
        Set<Long> shopIds = new HashSet<>();

        for (EmployeeApiResponse.EmployeeDto emp : employees) {
            if (emp.getUser() != null && emp.getUser().getId() != null) {
                userIds.add(emp.getUser().getId());
            }
            if (emp.getProfileId() != null) {
                profileIds.add(emp.getProfileId());
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
            if (emp.getShopId() != null) {
                shopIds.add(emp.getShopId());
            }
        }

        // ========================================
        // PHASE 2: Load existing entities into in-memory cache
        // ========================================
        Map<String, PosUser> userCache = new HashMap<>();
        for (PosUser u : posUserRepository.findAllById(userIds)) {
            userCache.put(u.getId(), u);
        }

        Map<String, PosProfile> profileCache = new HashMap<>();
        for (PosProfile p : posProfileRepository.findAllById(profileIds)) {
            profileCache.put(p.getId(), p);
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
        List<PosProfile> profilesToSave = new ArrayList<>();
        List<PosDepartment> departmentsToSave = new ArrayList<>();
        List<PosSaleGroup> saleGroupsToSave = new ArrayList<>();
        List<PosShopUser> shopUsersToSave = new ArrayList<>();
        List<PosSaleGroupMember> membersToSave = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();

        for (EmployeeApiResponse.EmployeeDto emp : employees) {
            try {
                // --- 1. PosUser ---
                PosUser posUser = null;
                if (emp.getUser() != null && emp.getUser().getId() != null) {
                    EmployeeApiResponse.EmployeeDto.UserDto userDto = emp.getUser();
                    String userId = userDto.getId();
                    posUser = userCache.get(userId);

                    if (posUser == null) {
                        posUser = new PosUser();
                        posUser.setId(userId);
                        userCache.put(userId, posUser);
                        insertedUsers++;
                    } else {
                        updatedUsers++;
                    }

                    posUser.setName(userDto.getName() != null ? userDto.getName() : "");
                    posUser.setEmail(userDto.getEmail());
                    posUser.setFbId(userDto.getFbId());
                    posUser.setPhoneNumber(userDto.getPhoneNumber());
                    posUser.setAvatarUrl(userDto.getAvatarUrl());
                    posUser.setCreatedAt(now);
                    posUser.setUpdatedAt(now);

                    usersToSave.add(posUser);
                }

                // --- 2. PosProfile ---
                PosProfile profile = null;
                if (emp.getProfileId() != null) {
                    String profileId = emp.getProfileId();
                    profile = profileCache.get(profileId);

                    if (profile == null) {
                        profile = new PosProfile();
                        profile.setId(profileId);
                        profileCache.put(profileId, profile);
                        insertedProfiles++;
                    } else {
                        updatedProfiles++;
                    }

                    profile.setName("Profile " + profileId.substring(0, Math.min(8, profileId.length())));
                    profile.setShopId(getValidShopId(emp.getShopId()));
                    profile.setCreatedAt(now);
                    profile.setUpdatedAt(now);

                    profilesToSave.add(profile);
                }

                // --- 3. PosDepartment ---
                PosDepartment department = null;
                if (emp.getDepartment() != null && emp.getDepartmentId() != null) {
                    Long deptId = emp.getDepartmentId();
                    department = departmentCache.get(deptId);

                    if (department == null) {
                        department = new PosDepartment();
                        department.setId(deptId);
                        departmentCache.put(deptId, department);
                        insertedDepartments++;
                    } else {
                        updatedDepartments++;
                    }

                    department.setName(emp.getDepartment().getName());
                    department.setShopId(getValidShopId(emp.getShopId()));
                    department.setCreatedAt(now);

                    departmentsToSave.add(department);
                }

                // --- 4. PosSaleGroup ---
                PosSaleGroup saleGroup = null;
                if (emp.getSale_group() != null && emp.getSale_group().getId() != null) {
                    Integer sgId = emp.getSale_group().getId();
                    saleGroup = saleGroupCache.get(sgId);

                    if (saleGroup == null) {
                        saleGroup = new PosSaleGroup();
                        saleGroup.setId(sgId);
                        saleGroupCache.put(sgId, saleGroup);
                        insertedSaleGroups++;
                    } else {
                        updatedSaleGroups++;
                    }

                    saleGroup.setName(emp.getSale_group().getName());
                    saleGroup.setShopId(getValidShopId(emp.getShopId()));

                    saleGroupsToSave.add(saleGroup);
                }

                // --- 5. PosShopUser ---
                if (emp.getShopUserId() != null) {
                    PosShopUser shopUser = shopUserCache.get(emp.getShopUserId());
                    boolean isNew = (shopUser == null);

                    if (isNew) {
                        shopUser = new PosShopUser();
                        shopUser.setId(emp.getShopUserId());
                        shopUserCache.put(emp.getShopUserId(), shopUser);
                        insertedShopUsers++;
                    } else {
                        updatedShopUsers++;
                    }

                    shopUser.setShopId(getValidShopId(emp.getShopId()));

                    // FK-safe: only set userId if user exists in DB
                    String userId = emp.getUserId();
                    if (userId != null && userCache.containsKey(userId)) {
                        shopUser.setUserId(userId);
                    }

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
                    shopUser.setUpdatedAt(now);

                    shopUsersToSave.add(shopUser);
                }

                // --- 6. PosSaleGroupMember ---
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
        try {
            // Priority 1: Save root entities
            if (!usersToSave.isEmpty()) {
                for (PosUser user : usersToSave) {
                    posUserRepository.save(user);
                }
                entityManager.flush();
                log.debug("Saved {} pos_users", usersToSave.size());
            }

            if (!profilesToSave.isEmpty()) {
                for (PosProfile p : profilesToSave) {
                    posProfileRepository.save(p);
                }
                entityManager.flush();
                log.debug("Saved {} pos_profiles", profilesToSave.size());
            }

            if (!departmentsToSave.isEmpty()) {
                for (PosDepartment dept : departmentsToSave) {
                    posDepartmentRepository.save(dept);
                }
                entityManager.flush();
                log.debug("Saved {} pos_departments", departmentsToSave.size());
            }

            if (!saleGroupsToSave.isEmpty()) {
                for (PosSaleGroup sg : saleGroupsToSave) {
                    posSaleGroupRepository.save(sg);
                }
                entityManager.flush();
                log.debug("Saved {} pos_sale_groups", saleGroupsToSave.size());
            }

            // Priority 2: Save dependent entities
            int validShopUsers = 0;
            if (!shopUsersToSave.isEmpty()) {
                for (PosShopUser shopUser : shopUsersToSave) {
                    if (shopUser.getUserId() == null) {
                        log.warn("Skipping shop_user {} with null user_id", shopUser.getId());
                        continue;
                    }
                    posShopUserRepository.save(shopUser);
                    validShopUsers++;
                }
                entityManager.flush();
                log.debug("Saved {} pos_shop_users (skipped {} with null user_id)", validShopUsers, shopUsersToSave.size() - validShopUsers);
            }

            // Priority 3: Save leaf entities
            if (!membersToSave.isEmpty()) {
                posSaleGroupMemberRepository.saveAll(membersToSave);
                log.debug("Saved {} pos_sale_group_members", membersToSave.size());
            }
        } catch (Exception e) {
            log.error("Save failed: {}", e.getMessage(), e);
            throw e;
        }

        String message = String.format(
            "Synced %d employees: %d users, %d profiles, %d shop_users, %d departments, %d sale_groups, %d group_members",
            totalFromApi, usersToSave.size(), profilesToSave.size(), shopUsersToSave.size(),
            departmentsToSave.size(), saleGroupsToSave.size(), membersToSave.size());

        log.info("Employee sync completed: {}", message);

        return EmployeeSyncResult.builder()
            .totalFromApi(totalFromApi)
            .insertedUsers(insertedUsers)
            .updatedUsers(updatedUsers)
            .insertedProfiles(insertedProfiles)
            .updatedProfiles(updatedProfiles)
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

    private long getValidShopId(Long shopId) {
        if (shopId == null) {
            return DEFAULT_SHOP_ID;
        }
        if (posShopRepository.existsById(shopId)) {
            return shopId;
        }
        log.warn("Shop ID {} does not exist in pos_shop table, using default {}", shopId, DEFAULT_SHOP_ID);
        return DEFAULT_SHOP_ID;
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
}
