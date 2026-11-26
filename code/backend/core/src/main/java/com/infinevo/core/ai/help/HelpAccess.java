package com.infinevo.core.ai.help;

import com.infinevo.core.ai.AiCaller;
import com.infinevo.core.navigation.NavigationItemResponse;
import com.infinevo.core.navigation.NavigationResponse;
import com.infinevo.core.navigation.NavigationService;
import com.infinevo.shared.authz.RoleSource;
import com.infinevo.shared.tenant.TenantContext;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Who may see which help article (W-114.11, "Who may see an article"), for the signed-in caller.
 *
 * <p>Check 1: the article's {@code screen} is a path in the caller's navigation feed ({@link NavigationService#navigation()},
 * children included), which has already dropped items whose module the customer does not hold and items whose action
 * the caller lacks. Check 2: an article's extra {@code action} is one the caller holds - the feed's action set, which is
 * {@code PermissionService.currentActions()}, the set {@code PermissionService.holds} answers from. Role codes are read
 * for ranking and for the role guide (W-114.12) only; a failure to read them leaves them empty and changes no access.
 */
@Component
public class HelpAccess {

    private static final Logger log = LoggerFactory.getLogger(HelpAccess.class);

    private final Supplier<HelpViewer> viewer;

    @Autowired
    public HelpAccess(NavigationService navigation, ObjectProvider<RoleSource> roles) {
        Objects.requireNonNull(navigation, "navigation must not be null");
        this.viewer = () -> fromFeed(navigation.navigation(), roles.getIfAvailable());
    }

    private HelpAccess(Supplier<HelpViewer> viewer) {
        this.viewer = viewer;
    }

    /** For tests: every call answers this viewer. */
    public static HelpAccess fixed(HelpViewer viewer) {
        Objects.requireNonNull(viewer, "viewer must not be null");
        return new HelpAccess(() -> viewer);
    }

    /** The current caller, worked out now from the feed. */
    public HelpViewer currentViewer() {
        return viewer.get();
    }

    static HelpViewer fromFeed(NavigationResponse feed, RoleSource roleSource) {
        Set<String> paths = new HashSet<>();
        Map<String, String> leaves = new LinkedHashMap<>();
        collect(feed.items(), paths, leaves);
        return new HelpViewer(paths, feed.actions(), roles(roleSource), feed.modules(), leaves);
    }

    private static void collect(List<NavigationItemResponse> items, Set<String> into, Map<String, String> leaves) {
        if (items == null) {
            return;
        }
        for (NavigationItemResponse item : items) {
            if (item.path() != null) {
                into.add(item.path());
                if ((item.children() == null || item.children().isEmpty()) && item.key() != null) {
                    leaves.putIfAbsent(item.key(), item.path());
                }
            }
            collect(item.children(), into, leaves);
        }
    }

    private static Set<String> roles(RoleSource source) {
        Optional<UUID> tenant = TenantContext.current();
        Optional<UUID> user = AiCaller.currentUserId();
        if (source == null || tenant.isEmpty() || user.isEmpty()) {
            return Set.of();
        }
        try {
            Set<String> codes = source.roleCodesOf(tenant.get(), user.get());
            return codes == null ? Set.of() : codes;
        } catch (RuntimeException e) {
            log.warn(
                    "Help: role codes could not be read; search ranks without them ({})",
                    e.getClass().getName());
            return Set.of();
        }
    }
}
