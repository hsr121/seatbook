package com.seatbook.observability;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.Statement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

/**
 * Readiness checks the DB through its OWN 1-connection pool with a 2s timeout. Using the main pool would make
 * the probe queue behind 20k requests for up to 60s and get the instance killed exactly when it is busiest.
 * Fails closed: any exception => DOWN => /actuator/health/readiness returns 503.
 * (Not a DataSource bean on purpose: that would disable Boot's primary DataSource auto-config.)
 */
@Component("dbReadiness")
public class DbReadinessIndicator extends AbstractHealthIndicator {
    private final HikariDataSource ds;

    public DbReadinessIndicator(@Value("${spring.datasource.url}") String url,
                                @Value("${spring.datasource.username}") String user,
                                @Value("${spring.datasource.password}") String pass) {
        HikariConfig c = new HikariConfig();
        c.setJdbcUrl(url);
        c.setUsername(user);
        c.setPassword(pass);
        c.setMaximumPoolSize(1);
        c.setMinimumIdle(0);
        c.setConnectionTimeout(2000);
        c.setInitializationFailTimeout(-1);   // don't fail app boot; report DOWN instead
        c.setPoolName("readiness");
        this.ds = new HikariDataSource(c);
    }

    @Override
    protected void doHealthCheck(Health.Builder b) throws Exception {
        try (Connection con = ds.getConnection(); Statement st = con.createStatement()) {
            st.setQueryTimeout(2);
            st.execute("SELECT 1");
            b.up();
        }
    }

    @PreDestroy
    void close() { ds.close(); }
}
