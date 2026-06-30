package mera.mera_v2.lark.sync.service;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AttendanceSyncResult {
    private boolean success;
    private String jobId;
    private String status;
    private int totalEmployees;
    private int totalRequests;
    private int totalSuccessEmployees;
    private int totalInvalidEmployees;
    private int totalFailedRequests;
    private String message;
}
