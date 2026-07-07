package mera.mera_v2.pos.sync.service;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CustomerSyncResult {
    private int totalCustomersFromApi;
    private int fetchedCustomers;
    private int insertedCustomers;
    private int updatedCustomers;
    private int insertedNotes;
    private int updatedNotes;
    private int insertedEditHistory;
    private int skippedNotes;
    private int insertedPhones;
}