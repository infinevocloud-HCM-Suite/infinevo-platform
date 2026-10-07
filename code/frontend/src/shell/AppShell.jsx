import React from 'react';
import { Layout, Menu, Skeleton, Typography, Button, Result, theme as antdTheme } from 'antd';
import { Link, Routes, Route, useLocation } from 'react-router-dom';
import { useNavigation } from './navigation/useNavigation.js';
import { navLabel } from './navigation/navLabels.js';
import { routesFromFeed, portalRoutes } from './routes.js';
import { Header } from './Header.jsx';
import { useImpersonationSession } from './useImpersonationSession.js';
import { ShellBoundary } from './ShellBoundary.jsx';
import { NotFound, NoModules } from './screens/index.js';
import { theme } from '../shared/theme.js';

const { Sider, Content } = Layout;

/**
 * The platform-staff tenant screens (W-65.1, W-65.3). While an "act as" session is live the feed
 * is the customer's and no longer names this path, yet staff still need the tenant page to read
 * the session's audit and to stop it. So these routes stay mounted for the life of a session; the
 * menu still follows the feed, and every endpoint behind them refuses on its own (W-12.2).
 */
const IMPERSONATION_ADMIN_PATH = '/admin/tenants';

/**
 * Layout and navigation shell (W-12.3 §5, W-45 §5).
 *
 * Navigation is driven entirely by the server-returned feed:
 *   GET /api/v1/navigation → { items, actions }
 *
 * The shell renders exactly what the server says. There is no static menu
 * array anywhere here. An empty feed → empty sidebar and NoModules placeholder.
 */
export function AppShell() {
  const { items, loading, error, refetch } = useNavigation();
  const location = useLocation();
  const { token } = antdTheme.useToken();
  const session = useImpersonationSession();

  const menuItems = buildMenuItems(items);
  const selectedKey = findSelectedKey(items, location.pathname);
  // Only register routes whose path is in the navigation feed — a route not in the
  // feed is not registered at all (W-12.3 §5). An empty feed → empty route tree.
  // The one exception: a live impersonation session keeps the tenant admin routes mounted.
  const feedRoutes = routesFromFeed(
    session ? [...(items || []), { path: IMPERSONATION_ADMIN_PATH }] : items,
  );
  const onSessionAdminPath =
    !!session &&
    (location.pathname === IMPERSONATION_ADMIN_PATH ||
      location.pathname.startsWith(`${IMPERSONATION_ADMIN_PATH}/`));

  const siderWidth = theme.components.Layout.siderWidth;
  const headerHeight = theme.components.Layout.headerHeight;

  return (
    <ShellBoundary>
      <Layout style={{ minHeight: '100vh' }}>
        <Sider
          width={siderWidth}
          theme="dark"
          collapsible={false}
          style={{ position: 'fixed', left: 0, top: 0, bottom: 0, overflowY: 'auto', zIndex: 100 }}
        >
          <div
            style={{
              height: headerHeight,
              display: 'flex',
              alignItems: 'center',
              padding: `0 ${token.padding}px`,
              borderBottom: `1px solid ${token.colorBorderSecondary}`,
            }}
          >
            <Typography.Text strong style={{ color: token.colorBgContainer, fontSize: token.fontSizeHeading4, letterSpacing: 1 }}>
              Infinevo
            </Typography.Text>
          </div>

          {loading ? (
            <div style={{ padding: `${token.padding}px ${token.padding}px` }}>
              {[...Array(5)].map((_, i) => (
                <Skeleton
                  key={i}
                  active
                  title={{ width: '80%' }}
                  paragraph={false}
                  style={{ marginBottom: token.marginSM }}
                />
              ))}
            </div>
          ) : (
            <Menu
              theme="dark"
              mode="inline"
              selectedKeys={selectedKey ? [selectedKey] : []}
              items={menuItems}
              style={{ borderRight: 0, marginTop: token.sizeUnit * 2 }}
            />
          )}
        </Sider>

        <Layout style={{ marginLeft: siderWidth }}>
          <Header />
          <Content style={{ padding: token.paddingLG, background: token.colorBgLayout, minHeight: `calc(100vh - ${headerHeight}px)` }}>
            {!loading && error ? (
              <Result
                status="500"
                title="Navigation Unavailable"
                subTitle="Unable to load navigation feed. Please check your connection and try again."
                extra={
                  <Button
                    type="primary"
                    onClick={() => (refetch ? refetch() : window.location.reload())}
                    id="btn-retry-navigation"
                  >
                    Retry
                  </Button>
                }
              />
            ) : loading && (!items || items.length === 0) && !onSessionAdminPath ? (
              // Nothing to route yet. Rendering the routes here would show NotFound for a
              // path the feed is about to name.
              <Skeleton active />
            ) : (!items || items.length === 0) &&
              !location.pathname.startsWith('/me') &&
              !onSessionAdminPath ? (
              <NoModules />
            ) : (
              <React.Suspense fallback={<Skeleton active />}>
                <Routes>
                  {portalRoutes.map((route) => (
                    <Route key={route.path} path={route.path} element={route.element} />
                  ))}
                  {feedRoutes.map((route) => (
                    <Route key={route.path} path={route.path} element={route.element} />
                  ))}
                  <Route path="*" element={<NotFound />} />
                </Routes>
              </React.Suspense>
            )}
          </Content>
        </Layout>
      </Layout>
    </ShellBoundary>
  );
}

/**
 * Convert navigation items from the feed into Ant Design Menu item descriptors.
 * No icon keys are emitted — the feed carries only label keys and paths; `navLabel` turns a key into words.
 */
function buildMenuItems(items) {
  if (!items || items.length === 0) return [];
  return items.map((item) => {
    if (item.children && item.children.length > 0) {
      return {
        key: item.key,
        label: navLabel(item.labelKey),
        children: buildMenuItems(item.children),
      };
    }
    return {
      key: item.key,
      label: <Link to={item.path}>{navLabel(item.labelKey)}</Link>,
    };
  });
}

/**
 * Walk the item tree to find which key matches the current URL prefix.
 */
function findSelectedKey(items, pathname) {
  if (!items) return null;
  for (const item of items) {
    if (item.children && item.children.length > 0) {
      const child = findSelectedKey(item.children, pathname);
      if (child) return child;
    } else if (item.path && pathname.startsWith(item.path)) {
      return item.key;
    }
  }
  return null;
}
