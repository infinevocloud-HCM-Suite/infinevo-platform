package com.infinevo.core.navigation;

import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes {@code GET /api/v1/navigation} for the authenticated user and bound tenant (W-12.3).
 *
 * <p>Every signed-in user has a menu (12-core-contracts.md §2); no {@code @RequiresAction}
 * gates the endpoint itself. Items inside are filtered by module entitlement and held action codes.
 */
@RestController
@RequestMapping("/api/v1/navigation")
public class NavigationController {

    private final NavigationService navigationService;

    public NavigationController(NavigationService navigationService) {
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService must not be null");
    }

    @GetMapping
    public ResponseEntity<NavigationResponse> getNavigation() {
        return ResponseEntity.ok(navigationService.navigation());
    }
}
