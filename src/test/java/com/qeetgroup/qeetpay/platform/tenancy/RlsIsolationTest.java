package com.qeetgroup.qeetpay.platform.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves the multi-tenant RLS backbone (TAD §6.1): with {@code app.current_merchant_id} set, a
 * connection sees exactly one merchant's ledger rows and nothing else.
 *
 * <p>RLS is bypassed by superusers, so the checks run under {@code qeet_pay_app} — the NOSUPERUSER,
 * append-only role created by migration V2, i.e. the same least-privilege posture production uses.
 * Seeding runs as the container superuser (RLS bypassed) on purpose.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RlsIsolationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired DataSource dataSource;

    private final UUID merchantA = UUID.randomUUID();
    private final UUID merchantB = UUID.randomUUID();

    @BeforeEach
    void seed() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.createStatement().execute("DELETE FROM ledger.accounts");
            c.createStatement().execute("DELETE FROM platform.merchants");
            insertMerchant(c, merchantA, "merchant-a");
            insertMerchant(c, merchantB, "merchant-b");
            insertAccount(c, merchantA, "settlement");
            insertAccount(c, merchantA, "revenue");
            insertAccount(c, merchantB, "settlement");
        }
    }

    @Test
    void rlsScopesReadsToTheActiveMerchant() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.createStatement().execute("SET ROLE qeet_pay_app");

            assertThat(countVisibleAccounts(c, merchantA)).isEqualTo(2);
            assertThat(countVisibleAccounts(c, merchantB)).isEqualTo(1);
            assertThat(countVisibleAccounts(c, UUID.randomUUID())).isZero();

            c.createStatement().execute("RESET ROLE");
        }
    }

    @Test
    void appRoleCannotMutateTheAppendOnlyLedger() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.createStatement().execute("SET ROLE qeet_pay_app");
            setMerchant(c, merchantA);
            org.junit.jupiter.api.Assertions.assertThrows(
                    java.sql.SQLException.class,
                    () -> c.createStatement().executeUpdate("DELETE FROM ledger.accounts"));
            c.createStatement().execute("RESET ROLE");
        }
    }

    private int countVisibleAccounts(Connection c, UUID merchant) throws Exception {
        setMerchant(c, merchant);
        try (var st = c.createStatement();
                var rs = st.executeQuery("SELECT count(*) FROM ledger.accounts")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void setMerchant(Connection c, UUID merchant) throws Exception {
        try (var ps = c.prepareStatement("SELECT set_config('app.current_merchant_id', ?, false)")) {
            ps.setString(1, merchant.toString());
            ps.executeQuery().close();
        }
    }

    private void insertMerchant(Connection c, UUID id, String slug) throws Exception {
        try (var ps =
                c.prepareStatement(
                        "INSERT INTO platform.merchants (id, slug, name) VALUES (?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setString(2, slug);
            ps.setString(3, slug);
            ps.executeUpdate();
        }
    }

    private void insertAccount(Connection c, UUID merchant, String code) throws Exception {
        try (var ps =
                c.prepareStatement(
                        "INSERT INTO ledger.accounts (id, merchant_id, code, name, type, currency)"
                                + " VALUES (?, ?, ?, ?, 'REVENUE', 'INR')")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, merchant);
            ps.setString(3, code);
            ps.setString(4, code);
            ps.executeUpdate();
        }
    }
}
