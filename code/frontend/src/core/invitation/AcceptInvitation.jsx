import { useState, useEffect } from 'react';
import { Card, Button, Input, Form, Typography, Space, Alert, Result } from 'antd';
import { publicInvitationService } from './publicInvitationService.js';

const { Title, Paragraph } = Typography;
const { TextArea } = Input;

const GENERIC_409_MESSAGE =
  'This invitation cannot be used. It may have expired, been used or been withdrawn. Ask for a new invitation.';
const SERVICE_503_MESSAGE = 'Try again in a few minutes';

/**
 * The realm's password policy, as words (infra/keycloak/infinevo-realm.json `passwordPolicy`). The server
 * is the judge: Keycloak applies the policy when the password is set and its 400 names the rule that
 * failed (D-88). This text only tells the invitee what to aim for before they submit.
 */
export const PASSWORD_RULES =
  'At least 10 characters, with an upper-case letter, a lower-case letter, a digit and a symbol. ' +
  'It must not contain your email address.';

/** The one rule checked here before the request: the shortest password the realm accepts. */
export const PASSWORD_MIN_LENGTH = 10;

/**
 * What the invitee does next, by the accept reply's `outcome` (D-62, D-88). The password is chosen on this
 * page and set by the accept call, so there is no second email and nobody is sent to the sign-in screen
 * without a password.
 */
export const ACCEPTED_NEXT_STEP = {
  PASSWORD_SET: {
    title: 'Your password is set',
    text:
      "You're in. Sign in with this email address and the password you just chose. If this browser is " +
      'signed in to Infinevo as someone else, sign out first or use a private window.',
  },
  EXISTING_ACCOUNT: {
    title: 'Invitation accepted',
    text:
      'You already have an Infinevo account, so its password is unchanged. Sign in with it, or choose ' +
      '"Forgot password?" on the sign-in page if you no longer have it.',
  },
};

const ACCEPTED_FALLBACK = ACCEPTED_NEXT_STEP.PASSWORD_SET;

