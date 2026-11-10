package mera.mera_v2.lark.sync.service;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletionException;
import javax.sql.DataSource;
import mera.mera_v2.lark.sync.dto.AttendanceSyncRequest;
import mera.mera_v2.repository.LarkEmployeeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttendanceConnectionLifecycleTest {
    private final DataSource dataSource = mock(DataSource.class);
    private final LarkEmployeeRepository employees = mock(LarkEmployeeRepository.class);
    private final LarkAttendanceSyncService service = spy(new LarkAttendanceSyncService(
            null, employees, null, null, null, null, null, dataSource, mock(JdbcTemplate.class)));

    @Test
    void lockConnectionUsesDedicatedSessionAndPreservesConnectionOptions() throws Exception {
        HikariDataSource pool = mock(HikariDataSource.class);
        when(dataSource.unwrap(HikariDataSource.class)).thenReturn(pool);
        when(pool.getJdbcUrl()).thenReturn("jdbc:mariadb://test/pos_db");
        when(pool.getUsername()).thenReturn("test-user");
        when(pool.getPassword()).thenReturn("test-password");
        Properties options = new Properties();
        options.setProperty("sslMode", "verify-full");
        when(pool.getDataSourceProperties()).thenReturn(options);
        Connection dedicated = mock(Connection.class);
        try (var driver = mockStatic(DriverManager.class)) {
            driver.when(() -> DriverManager.getConnection(anyString(), any(Properties.class)))
                    .thenAnswer(invocation -> {
                        Properties actual = invocation.getArgument(1);
                        assertEquals("verify-full", actual.getProperty("sslMode"));
                        assertEquals("test-user", actual.getProperty("user"));
                        assertEquals("test-password", actual.getProperty("password"));
                        return dedicated;
                    });
            assertSame(dedicated, service.openLockConnection());
            verify(dataSource, never()).getConnection();
            assertFalse(options.containsKey("password"));
        }
    }

    @Test
    void manualRepositoryCallsRunOutsideRequestThreadAndReleaseLock() throws Exception {
        Connection connection = lockConnection();
        String caller = Thread.currentThread().getName();
        when(employees.findAll()).thenAnswer(invocation -> {
            assertNotEquals(caller, Thread.currentThread().getName());
            assertTrue(Thread.currentThread().getName().startsWith("lark-attendance-coordinator"));
            return List.of();
        });
        startWorkers();
        try {
            assertFalse(service.syncAttendance(request(), "manual", null).isSuccess());
            verify(connection).prepareStatement("SELECT RELEASE_LOCK(SHA2(?, 256))");
            verify(connection).close();
            verify(dataSource, never()).getConnection();
        } finally {
            service.stopManualExecutor();
        }
    }

    @Test
    void repositoryFailureStillReleasesDedicatedSession() throws Exception {
        Connection connection = lockConnection();
        when(employees.findAll()).thenThrow(new IllegalStateException("test failure"));
        startWorkers();
        try {
            assertThrows(CompletionException.class, () -> service.syncAttendance(request(), "manual", null));
            verify(connection).prepareStatement("SELECT RELEASE_LOCK(SHA2(?, 256))");
            verify(connection).close();
        } finally {
            service.stopManualExecutor();
        }
    }

    private Connection lockConnection() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(1);
        doReturn(connection).when(service).openLockConnection();
        return connection;
    }

    private void startWorkers() {
        ReflectionTestUtils.setField(service, "manualWorkers", 2);
        service.startManualExecutor();
    }

    private AttendanceSyncRequest request() {
        AttendanceSyncRequest request = new AttendanceSyncRequest();
        request.setStartDate(LocalDate.of(2026, 10, 1));
        request.setEndDate(LocalDate.of(2026, 10, 31));
        return request;
    }
}
