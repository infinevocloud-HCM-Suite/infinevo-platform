package com.infinevo.core.ai;

import java.util.List;

/**
 * How a module offers tools to the AI helper (W-114.3). A module declares one bean implementing this; {@code core}
 * never names another module, the way {@code core/portal/PortalPanelProvider.java} lets modules offer portal panels.
 * {@link AiToolRegistry} collects every provider at startup.
 */
public interface AiToolProvider {

    List<AiTool> tools();
}
