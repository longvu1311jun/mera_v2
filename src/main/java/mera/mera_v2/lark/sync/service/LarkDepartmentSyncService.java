package mera.mera_v2.lark.sync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.LarkDepartment;
import mera.mera_v2.lark.sync.client.LarkDepartmentClient;
import mera.mera_v2.lark.sync.client.LarkDepartmentClient.DepartmentChildrenResponse;
import mera.mera_v2.lark.sync.client.LarkDepartmentClient.DepartmentItem;
import mera.mera_v2.repository.LarkDepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LarkDepartmentSyncService {

    private final LarkDepartmentClient departmentClient;
    private final LarkDepartmentRepository departmentRepository;

    @Transactional
    public DepartmentSyncResult syncAllDepartments(String userToken) {
        log.info("[lark-sync] start sync departments");

        int inserted = 0, updated = 0, skippedDeleted = 0;

        Queue<QueueItem> queue = new LinkedList<>();
        queue.add(new QueueItem("0", null));
        Set<String> visited = new HashSet<>();

        while (!queue.isEmpty()) {
            QueueItem current = queue.poll();

            if (visited.contains(current.openDepartmentId)) continue;
            visited.add(current.openDepartmentId);

            try {
                String pageToken = null;
                do {
                    DepartmentChildrenResponse response = departmentClient.getDepartmentChildren(
                            current.openDepartmentId, pageToken, userToken);

                    for (DepartmentItem item : response.getItems()) {
                        if (item.isDeleted()) {
                            skippedDeleted++;
                            continue;
                        }

                        LarkDepartment dept = upsertDepartment(item, current.parentDbId);
                        if (dept != null) {
                            inserted++;
                            queue.add(new QueueItem(item.getOpenDepartmentId(), dept.getId()));
                        } else {
                            updated++;
                            queue.add(new QueueItem(item.getOpenDepartmentId(), findDepartmentDbId(item.getOpenDepartmentId())));
                        }
                    }

                    pageToken = response.hasMore() ? response.getPageToken() : null;

                } while (pageToken != null);

            } catch (Exception e) {
                log.warn("[lark-sync] error endpoint=/contact/v3/departments/... " +
                        "params=parent_id={} code={} msg={}",
                        current.openDepartmentId, getErrorCode(e), e.getMessage());
            }
        }

        String message = String.format("departments inserted=%d updated=%d skipped_deleted=%d",
                inserted, updated, skippedDeleted);
        log.info("[lark-sync] {}", message);

        return DepartmentSyncResult.builder()
                .inserted(inserted)
                .updated(updated)
                .skippedDeleted(skippedDeleted)
                .message(message)
                .build();
    }

    private LarkDepartment upsertDepartment(DepartmentItem item, String parentDbId) {
        Optional<LarkDepartment> existing = departmentRepository.findByOpenId(item.getOpenDepartmentId());

        LocalDateTime now = LocalDateTime.now();

        if (existing.isPresent()) {
            LarkDepartment dept = existing.get();
            dept.setName(item.getName());
            dept.setParentId(parentDbId);
            dept.setUpdatedAt(now);
            return departmentRepository.save(dept);
        } else {
            LarkDepartment dept = new LarkDepartment();
            dept.setId(UUID.randomUUID().toString());
            dept.setName(item.getName());
            dept.setOpenId(item.getOpenDepartmentId());
            dept.setParentId(parentDbId);
            dept.setCreatedAt(now);
            dept.setUpdatedAt(now);
            return departmentRepository.save(dept);
        }
    }

    private String findDepartmentDbId(String openId) {
        return departmentRepository.findByOpenId(openId)
                .map(LarkDepartment::getId)
                .orElse(null);
    }

    private int getErrorCode(Exception e) {
        return -1;
    }

    private static class QueueItem {
        final String openDepartmentId;
        final String parentDbId;

        QueueItem(String openDepartmentId, String parentDbId) {
            this.openDepartmentId = openDepartmentId;
            this.parentDbId = parentDbId;
        }
    }
}
