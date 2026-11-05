import { useContext, useEffect, useRef, useState } from 'react';
import PropTypes from 'prop-types';
import { ReactReduxContext } from 'react-redux';
import { Layout, Button, Space, Typography, Alert, Avatar, Dropdown, Tag, theme } from 'antd';
import { CompassOutlined, DownOutlined, IdcardOutlined, LogoutOutlined, MenuOutlined, UserOutlined } from '@ant-design/icons';
import { keycloak, logout } from './auth/keycloak.js';
import { useMe, fetchMe } from './auth/useMe.js';
import { roleLabel } from './auth/roleLabels.js';
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

/** "Acme Ltd" → "AL", "Infinevo" → "I": the first letter of up to two words (W-73.1 §5). */
export function initialsOf(name) {
  if (!name || typeof name !== 'string') return '';
  return name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word.charAt(0).toUpperCase())
    .join('');
}

/**
 * Shell header (W-45 §5, W-73.1 §5).
 *
 * Left: the company's logo - or a circle with its initials, never an empty box - its name and,
 * when one is set, its tagline. Right: the signed-in user's name (from `/me`, falling back to the
 * token's), one chip per role, and a user menu with My profile (D-75, when the shell passes
 * `onMyProfile` - it does when the feed names the self-service portal item), Getting started (W-73.8, when
 * the shell passes `onGettingStarted`) and Sign out.
 *
 * While platform staff act inside a customer tenant (W-65.3) it also:
 *   - shows the banner "Acting as {userLabel} in {tenantName} until {expiresAt}" with Stop
 *   - wires the API client to the store: `X-Impersonation` from the session, and a
 *     `403 IMPERSONATION_INVALID` clears it
 *   - refetches the navigation feed and `/me` whenever a session starts or stops, since both are
 *     the target's while acting and the staff member's otherwise
 *
 * On a phone (W-80.1) the shell passes `compact` - no tagline, role chips or user name, a smaller
 * logo - and `onOpenMenu`, which puts a menu button first, since the side menu is a drawer there.
 */
