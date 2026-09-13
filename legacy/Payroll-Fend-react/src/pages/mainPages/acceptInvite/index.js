import React, { useState, useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { getInvitationDetails, processInvitation } from '../../../shared/services/invitationService';
import './acceptInvite.css';

const AcceptInvite = () => {
  const location = useLocation();
  const navigate = useNavigate();
  
  const [queryParams, setQueryParams] = useState({
    user: '',
    type: '',
    orgId: ''
  });
  
  const [invitationData, setInvitationData] = useState(null);
  const [inviteStatus, setInviteStatus] = useState('pending'); // pending, accepted, declined
  const [showReasonModal, setShowReasonModal] = useState(false);
  const [declineReason, setDeclineReason] = useState('');
  const [loading, setLoading] = useState(false);
  const [fetchingDetails, setFetchingDetails] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');

  // Parse query parameters from URL and fetch invitation details
  useEffect(() => {
    const fetchInvitation = async () => {
      const searchParams = new URLSearchParams(location.search);
      const user = searchParams.get('user');
      const type = searchParams.get('type');
      const orgId = searchParams.get('orgId');

      if (user && type && orgId) {
        const params = {
          user: decodeURIComponent(user),
          type: decodeURIComponent(type),
          orgId: decodeURIComponent(orgId)
        };
        setQueryParams(params);
        
        // Fetch invitation details from API
        try {
          setFetchingDetails(true);
          const data = await getInvitationDetails(params);
          setInvitationData(data);
          
          // Check if invitation is already accepted
          if (data.status === 'accepted') {
            setErrorMessage('This invitation has already been accepted.');
          }
        } catch (error) {
          console.error('Error fetching invitation details:', error);
          const errorMsgStr = error.response?.data?.message || (typeof error.response?.data === 'string' ? error.response.data : '') || 'Failed to load invitation details. Please check if the link is valid.';
          setErrorMessage(errorMsgStr);
        } finally {
          setFetchingDetails(false);
        }
      } else {
        setErrorMessage('Invalid invite link. Missing required parameters.');
        setFetchingDetails(false);
      }
    };
    
    fetchInvitation();
  }, [location.search]);

  // Handle accept invite
  const handleAcceptInvite = async () => {
    setLoading(true);
    setErrorMessage('');
    
    try {
      // Make API call to accept invite using new endpoint
      const response = await processInvitation({
        userEmail: queryParams.user,
        organizationId: queryParams.orgId,
        type: queryParams.type,
        action: 'ACCEPT'
      });

      setInviteStatus('accepted');
      
      // Redirect after 3 seconds
      setTimeout(() => {
        navigate('/login');
      }, 3000);
    } catch (error) {
      console.error('Error accepting invite:', error);
      const errorMsgStr = error.response?.data?.message || (typeof error.response?.data === 'string' ? error.response.data : '') || 'An error occurred while accepting the invite.';
      setErrorMessage(errorMsgStr);
    } finally {
      setLoading(false);
    }
  };

  // Handle decline invite
  const handleDeclineInvite = () => {
    setShowReasonModal(true);
  };

  // Submit decline with reason
  const handleSubmitDecline = async () => {
    if (!declineReason.trim()) {
      setErrorMessage('Please provide a reason for declining the invite.');
      return;
    }

    setLoading(true);
    setErrorMessage('');

    try {
      // Make API call to decline invite using new endpoint
      const response = await processInvitation({
        userEmail: queryParams.user,
        organizationId: queryParams.orgId,
        type: queryParams.type,
        action: 'REJECT',
        rejectionReason: declineReason
      });

      setInviteStatus('declined');
      setShowReasonModal(false);
      
      // Redirect after 3 seconds
      setTimeout(() => {
        navigate('/');
      }, 3000);
    } catch (error) {
      console.error('Error declining invite:', error);
      const errorMsgStr = error.response?.data?.message || (typeof error.response?.data === 'string' ? error.response.data : '') || 'An error occurred while declining the invite.';
      setErrorMessage(errorMsgStr);
    } finally {
      setLoading(false);
    }
  };

  // Render loading state while fetching details
  if (fetchingDetails) {
    return (
      <div className="accept-invite-container">
        <div className="invite-card">
          <div className="invite-icon">📧</div>
          <h2>Loading Invitation Details...</h2>
          <div className="loading-dots">
            <span></span>
            <span></span>
            <span></span>
          </div>
        </div>
      </div>
    );
  }

  // Render error state
  if (errorMessage && inviteStatus === 'pending') {
    const isExpired = errorMessage.toLowerCase().includes('expired');

    return (
      <div className="accept-invite-container">
        <div className={`invite-card ${isExpired ? 'expired-card' : 'error-card'}`}>
          <div className={isExpired ? 'expired-icon' : 'error-icon'}>
            {isExpired ? '⌛' : '⚠️'}
          </div>
          <h2>{isExpired ? 'Link Expired' : 'Error'}</h2>
          <p className="error-message">
            {isExpired 
              ? 'This invitation link has expired. For security reasons, invitation links are only valid for 5 minutes. Please contact your HR or Administrator to get a new invitation.' 
              : errorMessage}
          </p>
          <button 
            className="btn-secondary"
            onClick={() => navigate('/')}
          >
            Go to Home
          </button>
        </div>
      </div>
    );
  }

  // Render pending state (invitation decision)
  if (inviteStatus === 'pending' && invitationData) {
    return (
      <div className="accept-invite-container">
        <div className="invite-card">
          <div className="invite-icon">📧</div>
          <h2>You've Been Invited!</h2>
          <div className="invite-details">
            <p><strong>Name:</strong> {invitationData.name}</p>
            <p><strong>Email:</strong> {invitationData.email}</p>
            <p><strong>Role:</strong> {invitationData.role}</p>
            <p><strong>Type:</strong> {invitationData.type}</p>
            <p><strong>Organization:</strong> {invitationData.organization}</p>
          </div>
          <p className="invite-message">
            Would you like to accept this invitation to join the organization?
          </p>
          
          {errorMessage && (
            <div className="error-banner">{errorMessage}</div>
          )}
          
          <div className="button-group">
            <button 
              className="btn-accept"
              onClick={handleAcceptInvite}
              disabled={loading}
            >
              {loading ? 'Processing...' : 'Yes, Accept Invite'}
            </button>
            <button 
              className="btn-decline"
              onClick={handleDeclineInvite}
              disabled={loading}
            >
              No, Decline Invite
            </button>
          </div>
        </div>

        {/* Decline Reason Modal */}
        {showReasonModal && (
          <div className="modal-overlay">
            <div className="modal-content">
              <h3>Why are you declining this invitation?</h3>
              <textarea
                className="reason-textarea"
                value={declineReason}
                onChange={(e) => setDeclineReason(e.target.value)}
                placeholder="Please provide a reason for declining..."
                rows="5"
              />
              {errorMessage && (
                <div className="error-banner">{errorMessage}</div>
              )}
              <div className="modal-buttons">
                <button 
                  className="btn-submit"
                  onClick={handleSubmitDecline}
                  disabled={loading}
                >
                  {loading ? 'Submitting...' : 'Submit'}
                </button>
                <button 
                  className="btn-cancel"
                  onClick={() => {
                    setShowReasonModal(false);
                    setDeclineReason('');
                    setErrorMessage('');
                  }}
                  disabled={loading}
                >
                  Cancel
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    );
  }

  // Render accepted state
  if (inviteStatus === 'accepted') {
    return (
      <div className="accept-invite-container">
        <div className="invite-card success-card">
          <div className="success-icon">✓</div>
          <h2>Thanks for Accepting the Invite!</h2>
          <p className="success-message">
            Your invitation has been accepted successfully.
          </p>
          <p className="success-message">
            We have sent you an email with the login credentials and application link. 
            Please check your inbox.
          </p>
          <div className="loading-dots">
            <span></span>
            <span></span>
            <span></span>
          </div>
        </div>
      </div>
    );
  }

  // Render declined state
  if (inviteStatus === 'declined') {
    return (
      <div className="accept-invite-container">
        <div className="invite-card declined-card">
          <div className="declined-icon">✗</div>
          <h2>Invitation Declined</h2>
          <p className="declined-message">
            Thank you for your response. Your decline has been recorded. You will be redirected shortly.
          </p>
          <div className="loading-dots">
            <span></span>
            <span></span>
            <span></span>
          </div>
        </div>
      </div>
    );
  }

  return null;
};

export default AcceptInvite;
