package com.infinevo.payroll.taxcalc.recalc;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration providing the named asynchronous executor for tax recalculations (W-33.3).
 */
@Configuration
@EnableAsync
public class TaxRecalcConfig {

    @Bean("taxRecalc")
    public Executor taxRecalcExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(100);
        ex.setThreadNamePrefix("tax-recalc-");
        ex.initialize();
        return ex;
    }
}
