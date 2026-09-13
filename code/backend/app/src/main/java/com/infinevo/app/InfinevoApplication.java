package com.infinevo.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The web role.
 *
 * <p>Serves HTTP. It does <strong>not</strong> consume the queue and runs no scheduled job -
 * both belong to the worker role, which is the same modules with a different entry point.
 *
 * <p>The reason for the split is concrete: two {@code @Scheduled} jobs run in the frozen
 * system with no cluster locking, so the moment a second instance exists they fire twice.
 * Confining scheduled work to a single worker role with a lock turns a scaling blocker into
 * a solved problem - see {@code docs/target-state/03-code-structure.md} section 4.
 */
@SpringBootApplication(scanBasePackages = "com.infinevo")
public class InfinevoApplication {

    public static void main(String[] args) {
        SpringApplication.run(InfinevoApplication.class, args);
    }
}