export function AcceptInvitation() {
  const [token] = useState(() => {
    if (typeof window === 'undefined') return '';
    try {
      const params = new URLSearchParams(window.location.search);
      return params.get('token') || '';
    } catch {
      return '';
    }
  });

  // Remove token from address bar on mount
  useEffect(() => {
    if (typeof window !== 'undefined' && window.location.search) {
      window.history.replaceState({}, document.title, window.location.pathname);
    }
  }, []);

  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordError, setPasswordError] = useState(null);

  const [accepting, setAccepting] = useState(false);
  const [declining, setDeclining] = useState(false);
  const [showDeclineForm, setShowDeclineForm] = useState(false);
  const [declineReason, setDeclineReason] = useState('');
  const [declineError, setDeclineError] = useState(null);

  const [status, setStatus] = useState('idle'); // 'idle' | 'accepted' | 'declined' | 'error'
  const [outcome, setOutcome] = useState(null);
  const [errorMessage, setErrorMessage] = useState(null);

  const formatError = (err) => {
    if (err?.status === 409 || err?.code === 'CONFLICT') {
      return GENERIC_409_MESSAGE;
    }
    if (err?.status === 503 || err?.code === 'INTERNAL') {
      return SERVICE_503_MESSAGE;
    }
    return err?.message || GENERIC_409_MESSAGE;
  };

  /** The checks worth making before the request; the realm policy itself is applied by the server. */
  const localPasswordError = () => {
    if (!password) return 'Choose a password';
    if (password.length < PASSWORD_MIN_LENGTH) return `Use at least ${PASSWORD_MIN_LENGTH} characters`;
    if (password !== confirmPassword) return 'The two passwords do not match';
    return null;
  };

  const handleAccept = async () => {
    if (!token) {
      setStatus('error');
      setErrorMessage(GENERIC_409_MESSAGE);
      return;
    }

    const local = localPasswordError();
    if (local) {
      setPasswordError(local);
      return;
    }

    setAccepting(true);
    setErrorMessage(null);
    setPasswordError(null);
    try {
      const reply = await publicInvitationService.accept(token, password);
      setOutcome(reply?.outcome ?? null);
      setStatus('accepted');
    } catch (err) {
      // A refused password (D-88) is shown beside the field, in the realm's own words; the invitation is
      // still usable, so the page stays on the form rather than ending in the error state.
      if (err?.status === 400 || err?.code === 'VALIDATION_FAILED') {
        setPasswordError(err?.message || 'The password does not meet the password policy');
      } else {
        setStatus('error');
        setErrorMessage(formatError(err));
      }
    } finally {
      setAccepting(false);
    }
  };

  const handleDeclineSubmit = async () => {
    const trimmed = declineReason.trim();
    if (!trimmed) {
      setDeclineError('Reason must not be blank');
      return;
    }
    if (trimmed.length > 500) {
      setDeclineError('Reason cannot exceed 500 characters');
      return;
    }

    if (!token) {
      setStatus('error');
      setErrorMessage(GENERIC_409_MESSAGE);
      return;
    }

    setDeclining(true);
    setDeclineError(null);
    setErrorMessage(null);
    try {
      await publicInvitationService.decline(token, trimmed);
      setStatus('declined');
    } catch (err) {
      setStatus('error');
      setErrorMessage(formatError(err));
    } finally {
      setDeclining(false);
    }
  };

  if (status === 'accepted') {
    const next = ACCEPTED_NEXT_STEP[outcome] ?? ACCEPTED_FALLBACK;
    return (
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: '100vh',
          backgroundColor: '#f5f5f5',
          padding: 16,
        }}
      >
        <Card style={{ maxWidth: 520, width: '100%', textAlign: 'center', boxShadow: '0 4px 12px rgba(0,0,0,0.08)' }}>
          <Result
            status="success"
            title={next.title}
            subTitle={next.text}
            extra={[
              <Button type="primary" key="signin" id="btn-sign-in" href="/">
                Sign in
              </Button>,
            ]}
          />
        </Card>
      </div>
    );
  }

  if (status === 'declined') {
    return (
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: '100vh',
          backgroundColor: '#f5f5f5',
          padding: 16,
        }}
      >
        <Card style={{ maxWidth: 520, width: '100%', textAlign: 'center', boxShadow: '0 4px 12px rgba(0,0,0,0.08)' }}>
          <Result
            status="info"
            title="Invitation Declined"
            subTitle="Declined."
          />
        </Card>
      </div>
    );
  }

  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '100vh',
        backgroundColor: '#f5f5f5',
        padding: 16,
      }}
    >
      <Card
        style={{
          maxWidth: 520,
          width: '100%',
          boxShadow: '0 4px 12px rgba(0,0,0,0.08)',
          borderRadius: 8,
        }}
      >
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <Title level={3} style={{ marginBottom: 8 }}>
            Platform Invitation
          </Title>
          <Paragraph type="secondary">
            You have been invited to join the Infinevo HCM platform. Choose a password to accept, or decline below.
          </Paragraph>
          <Alert
            type="info"
            showIcon
            id="alert-other-account"
            style={{ textAlign: 'left' }}
            message="Signed in to Infinevo as someone else in this browser? Sign out first, or open this link in a private window."
          />
        </div>

        {errorMessage && (
          <Alert
            type="error"
            message={errorMessage}
            showIcon
            style={{ marginBottom: 20 }}
            id="alert-error-message"
          />
        )}

        {!token && !errorMessage && (
          <Alert
            type="warning"
            message={GENERIC_409_MESSAGE}
            showIcon
            style={{ marginBottom: 20 }}
            id="alert-missing-token"
          />
        )}

        {!showDeclineForm ? (
          <Form layout="vertical" onFinish={handleAccept}>
            <Form.Item
              label="Choose a password"
              extra={PASSWORD_RULES}
              validateStatus={passwordError ? 'error' : undefined}
              help={passwordError || undefined}
              style={{ marginBottom: 16 }}
            >
              <Input.Password
                id="input-password"
                autoComplete="new-password"
                value={password}
                disabled={!token}
                onChange={(e) => {
                  setPassword(e.target.value);
                  if (passwordError) setPasswordError(null);
                }}
              />
            </Form.Item>
            <Form.Item label="Confirm password" style={{ marginBottom: 20 }}>
              <Input.Password
                id="input-confirm-password"
                autoComplete="new-password"
                value={confirmPassword}
                disabled={!token}
                onChange={(e) => {
                  setConfirmPassword(e.target.value);
                  if (passwordError) setPasswordError(null);
                }}
              />
            </Form.Item>
            <Space orientation="vertical" style={{ width: '100%' }} size="middle">
              <Button
                type="primary"
                size="large"
                block
                htmlType="submit"
                id="btn-accept-invitation"
                loading={accepting}
                disabled={!token}
              >
                Set password and accept
              </Button>
              <Button
                size="large"
                block
                danger
                id="btn-show-decline"
                disabled={!token}
                onClick={() => {
                  setShowDeclineForm(true);
                  setDeclineError(null);
                }}
              >
                Decline Invitation
              </Button>
            </Space>
          </Form>
        ) : (
          <div>
            <Title level={5} style={{ marginBottom: 8 }}>
              Reason for declining
            </Title>
            <Paragraph type="secondary" style={{ fontSize: 13, marginBottom: 12 }}>
              Please provide a reason why you are declining this invitation (required, maximum 500 characters).
            </Paragraph>

            {declineError && (
              <Alert
                type="error"
                message={declineError}
                showIcon
                style={{ marginBottom: 12 }}
                id="alert-decline-error"
              />
            )}

            <Form layout="vertical">
              <Form.Item style={{ marginBottom: 16 }}>
                <TextArea
                  rows={4}
                  id="textarea-decline-reason"
                  placeholder="Enter reason for declining..."
                  value={declineReason}
                  maxLength={500}
                  showCount
                  onChange={(e) => {
                    setDeclineReason(e.target.value);
                    if (declineError) setDeclineError(null);
                  }}
                />
              </Form.Item>

              <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
                <Space>
                  <Button
                    id="btn-cancel-decline"
                    onClick={() => {
                      setShowDeclineForm(false);
                      setDeclineError(null);
                    }}
                  >
                    Back
                  </Button>
                  <Button
                    type="primary"
                    danger
                    id="btn-confirm-decline"
                    loading={declining}
                    onClick={handleDeclineSubmit}
                  >
                    Confirm Decline
                  </Button>
                </Space>
              </Form.Item>
            </Form>
          </div>
        )}
      </Card>
    </div>
  );
}
