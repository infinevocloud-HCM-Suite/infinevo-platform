package com.infinevo.core.ai;

/**
 * A screen of the caller's own menu (W-114.13): its plain name and its path. Both come from the product - the menu
 * item's path and its name in {@code help/screens.json} - never from the client.
 */
public record AiScreen(String title, String path) {}
