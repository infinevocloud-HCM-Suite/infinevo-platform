import axios from 'axios';
import { GlobalConst } from '../appConfig/globalConst';

const FED_SECRET_HEADER = {
  'X-Secret-Fed-Code': 'payroll-fed-secret-123'
};


/**
 * Get invitation details by email
 */
export const getInvitationDetails = async ({ user, type, orgId }) => {
  const params = { user, type, orgId };
  const response = await axios.get(`${GlobalConst.API_URL}/api/public/invitation-by-email`, {
    headers: FED_SECRET_HEADER,
    params,
  });
  return response.data;
};

/**
 * Process invitation (accept or reject)
 */
export const processInvitation = async ({ userEmail, organizationId, type, action, rejectionReason }) => {
  const body = { userEmail, organizationId, type, action };
  if (action === 'REJECT' && rejectionReason) {
    body.rejectionReason = rejectionReason;
  }
  const response = await axios.post(`${GlobalConst.API_URL}/api/public/process-invitation`, body, {
    headers: FED_SECRET_HEADER,
  });
  return response.data;
};
