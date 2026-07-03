package mera.mera_v2.pos.assignment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.EmployeeMapping;
import mera.mera_v2.entity.LarkAttendancePunch;
import mera.mera_v2.entity.LarkEmployee;
import mera.mera_v2.entity.OrderAssignment;
import mera.mera_v2.lark.webhook.dto.PosOrderWebhook;
import mera.mera_v2.lark.webhook.service.PosToBitableMapper;
import mera.mera_v2.pos.sync.client.OrderApiClient;
import mera.mera_v2.repository.EmployeeMappingRepository;
import mera.mera_v2.repository.LarkAttendancePunchRepository;
import mera.mera_v2.repository.LarkEmployeeRepository;
import mera.mera_v2.repository.OrderAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderAssignmentService {

    private final LarkAttendancePunchRepository attendancePunchRepository;
    private final EmployeeMappingRepository employeeMappingRepository;
    private final OrderAssignmentRepository orderAssignmentRepository;
    private final LarkEmployeeRepository larkEmployeeRepository;
    private final OrderApiClient orderApiClient;
    private final PosToBitableMapper posToBitableMapper;
    private final OrderAssignmentConfigService configService;

    private static final LocalDateTime TODAY_START = LocalDate.now().atStartOfDay();
    private static final LocalDateTime TODAY_END = LocalDate.now().plusDays(1).atStartOfDay();

    public enum EmployeeGroup {
        PRIORITY,   // Uu tien: it assignment hon trung binh
        ON_TIME,    // Dung gio
        LATE,       // Muon
        OFF         // Nghi (khong diem danh)
    }

    public static class EmployeeWithPunchStatus {
        private final EmployeeMapping employee;
        private final boolean onTime;
        private final String checkinTime;
        private final EmployeeGroup group;
        private final long todayAssignments;

        public EmployeeWithPunchStatus(EmployeeMapping employee, boolean onTime, String checkinTime,
                                       EmployeeGroup group, long todayAssignments) {
            this.employee = employee;
            this.onTime = onTime;
            this.checkinTime = checkinTime;
            this.group = group;
            this.todayAssignments = todayAssignments;
        }

        public EmployeeMapping getEmployee() { return employee; }
        public boolean isOnTime() { return onTime; }
        public String getCheckinTime() { return checkinTime; }
        public EmployeeGroup getGroup() { return group; }
        public long getTodayAssignments() { return todayAssignments; }
    }

    /**
     * Check if checkin time is on-time based on config threshold.
     */
    public boolean isOnTime(String punchTime) {
        if (punchTime == null || punchTime.isBlank()) {
            return false;
        }
        try {
            String thresholdStr = configService.getConfig("on_time_threshold");
            String[] thresholdParts = thresholdStr.split(":");
            LocalTime threshold = LocalTime.of(
                    Integer.parseInt(thresholdParts[0]),
                    Integer.parseInt(thresholdParts[1])
            );

            String[] parts = punchTime.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            LocalTime checkin = LocalTime.of(hour, minute);
            return !checkin.isAfter(threshold);
        } catch (Exception e) {
            log.warn("Cannot parse punchTime '{}': {}", punchTime, e.getMessage());
            return punchTime.compareTo("08:00") <= 0;
        }
    }

    /**
     * Check if late employees should be included in average calculation.
     */
    private boolean isAvgIncludeLate() {
        return configService.getConfigAsBoolean("avg_calculation_include_late");
    }

    /**
     * Check if late employees can get priority.
     */
    private boolean isLateInPriority() {
        return configService.getConfigAsBoolean("include_late_in_priority");
    }

    /**
     * Lay danh sach nhan vien, phan nhom theo:
     * - PRIORITY: it assignment hon trung binh
     * - ON_TIME: dung gio (>= trung binh)
     * - LATE: muon
     * - OFF: nghi (khong diem danh)
     *
     * Sap xep trong moi nhom theo hireDate tang dan
     */
    public List<EmployeeWithPunchStatus> getTodayCheckedInEmployeesWithStatus() {
        return getCheckedInEmployeesWithStatusByDate(LocalDate.now());
    }

    /**
     * Lay danh sach nhan vien diem danh theo ngay cu the, phan nhom va sap xep.
     */
    public List<EmployeeWithPunchStatus> getCheckedInEmployeesWithStatusByDate(LocalDate date) {
        List<String> checkedInEmployeeIds = attendancePunchRepository.findDistinctEmployeeIdsCheckedIn(date);

        log.info("Tim thay {} nhan vien diem danh trong ngay {}",
                checkedInEmployeeIds.size(), date);

        // Lay tat ca employee mappings
        List<EmployeeMapping> allEmployees = employeeMappingRepository.findAll().stream()
                .filter(em -> em.getHireDate() != null)
                .collect(Collectors.toList());

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        // Lay so assignment trong ngay cho nhung nhan vien diem danh
        Map<String, Long> assignmentCounts = new HashMap<>();
        for (EmployeeMapping em : allEmployees) {
            if (checkedInEmployeeIds.contains(em.getLarkEmployeeId())) {
                long count = orderAssignmentRepository.countTodayByEmployee(
                        em.getLarkEmployeeId(), startOfDay, endOfDay);
                assignmentCounts.put(em.getLarkEmployeeId(), count);
            }
        }

        // Tinh trung binh assignment (co/khong tinh nhom muon)
        long totalAssignments = 0;
        int totalCount = 0;
        for (EmployeeMapping em : allEmployees) {
            if (checkedInEmployeeIds.contains(em.getLarkEmployeeId())) {
                String checkinTime = getCheckinTimeByDate(em.getLarkEmployeeId(), date);
                boolean isOnTime = isOnTime(checkinTime);

                if (isOnTime || isAvgIncludeLate()) {
                    totalAssignments += assignmentCounts.getOrDefault(em.getLarkEmployeeId(), 0L);
                    totalCount++;
                }
            }
        }
        double avgAssignments = totalCount > 0 ? (double) totalAssignments / totalCount : 0;
        log.info("Trung binh assignment ngay {} (includeLate={}): {:.2f} ({}/{})",
                date, isAvgIncludeLate(), avgAssignments, totalAssignments, totalCount);

        List<EmployeeWithPunchStatus> result = new ArrayList<>();

        for (EmployeeMapping em : allEmployees) {
            String empId = em.getLarkEmployeeId();
            String checkinTime = getCheckinTimeByDate(empId, date);
            boolean checkedIn = checkedInEmployeeIds.contains(empId) && checkinTime != null;
            boolean onTime = checkedIn && isOnTime(checkinTime);
            long assignments = assignmentCounts.getOrDefault(empId, 0L);

            EmployeeGroup group;
            if (!checkedIn) {
                group = EmployeeGroup.OFF;
            } else if (onTime) {
                if (assignments < avgAssignments) {
                    group = EmployeeGroup.PRIORITY;
                } else {
                    group = EmployeeGroup.ON_TIME;
                }
            } else {
                if (isLateInPriority() && assignments < avgAssignments) {
                    group = EmployeeGroup.PRIORITY;
                } else {
                    group = EmployeeGroup.LATE;
                }
            }

            result.add(new EmployeeWithPunchStatus(em, onTime, checkinTime, group, assignments));
        }

        // Sap xep theo nhom (priority -> on_time -> late -> off), trong moi nhom theo hireDate
        result.sort((a, b) -> {
            if (a.getGroup() != b.getGroup()) {
                return a.getGroup().ordinal() - b.getGroup().ordinal();
            }
            LocalDate hireA = a.getEmployee().getHireDate();
            LocalDate hireB = b.getEmployee().getHireDate();
            if (hireA == null && hireB == null) return 0;
            if (hireA == null) return 1;
            if (hireB == null) return -1;
            return hireA.compareTo(hireB);
        });

        return result;
    }

    /**
     * Lay danh sach nhan vien diem danh trong ngay, sap xep theo hireDate tang dan.
     * (Ke thua de ho tro code cu)
     */
    public List<EmployeeMapping> getTodayCheckedInEmployeesSorted() {
        return getTodayCheckedInEmployeesWithStatus().stream()
                .filter(e -> e.getGroup() != EmployeeGroup.OFF)
                .map(EmployeeWithPunchStatus::getEmployee)
                .collect(Collectors.toList());
    }

    /**
     * Lay thong tin diem danh (gio checkin) cua nhan vien trong ngay.
     */
    public String getCheckinTime(String larkEmployeeId) {
        return getCheckinTimeByDate(larkEmployeeId, LocalDate.now());
    }

    /**
     * Lay thong tin diem danh (gio checkin) cua nhan vien theo ngay.
     */
    public String getCheckinTimeByDate(String larkEmployeeId, LocalDate date) {
        List<LarkAttendancePunch> punches = attendancePunchRepository
                .findByEmployeeIdAndAttendanceDate(larkEmployeeId, date);

        return punches.stream()
                .filter(p -> p.getPunchType() != null && p.getPunchType() == 1)
                .findFirst()
                .map(LarkAttendancePunch::getPunchTime)
                .orElse(null);
    }

    /**
     * Phan cong don hang cho CSKH tiep theo (vong tron).
     * Su dung khi co day du thong tin tu webhook.
     */
    @Transactional
    public AssignmentResult assignOrderToNextCskh(Long orderId, PosOrderWebhook orderWebhook) {
        return assignOrderToNextCskh(orderId, null, null, orderWebhook);
    }

    /**
     * Phan cong don hang cho CSKH tiep theo (vong tron).
     * Su dung khi resync tu POS API.
     *
     * @param orderId       ID đơn hàng
     * @param orderCode     Mã đơn hàng
     * @param customerInfo  Thông tin khách hàng (name, phone)
     * @param orderWebhook  PosOrderWebhook (null khi resync)
     */
    @Transactional
    public AssignmentResult assignOrderToNextCskh(Long orderId, String orderCode,
            Map<String, String> customerInfo, PosOrderWebhook orderWebhook) {
        List<EmployeeWithPunchStatus> employeesWithStatus = getTodayCheckedInEmployeesWithStatus();
        
        // Loc bo nhung nhan vien nghi
        List<EmployeeWithPunchStatus> workingEmployees = employeesWithStatus.stream()
                .filter(e -> e.getGroup() != EmployeeGroup.OFF)
                .collect(Collectors.toList());
        
        if (workingEmployees.isEmpty()) {
            throw new RuntimeException("Khong co nhan vien diem danh trong ngay");
        }

        // Phan nhom theo thu tu uu tien: PRIORITY -> ON_TIME -> LATE
        List<EmployeeGroup> priorityOrder = Arrays.asList(
                EmployeeGroup.PRIORITY,
                EmployeeGroup.ON_TIME,
                EmployeeGroup.LATE
        );

        EmployeeMapping selected = null;
        for (EmployeeGroup group : priorityOrder) {
            List<EmployeeWithPunchStatus> groupEmployees = workingEmployees.stream()
                    .filter(e -> e.getGroup() == group)
                    .collect(Collectors.toList());

            if (!groupEmployees.isEmpty()) {
                selected = findEmployeeWithMinAssignments(groupEmployees, employeesWithStatus);
                if (selected != null) {
                    log.info("Chon tu nhom {}: nhan vien {}", group, selected.getLarkEmployeeName());
                    break;
                }
            }
        }

        if (selected == null) {
            throw new RuntimeException("Khong tim thay nhan vien phu hop");
        }

        // Lay thong tin trang thai diem danh
        String checkinTime = null;
        EmployeeGroup group = null;
        for (EmployeeWithPunchStatus ems : employeesWithStatus) {
            if (ems.getEmployee().getLarkEmployeeId().equals(selected.getLarkEmployeeId())) {
                checkinTime = ems.getCheckinTime();
                group = ems.getGroup();
                break;
            }
        }

        log.info("Chon nhan vien {} (group={}, checkin={}, hireDate={}) cho order {}",
                selected.getLarkEmployeeName(), group, checkinTime, selected.getHireDate(), orderId);

        // Goi POS API de update assigning_care_id
        try {
            orderApiClient.updateAssigningCare(orderId, selected.getPosUserId());
        } catch (Exception e) {
            log.error("Loi khi goi POS API updateAssigningCare: {}", e.getMessage());
            throw new RuntimeException("Loi khi update don hang: " + e.getMessage(), e);
        }

        // Lay Lark openId cua nhan vien
        String cskhOpenId = larkEmployeeRepository.findById(selected.getLarkEmployeeId())
                .map(LarkEmployee::getOpenId)
                .orElse(null);

        // Lay thong tin khach hang va order code
        String customerName;
        String customerPhone;
        if (orderWebhook != null) {
            customerName = posToBitableMapper.getTenKhach(orderWebhook);
            customerPhone = posToBitableMapper.getDienThoai(orderWebhook);
        } else if (customerInfo != null) {
            customerName = customerInfo.get("name");
            customerPhone = customerInfo.get("phone");
        } else {
            customerName = null;
            customerPhone = null;
        }

        // Luu assignment history
        OrderAssignment assignment = new OrderAssignment();
        assignment.setOrderId(orderId);
        assignment.setOrderCode(orderCode); // orderCode tu resync hoac null
        assignment.setEmployeeMappingId(selected.getId());
        assignment.setLarkEmployeeId(selected.getLarkEmployeeId());
        assignment.setLarkEmployeeName(selected.getLarkEmployeeName());
        assignment.setCustomerName(customerName);
        assignment.setCustomerPhone(customerPhone);
        assignment.setAssignedAt(LocalDateTime.now());
        orderAssignmentRepository.save(assignment);

        log.info("Da luu assignment: orderId={}, orderCode={}, employee={}, customer={}",
                orderId, orderCode, selected.getLarkEmployeeName(), customerName);

        return AssignmentResult.builder()
                .employee(selected)
                .cskhOpenId(cskhOpenId)
                .customerName(customerName)
                .customerPhone(customerPhone)
                .build();
    }

    /**
     * Tim nhan vien co so assignment it nhat trong danh sach.
     * Neu co nhieu nhan vien cung so assignment, chon nhan vien co hireDate som nhat.
     */
    private EmployeeMapping findEmployeeWithMinAssignments(
            List<EmployeeWithPunchStatus> employees,
            List<EmployeeWithPunchStatus> allEmployees) {

        if (employees == null || employees.isEmpty()) {
            return null;
        }

        // Tinh so assignment hien tai cua moi nhan vien
        Map<String, Long> currentCounts = new HashMap<>();
        for (EmployeeWithPunchStatus ems : allEmployees) {
            String empId = ems.getEmployee().getLarkEmployeeId();
            long count = orderAssignmentRepository.countTodayByEmployee(empId, TODAY_START, TODAY_END);
            currentCounts.put(empId, count);
        }

        return employees.stream()
                .min(Comparator
                        .comparingLong((EmployeeWithPunchStatus ems) ->
                                currentCounts.getOrDefault(ems.getEmployee().getLarkEmployeeId(), 0L))
                        .thenComparing(ems -> ems.getEmployee().getHireDate() != null
                                ? ems.getEmployee().getHireDate() : java.time.LocalDate.MAX))
                .map(EmployeeWithPunchStatus::getEmployee)
                .orElse(null);
    }

    /**
     * Lay danh sach assignment trong ngay.
     */
    public List<OrderAssignment> getTodayAssignments() {
        return orderAssignmentRepository.findTodayAssignments(TODAY_START, TODAY_END);
    }

    /**
     * Lay danh sach assignment theo ngay cu the.
     */
    public List<OrderAssignment> getAssignmentsByDate(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        return orderAssignmentRepository.findTodayAssignments(startOfDay, endOfDay);
    }

    /**
     * Lay danh sach assignment theo ngay va larkEmployeeId, sap xep moi nhat truoc.
     */
    public List<OrderAssignment> getAssignmentsByDateAndEmployeeId(LocalDate date, String larkEmployeeId) {
        return getAssignmentsByDate(date).stream()
                .filter(a -> larkEmployeeId.equals(a.getLarkEmployeeId()))
                .sorted((a, b) -> {
                    if (a.getAssignedAt() == null && b.getAssignedAt() == null) return 0;
                    if (a.getAssignedAt() == null) return 1;
                    if (b.getAssignedAt() == null) return -1;
                    return b.getAssignedAt().compareTo(a.getAssignedAt());
                })
                .collect(Collectors.toList());
    }

    /**
     * Thong ke assignment trong ngay hien tai.
     */
    public Map<String, Object> getTodayStats() {
        return getStatsByDate(LocalDate.now());
    }

    /**
     * Thong ke assignment theo ngay.
     */
    public Map<String, Object> getStatsByDate(LocalDate date) {
        List<OrderAssignment> dateAssignments = getAssignmentsByDate(date);
        List<EmployeeWithPunchStatus> employeesWithStatus = getCheckedInEmployeesWithStatusByDate(date);

        Map<String, Long> countByEmployee = new LinkedHashMap<>();
        for (EmployeeWithPunchStatus ems : employeesWithStatus) {
            String name = ems.getEmployee().getLarkEmployeeName();
            countByEmployee.put(name, ems.getTodayAssignments());
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalAssignments", dateAssignments.size());
        stats.put("totalEmployees", employeesWithStatus.stream()
                .filter(e -> e.getGroup() != EmployeeGroup.OFF).count());
        stats.put("assignmentsByEmployee", countByEmployee);
        stats.put("date", date.toString());

        return stats;
    }
}
