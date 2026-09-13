package com.infinevo.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
public class InfinevoWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(InfinevoWorkerApplication.class, args);
    }
}
