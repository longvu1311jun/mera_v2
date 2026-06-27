package mera.mera_v2.pos.sync.service;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmployeeSyncResult {
    
    private int totalFromApi;
    private int insertedUsers;
    private int updatedUsers;
    private int insertedProfiles;
    private int updatedProfiles;
    private int insertedShopUsers;
    private int updatedShopUsers;
    private int insertedDepartments;
    private int updatedDepartments;
    private int insertedSaleGroups;
    private int updatedSaleGroups;
    private int insertedSaleGroupMembers;
    private int skippedCount;
    private String message;
}
