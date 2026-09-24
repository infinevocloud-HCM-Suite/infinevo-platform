package com.infinevo.shared.pool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * W-55 — HikariCP Connection Pool Calibration & Query Batching Verification Test (PLAT-06, D-10, D-18, D-19).
 *
 * <p>Asserts that:
 * <ul>
 *   <li>The web application pool ({@code app}) is calibrated for 10 concurrent connections, 5 idle, 30s leak detection.</li>
 *   <li>The batch worker pool ({@code worker}) is calibrated for 5 max connections, 2 idle, 30s leak detection.</li>
 *   <li>Both modules configure {@code spring.jpa.properties.hibernate.default_batch_fetch_size: 25}.</li>
 *   <li>Connection leak detection is strictly positive (> 0) to prevent silent pool starvation.</li>
 * </ul>
 */
class HikariPoolCalibrationTest {

    @Test
    @DisplayName("App application.yml Hikari pool is calibrated for web role")
    void appHikariPoolIsCalibrated() throws Exception {
        Map<String, Object> yml = loadYaml("../app/src/main/resources/application.yml");
        Map<String, Object> hikari = getMap(yml, "spring", "datasource", "hikari");

        assertThat(hikari.get("pool-name")).isEqualTo("InfinevoAppHikariPool");
        assertThat(Integer.valueOf(hikari.get("maximum-pool-size").toString())).isEqualTo(10);
        assertThat(Integer.valueOf(hikari.get("minimum-idle").toString())).isEqualTo(5);
        assertThat(Long.valueOf(hikari.get("idle-timeout").toString())).isEqualTo(300000L);
        assertThat(Long.valueOf(hikari.get("max-lifetime").toString())).isEqualTo(1800000L);
        assertThat(Long.valueOf(hikari.get("connection-timeout").toString())).isEqualTo(5000L);
        assertThat(Long.valueOf(hikari.get("leak-detection-threshold").toString()))
                .isEqualTo(30000L);

        Map<String, Object> jpaProps = getMap(yml, "spring", "jpa", "properties", "hibernate");
        assertThat(Integer.valueOf(jpaProps.get("default_batch_fetch_size").toString()))
                .isEqualTo(25);
    }

    @Test
    @DisplayName("Worker application.yml Hikari pool is calibrated for batch role")
    void workerHikariPoolIsCalibrated() throws Exception {
        Map<String, Object> yml = loadYaml("../worker/src/main/resources/application.yml");
        Map<String, Object> hikari = getMap(yml, "spring", "datasource", "hikari");

        assertThat(hikari.get("pool-name")).isEqualTo("InfinevoWorkerHikariPool");
        assertThat(Integer.valueOf(hikari.get("maximum-pool-size").toString())).isEqualTo(5);
        assertThat(Integer.valueOf(hikari.get("minimum-idle").toString())).isEqualTo(2);
        assertThat(Long.valueOf(hikari.get("idle-timeout").toString())).isEqualTo(300000L);
        assertThat(Long.valueOf(hikari.get("max-lifetime").toString())).isEqualTo(1800000L);
        assertThat(Long.valueOf(hikari.get("connection-timeout").toString())).isEqualTo(5000L);
        assertThat(Long.valueOf(hikari.get("leak-detection-threshold").toString()))
                .isEqualTo(30000L);

        Map<String, Object> jpaProps = getMap(yml, "spring", "jpa", "properties", "hibernate");
        assertThat(Integer.valueOf(jpaProps.get("default_batch_fetch_size").toString()))
                .isEqualTo(25);
    }

    @Test
    @DisplayName("Deliberate break: Pool with disabled leak detection is rejected")
    void deliberateBreakZeroLeakDetectionFailsValidation() {
        Map<String, Object> uncalibratedHikari = Map.of(
                "maximum-pool-size", 10,
                "minimum-idle", 5,
                "connection-timeout", 5000,
                "leak-detection-threshold", 0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            validateHikariCalibration(uncalibratedHikari);
        });

        assertThat(ex.getMessage()).contains("leak-detection-threshold must be positive");
    }

    @Test
    @DisplayName("Deliberate break: Pool with missing minimum-idle is rejected")
    void deliberateBreakMissingMinimumIdleFailsValidation() {
        Map<String, Object> uncalibratedHikari = Map.of(
                "maximum-pool-size", 10,
                "connection-timeout", 5000,
                "leak-detection-threshold", 30000);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            validateHikariCalibration(uncalibratedHikari);
        });

        assertThat(ex.getMessage()).contains("minimum-idle must be explicitly set");
    }

    private void validateHikariCalibration(Map<String, Object> hikari) {
        if (!hikari.containsKey("minimum-idle")) {
            throw new IllegalArgumentException("minimum-idle must be explicitly set");
        }
        long leak = Long.parseLong(
                hikari.getOrDefault("leak-detection-threshold", "0").toString());
        if (leak <= 0) {
            throw new IllegalArgumentException("leak-detection-threshold must be positive (> 0 ms)");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(String relativePath) throws Exception {
        Path path = Path.of(relativePath);
        if (!Files.exists(path)) {
            // fallback if run from root or test directory
            path = Path.of("code/backend").resolve(relativePath);
        }
        try (InputStream is = Files.newInputStream(path)) {
            return new Yaml().load(is);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> root, String... keys) {
        Map<String, Object> current = root;
        for (String key : keys) {
            Object val = current.get(key);
            if (val == null) {
                throw new IllegalStateException("Key not found in YAML: " + key);
            }
            if (!(val instanceof Map)) {
                throw new IllegalStateException("Value at " + key + " is not a Map: " + val);
            }
            current = (Map<String, Object>) val;
        }
        return current;
    }
}
