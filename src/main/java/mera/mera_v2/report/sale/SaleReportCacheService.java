package mera.mera_v2.report.sale;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.Optional;
import mera.mera_v2.model.SaleReportCacheEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SaleReportCacheService {

  private static final Logger log = LoggerFactory.getLogger(SaleReportCacheService.class);

  private final ObjectMapper objectMapper;

  @Value("${report.cache.dir:./data/report-cache}")
  private String cacheDir;

  @Value("${report.cache.ttl-seconds:3600}")
  private long ttlSeconds;

  public SaleReportCacheService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @PostConstruct
  public void init() {
    try {
      Files.createDirectories(Paths.get(cacheDir));
      log.info("âœ… Sale report cache dir: {}", Paths.get(cacheDir).toAbsolutePath());
    } catch (IOException e) {
      log.warn("âš ï¸ Cannot create cache dir: {}", e.getMessage());
    }
  }

  public Optional<SaleReportCacheEntry> get(String range) {
    Path file = filePath(range);
    if (!Files.exists(file)) return Optional.empty();

    try {
      String json = Files.readString(file, StandardCharsets.UTF_8);
      SaleReportCacheEntry entry = objectMapper.readValue(json, SaleReportCacheEntry.class);

      if (isExpired(entry)) {
        log.info("ðŸ§¹ Cache expired for range={}, deleting {}", range, file.getFileName());
        safeDelete(file);
        return Optional.empty();
      }

      return Optional.of(entry);
    } catch (Exception e) {
      log.warn("âš ï¸ Read cache failed (range={}): {}", range, e.getMessage());
      return Optional.empty();
    }
  }

  public void put(SaleReportCacheEntry entry) {
    if (entry == null || entry.getRange() == null) return;

    Path file = filePath(entry.getRange());
    Path tmp = Paths.get(file.toString() + ".tmp");

    try {
      Files.createDirectories(file.getParent());
      String json = objectMapper.writeValueAsString(entry);
      Files.writeString(tmp, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

      // atomic replace (náº¿u OS support)
      try {
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (Exception atomicFail) {
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
      }

      log.info("âœ… Saved cache for range={} -> {}", entry.getRange(), file.getFileName());
    } catch (Exception e) {
      log.warn("âš ï¸ Save cache failed (range={}): {}", entry.getRange(), e.getMessage());
      safeDelete(tmp);
    }
  }

  public void clear(String range) {
    safeDelete(filePath(range));
  }

  private boolean isExpired(SaleReportCacheEntry entry) {
    if (entry == null) return true;
    long ageMs = Instant.now().toEpochMilli() - entry.getFetchedAtEpochMs();
    return ageMs > ttlSeconds * 1000L;
  }

  private Path filePath(String range) {
    String safe = sanitize(range);
    return Paths.get(cacheDir).resolve("sale-report_" + safe + ".json");
  }

  private String sanitize(String s) {
    if (s == null) return "null";
    return s.replaceAll("[^a-zA-Z0-9_-]", "_");
  }

  private void safeDelete(Path p) {
    try {
      if (p != null && Files.exists(p)) Files.delete(p);
    } catch (Exception ignored) {}
  }
}