export function Header({
  style,
  tenantName,
  tenantLogoUrl,
  tagline,
  onGettingStarted,
  onMyProfile,
  compact = false,
  onOpenMenu,
}) {
  const { token } = theme.useToken();
  const store = useContext(ReactReduxContext)?.store ?? null;
  const session = useImpersonationSession(store);
  const me = useMe();
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
    fetchMe().catch(() => {});
  }, [sessionId]);

  // A signed logo link that stops working (it lives a day; the page may be open longer) asks the
  // feed for a fresh one - once per tenant and act-as session, not once per link. Every refetch
  // signs a new link, so keying the retry on the link would refetch forever while the logo itself
  // is broken (a missing blob, storage down). After the one retry a failing logo shows the
  // initials (spec §9).
  const [logoFailed, setLogoFailed] = useState(false);
  const logoRetriedFor = useRef(null);
  const brandingKey = `${tenantName ?? ''}|${sessionId ?? ''}`;
  useEffect(() => {
    setLogoFailed(false);
  }, [tenantLogoUrl]);
  const onLogoError = () => {
    setLogoFailed(true);
    if (logoRetriedFor.current !== brandingKey) {
      logoRetriedFor.current = brandingKey;
      fetchNavigationFeed().catch(() => {});
    }
  };

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
    me.displayName ||
    keycloak?.tokenParsed?.name ||
    keycloak?.tokenParsed?.preferred_username ||
    '';
  const roles = Array.isArray(me.roles) ? me.roles : [];
  const initials = initialsOf(tenantName);
  const showLogo = !!tenantLogoUrl && !logoFailed;

  const userMenu = {
    items: [
      ...(tagline && !compact
        ? [
            {
              key: 'tagline',
              disabled: true,
              label: (
                <Text type="secondary" data-testid="tenant-tagline" style={{ fontSize: token.fontSizeSM, display: 'block' }}>
                  {tagline}
                </Text>
              ),
            },
          ]
        : []),
      ...(roles.length > 0 && !compact
        ? [
            {
              key: 'roles',
              disabled: true,
              label: (
                <Space size={4} wrap data-testid="role-chips" style={{ padding: `${token.paddingXXS}px 0` }}>
                  {roles.map((code) => (
                    <Tag key={code} color="blue" style={{ marginInlineEnd: 0 }}>
                      {roleLabel(code)}
                    </Tag>
                  ))}
                </Space>
              ),
            },
          ]
        : []),
      ...((tagline && !compact) || (roles.length > 0 && !compact) ? [{ type: 'divider' }] : []),
      ...(onMyProfile ? [{ key: 'profile', label: 'My profile', icon: <IdcardOutlined /> }] : []),
      ...(onGettingStarted ? [{ key: 'welcome', label: 'Getting started', icon: <CompassOutlined /> }] : []),
      { key: 'signout', label: 'Sign out', icon: <LogoutOutlined /> },
    ],
    onClick: ({ key }) => {
      if (key === 'profile') onMyProfile();
      if (key === 'welcome') onGettingStarted();
      if (key === 'signout') logout();
    },
  };

  return (
    <>
      <AntHeader
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 99,
          background: token.colorBgContainer,
          padding: `0 ${compact ? token.padding : token.paddingLG}px`,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: token.marginSM,
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
          boxShadow: token.boxShadowSecondary,
          ...style,
        }}
      >
        {onOpenMenu && (
          <Button
            type="text"
            aria-label="Open menu"
            data-testid="open-menu"
            icon={<MenuOutlined />}
            onClick={onOpenMenu}
          />
        )}
        {tenantName ? (
          // A plain flex row, not a Space: a Space item never shrinks, so a long name could not be
          // cut short on a phone (W-80.1).
          <div
            data-testid="tenant-display"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: compact ? token.marginXS : token.margin,
              ...(compact ? { flex: 1, minWidth: 0 } : {}),
            }}
          >
            {showLogo ? (
              <img
                src={tenantLogoUrl}
                alt={`${tenantName} logo`}
                data-testid="tenant-logo"
                onError={onLogoError}
                style={{ height: token.controlHeightLG, maxWidth: compact ? 96 : 160, objectFit: 'contain', display: 'block', flexShrink: 0 }}
              />
            ) : (
              <Avatar
                size="large"
                data-testid="tenant-initials"
                style={{ background: token.colorPrimary, color: token.colorBgContainer, fontWeight: 600, flexShrink: 0 }}
              >
                {initials}
              </Avatar>
            )}
            <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3, minWidth: 0 }}>
              <Text strong ellipsis={compact} style={{ fontSize: compact ? token.fontSize : token.fontSizeLG }}>
                {tenantName}
              </Text>
            </div>
          </div>
        ) : (
          <span />
        )}
        <Space size="middle" align="center">
          <Dropdown menu={userMenu} trigger={['click']} placement="bottomRight">
            <Button type="text" aria-label="User menu" data-testid="user-display">
              <Space size="small">
                <UserOutlined style={{ color: token.colorTextSecondary }} />
                {displayName && !compact && <Text strong>{displayName}</Text>}
                <DownOutlined style={{ color: token.colorTextSecondary, fontSize: token.fontSizeSM }} />
              </Space>
            </Button>
          </Dropdown>
        </Space>
      </AntHeader>
      {session && (
        <Alert
          banner
          type="warning"
          data-testid="impersonation-banner"
          style={{
            position: 'sticky',
            top: appTheme.components.Layout.headerHeight,
            zIndex: 98,
            padding: `${token.paddingXS}px ${token.padding}px`,
          }}
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
  tenantLogoUrl: PropTypes.string,
  tagline: PropTypes.string,
  onGettingStarted: PropTypes.func,
  onMyProfile: PropTypes.func,
  compact: PropTypes.bool,
  onOpenMenu: PropTypes.func,
};
