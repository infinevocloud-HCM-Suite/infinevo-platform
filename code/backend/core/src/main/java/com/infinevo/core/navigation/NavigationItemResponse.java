package com.infinevo.core.navigation;

import java.util.List;

/**
 * A single item in the navigation feed (W-12.3).
 *
 * @param key unique identifier (e.g. {@code core.employee}, {@code hrms.leave})
 * @param labelKey translation / i18n label key (e.g. {@code nav.employees})
 * @param path frontend route path (e.g. {@code /employees})
 * @param children nested child items, if any
 */
public record NavigationItemResponse(String key, String labelKey, String path, List<NavigationItemResponse> children) {

    public NavigationItemResponse(String key, String labelKey, String path) {
        this(key, labelKey, path, List.of());
    }
}
