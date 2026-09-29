package com.infinevo.worker.retention;

import com.infinevo.shared.tenant.TenantBindingDataSourceProxy;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Datasource configuration for dedicated {@code retention_user} role (W-22.2).
 */
@Configuration
public class RetentionDataSourceConfig {

    @Bean(name = "retentionDataSource")
    @ConditionalOnMissingBean(name = "retentionDataSource")
    public DataSource retentionDataSource(
            @Value("${worker.retention.datasource.url}") String url,
            @Value("${worker.retention.datasource.username}") String username,
            @Value("${worker.retention.datasource.password}") String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("InfinevoRetentionHikariPool");
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5000);
        return new TenantBindingDataSourceProxy(new HikariDataSource(config));
    }

    @Bean(name = "retentionJdbcTemplate")
    @ConditionalOnMissingBean(name = "retentionJdbcTemplate")
    public JdbcTemplate retentionJdbcTemplate(@Qualifier("retentionDataSource") DataSource retentionDataSource) {
        return new JdbcTemplate(retentionDataSource);
    }
}
