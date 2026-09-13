import { Layout, Typography } from 'antd';

const { Header, Content } = Layout;

/**
 * Layout and navigation. Navigation is driven by entitlement (W-11), so this is the one
 * place allowed to import from every module - see the eslint override.
 *
 * Placeholder until W-11. It renders, which is all W-01 claims.
 */
export function AppShell() {
  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header>
        <Typography.Title level={4} style={{ color: '#fff', margin: 0, lineHeight: '64px' }}>
          Infinevo
        </Typography.Title>
      </Header>
      <Content style={{ padding: 24 }}>
        <Typography.Paragraph>
          Platform skeleton. Modules register their routes here as they are built.
        </Typography.Paragraph>
      </Content>
    </Layout>
  );
}
