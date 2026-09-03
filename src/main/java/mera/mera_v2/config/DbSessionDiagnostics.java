package mera.mera_v2.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Log cac bien timeout THUC TE cua session JDBC ngay khi app khoi dong.
 *
 * Ly do can: `sessionVariables` trong JDBC URL neu bi driver bo qua thi khong co canh bao nao,
 * app van chay binh thuong voi innodb_lock_wait_timeout mac dinh 50s va lock_wait_timeout
 * mac dinh 86400s. Khi do moi thread ket khoa se om connection rat lau roi can pool, ma nhin
 * log ung dung thi khong the biet la do config khong an.
 *
 * Doc log dong "[DbDiag]" luc khoi dong de biet chac tri so nao dang thuc su ap dung.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbSessionDiagnostics implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        String sql = "SELECT @@session.innodb_lock_wait_timeout, @@session.lock_wait_timeout, "
                + "@@session.max_statement_time, @@session.autocommit, @@session.tx_isolation, "
                + "@@global.max_connections, @@global.innodb_lock_wait_timeout";
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                log.info("[DbDiag] session.innodb_lock_wait_timeout={}s  session.lock_wait_timeout={}s  "
                                + "session.max_statement_time={}s  autocommit={}  isolation={}  "
                                + "global.max_connections={}  global.innodb_lock_wait_timeout={}s",
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7));
                log.info("[DbDiag] Neu innodb_lock_wait_timeout van la 50 va lock_wait_timeout van la 86400 "
                        + "thi sessionVariables trong JDBC URL KHONG duoc ap dung.");
            }
        } catch (Exception e) {
            log.warn("[DbDiag] Khong doc duoc bien session: {}", e.getMessage());
        }
    }
}
