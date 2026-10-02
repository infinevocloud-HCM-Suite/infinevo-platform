package com.infinevo.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.core.subscription.EntitlementReadService;
import com.infinevo.core.subscription.SubscriptionService;
import com.infinevo.core.subscription.SubscriptionStatus;
import com.infinevo.shared.entitlement.EntitlementSnapshot;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * W-65.1 review: the Infinevo platform tenant's own subscription row.
 *
 * <p>{@code V082} once wrote its status as {@code 'active'}, in lower case, while the code reads the column as the
 * {@code SubscriptionStatus} enum, whose names are upper case. The row could not be loaded as an entity, so the
 * platform tenant's entitlement lookup and its own subscription read failed, and the tenant list showed it with a
 * status no customer has. {@code V138} corrects the value and adds a CHECK so it cannot come back.
 *
 * <p>These run through the real service, the real entitlement lookup and the real HTTP endpoints against the
 * row {@code V082} wrote, not a row the test inserted.
 */
class PlatformSubscriptionIT extends ImpersonationItSupport {

    private static final Set<String> STATUSES =
            Arrays.stream(SubscriptionStatus.values()).map(Enum::name).collect(Collectors.toSet());

    @Autowired
    private SubscriptionService subscriptions;

    @Autowired
    private EntitlementReadService entitlements;

    private Staff staff;

    @BeforeEach
    void setUp() throws Exception {
        staff = newStaff("staff-" + java.util.UUID.randomUUID());
    }

    @Test
    @DisplayName("The platform tenant's subscription loads, and it is ACTIVE")
    void platformSubscriptionLoadsAsActive() {
        TenantContext.set(PLATFORM);
        try {
            assertThat(subscriptions.getSubscription(PLATFORM).status()).isEqualTo(SubscriptionStatus.ACTIVE);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("The platform tenant's entitlement snapshot is built, active, and holds no module")
    void platformEntitlementSnapshotIsBuilt() {
        TenantContext.set(PLATFORM);
        try {
            EntitlementSnapshot snapshot = entitlements.snapshotOf(PLATFORM);

            assertThat(snapshot).isNotNull();
            assertThat(snapshot.suspended()).isFalse();
            assertThat(entitlements.modulesOf(PLATFORM)).isEmpty();
            assertThat(entitlements.isSuspended(PLATFORM)).isFalse();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("Platform staff read their own tenant's subscription over HTTP: 200, ACTIVE")
    void staffReadTheirOwnSubscription() throws Exception {
        mvc.perform(asStaff(staff, get("/api/v1/tenants/{id}/subscription", PLATFORM)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("The tenant list shows every tenant, the platform tenant included, with a real SubscriptionStatus")
    void tenantListStatusesAreAllRealStatuses() throws Exception {
        String body = mvc.perform(asStaff(staff, get("/api/v1/tenants")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode rows = json.readTree(body);
        Set<String> seen = new HashSet<>();
        String platformStatus = null;
        for (JsonNode row : rows) {
            // A tenant with no subscription row has no status at all; that is not what is under test.
            String status =
                    row.path("status").isNull() ? null : row.path("status").asText();
            if (status != null) {
                seen.add(status);
            }
            if (PLATFORM.toString().equals(row.path("tenant_id").asText())) {
                platformStatus = status;
            }
        }
        assertThat(platformStatus).as("the platform tenant is in the list").isEqualTo("ACTIVE");
        assertThat(seen).as("no status the enum does not know").isSubsetOf(STATUSES);
    }
}
