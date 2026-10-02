package com.infinevo.core.navigation;

import java.util.List;

/**
 * Menu items a module adds to the navigation catalogue (W-47.2, founder 2026-10-01).
 *
 * <p>{@link NavigationCatalogue#DEFAULT_ITEMS} holds the core items. A module item lives in its own
 * module, next to the controller it targets, because {@code core} cannot hold a module's controller
 * and {@link NavigationCatalogueValidator} refuses a leaf whose endpoint the context does not map. A
 * contributed item is filtered and validated exactly as a catalogue one: by its module, by its
 * action, and for a {@code GET} mapping at boot.
 */
public interface NavigationContributor {

    /** The items this module adds, in menu order; appended after the core items. */
    List<NavigationCatalogue.ItemDefinition> items();
}
