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

    private static final LocalDateTime TODAY_START = LocalDate.now().atStartOfDay();
    private static final LocalDateTime TODAY_END = LocalDate.now().plusDays(1).atStartOfDay();

    /**
     * Lấy danh sách nhân viên điểm danh trong ngày, sắp xếp theo hireDate tăng dần.
     */
    public List<EmployeeMapping> getTodayCheckedInEmployeesSorted() {
        LocalDate today = LocalDate.now();
        List<String> checkedInEmployeeIds = attendancePunchRepository.findDistinctEmployeeIdsCheckedIn(today);

        if (checkedInEmployeeIds.isEmpty()) {
            log.info("Khong co nhan vien diem danh trong ngay {}", today);
            return Collections.emptyList();
        }

        log.info("Tim thay {} nhan vien diem danh trong ngay {}", checkedInEmployeeIds.size(), today);

        List<EmployeeMapping> employees = employeeMappingRepository.findAll().stream()
                .filter(em -> checkedInEmployeeIds.contains(em.getLarkEmployeeId()))
                .filter(em -> em.getHireDate() != null)
                .sorted(Comparator.comparing(EmployeeMapping::getHireDate))
                .collect(Collectors.toList());

        log.info("Co {} nhan vien co mapping va co hireDate", employees.size());
        employees.forEach(em ->
                log.info("  - {}: hireDate={}, posUserId={}",
                        em.getLarkEmployeeName(), em.getHireDate(), em.getPosUserId())
        );

        return employees;
    }

    /**
     * Lấy thông tin điểm danh (giờ checkin) của nhân viên trong ngày.
     */
    public String getCheckinTime(String larkEmployeeId) {
        LocalDate today = LocalDate.now();
        List<LarkAttendancePunch> punches = attendancePunchRepository
                .findByEmployeeIdAndAttendanceDate(larkEmployeeId, today);

        return punches.stream()
                .filter(p -> p.getPunchType() != null && p.getPunchType() == 1)
                .findFirst()
                .map(LarkAttendancePunch::getPunchTime)
                .orElse(null);
    }

    /**
     * Phân công đơn hàng cho CSKH tiếp theo (vòng tròn).
     * - Chọn nhân viên có hireDate sớm nhất và chưa đạt giới hạn
     * - Nếu hết nhân viên, quay vòng từ đầu
     */
    @Transactional
    public AssignmentResult assignOrderToNextCskh(Long orderId, PosOrderWebhook orderWebhook) {
        List<EmployeeMapping> employees = getTodayCheckedInEmployeesSorted();
        if (employees.isEmpty()) {
            throw new RuntimeException("Khong co nhan vien diem danh trong ngay");
        }

        // Đếm số assignment của mỗi nhân viên trong ngày
        Map<String, Long> assignmentCounts = new HashMap<>();
        for (EmployeeMapping em : employees) {
            long count = orderAssignmentRepository.countTodayByEmployee(
                    em.getLarkEmployeeId(), TODAY_START, TODAY_END);
            assignmentCounts.put(em.getLarkEmployeeId(), count);
            em.setAssignmentCountCache((int) count);
        }

        // Tìm nhân viên có số assignment ít nhất
        EmployeeMapping selected = employees.stream()
                .min(Comparator.comparingInt(em -> em.getAssignmentCountCache()))
                .orElseThrow(() -> new RuntimeException("Khong tim thay nhan vien phu hop"));

        log.info("Chon nhan vien {} (hireDate={}, assignments={}) cho order {}",
                selected.getLarkEmployeeName(), selected.getHireDate(),
                assignmentCounts.get(selected.getLarkEmployeeId()), orderId);

        // Gọi POS API để update assigning_care_id
        try {
            orderApiClient.updateAssigningCare(orderId, selected.getPosUserId());
        } catch (Exception e) {
            log.error("Loi khi goi POS API updateAssigningCare: {}", e.getMessage());
            throw new RuntimeException("Loi khi update don hang: " + e.getMessage(), e);
        }

        // Lấy Lark openId của nhân viên
        String cskhOpenId = larkEmployeeRepository.findById(selected.getLarkEmployeeId())
                .map(LarkEmployee::getOpenId)
                .orElse(null);

        // Lưu assignment history
        OrderAssignment assignment = new OrderAssignment();
        assignment.setOrderId(orderId);
        assignment.setEmployeeMappingId(selected.getId());
        assignment.setLarkEmployeeId(selected.getLarkEmployeeId());
        assignment.setLarkEmployeeName(selected.getLarkEmployeeName());
        assignment.setCustomerName(posToBitableMapper.getTenKhach(orderWebhook));
        assignment.setCustomerPhone(posToBitableMapper.getDienThoai(orderWebhook));
        assignment.setAssignedAt(LocalDateTime.now());
        orderAssignmentRepository.save(assignment);

        log.info("Da luu assignment: orderId={}, employee={}, customer={}",
                orderId, selected.getLarkEmployeeName(),
                posToBitableMapper.getTenKhach(orderWebhook));

        // Lấy thông tin khách hàng
        String customerName = posToBitableMapper.getTenKhach(orderWebhook);
        String customerPhone = posToBitableMapper.getDienThoai(orderWebhook);

        return AssignmentResult.builder()
                .employee(selected)
                .cskhOpenId(cskhOpenId)
                .customerName(customerName)
                .customerPhone(customerPhone)
                .build();
    }

    /**
     * Lấy danh sách assignment trong ngày.
     */
    public List<OrderAssignment> getTodayAssignments() {
        return orderAssignmentRepository.findTodayAssignments(TODAY_START, TODAY_END);
    }

    /**
     * Thống kê assignment trong ngày.
     */
    public Map<String, Object> getTodayStats() {
        List<OrderAssignment> todayAssignments = getTodayAssignments();
        List<EmployeeMapping> employees = getTodayCheckedInEmployeesSorted();

        Map<String, Long> countByEmployee = new LinkedHashMap<>();
        for (EmployeeMapping em : employees) {
            String name = em.getLarkEmployeeName();
            long count = todayAssignments.stream()
                    .filter(a -> a.getLarkEmployeeId().equals(em.getLarkEmployeeId()))
                    .count();
            countByEmployee.put(name, count);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalAssignments", todayAssignments.size());
        stats.put("totalEmployees", employees.size());
        stats.put("assignmentsByEmployee", countByEmployee);
        stats.put("date", LocalDate.now().toString());

        return stats;
    }
}
