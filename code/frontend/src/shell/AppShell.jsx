import React from 'react';
import PropTypes from 'prop-types';
import { Layout, Menu, Skeleton, Typography, Button, Result, theme as antdTheme } from 'antd';
import { Link, Navigate, Routes, Route, useLocation, useNavigate } from 'react-router-dom';
import { useNavigation } from './navigation/useNavigation.js';
import { navLabel } from './navigation/navLabels.js';
import { routesFromFeed, portalRoutes } from './routes.js';
import { moduleEmployeeTabs } from './moduleTabs.js';
import { ModuleEmployeeTabsProvider } from './navigation/moduleEmployeeTabs.js';
import { Header } from './Header.jsx';
import { useImpersonationSession } from './useImpersonationSession.js';
import { ShellBoundary } from './ShellBoundary.jsx';
import { NotFound, NoModules } from './screens/index.js';
import { Welcome } from './screens/Welcome.jsx';
import { useMe } from './auth/useMe.js';
import { theme } from '../shared/theme.js';

const { Sider, Content } = Layout;

/**
 * The platform-staff tenant screens (W-65.1, W-65.3). While an "act as" session is live the feed
 * is the customer's and no longer names this path, yet staff still need the tenant page to read
 * the session's audit and to stop it. So these routes stay mounted for the life of a session; the
 * menu still follows the feed, and every endpoint behind them refuses on its own (W-12.2).
 */
const IMPERSONATION_ADMIN_PATH = '/admin/tenants';

/** The first-sign-in page (W-73.8). Mounted for every signed-in user, like the portal. */
const WELCOME_PATH = '/welcome';

/**
 * Where `/` goes: the welcome page until the user dismisses it (W-73.8), the home path after.
 * It waits for `/me` - deciding before it answers would send everyone home and skip the page.
 */
function RootRedirect({ homePath, me }) {
  if (me.loading) return <Skeleton active />;
  return <Navigate to={me.welcomeSeen ? homePath : WELCOME_PATH} replace />;
}

RootRedirect.propTypes = {
  homePath: PropTypes.string.isRequired,
  me: PropTypes.shape({ loading: PropTypes.bool, welcomeSeen: PropTypes.bool }).isRequired,
};

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
  const { items, actions, modules, tenantName, tenantLogoUrl, tagline, homePath: feedHomePath, loading, error, refetch } =
    useNavigation();
  const location = useLocation();
  const { token } = antdTheme.useToken();
  const session = useImpersonationSession();
  const me = useMe();
  const navigate = useNavigate();

  const menuItems = buildMenuItems(items);
  const selectedKey = findSelectedKey(items, location.pathname);
  // Groups start collapsed; the one holding the current screen opens (D-34). The user may open
  // others, and moving to a screen in another group opens that group instead.
  const selectedGroups = parentKeys(items, selectedKey) || [];
  const selectedGroupsId = selectedGroups.join('|');
  const [openKeys, setOpenKeys] = React.useState(selectedGroups);
  React.useEffect(() => {
    setOpenKeys(selectedGroupsId ? selectedGroupsId.split('|') : []);
  }, [selectedGroupsId]);
  // `/` is where Keycloak returns the user after login. It is not a menu path, so it opens the
  // caller's home page - the server names it by role (D-35) - or else the first screen the feed names.
  const homePath = feedHomePath || firstPath(items);
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
              flexDirection: 'column',
              justifyContent: 'center',
              padding: `0 ${token.padding}px`,
              borderBottom: `1px solid ${token.colorBorderSecondary}`,
            }}
          >
            <Typography.Text strong style={{ color: token.colorBgContainer, fontSize: token.fontSizeLG, lineHeight: 1.3 }}>
              Infinevo HCM Suite
            </Typography.Text>
            <Typography.Text style={{ color: token.colorBgContainer, fontSize: token.fontSizeSM, opacity: 0.75, lineHeight: 1.3 }}>
              Human Capital Management
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
              openKeys={openKeys}
              onOpenChange={setOpenKeys}
              items={menuItems}
              style={{ borderRight: 0, marginTop: token.sizeUnit * 2 }}
            />
          )}
        </Sider>

        <Layout style={{ marginLeft: siderWidth }}>
          <Header
            tenantName={tenantName}
            tenantLogoUrl={tenantLogoUrl}
            tagline={tagline}
            onGettingStarted={() => navigate(WELCOME_PATH)}
          />
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
              // An employee whose feed is empty still has the portal: the server's home sends them there,
              // through the welcome page on the first sign-in. Anyone else with no feed sees NoModules,
              // which wins over the welcome page (W-73.8 §9).
              feedHomePath && feedHomePath.startsWith('/me') && location.pathname === '/' ? (
                <RootRedirect homePath={feedHomePath} me={me} />
              ) : feedHomePath && feedHomePath.startsWith('/me') && location.pathname === WELCOME_PATH ? (
                <Welcome homePath={feedHomePath} />
              ) : (
                <NoModules />
              )
            ) : (
              // The modules' employee page tabs reach core's EmployeePage through this (D-66).
              <ModuleEmployeeTabsProvider tabs={moduleEmployeeTabs} modules={modules} actions={actions}>
                <React.Suspense fallback={<Skeleton active />}>
                  <Routes>
                    {homePath && <Route path="/" element={<RootRedirect homePath={homePath} me={me} />} />}
                    <Route path={WELCOME_PATH} element={<Welcome homePath={homePath} />} />
                    {portalRoutes.map((route) => (
                      <Route key={route.path} path={route.path} element={route.element} />
                    ))}
                    {feedRoutes.map((route) => (
                      <Route key={route.path} path={route.path} element={route.element} />
                    ))}
                    <Route path="*" element={<NotFound />} />
                  </Routes>
                </React.Suspense>
              </ModuleEmployeeTabsProvider>
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
 * The first navigable path in the feed, depth first - the menu's first entry.
 */
function firstPath(items) {
  for (const item of items || []) {
    if (item.children && item.children.length > 0) {
      const child = firstPath(item.children);
      if (child) return child;
    } else if (item.path) {
      return item.path;
    }
  }
  return null;
}

/**
 * The keys of the groups enclosing `key`, outermost first; null when `key` is not in the tree.
 */
function parentKeys(items, key, trail = []) {
  if (!key) return null;
  for (const item of items || []) {
    if (item.key === key) return trail;
    if (item.children && item.children.length > 0) {
      const found = parentKeys(item.children, key, [...trail, item.key]);
      if (found) return found;
    }
  }
  return null;
}

/**
 * Walk the item tree to find which key matches the current URL prefix.
 */
// The leaf whose path is the longest one the URL sits at or under, so `/admin/tenants/x` selects
// Tenants rather than the `/admin` dashboard (W-73.2).
function findSelectedKey(items, pathname) {
  let best = null;
  let bestLength = -1;
  const visit = (list) => {
    for (const item of list || []) {
      if (item.children && item.children.length > 0) {
        visit(item.children);
      } else if (item.path && pathMatches(item.path, pathname) && item.path.length > bestLength) {
        best = item.key;
        bestLength = item.path.length;
      }
    }
  };
  visit(items);
  return best;
}

function pathMatches(path, pathname) {
  if (path === '/') return pathname === '/';
  return pathname === path || pathname.startsWith(path.endsWith('/') ? path : `${path}/`);
}
