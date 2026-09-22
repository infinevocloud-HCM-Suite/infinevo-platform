package com.infinevo.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * The batch role.
 *
 * <p>Consumes the queue and runs scheduled jobs, with cluster locking so that a job fires
 * once however many instances are running. Exposes a health endpoint and nothing else.
 *
 * <p>Same three business modules as the web role, same image - only the entry point and
 * what it starts differ. It scales on queue depth; the web role scales on request
 * concurrency.
 */
@SpringBootApplication(scanBasePackages = "com.infinevo")
@EnableJpaRepositories(basePackages = "com.infinevo")
@EntityScan(basePackages = "com.infinevo")
public class InfinevoWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(InfinevoWorkerApplication.class, args);
    }
}
