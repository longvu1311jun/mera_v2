package mera.mera_v2.lark.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.LarkBitableConfig;
import mera.mera_v2.repository.LarkBitableConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LarkBitableConfigService {

    private final LarkBitableConfigRepository repository;

    public List<LarkBitableConfig> getAllConfigs() {
        return repository.findAllByOrderByIdAsc();
    }

    public Optional<LarkBitableConfig> getConfigById(Long id) {
        return repository.findById(id);
    }

    public Optional<LarkBitableConfig> getDefaultConfig() {
        return repository.findByIsDefaultTrue();
    }

    public Optional<LarkBitableConfig> getDefaultConfigByShop(Long shopId) {
        return repository.findByShopIdAndIsDefaultTrue(shopId);
    }

    @Transactional
    public LarkBitableConfig saveConfig(LarkBitableConfig config) {
        if (config.getIsDefault() != null && config.getIsDefault()) {
            repository.findByIsDefaultTrue().ifPresent(existing -> {
                existing.setIsDefault(false);
                repository.save(existing);
            });
        }
        return repository.save(config);
    }

    @Transactional
    public void deleteConfig(Long id) {
        repository.deleteById(id);
    }

    @Transactional
    public LarkBitableConfig setDefault(Long id) {
        repository.findByIsDefaultTrue().ifPresent(existing -> {
            existing.setIsDefault(false);
            repository.save(existing);
        });
        
        Optional<LarkBitableConfig> config = repository.findById(id);
        if (config.isPresent()) {
            config.get().setIsDefault(true);
            return repository.save(config.get());
        }
        return null;
    }
}
