import PropTypes from 'prop-types';
import { Layout, Button, Space, Typography, theme } from 'antd';
import { LogoutOutlined, UserOutlined } from '@ant-design/icons';
import { keycloak, logout } from './auth/keycloak.js';

const { Header: AntHeader } = Layout;
const { Text } = Typography;

/**
 * Shell header (W-45 §5).
 * Shows the authenticated user's name (falling back to username) and a working logout button.
 */
export function Header({ style }) {
  const { token } = theme.useToken();

  const displayName =
    keycloak?.tokenParsed?.name ||
    keycloak?.tokenParsed?.preferred_username ||
    '';

  return (
    <AntHeader
      style={{
        position: 'sticky',
        top: 0,
        zIndex: 99,
        background: token.colorBgContainer,
        padding: `0 ${token.paddingLG}px`,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'flex-end',
        borderBottom: `1px solid ${token.colorBorderSecondary}`,
        boxShadow: token.boxShadowSecondary,
        ...style,
      }}
    >
      <Space size="middle">
        {displayName && (
          <Space size="small" data-testid="user-display">
            <UserOutlined style={{ color: token.colorTextSecondary }} />
            <Text strong>{displayName}</Text>
          </Space>
        )}
        <Button
          type="text"
          icon={<LogoutOutlined />}
          onClick={() => logout()}
          aria-label="Logout"
        >
          Logout
        </Button>
      </Space>
    </AntHeader>
  );
}

Header.propTypes = {
  style: PropTypes.object,
};
