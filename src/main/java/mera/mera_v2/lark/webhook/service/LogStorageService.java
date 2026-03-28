package mera.mera_v2.lark.webhook.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Service để lưu trữ log trong memory để hiển thị trên web
 */
@Slf4j
@Service
public class LogStorageService {
    
    private static final int MAX_LOGS = 1000; // Giới hạn số log tối đa
    private final Queue<LogEntry> logs = new ConcurrentLinkedQueue<>();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    /**
     * Lưu một log entry
     */
    public void addLog(String level, String logger, String message, String thread, Throwable throwable) {
        LogEntry entry = new LogEntry(
            LocalDateTime.now(),
            level,
            logger,
            message,
            thread,
            throwable != null ? getStackTrace(throwable) : null
        );
        
        logs.offer(entry);
        
        // Giới hạn số lượng log
        while (logs.size() > MAX_LOGS) {
            logs.poll();
        }
    }
    
    /**
     * Lấy tất cả logs
     */
    public List<LogEntry> getAllLogs() {
        return new ArrayList<>(logs);
    }
    
    /**
     * Lấy logs với filter
     */
    public List<LogEntry> getLogs(String level, String search, int limit) {
        return logs.stream()
            .filter(entry -> level == null || level.isEmpty() || entry.getLevel().equalsIgnoreCase(level))
            .filter(entry -> search == null || search.isEmpty() || 
                entry.getMessage().toLowerCase().contains(search.toLowerCase()) ||
                entry.getLogger().toLowerCase().contains(search.toLowerCase()))
            .sorted(Comparator.comparing(LogEntry::getTimestamp).reversed())
            .limit(limit > 0 ? limit : MAX_LOGS)
            .collect(Collectors.toList());
    }
    
    /**
     * Xóa tất cả logs
     */
    public void clearLogs() {
        logs.clear();
    }
    
    /**
     * Lấy số lượng logs
     */
    public int getLogCount() {
        return logs.size();
    }
    
    private String getStackTrace(Throwable throwable) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
    
    /**
     * Log entry model
     */
    public static class LogEntry {
        private final LocalDateTime timestamp;
        private final String level;
        private final String logger;
        private final String message;
        private final String thread;
        private final String stackTrace;
        
        public LogEntry(LocalDateTime timestamp, String level, String logger, 
                       String message, String thread, String stackTrace) {
            this.timestamp = timestamp;
            this.level = level;
            this.logger = logger;
            this.message = message;
            this.thread = thread;
            this.stackTrace = stackTrace;
        }
        
        public LocalDateTime getTimestamp() {
            return timestamp;
        }
        
        public String getTimestampFormatted() {
            return timestamp.format(FORMATTER);
        }
        
        public String getLevel() {
            return level;
        }
        
        public String getLogger() {
            return logger;
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getThread() {
            return thread;
        }
        
        public String getStackTrace() {
            return stackTrace;
        }
        
        public boolean hasStackTrace() {
            return stackTrace != null && !stackTrace.isEmpty();
        }
        
        public String getLevelColor() {
            switch (level.toUpperCase()) {
                case "ERROR":
                    return "#ff4444";
                case "WARN":
                    return "#ffaa00";
                case "INFO":
                    return "#4488ff";
                case "DEBUG":
                    return "#888888";
                default:
                    return "#000000";
            }
        }
    }
}
