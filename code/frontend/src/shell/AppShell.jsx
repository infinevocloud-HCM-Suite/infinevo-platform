import React from 'react';
import PropTypes from 'prop-types';
import { Layout, Menu, Skeleton, Typography, Button, Result, Drawer, Grid, theme as antdTheme } from 'antd';
import { MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import { Link, Navigate, Routes, Route, useLocation, useNavigate } from 'react-router-dom';
import { useNavigation } from './navigation/useNavigation.js';
import { navLabel } from './navigation/navLabels.js';
import { navIcon } from './navigation/navIcons.jsx';
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
import './phone.css';

const { Sider, Content } = Layout;

/** W-83.5: widest the page content gets, in px. */
export const CONTENT_MAX_WIDTH = 1440;
/** W-83.7: the side menu collapsed to icons, in px. */
export const SIDER_COLLAPSED_WIDTH = 64;

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
 * The portal's profile panel (D-75), which the header's user menu opens as My profile whenever the
 * feed names the self-service portal item - the server shows that item only to a caller with a
 * linked employee record, so the header link and the menu item agree. The shell reads it from the
 * feed it already holds: a second feed hook in the header would refetch the feed twice on every
 * tenant change.
 */
const MY_PROFILE_PATH = '/me/profile';
const PORTAL_KEY = 'core.me';
const PORTAL_PATH = '/me';

function hasPortalItem(items) {
  return (items || []).some(
    (item) => item.key === PORTAL_KEY || item.path === PORTAL_PATH || hasPortalItem(item.children),
  );
}

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
  const ownsProfile = hasPortalItem(items);

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

  // A phone is below the md breakpoint (W-80.1). Until the breakpoints answer, `md` is undefined
  // and the screen counts as a desktop, so a desktop never flashes the phone layout.
  const screens = Grid.useBreakpoint();
  const phone = screens.md === false;
  const [menuOpen, setMenuOpen] = React.useState(false);
  React.useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname, phone]);

  // W-83.7: the side menu collapses to icons. The choice lasts for the page: browser storage is
  // off limits (W-45 §5b), and a server-side preference is its own ticket.
  const [collapsed, setCollapsed] = React.useState(false);
  const onCollapse = (next) => setCollapsed(next);
  // The phone drawer always shows labels.
  const iconsOnly = collapsed && !phone;
  const currentSiderWidth = iconsOnly ? SIDER_COLLAPSED_WIDTH : siderWidth;

  const navigation = (
    <>
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
        {/* W-83.5: the product name alone, level with the header; W-83.7: a monogram when collapsed,
            and the collapse button up here, in the flow, so it never covers the last menu item */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: token.marginXS }}>
          <Typography.Text
            strong
            ellipsis
            data-testid="brand"
            style={{ color: token.colorText, fontSize: token.fontSizeLG, lineHeight: 1.3, textAlign: iconsOnly ? 'center' : 'start' }}
          >
            {iconsOnly ? 'IH' : 'Infinevo HCM Suite'}
          </Typography.Text>
          {!phone && !iconsOnly && (
            <Button
              type="text"
              size="small"
              aria-label="Collapse menu"
              data-testid="sider-trigger"
              icon={<MenuFoldOutlined />}
              onClick={() => onCollapse(true)}
            />
          )}
        </div>
        {iconsOnly && (
          <Button
            type="text"
            size="small"
            aria-label="Expand menu"
            data-testid="sider-trigger"
            icon={<MenuUnfoldOutlined />}
            onClick={() => onCollapse(false)}
            style={{ alignSelf: 'center', marginTop: token.marginXXS }}
          />
        )}
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
          theme="light"
          mode="inline"
          selectedKeys={selectedKey ? [selectedKey] : []}
          // Collapsed, groups open as hover popups; controlling them would pin one open after a pick.
          openKeys={iconsOnly ? undefined : openKeys}
          onOpenChange={setOpenKeys}
          // A pick closes the phone drawer even when it is the screen already open.
          onClick={() => setMenuOpen(false)}
          items={menuItems}
          style={{ borderRight: 0, marginTop: token.sizeUnit * 2 }}
        />
      )}
    </>
  );

  return (
    <ShellBoundary>
      <Layout style={{ minHeight: '100vh' }}>
        {phone ? (
          <Drawer
            placement="left"
            open={menuOpen}
            onClose={() => setMenuOpen(false)}
            width={siderWidth}
            closable={false}
            rootClassName="shell-nav-drawer"
            styles={{ body: { padding: 0, background: theme.components.Layout.siderBg } }}
          >
            {navigation}
          </Drawer>
        ) : (
          <Sider
            width={siderWidth}
            theme="light"
            collapsible
            collapsed={iconsOnly}
            onCollapse={onCollapse}
            collapsedWidth={SIDER_COLLAPSED_WIDTH}
            trigger={null}
            style={{
              position: 'fixed',
              left: 0,
              top: 0,
              bottom: 0,
              overflowY: 'auto',
              zIndex: 100,
              borderRight: `1px solid ${token.colorBorderSecondary}`,
            }}
          >
            {navigation}
          </Sider>
        )}

        <Layout style={{ marginLeft: phone ? 0 : currentSiderWidth }}>
          <Header
            tenantName={tenantName}
            tenantLogoUrl={tenantLogoUrl}
            tagline={tagline}
            onGettingStarted={() => navigate(WELCOME_PATH)}
            onMyProfile={ownsProfile ? () => navigate(MY_PROFILE_PATH) : undefined}
            compact={phone}
            onOpenMenu={phone ? () => setMenuOpen(true) : undefined}
          />
          <Content style={{ padding: phone ? token.paddingSM : token.paddingLG, background: token.colorBgLayout, minHeight: `calc(100vh - ${headerHeight}px)` }}>
            {/* W-83.5: content stops at 1440 px so columns do not spread across a wide screen */}
            <div style={{ maxWidth: CONTENT_MAX_WIDTH, margin: '0 auto' }}>
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
              ) : feedHomePath && feedHomePath.startsWith('/me') ? (
                <RootRedirect homePath={feedHomePath} me={me} />
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
                    <Route path="*" element={<NotFound homePath={homePath || '/'} />} />
                  </Routes>
                </React.Suspense>
              </ModuleEmployeeTabsProvider>
            )}
            </div>
          </Content>
        </Layout>
      </Layout>
    </ShellBoundary>
  );
}

/**
 * Convert navigation items from the feed into Ant Design Menu item descriptors.
 * The feed carries only label keys and paths; `navLabel` turns a key into words and, for a top-level
 * item, `navIcon` picks the line icon (W-83.7).
 */
function buildMenuItems(items, topLevel = true) {
  if (!items || items.length === 0) return [];
  return items.map((item) => {
    if (item.children && item.children.length > 0) {
      return {
        key: item.key,
        label: navLabel(item.labelKey),
        ...(topLevel ? { icon: navIcon(item.labelKey) } : {}),
        children: buildMenuItems(item.children, false),
      };
    }
    return {
      key: item.key,
      ...(topLevel ? { icon: navIcon(item.labelKey) } : {}),
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
