import { useContext, useEffect, useRef, useState } from 'react';
import PropTypes from 'prop-types';
import { ReactReduxContext } from 'react-redux';
import { Layout, Button, Space, Typography, Alert, theme } from 'antd';
import { BankOutlined, LogoutOutlined, UserOutlined } from '@ant-design/icons';
import { keycloak, logout } from './auth/keycloak.js';
import { fetchNavigationFeed } from './navigation/useNavigation.js';
import { setImpersonationProvider, setImpersonationInvalidHandler } from '../shared/api/client.js';
import { errorMsg } from '../shared/ui/msgHelper.js';
import { theme as appTheme } from '../shared/theme.js';
import { impersonationService } from '../core/admin/impersonationService.js';
import { useImpersonationSession } from './useImpersonationSession.js';

const { Header: AntHeader } = Layout;
const { Text } = Typography;

/** Banner time: the session's expiry in the browser's clock, hours and minutes. */
function formatExpiry(expiresAt) {
  if (!expiresAt) return '';
  const date = new Date(expiresAt);
  return Number.isNaN(date.getTime())
    ? String(expiresAt)
    : date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

/**
 * Shell header (W-45 §5).
 * Shows the signed-in tenant's name, the authenticated user's name (falling back to username)
 * and a working logout button.
 *
 * While platform staff act inside a customer tenant (W-65.3) it also:
 *   - shows the banner "Acting as {userLabel} in {tenantName} until {expiresAt}" with Stop
 *   - wires the API client to the store: `X-Impersonation` from the session, and a
 *     `403 IMPERSONATION_INVALID` clears it
 *   - refetches the navigation feed whenever a session starts or stops, since the feed is the
 *     target's while acting and the staff member's otherwise
 */
export function Header({ style, tenantName }) {
  const { token } = theme.useToken();
  const store = useContext(ReactReduxContext)?.store ?? null;
  const session = useImpersonationSession(store);
  const [stopping, setStopping] = useState(false);

  useEffect(() => {
    if (!store) return undefined;
    setImpersonationProvider(() => store.getState().impersonation?.session?.sessionId ?? null);
    setImpersonationInvalidHandler(() => {
      if (!store.getState().impersonation?.session) return;
      store.dispatch({ type: 'impersonation/stopped' });
      errorMsg('Impersonation ended', 'The session expired or was closed. You are back in your own tenant.');
    });
    return () => {
      setImpersonationProvider(null);
      setImpersonationInvalidHandler(null);
    };
  }, [store]);

  const sessionId = session?.sessionId ?? null;
  const previousSessionId = useRef(sessionId);
  useEffect(() => {
    if (previousSessionId.current === sessionId) return;
    previousSessionId.current = sessionId;
    fetchNavigationFeed().catch(() => {});
  }, [sessionId]);

  const stop = async () => {
    setStopping(true);
    try {
      await impersonationService.close(session.sessionId);
    } catch (err) {
      // An expired or already-closed session is still over for this tab.
      if (err?.status !== 404 && err?.code !== 'IMPERSONATION_INVALID') errorMsg(err);
    } finally {
      store?.dispatch({ type: 'impersonation/stopped' });
      setStopping(false);
    }
  };

  const displayName =
    keycloak?.tokenParsed?.name ||
    keycloak?.tokenParsed?.preferred_username ||
    '';

  return (
    <>
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
          {tenantName && (
            <Space size="small" data-testid="tenant-display">
              <BankOutlined style={{ color: token.colorTextSecondary }} />
              <Text>{tenantName}</Text>
            </Space>
          )}
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
      {session && (
        <Alert
          banner
          type="warning"
          data-testid="impersonation-banner"
          style={{ position: 'sticky', top: appTheme.components.Layout.headerHeight, zIndex: 98 }}
          message={`Acting as ${session.userLabel} in ${session.tenantName} until ${formatExpiry(session.expiresAt)}`}
          action={
            <Button size="small" danger loading={stopping} onClick={stop}>
              Stop
            </Button>
          }
        />
      )}
    </>
  );
}

Header.propTypes = {
  style: PropTypes.object,
  tenantName: PropTypes.string,
};
