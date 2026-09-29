package com.infinevo.worker.report;

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
 * Datasource configuration for dedicated {@code readonly_user} role (W-23.2, 02-data-model.md:400).
 *
 * <p>Wrapped with {@link TenantBindingDataSourceProxy} so reads are strictly scoped to the bound tenant via RLS.
 */
@Configuration
public class ReadOnlyDataSourceConfig {

    @Bean(name = "reportReadDataSource")
    @ConditionalOnMissingBean(name = "reportReadDataSource")
    public DataSource reportReadDataSource(
            @Value("${worker.report.datasource.url}") String url,
            @Value("${worker.report.datasource.username}") String username,
            @Value("${worker.report.datasource.password}") String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("InfinevoReportReadOnlyHikariPool");
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5000);
        return new TenantBindingDataSourceProxy(new HikariDataSource(config));
    }

    @Bean(name = "reportReadJdbcTemplate")
    @ConditionalOnMissingBean(name = "reportReadJdbcTemplate")
    public JdbcTemplate reportReadJdbcTemplate(@Qualifier("reportReadDataSource") DataSource reportReadDataSource) {
        return new JdbcTemplate(reportReadDataSource);
    }
}
