package mera.mera_v2.pos.mapping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.EmployeeMapping;
import mera.mera_v2.entity.LarkEmployee;
import mera.mera_v2.entity.LarkDepartment;
import mera.mera_v2.entity.PosUser;
import mera.mera_v2.repository.EmployeeMappingRepository;
import mera.mera_v2.repository.LarkDepartmentRepository;
import mera.mera_v2.repository.LarkEmployeeRepository;
import mera.mera_v2.repository.PosUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeMappingService {

    private final EmployeeMappingRepository mappingRepository;
    private final LarkEmployeeRepository larkEmployeeRepository;
    private final LarkDepartmentRepository departmentRepository;
    private final PosUserRepository posUserRepository;

    public List<LarkEmployeeWithMapping> getLarkEmployeesWithMapping() {
        List<LarkEmployee> larkEmployees = larkEmployeeRepository.findAll();
        Map<String, EmployeeMapping> mappingByLarkId = mappingRepository.findAll().stream()
                .filter(m -> m.getLarkEmployeeId() != null)
                .collect(Collectors.toMap(EmployeeMapping::getLarkEmployeeId, m -> m, (a, b) -> b));

        Map<String, String> departmentNames = departmentRepository.findAll().stream()
                .collect(Collectors.toMap(LarkDepartment::getId, LarkDepartment::getName, (a, b) -> a));

        return larkEmployees.stream()
                .map(lark -> {
                    EmployeeMapping mapping = mappingByLarkId.get(lark.getId());
                    String deptName = lark.getDepartmentId() != null
                            ? departmentNames.getOrDefault(lark.getDepartmentId(), lark.getDepartmentId())
                            : null;
                    // Ưu tiên hireDate từ employee_mappings, nếu không có thì lấy từ lark
                    String hireDateStr = null;
                    if (mapping != null && mapping.getHireDate() != null) {
                        hireDateStr = mapping.getHireDate().toString();
                    } else if (lark.getHireDate() != null) {
                        hireDateStr = lark.getHireDate().toString();
                    }
                    return new LarkEmployeeWithMapping(
                            lark.getId(),
                            lark.getName(),
                            lark.getEmployeeNo(),
                            lark.getEmail(),
                            lark.getPhoneNumber(),
                            deptName,
                            lark.getJobTitle(),
                            mapping != null ? mapping.getPosUserId() : null,
                            mapping != null ? mapping.getPosUserName() : null,
                            mapping != null,
                            hireDateStr
                    );
                })
                .toList();
    }

    public List<PosUserSimple> getAllPosUsers() {
        return posUserRepository.findAll().stream()
                .map(p -> new PosUserSimple(p.getId(), p.getName(), p.getEmail()))
                .toList();
    }

    public Map<String, Long> getStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("totalLark", (long) larkEmployeeRepository.findAll().size());
        stats.put("totalPos", (long) posUserRepository.findAll().size());
        stats.put("mapped", mappingRepository.countMapped());
        stats.put("unmapped", mappingRepository.countUnmapped());
        return stats;
    }

    @Transactional
    public void saveMapping(String larkEmployeeId, String posUserId) {
        LarkEmployee lark = larkEmployeeRepository.findById(larkEmployeeId).orElse(null);
        PosUser pos = posUserId != null ? posUserRepository.findById(posUserId).orElse(null) : null;

        EmployeeMapping mapping = mappingRepository.findByLarkEmployeeId(larkEmployeeId)
                .orElse(new EmployeeMapping());

        mapping.setLarkEmployeeId(larkEmployeeId);
        if (lark != null) {
            mapping.setLarkEmployeeName(lark.getName());
            mapping.setLarkEmployeeNo(lark.getEmployeeNo());
            mapping.setLarkDepartment(lark.getDepartmentId());
            mapping.setHireDate(lark.getHireDate());
        }

        mapping.setPosUserId(posUserId);
        if (pos != null) {
            mapping.setPosUserName(pos.getName());
            mapping.setPosUserEmail(pos.getEmail());
        }

        mapping.setIsMapped(posUserId != null && !posUserId.isBlank());
        mapping.setUpdatedAt(LocalDateTime.now());

        if (mapping.getCreatedAt() == null) {
            mapping.setCreatedAt(LocalDateTime.now());
        }

        mappingRepository.save(mapping);
    }

    @Transactional
    public void saveMappings(List<Map<String, String>> mappings) {
        for (Map<String, String> m : mappings) {
            String larkId = m.get("larkEmployeeId");
            String posId = m.get("posUserId");
            String hireDateStr = m.get("hireDate");
            if (larkId != null && !larkId.isBlank()) {
                LarkEmployee lark = larkEmployeeRepository.findById(larkId).orElse(null);
                PosUser pos = posId != null ? posUserRepository.findById(posId).orElse(null) : null;

                EmployeeMapping mapping = mappingRepository.findByLarkEmployeeId(larkId)
                        .orElse(new EmployeeMapping());

                mapping.setLarkEmployeeId(larkId);
                if (lark != null) {
                    mapping.setLarkEmployeeName(lark.getName());
                    mapping.setLarkEmployeeNo(lark.getEmployeeNo());
                    mapping.setLarkDepartment(lark.getDepartmentId());
                }

                mapping.setPosUserId(posId);
                if (pos != null) {
                    mapping.setPosUserName(pos.getName());
                    mapping.setPosUserEmail(pos.getEmail());
                }

                if (hireDateStr != null && !hireDateStr.isBlank()) {
                    mapping.setHireDate(LocalDate.parse(hireDateStr));
                }

                mapping.setIsMapped(posId != null && !posId.isBlank());
                mapping.setUpdatedAt(LocalDateTime.now());

                if (mapping.getCreatedAt() == null) {
                    mapping.setCreatedAt(LocalDateTime.now());
                }

                mappingRepository.save(mapping);
            }
        }
    }

    @Transactional
    public void updateHireDates(List<Map<String, String>> updates) {
        for (Map<String, String> update : updates) {
            String larkId = update.get("larkEmployeeId");
            String hireDateStr = update.get("hireDate");
            if (larkId != null && hireDateStr != null && !hireDateStr.isBlank()) {
                larkEmployeeRepository.findById(larkId).ifPresent(lark -> {
                    lark.setHireDate(LocalDate.parse(hireDateStr));
                    larkEmployeeRepository.save(lark);
                    log.info("Updated hire date for employee {}: {}", larkId, hireDateStr);
                });
            }
        }
    }

    public record LarkEmployeeWithMapping(
            String larkEmployeeId,
            String larkEmployeeName,
            String larkEmployeeNo,
            String larkEmployeeEmail,
            String larkEmployeePhone,
            String larkDepartment,
            String larkJobTitle,
            String mappedPosUserId,
            String mappedPosUserName,
            boolean isMapped,
            String larkHireDate
    ) {}

    public record PosUserSimple(
            String id,
            String name,
            String email
    ) {}
}
