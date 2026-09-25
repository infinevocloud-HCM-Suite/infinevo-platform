# W-59 — raw scanner output, 2026-09-15

Measured on `main` at `d3eb07e`. Trivy via `aquasec/trivy:latest` (Docker 29.5.3).
Backend used `--offline-scan` against the local `~/.m2` because Maven Central rate-limits
unauthenticated resolution (HTTP 429, 30-minute block).

## Backend — `trivy fs --offline-scan --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed code/backend`

```

Report Summary

┌───────────────────┬──────┬─────────────────┐
│      Target       │ Type │ Vulnerabilities │
├───────────────────┼──────┼─────────────────┤
│ app/pom.xml       │ pom  │       30        │
├───────────────────┼──────┼─────────────────┤
│ core/pom.xml      │ pom  │        3        │
├───────────────────┼──────┼─────────────────┤
│ hrms/pom.xml      │ pom  │        3        │
├───────────────────┼──────┼─────────────────┤
│ migration/pom.xml │ pom  │        0        │
├───────────────────┼──────┼─────────────────┤
│ payroll/pom.xml   │ pom  │        3        │
├───────────────────┼──────┼─────────────────┤
│ pom.xml           │ pom  │       72        │
├───────────────────┼──────┼─────────────────┤
│ shared/pom.xml    │ pom  │        3        │
├───────────────────┼──────┼─────────────────┤
│ worker/pom.xml    │ pom  │       30        │
└───────────────────┴──────┴─────────────────┘
Legend:
- '-': Not scanned
- '0': Clean (no security findings detected)


app/pom.xml (pom)
=================
Total: 30 (HIGH: 24, CRITICAL: 6)

┌───────────────────────────────────────────────────────┬─────────────────────┬──────────┬────────┬───────────────────┬───────────────────────────┬──────────────────────────────────────────────────────────────┐
│                        Library                        │    Vulnerability    │ Severity │ Status │ Installed Version │       Fixed Version       │                            Title                             │
├───────────────────────────────────────────────────────┼─────────────────────┼──────────┼────────┼───────────────────┼───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ com.fasterxml.jackson.core:jackson-core               │ GHSA-r7wm-3cxj-wff9 │ HIGH     │ fixed  │ 2.17.3            │ 2.18.8, 2.21.4            │ jackson-core: Async parser maxNumberLength bypass via        │
│                                                       │                     │          │        │                   │                           │ chunked digit accumulation (incomplete fix for...            │
│                                                       │                     │          │        │                   │                           │ https://github.com/advisories/GHSA-r7wm-3cxj-wff9            │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ com.fasterxml.jackson.core:jackson-databind           │ CVE-2026-54512      │          │        │                   │ 2.18.8, 3.1.4, 2.21.4     │ jackson-databind: jackson-databind: Arbitrary code execution │
│                                                       │                     │          │        │                   │                           │ via PolymorphicTypeValidator bypass                          │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-54512                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-54513      │          │        │                   │ 2.18.8, 2.21.4, 3.1.4     │ jackson-databind: Jackson-databind: Security bypass allows   │
│                                                       │                     │          │        │                   │                           │ arbitrary code execution                                     │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-54513                   │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        ├───────────────────┼───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ io.micrometer:micrometer-core                         │ CVE-2026-40984      │          │        │ 1.13.15           │ 1.16.6, 1.15.12           │ micrometer-core: micrometer-jetty11: micrometer-jetty12:     │
│                                                       │                     │          │        │                   │                           │ Micrometer: Denial of Service via specially crafted HTTP     │
│                                                       │                     │          │        │                   │                           │ requests...                                                  │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-40984                   │
├───────────────────────────────────────────────────────┼─────────────────────┼──────────┤        ├───────────────────┼───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ org.apache.tomcat.embed:tomcat-embed-core             │ CVE-2026-41293      │ CRITICAL │        │ 10.1.42           │ 9.0.118, 10.1.55, 11.0.22 │ tomcat-coyote: Apache Tomcat: HTTP/2 request headers not     │
│                                                       │                     │          │        │                   │                           │ validated                                                    │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-41293                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-43512      │          │        │                   │                           │ tomcat-coyote: Apache Tomcat: Authentication bypass via      │
│                                                       │                     │          │        │                   │                           │ digest authentication                                        │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-43512                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-43515      │          │        │                   │                           │ tomcat-coyote: tomcat: Improper Authorization allows         │
│                                                       │                     │          │        │                   │                           │ security bypass                                              │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-43515                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-65182      │          │        │                   │ 11.0.25, 10.1.58, 9.0.121 │ Apache Tomcat: Apache Tomcat: Security constraint bypass due │
│                                                       │                     │          │        │                   │                           │ to improper access control...                                │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-65182                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-65905      │          │        │                   │                           │ tomcat: Apache Tomcat: Authentication bypass via limited     │
│                                                       │                     │          │        │                   │                           │ replay attack in DIGEST authenticator...                     │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-65905                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-68525      │          │        │                   │                           │ org.apache.tomcat/tomcat: Apache Tomcat: Unauthorized        │
│                                                       │                     │          │        │                   │                           │ resource access via FORM authentication bypass               │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-68525                   │
│                                                       ├─────────────────────┼──────────┤        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2025-48989      │ HIGH     │        │                   │ 11.0.10, 10.1.44, 9.0.108 │ tomcat: http/2 "MadeYouReset" DoS attack through HTTP/2      │
│                                                       │                     │          │        │                   │                           │ control frames                                               │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2025-48989                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2025-52520      │          │        │                   │ 11.0.9, 10.1.43, 9.0.107  │ tomcat: Apache Tomcat denial of service                      │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2025-52520                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2025-53506      │          │        │                   │ 9.0.107, 10.1.43, 11.0.9  │ tomcat: Apache Tomcat denial of service                      │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2025-53506                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2025-55752      │          │        │                   │ 11.0.11, 10.1.45, 9.0.109 │ tomcat: org.apache.tomcat/tomcat-catalina: Apache Tomcat:    │
│                                                       │                     │          │        │                   │                           │ Directory traversal via rewrite with possible RCE            │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2025-55752                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-24734      │          │        │                   │ 11.0.18, 10.1.52, 9.0.115 │ tomcat: Apache Tomcat: Certificate revocation bypass due to  │
│                                                       │                     │          │        │                   │                           │ improper OCSP response validation...                         │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-24734                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-24880      │          │        │                   │ 9.0.116, 10.1.52, 11.0.20 │ Apache Tomcat: Apache Tomcat: HTTP Request/Response          │
│                                                       │                     │          │        │                   │                           │ Smuggling via invalid chunk extension                        │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-24880                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-34483      │          │        │                   │ 9.0.116, 10.1.54, 11.0.21 │ Apache Tomcat: Apache Tomcat: Information disclosure due to  │
│                                                       │                     │          │        │                   │                           │ improper encoding in JsonAccessLogValve...                   │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-34483                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-34487      │          │        │                   │ 9.0.117, 10.1.54, 11.0.21 │ Apache Tomcat: Apache Tomcat: Information disclosure via     │
│                                                       │                     │          │        │                   │                           │ sensitive data in log files...                               │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-34487                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-41284      │          │        │                   │ 9.0.118, 10.1.55, 11.0.22 │ tomcat: Apache Tomcat: Denial of Service due to uncontrolled │
│                                                       │                     │          │        │                   │                           │ resource allocation                                          │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-41284                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-42498      │          │        │                   │                           │ tomcat-coyote: Apache Tomcat: Information disclosure due to  │
│                                                       │                     │          │        │                   │                           │ HTTP Authentication Header exposure during...                │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-42498                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-43513      │          │        │                   │                           │ tomcat-catalina: Apache Tomcat: Improper Handling of Case    │
│                                                       │                     │          │        │                   │                           │ Sensitivity in LockOutRealm                                  │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-43513                   │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        ├───────────────────┼───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ org.postgresql:postgresql                             │ CVE-2026-42198      │          │        │ 42.7.7            │ 42.7.11                   │ jdbc.postgresql.org: pgjdbc: Client-side Denial of Service   │
│                                                       │                     │          │        │                   │                           │ via malicious SCRAM-SHA-256 authentication                   │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-42198                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-54291      │          │        │                   │ 42.7.12                   │ org.postgresql/postgresql: com.ongres.scram/scram-client:    │
│                                                       │                     │          │        │                   │                           │ pgjdbc: Man-in-the-middle protection bypass via              │
│                                                       │                     │          │        │                   │                           │ SCRAM-SHA-256-PLUS downgrade                                 │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-54291                   │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        ├───────────────────┼───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework.boot:spring-boot                  │ CVE-2026-40973      │          │        │ 3.3.13            │ 4.0.6, 3.5.14             │ Spring Boot: Spring Boot: Arbitrary Code Execution and       │
│                                                       │                     │          │        │                   │                           │ Session Hijacking via predictable...                         │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-40973                   │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework.boot:spring-boot-starter-actuator │ CVE-2026-22733      │          │        │                   │ 4.0.4, 3.5.12             │ Spring Boot has an Authentication Bypass under Actuator      │
│                                                       │                     │          │        │                   │                           │ CloudFoundry endpoints                                       │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-22733                   │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework.data:spring-data-commons          │ CVE-2026-41716      │          │        │                   │ 4.0.6, 3.5.12             │ Spring Data Commons: Spring Data Commons: Denial of Service  │
│                                                       │                     │          │        │                   │                           │ due to cache...                                              │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-41716                   │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        ├───────────────────┼───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework:spring-core                       │ CVE-2025-41249      │          │        │ 6.1.21            │ 6.2.11                    │ org.springframework/spring-core: Spring Framework Annotation │
│                                                       │                     │          │        │                   │                           │ Detection Vulnerability                                      │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2025-41249                   │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework:spring-expression                 │ CVE-2026-41850      │          │        │                   │ 7.0.8, 6.2.19             │ spring-framework: Spring Framework: Denial of Service via    │
│                                                       │                     │          │        │                   │                           │ specially crafted SpEL expressions                           │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-41850                   │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│ org.springframework:spring-webmvc                     │ CVE-2026-41842      │          │        │                   │                           │ spring-framework: Spring Framework: Denial of Service when   │
│                                                       │                     │          │        │                   │                           │ resolving static resources                                   │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-41842                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-41845      │          │        │                   │                           │ org.springframework: Spring Framework: Cross-site scripting  │
│                                                       │                     │          │        │                   │                           │ (XSS) via incorrect JavaScript escaping                      │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-41845                   │
└───────────────────────────────────────────────────────┴─────────────────────┴──────────┴────────┴───────────────────┴───────────────────────────┴──────────────────────────────────────────────────────────────┘

core/pom.xml (pom)
==================
Total: 3 (HIGH: 3, CRITICAL: 0)

┌───────────────────────────────────────┬────────────────┬──────────┬────────┬───────────────────┬───────────────┬──────────────────────────────────────────────────────────────┐
│                Library                │ Vulnerability  │ Severity │ Status │ Installed Version │ Fixed Version │                            Title                             │
├───────────────────────────────────────┼────────────────┼──────────┼────────┼───────────────────┼───────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework.boot:spring-boot  │ CVE-2026-40973 │ HIGH     │ fixed  │ 3.3.13            │ 4.0.6, 3.5.14 │ Spring Boot: Spring Boot: Arbitrary Code Execution and       │
│                                       │                │          │        │                   │               │ Session Hijacking via predictable...                         │
│                                       │                │          │        │                   │               │ https://avd.aquasec.com/nvd/cve-2026-40973                   │
├───────────────────────────────────────┼────────────────┤          │        ├───────────────────┼───────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework:spring-core       │ CVE-2025-41249 │          │        │ 6.1.21            │ 6.2.11        │ org.springframework/spring-core: Spring Framework Annotation │
│                                       │                │          │        │                   │               │ Detection Vulnerability                                      │
│                                       │                │          │        │                   │               │ https://avd.aquasec.com/nvd/cve-2025-41249                   │
├───────────────────────────────────────┼────────────────┤          │        │                   ├───────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework:spring-expression │ CVE-2026-41850 │          │        │                   │ 7.0.8, 6.2.19 │ spring-framework: Spring Framework: Denial of Service via    │
│                                       │                │          │        │                   │               │ specially crafted SpEL expressions                           │
│                                       │                │          │        │                   │               │ https://avd.aquasec.com/nvd/cve-2026-41850                   │
└───────────────────────────────────────┴────────────────┴──────────┴────────┴───────────────────┴───────────────┴──────────────────────────────────────────────────────────────┘

hrms/pom.xml (pom)
==================
Total: 3 (HIGH: 3, CRITICAL: 0)

┌───────────────────────────────────────┬────────────────┬──────────┬────────┬───────────────────┬───────────────┬──────────────────────────────────────────────────────────────┐
│                Library                │ Vulnerability  │ Severity │ Status │ Installed Version │ Fixed Version │                            Title                             │
├───────────────────────────────────────┼────────────────┼──────────┼────────┼───────────────────┼───────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework.boot:spring-boot  │ CVE-2026-40973 │ HIGH     │ fixed  │ 3.3.13            │ 4.0.6, 3.5.14 │ Spring Boot: Spring Boot: Arbitrary Code Execution and       │
│                                       │                │          │        │                   │               │ Session Hijacking via predictable...                         │
│                                       │                │          │        │                   │               │ https://avd.aquasec.com/nvd/cve-2026-40973                   │
├───────────────────────────────────────┼────────────────┤          │        ├───────────────────┼───────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework:spring-core       │ CVE-2025-41249 │          │        │ 6.1.21            │ 6.2.11        │ org.springframework/spring-core: Spring Framework Annotation │
│                                       │                │          │        │                   │               │ Detection Vulnerability                                      │
│                                       │                │          │        │                   │               │ https://avd.aquasec.com/nvd/cve-2025-41249                   │
├───────────────────────────────────────┼────────────────┤          │        │                   ├───────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework:spring-expression │ CVE-2026-41850 │          │        │                   │ 7.0.8, 6.2.19 │ spring-framework: Spring Framework: Denial of Service via    │
│                                       │                │          │        │                   │               │ specially crafted SpEL expressions                           │
│                                       │                │          │        │                   │               │ https://avd.aquasec.com/nvd/cve-2026-41850                   │
└───────────────────────────────────────┴────────────────┴──────────┴────────┴───────────────────┴───────────────┴──────────────────────────────────────────────────────────────┘

payroll/pom.xml (pom)
=====================
Total: 3 (HIGH: 3, CRITICAL: 0)

┌───────────────────────────────────────┬────────────────┬──────────┬────────┬───────────────────┬───────────────┬──────────────────────────────────────────────────────────────┐
│                Library                │ Vulnerability  │ Severity │ Status │ Installed Version │ Fixed Version │                            Title                             │
├───────────────────────────────────────┼────────────────┼──────────┼────────┼───────────────────┼───────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework.boot:spring-boot  │ CVE-2026-40973 │ HIGH     │ fixed  │ 3.3.13            │ 4.0.6, 3.5.14 │ Spring Boot: Spring Boot: Arbitrary Code Execution and       │
│                                       │                │          │        │                   │               │ Session Hijacking via predictable...                         │
│                                       │                │          │        │                   │               │ https://avd.aquasec.com/nvd/cve-2026-40973                   │
├───────────────────────────────────────┼────────────────┤          │        ├───────────────────┼───────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework:spring-core       │ CVE-2025-41249 │          │        │ 6.1.21            │ 6.2.11        │ org.springframework/spring-core: Spring Framework Annotation │
│                                       │                │          │        │                   │               │ Detection Vulnerability                                      │
│                                       │                │          │        │                   │               │ https://avd.aquasec.com/nvd/cve-2025-41249                   │
├───────────────────────────────────────┼────────────────┤          │        │                   ├───────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework:spring-expression │ CVE-2026-41850 │          │        │                   │ 7.0.8, 6.2.19 │ spring-framework: Spring Framework: Denial of Service via    │
│                                       │                │          │        │                   │               │ specially crafted SpEL expressions                           │
│                                       │                │          │        │                   │               │ https://avd.aquasec.com/nvd/cve-2026-41850                   │
└───────────────────────────────────────┴────────────────┴──────────┴────────┴───────────────────┴───────────────┴──────────────────────────────────────────────────────────────┘

pom.xml (pom)
=============
Total: 72 (HIGH: 60, CRITICAL: 12)

┌───────────────────────────────────────────────────────┬─────────────────────┬──────────┬────────┬───────────────────┬───────────────────────────┬──────────────────────────────────────────────────────────────┐
│                        Library                        │    Vulnerability    │ Severity │ Status │ Installed Version │       Fixed Version       │                            Title                             │
├───────────────────────────────────────────────────────┼─────────────────────┼──────────┼────────┼───────────────────┼───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ com.fasterxml.jackson.core:jackson-core               │ GHSA-r7wm-3cxj-wff9 │ HIGH     │ fixed  │ 2.17.3            │ 2.18.8, 2.21.4            │ jackson-core: Async parser maxNumberLength bypass via        │
│                                                       │                     │          │        │                   │                           │ chunked digit accumulation (incomplete fix for...            │
│                                                       │                     │          │        │                   │                           │ https://github.com/advisories/GHSA-r7wm-3cxj-wff9            │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ com.fasterxml.jackson.core:jackson-databind           │ CVE-2026-54512      │          │        │                   │ 2.18.8, 3.1.4, 2.21.4     │ jackson-databind: jackson-databind: Arbitrary code execution │
│                                                       │                     │          │        │                   │                           │ via PolymorphicTypeValidator bypass                          │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-54512                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-54513      │          │        │                   │ 2.18.8, 2.21.4, 3.1.4     │ jackson-databind: Jackson-databind: Security bypass allows   │
│                                                       │                     │          │        │                   │                           │ arbitrary code execution                                     │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-54513                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-54512      │          │        │                   │ 2.18.8, 3.1.4, 2.21.4     │ jackson-databind: jackson-databind: Arbitrary code execution │
│                                                       │                     │          │        │                   │                           │ via PolymorphicTypeValidator bypass                          │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-54512                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-54513      │          │        │                   │ 2.18.8, 2.21.4, 3.1.4     │ jackson-databind: Jackson-databind: Security bypass allows   │
│                                                       │                     │          │        │                   │                           │ arbitrary code execution                                     │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-54513                   │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        ├───────────────────┼───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ io.micrometer:micrometer-core                         │ CVE-2026-40984      │          │        │ 1.13.15           │ 1.16.6, 1.15.12           │ micrometer-core: micrometer-jetty11: micrometer-jetty12:     │
│                                                       │                     │          │        │                   │                           │ Micrometer: Denial of Service via specially crafted HTTP     │
│                                                       │                     │          │        │                   │                           │ requests...                                                  │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-40984                   │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
├───────────────────────────────────────────────────────┼─────────────────────┼──────────┤        ├───────────────────┼───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ org.apache.tomcat.embed:tomcat-embed-core             │ CVE-2026-41293      │ CRITICAL │        │ 10.1.42           │ 9.0.118, 10.1.55, 11.0.22 │ tomcat-coyote: Apache Tomcat: HTTP/2 request headers not     │
│                                                       │                     │          │        │                   │                           │ validated                                                    │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-41293                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-43512      │          │        │                   │                           │ tomcat-coyote: Apache Tomcat: Authentication bypass via      │
│                                                       │                     │          │        │                   │                           │ digest authentication                                        │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-43512                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-43515      │          │        │                   │                           │ tomcat-coyote: tomcat: Improper Authorization allows         │
│                                                       │                     │          │        │                   │                           │ security bypass                                              │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-43515                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-65182      │          │        │                   │ 11.0.25, 10.1.58, 9.0.121 │ Apache Tomcat: Apache Tomcat: Security constraint bypass due │
│                                                       │                     │          │        │                   │                           │ to improper access control...                                │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-65182                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-65905      │          │        │                   │                           │ tomcat: Apache Tomcat: Authentication bypass via limited     │
│                                                       │                     │          │        │                   │                           │ replay attack in DIGEST authenticator...                     │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-65905                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-68525      │          │        │                   │                           │ org.apache.tomcat/tomcat: Apache Tomcat: Unauthorized        │
│                                                       │                     │          │        │                   │                           │ resource access via FORM authentication bypass               │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-68525                   │
│                                                       ├─────────────────────┼──────────┤        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2025-48989      │ HIGH     │        │                   │ 11.0.10, 10.1.44, 9.0.108 │ tomcat: http/2 "MadeYouReset" DoS attack through HTTP/2      │
│                                                       │                     │          │        │                   │                           │ control frames                                               │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2025-48989                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2025-52520      │          │        │                   │ 11.0.9, 10.1.43, 9.0.107  │ tomcat: Apache Tomcat denial of service                      │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2025-52520                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2025-53506      │          │        │                   │ 9.0.107, 10.1.43, 11.0.9  │ tomcat: Apache Tomcat denial of service                      │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2025-53506                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2025-55752      │          │        │                   │ 11.0.11, 10.1.45, 9.0.109 │ tomcat: org.apache.tomcat/tomcat-catalina: Apache Tomcat:    │
│                                                       │                     │          │        │                   │                           │ Directory traversal via rewrite with possible RCE            │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2025-55752                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-24734      │          │        │                   │ 11.0.18, 10.1.52, 9.0.115 │ tomcat: Apache Tomcat: Certificate revocation bypass due to  │
│                                                       │                     │          │        │                   │                           │ improper OCSP response validation...                         │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-24734                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-24880      │          │        │                   │ 9.0.116, 10.1.52, 11.0.20 │ Apache Tomcat: Apache Tomcat: HTTP Request/Response          │
│                                                       │                     │          │        │                   │                           │ Smuggling via invalid chunk extension                        │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-24880                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-34483      │          │        │                   │ 9.0.116, 10.1.54, 11.0.21 │ Apache Tomcat: Apache Tomcat: Information disclosure due to  │
│                                                       │                     │          │        │                   │                           │ improper encoding in JsonAccessLogValve...                   │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-34483                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-34487      │          │        │                   │ 9.0.117, 10.1.54, 11.0.21 │ Apache Tomcat: Apache Tomcat: Information disclosure via     │
│                                                       │                     │          │        │                   │                           │ sensitive data in log files...                               │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-34487                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-41284      │          │        │                   │ 9.0.118, 10.1.55, 11.0.22 │ tomcat: Apache Tomcat: Denial of Service due to uncontrolled │
│                                                       │                     │          │        │                   │                           │ resource allocation                                          │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-41284                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-42498      │          │        │                   │                           │ tomcat-coyote: Apache Tomcat: Information disclosure due to  │
│                                                       │                     │          │        │                   │                           │ HTTP Authentication Header exposure during...                │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-42498                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-43513      │          │        │                   │                           │ tomcat-catalina: Apache Tomcat: Improper Handling of Case    │
│                                                       │                     │          │        │                   │                           │ Sensitivity in LockOutRealm                                  │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-43513                   │
│                                                       ├─────────────────────┼──────────┤        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-41293      │ CRITICAL │        │                   │                           │ tomcat-coyote: Apache Tomcat: HTTP/2 request headers not     │
│                                                       │                     │          │        │                   │                           │ validated                                                    │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-41293                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-43512      │          │        │                   │                           │ tomcat-coyote: Apache Tomcat: Authentication bypass via      │
│                                                       │                     │          │        │                   │                           │ digest authentication                                        │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-43512                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-43515      │          │        │                   │                           │ tomcat-coyote: tomcat: Improper Authorization allows         │
│                                                       │                     │          │        │                   │                           │ security bypass                                              │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-43515                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-65182      │          │        │                   │ 11.0.25, 10.1.58, 9.0.121 │ Apache Tomcat: Apache Tomcat: Security constraint bypass due │
│                                                       │                     │          │        │                   │                           │ to improper access control...                                │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-65182                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-65905      │          │        │                   │                           │ tomcat: Apache Tomcat: Authentication bypass via limited     │
│                                                       │                     │          │        │                   │                           │ replay attack in DIGEST authenticator...                     │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-65905                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-68525      │          │        │                   │                           │ org.apache.tomcat/tomcat: Apache Tomcat: Unauthorized        │
│                                                       │                     │          │        │                   │                           │ resource access via FORM authentication bypass               │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-68525                   │
│                                                       ├─────────────────────┼──────────┤        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2025-48989      │ HIGH     │        │                   │ 11.0.10, 10.1.44, 9.0.108 │ tomcat: http/2 "MadeYouReset" DoS attack through HTTP/2      │
│                                                       │                     │          │        │                   │                           │ control frames                                               │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2025-48989                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2025-52520      │          │        │                   │ 11.0.9, 10.1.43, 9.0.107  │ tomcat: Apache Tomcat denial of service                      │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2025-52520                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2025-53506      │          │        │                   │ 9.0.107, 10.1.43, 11.0.9  │ tomcat: Apache Tomcat denial of service                      │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2025-53506                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2025-55752      │          │        │                   │ 11.0.11, 10.1.45, 9.0.109 │ tomcat: org.apache.tomcat/tomcat-catalina: Apache Tomcat:    │
│                                                       │                     │          │        │                   │                           │ Directory traversal via rewrite with possible RCE            │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2025-55752                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-24734      │          │        │                   │ 11.0.18, 10.1.52, 9.0.115 │ tomcat: Apache Tomcat: Certificate revocation bypass due to  │
│                                                       │                     │          │        │                   │                           │ improper OCSP response validation...                         │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-24734                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-24880      │          │        │                   │ 9.0.116, 10.1.52, 11.0.20 │ Apache Tomcat: Apache Tomcat: HTTP Request/Response          │
│                                                       │                     │          │        │                   │                           │ Smuggling via invalid chunk extension                        │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-24880                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-34483      │          │        │                   │ 9.0.116, 10.1.54, 11.0.21 │ Apache Tomcat: Apache Tomcat: Information disclosure due to  │
│                                                       │                     │          │        │                   │                           │ improper encoding in JsonAccessLogValve...                   │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-34483                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-34487      │          │        │                   │ 9.0.117, 10.1.54, 11.0.21 │ Apache Tomcat: Apache Tomcat: Information disclosure via     │
│                                                       │                     │          │        │                   │                           │ sensitive data in log files...                               │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-34487                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-41284      │          │        │                   │ 9.0.118, 10.1.55, 11.0.22 │ tomcat: Apache Tomcat: Denial of Service due to uncontrolled │
│                                                       │                     │          │        │                   │                           │ resource allocation                                          │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-41284                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-42498      │          │        │                   │                           │ tomcat-coyote: Apache Tomcat: Information disclosure due to  │
│                                                       │                     │          │        │                   │                           │ HTTP Authentication Header exposure during...                │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-42498                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-43513      │          │        │                   │                           │ tomcat-catalina: Apache Tomcat: Improper Handling of Case    │
│                                                       │                     │          │        │                   │                           │ Sensitivity in LockOutRealm                                  │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-43513                   │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        ├───────────────────┼───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ org.postgresql:postgresql                             │ CVE-2026-42198      │          │        │ 42.7.7            │ 42.7.11                   │ jdbc.postgresql.org: pgjdbc: Client-side Denial of Service   │
│                                                       │                     │          │        │                   │                           │ via malicious SCRAM-SHA-256 authentication                   │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-42198                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-54291      │          │        │                   │ 42.7.12                   │ org.postgresql/postgresql: com.ongres.scram/scram-client:    │
│                                                       │                     │          │        │                   │                           │ pgjdbc: Man-in-the-middle protection bypass via              │
│                                                       │                     │          │        │                   │                           │ SCRAM-SHA-256-PLUS downgrade                                 │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-54291                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-42198      │          │        │                   │ 42.7.11                   │ jdbc.postgresql.org: pgjdbc: Client-side Denial of Service   │
│                                                       │                     │          │        │                   │                           │ via malicious SCRAM-SHA-256 authentication                   │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-42198                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-54291      │          │        │                   │ 42.7.12                   │ org.postgresql/postgresql: com.ongres.scram/scram-client:    │
│                                                       │                     │          │        │                   │                           │ pgjdbc: Man-in-the-middle protection bypass via              │
│                                                       │                     │          │        │                   │                           │ SCRAM-SHA-256-PLUS downgrade                                 │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-54291                   │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        ├───────────────────┼───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework.boot:spring-boot                  │ CVE-2026-40973      │          │        │ 3.3.13            │ 4.0.6, 3.5.14             │ Spring Boot: Spring Boot: Arbitrary Code Execution and       │
│                                                       │                     │          │        │                   │                           │ Session Hijacking via predictable...                         │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-40973                   │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework.boot:spring-boot-starter-actuator │ CVE-2026-22733      │          │        │                   │ 4.0.4, 3.5.12             │ Spring Boot has an Authentication Bypass under Actuator      │
│                                                       │                     │          │        │                   │                           │ CloudFoundry endpoints                                       │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-22733                   │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework.data:spring-data-commons          │ CVE-2026-41716      │          │        │                   │ 4.0.6, 3.5.12             │ Spring Data Commons: Spring Data Commons: Denial of Service  │
│                                                       │                     │          │        │                   │                           │ due to cache...                                              │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-41716                   │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        ├───────────────────┼───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework:spring-core                       │ CVE-2025-41249      │          │        │ 6.1.21            │ 6.2.11                    │ org.springframework/spring-core: Spring Framework Annotation │
│                                                       │                     │          │        │                   │                           │ Detection Vulnerability                                      │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2025-41249                   │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework:spring-expression                 │ CVE-2026-41850      │          │        │                   │ 7.0.8, 6.2.19             │ spring-framework: Spring Framework: Denial of Service via    │
│                                                       │                     │          │        │                   │                           │ specially crafted SpEL expressions                           │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-41850                   │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
│                                                       │                     │          │        │                   │                           │                                                              │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│ org.springframework:spring-webmvc                     │ CVE-2026-41842      │          │        │                   │                           │ spring-framework: Spring Framework: Denial of Service when   │
│                                                       │                     │          │        │                   │                           │ resolving static resources                                   │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-41842                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-41845      │          │        │                   │                           │ org.springframework: Spring Framework: Cross-site scripting  │
│                                                       │                     │          │        │                   │                           │ (XSS) via incorrect JavaScript escaping                      │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-41845                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-41842      │          │        │                   │                           │ spring-framework: Spring Framework: Denial of Service when   │
│                                                       │                     │          │        │                   │                           │ resolving static resources                                   │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-41842                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-41845      │          │        │                   │                           │ org.springframework: Spring Framework: Cross-site scripting  │
│                                                       │                     │          │        │                   │                           │ (XSS) via incorrect JavaScript escaping                      │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-41845                   │
└───────────────────────────────────────────────────────┴─────────────────────┴──────────┴────────┴───────────────────┴───────────────────────────┴──────────────────────────────────────────────────────────────┘

shared/pom.xml (pom)
====================
Total: 3 (HIGH: 3, CRITICAL: 0)

┌───────────────────────────────────────┬────────────────┬──────────┬────────┬───────────────────┬───────────────┬──────────────────────────────────────────────────────────────┐
│                Library                │ Vulnerability  │ Severity │ Status │ Installed Version │ Fixed Version │                            Title                             │
├───────────────────────────────────────┼────────────────┼──────────┼────────┼───────────────────┼───────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework.boot:spring-boot  │ CVE-2026-40973 │ HIGH     │ fixed  │ 3.3.13            │ 4.0.6, 3.5.14 │ Spring Boot: Spring Boot: Arbitrary Code Execution and       │
│                                       │                │          │        │                   │               │ Session Hijacking via predictable...                         │
│                                       │                │          │        │                   │               │ https://avd.aquasec.com/nvd/cve-2026-40973                   │
├───────────────────────────────────────┼────────────────┤          │        ├───────────────────┼───────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework:spring-core       │ CVE-2025-41249 │          │        │ 6.1.21            │ 6.2.11        │ org.springframework/spring-core: Spring Framework Annotation │
│                                       │                │          │        │                   │               │ Detection Vulnerability                                      │
│                                       │                │          │        │                   │               │ https://avd.aquasec.com/nvd/cve-2025-41249                   │
├───────────────────────────────────────┼────────────────┤          │        │                   ├───────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework:spring-expression │ CVE-2026-41850 │          │        │                   │ 7.0.8, 6.2.19 │ spring-framework: Spring Framework: Denial of Service via    │
│                                       │                │          │        │                   │               │ specially crafted SpEL expressions                           │
│                                       │                │          │        │                   │               │ https://avd.aquasec.com/nvd/cve-2026-41850                   │
└───────────────────────────────────────┴────────────────┴──────────┴────────┴───────────────────┴───────────────┴──────────────────────────────────────────────────────────────┘

worker/pom.xml (pom)
====================
Total: 30 (HIGH: 24, CRITICAL: 6)

┌───────────────────────────────────────────────────────┬─────────────────────┬──────────┬────────┬───────────────────┬───────────────────────────┬──────────────────────────────────────────────────────────────┐
│                        Library                        │    Vulnerability    │ Severity │ Status │ Installed Version │       Fixed Version       │                            Title                             │
├───────────────────────────────────────────────────────┼─────────────────────┼──────────┼────────┼───────────────────┼───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ com.fasterxml.jackson.core:jackson-core               │ GHSA-r7wm-3cxj-wff9 │ HIGH     │ fixed  │ 2.17.3            │ 2.18.8, 2.21.4            │ jackson-core: Async parser maxNumberLength bypass via        │
│                                                       │                     │          │        │                   │                           │ chunked digit accumulation (incomplete fix for...            │
│                                                       │                     │          │        │                   │                           │ https://github.com/advisories/GHSA-r7wm-3cxj-wff9            │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ com.fasterxml.jackson.core:jackson-databind           │ CVE-2026-54512      │          │        │                   │ 2.18.8, 3.1.4, 2.21.4     │ jackson-databind: jackson-databind: Arbitrary code execution │
│                                                       │                     │          │        │                   │                           │ via PolymorphicTypeValidator bypass                          │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-54512                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-54513      │          │        │                   │ 2.18.8, 2.21.4, 3.1.4     │ jackson-databind: Jackson-databind: Security bypass allows   │
│                                                       │                     │          │        │                   │                           │ arbitrary code execution                                     │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-54513                   │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        ├───────────────────┼───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ io.micrometer:micrometer-core                         │ CVE-2026-40984      │          │        │ 1.13.15           │ 1.16.6, 1.15.12           │ micrometer-core: micrometer-jetty11: micrometer-jetty12:     │
│                                                       │                     │          │        │                   │                           │ Micrometer: Denial of Service via specially crafted HTTP     │
│                                                       │                     │          │        │                   │                           │ requests...                                                  │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-40984                   │
├───────────────────────────────────────────────────────┼─────────────────────┼──────────┤        ├───────────────────┼───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ org.apache.tomcat.embed:tomcat-embed-core             │ CVE-2026-41293      │ CRITICAL │        │ 10.1.42           │ 9.0.118, 10.1.55, 11.0.22 │ tomcat-coyote: Apache Tomcat: HTTP/2 request headers not     │
│                                                       │                     │          │        │                   │                           │ validated                                                    │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-41293                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-43512      │          │        │                   │                           │ tomcat-coyote: Apache Tomcat: Authentication bypass via      │
│                                                       │                     │          │        │                   │                           │ digest authentication                                        │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-43512                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-43515      │          │        │                   │                           │ tomcat-coyote: tomcat: Improper Authorization allows         │
│                                                       │                     │          │        │                   │                           │ security bypass                                              │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-43515                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-65182      │          │        │                   │ 11.0.25, 10.1.58, 9.0.121 │ Apache Tomcat: Apache Tomcat: Security constraint bypass due │
│                                                       │                     │          │        │                   │                           │ to improper access control...                                │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-65182                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-65905      │          │        │                   │                           │ tomcat: Apache Tomcat: Authentication bypass via limited     │
│                                                       │                     │          │        │                   │                           │ replay attack in DIGEST authenticator...                     │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-65905                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-68525      │          │        │                   │                           │ org.apache.tomcat/tomcat: Apache Tomcat: Unauthorized        │
│                                                       │                     │          │        │                   │                           │ resource access via FORM authentication bypass               │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-68525                   │
│                                                       ├─────────────────────┼──────────┤        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2025-48989      │ HIGH     │        │                   │ 11.0.10, 10.1.44, 9.0.108 │ tomcat: http/2 "MadeYouReset" DoS attack through HTTP/2      │
│                                                       │                     │          │        │                   │                           │ control frames                                               │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2025-48989                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2025-52520      │          │        │                   │ 11.0.9, 10.1.43, 9.0.107  │ tomcat: Apache Tomcat denial of service                      │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2025-52520                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2025-53506      │          │        │                   │ 9.0.107, 10.1.43, 11.0.9  │ tomcat: Apache Tomcat denial of service                      │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2025-53506                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2025-55752      │          │        │                   │ 11.0.11, 10.1.45, 9.0.109 │ tomcat: org.apache.tomcat/tomcat-catalina: Apache Tomcat:    │
│                                                       │                     │          │        │                   │                           │ Directory traversal via rewrite with possible RCE            │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2025-55752                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-24734      │          │        │                   │ 11.0.18, 10.1.52, 9.0.115 │ tomcat: Apache Tomcat: Certificate revocation bypass due to  │
│                                                       │                     │          │        │                   │                           │ improper OCSP response validation...                         │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-24734                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-24880      │          │        │                   │ 9.0.116, 10.1.52, 11.0.20 │ Apache Tomcat: Apache Tomcat: HTTP Request/Response          │
│                                                       │                     │          │        │                   │                           │ Smuggling via invalid chunk extension                        │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-24880                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-34483      │          │        │                   │ 9.0.116, 10.1.54, 11.0.21 │ Apache Tomcat: Apache Tomcat: Information disclosure due to  │
│                                                       │                     │          │        │                   │                           │ improper encoding in JsonAccessLogValve...                   │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-34483                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-34487      │          │        │                   │ 9.0.117, 10.1.54, 11.0.21 │ Apache Tomcat: Apache Tomcat: Information disclosure via     │
│                                                       │                     │          │        │                   │                           │ sensitive data in log files...                               │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-34487                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-41284      │          │        │                   │ 9.0.118, 10.1.55, 11.0.22 │ tomcat: Apache Tomcat: Denial of Service due to uncontrolled │
│                                                       │                     │          │        │                   │                           │ resource allocation                                          │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-41284                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-42498      │          │        │                   │                           │ tomcat-coyote: Apache Tomcat: Information disclosure due to  │
│                                                       │                     │          │        │                   │                           │ HTTP Authentication Header exposure during...                │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-42498                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-43513      │          │        │                   │                           │ tomcat-catalina: Apache Tomcat: Improper Handling of Case    │
│                                                       │                     │          │        │                   │                           │ Sensitivity in LockOutRealm                                  │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-43513                   │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        ├───────────────────┼───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ org.postgresql:postgresql                             │ CVE-2026-42198      │          │        │ 42.7.7            │ 42.7.11                   │ jdbc.postgresql.org: pgjdbc: Client-side Denial of Service   │
│                                                       │                     │          │        │                   │                           │ via malicious SCRAM-SHA-256 authentication                   │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-42198                   │
│                                                       ├─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-54291      │          │        │                   │ 42.7.12                   │ org.postgresql/postgresql: com.ongres.scram/scram-client:    │
│                                                       │                     │          │        │                   │                           │ pgjdbc: Man-in-the-middle protection bypass via              │
│                                                       │                     │          │        │                   │                           │ SCRAM-SHA-256-PLUS downgrade                                 │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-54291                   │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        ├───────────────────┼───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework.boot:spring-boot                  │ CVE-2026-40973      │          │        │ 3.3.13            │ 4.0.6, 3.5.14             │ Spring Boot: Spring Boot: Arbitrary Code Execution and       │
│                                                       │                     │          │        │                   │                           │ Session Hijacking via predictable...                         │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-40973                   │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework.boot:spring-boot-starter-actuator │ CVE-2026-22733      │          │        │                   │ 4.0.4, 3.5.12             │ Spring Boot has an Authentication Bypass under Actuator      │
│                                                       │                     │          │        │                   │                           │ CloudFoundry endpoints                                       │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-22733                   │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework.data:spring-data-commons          │ CVE-2026-41716      │          │        │                   │ 4.0.6, 3.5.12             │ Spring Data Commons: Spring Data Commons: Denial of Service  │
│                                                       │                     │          │        │                   │                           │ due to cache...                                              │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-41716                   │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        ├───────────────────┼───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework:spring-core                       │ CVE-2025-41249      │          │        │ 6.1.21            │ 6.2.11                    │ org.springframework/spring-core: Spring Framework Annotation │
│                                                       │                     │          │        │                   │                           │ Detection Vulnerability                                      │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2025-41249                   │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        │                   ├───────────────────────────┼──────────────────────────────────────────────────────────────┤
│ org.springframework:spring-expression                 │ CVE-2026-41850      │          │        │                   │ 7.0.8, 6.2.19             │ spring-framework: Spring Framework: Denial of Service via    │
│                                                       │                     │          │        │                   │                           │ specially crafted SpEL expressions                           │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-41850                   │
├───────────────────────────────────────────────────────┼─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│ org.springframework:spring-webmvc                     │ CVE-2026-41842      │          │        │                   │                           │ spring-framework: Spring Framework: Denial of Service when   │
│                                                       │                     │          │        │                   │                           │ resolving static resources                                   │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-41842                   │
│                                                       ├─────────────────────┤          │        │                   │                           ├──────────────────────────────────────────────────────────────┤
│                                                       │ CVE-2026-41845      │          │        │                   │                           │ org.springframework: Spring Framework: Cross-site scripting  │
│                                                       │                     │          │        │                   │                           │ (XSS) via incorrect JavaScript escaping                      │
│                                                       │                     │          │        │                   │                           │ https://avd.aquasec.com/nvd/cve-2026-41845                   │
└───────────────────────────────────────────────────────┴─────────────────────┴──────────┴────────┴───────────────────┴───────────────────────────┴──────────────────────────────────────────────────────────────┘
```

