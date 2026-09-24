package com.infinevo.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * W-22.1 — actor resolution, changed-column diffing and the redaction deny-list (spec section 7).
 */
class AuditWriterTest {

    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String SUBJECT = "3f1b2c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";

    @BeforeEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    /** Every column of a plain table resolves to exactly one database column. */
    private static AuditWriter.Column[] resolved(String... names) {
        return java.util.Arrays.stream(names).map(AuditWriter.Column::of).toArray(AuditWriter.Column[]::new);
    }

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("sub", subject)
                .build();
    }

    @Test
    @DisplayName("Actor is the Keycloak JWT subject when a request is in flight")
    void actorIsJwtSubject() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt(SUBJECT), List.of()));

        AuditWriter.Actor actor = AuditWriter.resolveActor();

        assertThat(actor.label()).isEqualTo(SUBJECT);
        assertThat(actor.userId()).isEqualTo(UUID.fromString(SUBJECT));
    }

    @Test
    @DisplayName("A non-UUID subject still names the actor, but actor_user_id stays null (W-10 has not merged)")
    void nonUuidSubjectLeavesUserIdNull() {
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt("service-account-payroll"), List.of()));

        AuditWriter.Actor actor = AuditWriter.resolveActor();

        assertThat(actor.label()).isEqualTo("service-account-payroll");
        assertThat(actor.userId()).isNull();
    }

    @Test
    @DisplayName("Actor falls back to 'system' with no authentication")
    void actorFallsBackToSystem() {
        AuditWriter.Actor actor = AuditWriter.resolveActor();

        assertThat(actor.label()).isEqualTo(AuditWriter.SYSTEM_ACTOR);
        assertThat(actor.userId()).isNull();
    }

    @Test
    @DisplayName("An anonymous authentication is not an actor: it resolves to 'system'")
    void anonymousResolvesToSystem() {
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(AuditWriter.resolveActor().label()).isEqualTo(AuditWriter.SYSTEM_ACTOR);
    }

    @Test
    @DisplayName("A non-JWT authentication resolves to its name")
    void usernamePasswordAuthenticationResolvesToName() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(SUBJECT, "n/a", List.of()));

        assertThat(AuditWriter.resolveActor().label()).isEqualTo(SUBJECT);
    }

    @Test
    @DisplayName("Diffing names only the columns whose value changed")
    void diffNamesOnlyChangedColumns() {
        AuditWriter.Column[] columns = resolved("name", "status", "amount");
        Object[] before = {"Acme", "ACTIVE", new BigDecimal("100.00")};
        Object[] after = {"Acme Ltd", "ACTIVE", new BigDecimal("100.00")};

        assertThat(AuditWriter.changedProperties(columns, before, after)).containsExactly("name");
    }

    @Test
    @DisplayName("A null old state yields no changed columns rather than a false diff")
    void diffWithNullStateIsEmpty() {
        assertThat(AuditWriter.changedProperties(resolved("name"), null, new Object[] {"Acme"}))
                .isEmpty();
    }

    @Test
    @DisplayName("Values are restricted to the changed columns when a filter is given")
    void valuesAreRestrictedToChangedColumns() {
        AuditWriter.Column[] columns = resolved("name", "status");
        Object[] state = {"Acme Ltd", "ACTIVE"};

        assertThat(AuditWriter.values(columns, state, List.of("name"))).containsOnlyKeys("name");
    }

    @Test
    @DisplayName("Deny-list: password, secret, token and credential columns are redacted, case-insensitively")
    void denyListRedactsSecrets() {
        AuditWriter.Column[] columns =
                resolved("name", "password_hash", "API_SECRET", "refreshToken", "credentialBlob", "tokenised_name");
        Object[] state = {"Acme", "hunter2", "sk-live-1", "eyJhbGciOi", "blob", "harmless"};

        Map<String, String> values = AuditWriter.values(columns, state, null);

        assertThat(values).containsEntry("name", "Acme");
        assertThat(values.get("password_hash")).isEqualTo(AuditWriter.REDACTED);
        assertThat(values.get("API_SECRET")).isEqualTo(AuditWriter.REDACTED);
        assertThat(values.get("refreshToken")).isEqualTo(AuditWriter.REDACTED);
        assertThat(values.get("credentialBlob")).isEqualTo(AuditWriter.REDACTED);
        // A name merely containing "token" is redacted too: the deny-list errs towards withholding.
        assertThat(values.get("tokenised_name")).isEqualTo(AuditWriter.REDACTED);
        assertThat(values.values()).doesNotContain("hunter2", "sk-live-1", "eyJhbGciOi", "blob");
    }

    @Test
    @DisplayName("Short identity columns are matched as whole words, so company_name survives")
    void wholeWordDenyListDoesNotEatOrdinaryColumns() {
        AuditWriter.Column[] columns =
                resolved("pan_number", "pan", "ssn", "otp", "salt", "company_name", "shipping_pincode", "span");
        Object[] state = {"ABCDE1234F", "ABCDE1234F", "123-45-6789", "884412", "s41t", "Acme Ltd", "560001", "wide"};

        Map<String, String> values = AuditWriter.values(columns, state, null);

        // Redacted: each is an exact underscore-delimited word.
        assertThat(values.get("pan_number")).isEqualTo(AuditWriter.REDACTED);
        assertThat(values.get("pan")).isEqualTo(AuditWriter.REDACTED);
        assertThat(values.get("ssn")).isEqualTo(AuditWriter.REDACTED);
        assertThat(values.get("otp")).isEqualTo(AuditWriter.REDACTED);
        assertThat(values.get("salt")).isEqualTo(AuditWriter.REDACTED);

        // NOT redacted: "company_name" contains "pan", "shipping_pincode" contains "pin" and
        // "span" contains "pan". A substring rule would have withheld all three and made the
        // audit trail useless. This is the assertion that stops someone "simplifying" the matcher.
        assertThat(values).containsEntry("company_name", "Acme Ltd");
        assertThat(values).containsEntry("shipping_pincode", "560001");
        assertThat(values).containsEntry("span", "wide");
    }

    @Test
    @DisplayName("The longer secret names added after review are redacted")
    void extendedFragmentsAreRedacted() {
        AuditWriter.Column[] columns =
                resolved("api_key", "access_key", "private_key", "passphrase", "aadhaar_number", "passport_no");
        Object[] state = {"ak-1", "AKIA1", "-----BEGIN", "correct horse", "1234 5678 9012", "Z1234567"};

        Map<String, String> values = AuditWriter.values(columns, state, null);

        assertThat(values.values()).containsOnly(AuditWriter.REDACTED);
    }

    @Test
    @DisplayName("An unresolved property is redacted whatever it is called, and its toString never leaks")
    void unresolvedColumnIsRedactedRegardlessOfName() {
        // "homeAddress" is a Java property name, not a column name, so no deny-list entry can
        // ever match it. Before W-13.2 the whole embedded object's toString() was stored under
        // it. The flag, not the name, is what withholds it now.
        AuditWriter.Column[] columns = {
            AuditWriter.Column.of("bank_name"),
            AuditWriter.Column.unresolved("homeAddress"),
            AuditWriter.Column.unresolved("employer")
        };
        Object[] state = {"Acme Bank", new EmbeddedAddress("12 Secret Lane", "560001"), "Acme Ltd"};

        Map<String, String> values = AuditWriter.values(columns, state, null);

        assertThat(values.get("homeAddress")).isEqualTo(AuditWriter.REDACTED);
        assertThat(values.get("employer")).isEqualTo(AuditWriter.REDACTED);
        assertThat(values.values()).doesNotContain("12 Secret Lane", "560001", "Acme Ltd");
        assertThat(values.values()).noneMatch(value -> value != null && value.contains("Secret Lane"));
        // The name is still recorded: the trail says what changed, only the value is withheld.
        assertThat(values).containsKeys("bank_name", "homeAddress", "employer");
        // And an ordinary resolved column is untouched.
        assertThat(values).containsEntry("bank_name", "Acme Bank");
    }

    @Test
    @DisplayName("An unresolved property still counts as changed, so changed_columns still names it")
    void unresolvedColumnStillDiffs() {
        AuditWriter.Column[] columns = {AuditWriter.Column.unresolved("homeAddress"), AuditWriter.Column.of("city")};
        Object[] before = {new EmbeddedAddress("12 Secret Lane", "560001"), "Bengaluru"};
        Object[] after = {new EmbeddedAddress("9 Other Road", "560002"), "Bengaluru"};

        assertThat(AuditWriter.changedProperties(columns, before, after)).containsExactly("homeAddress");
    }

    @Test
    @DisplayName("W-13.2: bank account number and IFSC are redacted, the rest of the bank row is not")
    void bankColumnsAreRedactedWithoutOverReaching() {
        AuditWriter.Column[] columns = resolved(
                "bank_account_number", "account_number", "ifsc_code", "ifsc", "bank_name", "branch_name", "city");
        Object[] state = {
            "50100123456789", "50100123456789", "HDFC0001234", "HDFC0001234", "HDFC Bank", "Indiranagar", "Bengaluru"
        };

        Map<String, String> values = AuditWriter.values(columns, state, null);

        assertThat(values.get("bank_account_number")).isEqualTo(AuditWriter.REDACTED);
        assertThat(values.get("account_number")).isEqualTo(AuditWriter.REDACTED);
        assertThat(values.get("ifsc_code")).isEqualTo(AuditWriter.REDACTED);
        assertThat(values.get("ifsc")).isEqualTo(AuditWriter.REDACTED);
        assertThat(values.values()).doesNotContain("50100123456789", "HDFC0001234");

        // Over-redaction is a real failure mode: a trail of *** tells nobody anything. The rest
        // of the bank row, and an ordinary address column, must come through intact.
        assertThat(values).containsEntry("bank_name", "HDFC Bank");
        assertThat(values).containsEntry("branch_name", "Indiranagar");
        assertThat(values).containsEntry("city", "Bengaluru");
    }

    @Test
    @DisplayName("The two new fragments do not disturb the whole-word rule")
    void newFragmentsDoNotBreakOrdinaryNames() {
        // "ifsc" is a fragment, not a word, so "ifsc_code" hits - but nothing ordinary contains
        // it. "account_number" is long enough that "account_type" and "number_of_days" do not.
        assertThat(AuditWriter.isRedacted("account_type")).isFalse();
        assertThat(AuditWriter.isRedacted("number_of_days")).isFalse();
        assertThat(AuditWriter.isRedacted("account_holder_name")).isFalse();
        assertThat(AuditWriter.isRedacted("ACCOUNT_NUMBER")).isTrue();
        assertThat(AuditWriter.isRedacted("IFSC")).isTrue();
    }

    /** Stands in for an {@code @Embedded} value object: one property, several columns. */
    private record EmbeddedAddress(String line1, String zip) {}

    @Test
    @DisplayName("Money is serialised as a plain string, never as a float (CONVENTIONS section 2)")
    void moneyIsSerialisedAsString() {
        assertThat(AuditWriter.serialize(new BigDecimal("1250.5000"))).isEqualTo("1250.5000");
        assertThat(AuditWriter.serialize(new BigDecimal("1E+3"))).isEqualTo("1000");
        assertThat(AuditWriter.serialize(null)).isNull();
    }

    @Test
    @DisplayName("Row carries the bound tenant, the actor and the operation")
    void buildRowCarriesTenantAndActor() {
        TenantContext.set(TENANT);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt(SUBJECT), List.of()));

        AuditLog row = AuditWriter.buildRow(new AuditWriter.AuditChange(
                "UPDATE",
                "core",
                "tenant",
                TENANT.toString(),
                List.of("name"),
                Map.of("name", "Acme"),
                Map.of("name", "Acme Ltd")));

        assertThat(row.getTenantId()).isEqualTo(TENANT);
        assertThat(row.getActorLabel()).isEqualTo(SUBJECT);
        assertThat(row.getActorUserId()).isEqualTo(UUID.fromString(SUBJECT));
        assertThat(row.getOperation()).isEqualTo("UPDATE");
        assertThat(row.getEntitySchema()).isEqualTo("core");
        assertThat(row.getEntityTable()).isEqualTo("tenant");
        assertThat(row.getChangedColumns()).containsExactly("name");
        assertThat(row.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("An unbound tenant fails the write loudly rather than dropping the row (spec section 9)")
    void unboundTenantFailsLoudly() {
        assertThatThrownBy(() -> AuditWriter.buildRow(new AuditWriter.AuditChange(
                        "INSERT", "core", "tenant", "1", List.of("name"), null, Map.of("name", "Acme"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No tenant bound");
    }

    @Test
    @DisplayName("Every identity number on core.employee_identification is withheld")
    void identificationColumnsAreRedacted() {
        // W-13.2 review, F-2. PAN and Aadhaar were covered from the start; these four were not,
        // and they sit on the same row that this ticket turned auditing on for.
        AuditWriter.Column[] columns = resolved(
                "pan_number",
                "aadhaar_number",
                "social_insurance_number",
                "personal_tax_id",
                "id_document_number",
                "address_document_number",
                "immigration_status",
                "id_proof_type",
                "id_document_name");
        Object[] state = {
            "ABCDE1234F",
            "123412341234",
            "SI-99887766",
            "TAX-4455",
            "Z1234567",
            "EB-2026-0042",
            "CITIZEN",
            "PASSPORT",
            "Passport"
        };

        Map<String, String> values = AuditWriter.values(columns, state, null);

        assertThat(values.get("pan_number")).isEqualTo(AuditWriter.REDACTED);
        assertThat(values.get("aadhaar_number")).isEqualTo(AuditWriter.REDACTED);
        assertThat(values.get("social_insurance_number")).isEqualTo(AuditWriter.REDACTED);
        assertThat(values.get("personal_tax_id")).isEqualTo(AuditWriter.REDACTED);
        assertThat(values.get("id_document_number")).isEqualTo(AuditWriter.REDACTED);
        assertThat(values.get("address_document_number")).isEqualTo(AuditWriter.REDACTED);
        assertThat(values.values())
                .as("no identity number may reach core.audit_log, where readonly_user can read it")
                .doesNotContain("ABCDE1234F", "123412341234", "SI-99887766", "TAX-4455", "Z1234567", "EB-2026-0042");

        // What the section is, and which kind of document it is, stay readable - otherwise the
        // trail says only that something changed.
        assertThat(values).containsEntry("immigration_status", "CITIZEN");
        assertThat(values).containsEntry("id_proof_type", "PASSPORT");
        assertThat(values).containsEntry("id_document_name", "Passport");
    }

    @Test
    @DisplayName("The identification fragments do not reach ordinary columns")
    void identificationFragmentsDoNotOverReach() {
        AuditWriter.Column[] columns =
                resolved("document_count", "tax_regime", "insurance_provider", "employee_number", "note");
        Object[] state = {"3", "NEW", "Star Health", "EMP-0001", "joined mid-year"};

        Map<String, String> values = AuditWriter.values(columns, state, null);

        assertThat(values)
                .containsEntry("document_count", "3")
                .containsEntry("tax_regime", "NEW")
                .containsEntry("insurance_provider", "Star Health")
                .containsEntry("employee_number", "EMP-0001")
                .containsEntry("note", "joined mid-year");
    }
}
