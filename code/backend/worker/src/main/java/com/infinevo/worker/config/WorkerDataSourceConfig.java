package com.infinevo.worker.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The worker's own connection pool, {@code worker_user}, declared rather than auto-configured.
 *
 * <p><strong>Why it has to be declared.</strong> The worker has two more pools: {@code retention_user}
 * for the retention sweep ({@code W-22.2}) and {@code readonly_user} for report reads
 * ({@code W-23.2}). Spring Boot creates its {@code spring.datasource} pool only when no
 * {@link DataSource} bean exists, and its {@link JdbcTemplate} only when no {@code JdbcTemplate} bean
 * exists. With the other two declared, both back off: the worker would have no {@code worker_user} pool
 * at all, JPA would find two datasources and no primary, and the context would not start. Declaring the
 * pool from the same {@code spring.datasource} properties, as {@link Primary}, puts back exactly what
 * Boot would have built, and every unqualified injection — JPA, ShedLock, the sweeps' tenant lists —
 * resolves to it.
 *
 * <p>The tenant-binding post-processor in {@code shared} wraps it like any other pool, so it binds the
 * request's tenant on every connection.
 */
@Configuration(proxyBeanMethods = false)
public class WorkerDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    /** Hikari as Boot configures it: {@code spring.datasource.hikari.*} binds onto the pool. */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties) {
        return properties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
