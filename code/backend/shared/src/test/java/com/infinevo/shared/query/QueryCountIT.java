package com.infinevo.shared.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-55 — Related-data fetching and N+1 query elimination test (PLAT-06, DEBT-019).
 *
 * <p>Proves that:
 * <ul>
 *   <li>With {@code default_batch_fetch_size: 25} configured, reading 20 parent rows and navigating
 *       their child collections executes in exactly 2 SQL queries (1 parent query + 1 batched child query
 *       using {@code WHERE parent_id IN (?, ..., ?)}), rather than 21 queries (1 + N).</li>
 *   <li>Related collections are fetched in $O(1)$ database trips, eliminating the systemic N+1
 *       defect identified in {@code DEBT-019}.</li>
 * </ul>
 */
@SpringBootTest(classes = QueryCountIT.TestApp.class)
@TestPropertySource(
        properties = {
            "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.infinevo.shared.query.QueryCountIT$QueryCounter",
            "spring.jpa.properties.hibernate.default_batch_fetch_size=25",
            "spring.jpa.open-in-view=false"
        })
class QueryCountIT extends AbstractIntegrationTest {

    public static class QueryCounter implements StatementInspector {
        public static final AtomicInteger COUNT = new AtomicInteger(0);

        @Override
        public String inspect(String sql) {
            // Count SELECT queries executed by Hibernate
            if (sql != null && sql.trim().regionMatches(true, 0, "select", 0, 6)) {
                COUNT.incrementAndGet();
            }
            return sql;
        }

        public static void reset() {
            COUNT.set(0);
        }

        public static int get() {
            return COUNT.get();
        }
    }

    @Entity
    @Table(name = "probe_parent", schema = "core")
    public static class ProbeParent {
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @Column(name = "tenant_id", nullable = false)
        private UUID tenantId;

        @Column(name = "name", nullable = false)
        private String name;

        @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
        private List<ProbeChild> children = new ArrayList<>();

        public ProbeParent() {}

        public ProbeParent(UUID tenantId, String name) {
            this.tenantId = tenantId;
            this.name = name;
        }

        public UUID getId() {
            return id;
        }

        public List<ProbeChild> getChildren() {
            return children;
        }

        public void addChild(ProbeChild child) {
            children.add(child);
            child.parent = this;
        }
    }

    @Entity
    @Table(name = "probe_child", schema = "core")
    public static class ProbeChild {
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @Column(name = "tenant_id", nullable = false)
        private UUID tenantId;

        @Column(name = "title", nullable = false)
        private String title;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "parent_id", nullable = false)
        private ProbeParent parent;

        public ProbeChild() {}

        public ProbeChild(UUID tenantId, String title) {
            this.tenantId = tenantId;
            this.title = title;
        }

        public UUID getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }
    }

    public interface ProbeParentRepository extends JpaRepository<ProbeParent, UUID> {
        List<ProbeParent> findByTenantId(UUID tenantId);
    }

    @SpringBootApplication
    @EntityScan(basePackageClasses = {QueryCountIT.class})
    @EnableJpaRepositories(
            basePackageClasses = {QueryCountIT.class},
            considerNestedRepositories = true)
    static class TestApp {}

    @Autowired
    private ProbeParentRepository parentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final UUID TEST_TENANT = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeAll
    static void applySchema() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS core.probe_parent (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            tenant_id UUID NOT NULL,
                            name VARCHAR(100) NOT NULL
                        );

                        CREATE TABLE IF NOT EXISTS core.probe_child (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            tenant_id UUID NOT NULL,
                            parent_id UUID NOT NULL REFERENCES core.probe_parent(id),
                            title VARCHAR(100) NOT NULL
                        );

                        CREATE INDEX IF NOT EXISTS idx_probe_child_tenant_parent
                            ON core.probe_child (tenant_id, parent_id);
                        """);
            }
        }
    }

    @BeforeEach
    void seedData() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            parentRepository.deleteAll();
            for (int i = 1; i <= 20; i++) {
                ProbeParent parent = new ProbeParent(TEST_TENANT, "Parent-" + i);
                parent.addChild(new ProbeChild(TEST_TENANT, "Child-" + i + "-A"));
                parent.addChild(new ProbeChild(TEST_TENANT, "Child-" + i + "-B"));
                parentRepository.save(parent);
            }
        });
    }

    @Test
    @DisplayName("Batch fetching loads 20 parents and all children in exactly 2 queries (eliminates N+1)")
    void batchFetchingEliminatesNPlusOneQueries() {
        QueryCounter.reset();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            // 1. First query: fetch the list of 20 parents
            List<ProbeParent> parents = parentRepository.findByTenantId(TEST_TENANT);
            assertThat(parents).hasSize(20);

            // 2. Iterate each parent's lazy collection.
            // With batch fetch size 25, Hibernate loads all children in 1 single IN query instead of 20 queries.
            int totalChildren = 0;
            for (ProbeParent p : parents) {
                totalChildren += p.getChildren().size();
            }
            assertThat(totalChildren).isEqualTo(40);
        });

        int queryCount = QueryCounter.get();

        // Exactly 2 SELECT queries: 1 for parents + 1 batched IN query for children.
        // Without batch fetching (DEBT-019 defect), this would have been 21 queries.
        assertEquals(
                2,
                queryCount,
                "Expected exactly 2 queries (1 parent + 1 batched collection query), but got: " + queryCount);
    }
}
