import { apiClient } from '../../shared/api/client.js';

function unwrapEnvelope(res) {
  const body = res?.data !== undefined ? res.data : res;
  return body && typeof body === 'object' && 'data' in body ? body.data : body;
}

/**
 * Mobile Approval Service (W-98.5 §4.2)
 * Mobile client for retrieving aggregated pending approval instances
 * and executing 1-tap Approve and Reject actions.
 */
export const mobileApprovalService = {
  /**
   * Fetches pending approval tasks assigned to the caller.
   *
   * @param {Object} params
   * @param {number} [params.page=0]
   * @param {number} [params.size=50]
   * @param {string} [params.flowType=null]
   * @returns {Promise<{ rows: Array, total: number, countsByType: Object }>}
   */
  async getPendingApprovals({ page = 0, size = 50, flowType = null } = {}) {
    const res = await apiClient.get('/v1/approvals/pending', {
      params: { page, size },
    });
    const data = unwrapEnvelope(res);

    const items = Array.isArray(data)
      ? data
      : Array.isArray(data?.content)
      ? data.content
      : [];

    const total =
      data?.totalElements !== undefined
        ? Number(data.totalElements)
        : items.length;

    const countsByType = {
      ALL: total,
      LEAVE: 0,
      WORK_FROM_HOME: 0,
      REGULARIZATION: 0,
      OVERTIME: 0,
      REIMBURSEMENT: 0,
      TIMESHEET: 0,
      OTHER: 0,
    };

    const normalized = items.map((item) => {
      const type = (item.flowType || 'GENERAL').toUpperCase();
      if (countsByType[type] !== undefined) {
        countsByType[type] += 1;
      } else {
        countsByType.OTHER += 1;
      }

      return {
        id: item.id, // step ID
        instanceId: item.instanceId,
        flowType: type,
        summary: item.summary || item.itemRef || 'Approval Request',
        itemRef: item.itemRef || '',
        subjectEmployeeName: item.subjectEmployeeName || 'Employee',
        subjectEmployeeId: item.subjectEmployeeId || '',
        stepIndex: item.stepIndex ?? 0,
        totalSteps: item.totalSteps ?? 1,
        createdAt: item.createdAt || '',
        reason: item.reason || item.comments || item.summary || '',
        daysCount: item.daysCount || item.days || null,
        metadata: item.metadata || item.itemPayload || {},
      };
    });

    const filtered =
      flowType && flowType !== 'ALL'
        ? normalized.filter((item) => item.flowType === flowType.toUpperCase())
        : normalized;

    return {
      rows: filtered,
      total: filtered.length,
      unfilteredTotal: total,
      countsByType,
    };
  },

  /**
   * Fetches full instance details including multi-step approval history.
   *
   * @param {string} instanceId
   * @returns {Promise<{ instance: Object, history: Array }>}
   */
  async getInstanceDetail(instanceId) {
    if (!instanceId) {
      throw new Error('Instance ID is required');
    }

    const [instanceRes, historyRes] = await Promise.allSettled([
      apiClient.get(`/v1/approvals/${encodeURIComponent(instanceId)}`),
      apiClient.get(`/v1/approvals/${encodeURIComponent(instanceId)}/history`),
    ]);

    const instanceData =
      instanceRes.status === 'fulfilled'
        ? unwrapEnvelope(instanceRes.value)
        : {};

    const historyData =
      historyRes.status === 'fulfilled'
        ? unwrapEnvelope(historyRes.value)
        : [];

    const historyList = Array.isArray(historyData)
      ? historyData
      : historyData?.steps || historyData?.events || [];

    return {
      instance: instanceData,
      history: historyList,
    };
  },

  /**
   * Submits a decision (APPROVE / REJECT) for a pending approval step.
   *
   * @param {Object} params
   * @param {string} params.stepId
   * @param {'APPROVED'|'REJECTED'} params.decision
   * @param {string} [params.comment='']
   * @param {string} [params.approvedAmount]
   * @returns {Promise<Object>}
   */
  async decide({ stepId, decision, comment = '', approvedAmount = null }) {
    if (!stepId) {
      throw new Error('Step ID is required');
    }

    const normalizedDecision = (decision || '').toUpperCase();
    if (normalizedDecision !== 'APPROVED' && normalizedDecision !== 'REJECTED') {
      throw new Error('Decision must be either APPROVED or REJECTED');
    }

    if (normalizedDecision === 'REJECTED' && (!comment || !comment.trim())) {
      throw new Error('Rejection reason is required');
    }

    const payload = {
      decision: normalizedDecision,
      comment: comment.trim(),
    };

    if (approvedAmount !== null && approvedAmount !== undefined) {
      payload.approvedAmount = String(approvedAmount);
    }

    const res = await apiClient.post(
      `/v1/approvals/steps/${encodeURIComponent(stepId)}/decide`,
      payload
    );

    return unwrapEnvelope(res);
  },
};

