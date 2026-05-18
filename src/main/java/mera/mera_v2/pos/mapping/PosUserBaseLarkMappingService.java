package mera.mera_v2.pos.mapping;

import lombok.RequiredArgsConstructor;
import mera.mera_v2.entity.PosUser;
import mera.mera_v2.repository.PosUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PosUserBaseLarkMappingService {

    private final PosUserRepository posUserRepository;

    public List<PosUserBaseLarkDto> listUsers() {
        return posUserRepository.findAllByOrderByNameAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public int saveBaseLark(List<SavePosUserBaseLarkRequest.PosUserBaseLarkUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            return 0;
        }
        int saved = 0;
        for (SavePosUserBaseLarkRequest.PosUserBaseLarkUpdate update : updates) {
            if (update.id() == null || update.id().isBlank()) {
                continue;
            }
            PosUser user = posUserRepository.findById(update.id()).orElse(null);
            if (user == null) {
                continue;
            }
            String value = update.baseLark();
            user.setBaseLark(value != null && !value.isBlank() ? value.trim() : null);
            posUserRepository.save(user);
            saved++;
        }
        return saved;
    }

    private PosUserBaseLarkDto toDto(PosUser user) {
        return new PosUserBaseLarkDto(user.getId(), user.getName(), user.getBaseLark());
    }
}