## Frontend — `trivy fs --include-dev-deps --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed code/frontend`

```

Report Summary

┌───────────────────┬──────┬─────────────────┐
│      Target       │ Type │ Vulnerabilities │
├───────────────────┼──────┼─────────────────┤
│ package-lock.json │ npm  │        1        │
└───────────────────┴──────┴─────────────────┘
Legend:
- '-': Not scanned
- '0': Clean (no security findings detected)


package-lock.json (npm)
=======================
Total: 1 (HIGH: 1, CRITICAL: 0)

┌─────────┬────────────────┬──────────┬────────┬───────────────────┬──────────────────────┬──────────────────────────────────────────────────────────┐
│ Library │ Vulnerability  │ Severity │ Status │ Installed Version │    Fixed Version     │                          Title                           │
├─────────┼────────────────┼──────────┼────────┼───────────────────┼──────────────────────┼──────────────────────────────────────────────────────────┤
│ vite    │ CVE-2026-53571 │ HIGH     │ fixed  │ 5.4.21            │ 8.0.16, 7.3.5, 6.4.3 │ vite: `server.fs.deny` bypass on Windows alternate paths │
│         │                │          │        │                   │                      │ https://avd.aquasec.com/nvd/cve-2026-53571               │
└─────────┴────────────────┴──────────┴────────┴───────────────────┴──────────────────────┴──────────────────────────────────────────────────────────┘
```
