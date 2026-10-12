import { useState, useEffect } from 'react';
import { Card, Button, Input, Form, Typography, Space, Alert, Result } from 'antd';
import { publicInvitationService } from './publicInvitationService.js';

const { Title, Paragraph } = Typography;
const { TextArea } = Input;

const GENERIC_409_MESSAGE =
  'This invitation cannot be used. It may have expired, been used or been withdrawn. Ask for a new invitation.';
const SERVICE_503_MESSAGE = 'Try again in a few minutes';

/**
 * What the invitee does next, by the accept reply's `outcome` (D-62). Nobody is sent to the sign-in
 * screen before they have a password, or told to wait for a mail that will not come.
 */
export const ACCEPTED_NEXT_STEP = {
  SET_PASSWORD_EMAIL_SENT: {
    title: 'Check your email',
    text:
      "You're in. We've sent you a second email with a link to set your password. " +
      'Open it, choose a password, and you will be taken to sign in. If this browser is signed in to ' +
      'Infinevo as someone else, open the email link in a private window.',
    showSignIn: false,
  },
  SET_PASSWORD_EMAIL_FAILED: {
    title: 'Your account is ready',
    text:
      "We couldn't send the email to set your password. On the sign-in page, choose " +
      '"Forgot password?" and enter this email address to get the link.',
    showSignIn: true,
  },
  EXISTING_ACCOUNT: {
    title: 'Invitation accepted',
    text: 'You already have an Infinevo account. Sign in with your existing password.',
    showSignIn: true,
  },
};

const ACCEPTED_FALLBACK = ACCEPTED_NEXT_STEP.SET_PASSWORD_EMAIL_SENT;

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

  const handleAccept = async () => {
    if (!token) {
      setStatus('error');
      setErrorMessage(GENERIC_409_MESSAGE);
      return;
    }

    setAccepting(true);
    setErrorMessage(null);
    try {
      const reply = await publicInvitationService.accept(token);
      setOutcome(reply?.outcome ?? null);
      setStatus('accepted');
    } catch (err) {
      setStatus('error');
      setErrorMessage(formatError(err));
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
            extra={
              next.showSignIn
                ? [
                    <Button type="primary" key="signin" id="btn-sign-in" href="/">
                      Sign in
                    </Button>,
                  ]
                : [
                    <Paragraph key="later" type="secondary" style={{ marginBottom: 0 }}>
                      Already set your password? <a href="/">Sign in</a>
                    </Paragraph>,
                  ]
            }
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
            You have been invited to join the Infinevo HCM platform. Please accept or decline below.
          </Paragraph>
          <Alert
            type="info"
            showIcon
            id="alert-other-account"
            style={{ textAlign: 'left' }}
            message="Signed in to Infinevo as someone else in this browser? Sign out first, or open this link and the password email in a private window."
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
          <Space orientation="vertical" style={{ width: '100%' }} size="middle">
            <Button
              type="primary"
              size="large"
              block
              id="btn-accept-invitation"
              loading={accepting}
              disabled={!token}
              onClick={handleAccept}
            >
              Accept Invitation
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
