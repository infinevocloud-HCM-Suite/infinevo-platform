import { Layout, Menu, Skeleton, Typography } from 'antd';
import { Link, Routes, Route, useLocation } from 'react-router-dom';
import { useNavigation } from './navigation/useNavigation.js';
import { routesFromFeed } from './routes.js';

const { Sider, Header, Content } = Layout;

/**
 * Layout and navigation shell (W-12.3 §5).
 *
 * Navigation is driven entirely by the server-returned feed:
 *   GET /api/v1/navigation → { items, actions }
 *
 * The shell renders exactly what the server says. There is no static menu
 * array anywhere here — that is the Payroll pattern this ticket removes.
 * An empty feed → empty sidebar, not a default set of items.
 *
 * Renders a skeleton sidebar while loading so the layout does not flicker.
 */
export function AppShell() {
  const { items, loading } = useNavigation();
  const location = useLocation();

  const menuItems = buildMenuItems(items);
  const selectedKey = findSelectedKey(items, location.pathname);
  // Only register routes whose path is in the navigation feed — a route not in the
  // feed is not registered at all (W-12.3 §5). An empty feed → empty route tree.
  const feedRoutes = routesFromFeed(items);

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        width={220}
        theme="dark"
        collapsible={false}
        style={{ position: 'fixed', left: 0, top: 0, bottom: 0, overflowY: 'auto', zIndex: 100 }}
      >
        <div
          style={{
            height: 64,
            display: 'flex',
            alignItems: 'center',
            padding: '0 20px',
            borderBottom: '1px solid rgba(255,255,255,0.1)',
          }}
        >
          <Typography.Text strong style={{ color: '#fff', fontSize: 18, letterSpacing: 1 }}>
            Infinevo
          </Typography.Text>
        </div>

        {loading ? (
          <div style={{ padding: '16px 20px' }}>
            {[...Array(5)].map((_, i) => (
              <Skeleton
                key={i}
                active
                title={{ width: '80%' }}
                paragraph={false}
                style={{ marginBottom: 12 }}
              />
            ))}
          </div>
        ) : (
          <Menu
            theme="dark"
            mode="inline"
            selectedKeys={selectedKey ? [selectedKey] : []}
            items={menuItems}
            style={{ borderRight: 0, marginTop: 8 }}
          />
        )}
      </Sider>

      <Layout style={{ marginLeft: 220 }}>
        <Header
          style={{
            position: 'sticky',
            top: 0,
            zIndex: 99,
            background: '#fff',
            padding: '0 24px',
            display: 'flex',
            alignItems: 'center',
            borderBottom: '1px solid #f0f0f0',
            boxShadow: '0 1px 4px rgba(0,0,0,0.06)',
          }}
        />
        <Content style={{ padding: 24, background: '#f5f5f5', minHeight: 'calc(100vh - 64px)' }}>
          {/* Routes registered from the server feed only — no static route array anywhere (W-12.3 §5) */}
          <Routes>
            {feedRoutes.map((route) => (
              <Route key={route.path} path={route.path} element={route.element} />
            ))}
          </Routes>
        </Content>
      </Layout>
    </Layout>
  );
}

/**
 * Convert navigation items from the feed into Ant Design Menu item descriptors.
 * No icon keys are emitted — the feed carries only label keys and paths.
 */
function buildMenuItems(items) {
  if (!items || items.length === 0) return [];
  return items.map((item) => {
    if (item.children && item.children.length > 0) {
      return {
        key: item.key,
        label: item.labelKey,
        children: buildMenuItems(item.children),
      };
    }
    return {
      key: item.key,
      label: <Link to={item.path}>{item.labelKey}</Link>,
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
