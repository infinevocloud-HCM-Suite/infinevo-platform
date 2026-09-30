import { createService } from '@shared/api/createService.js';

export const delegationService = {
  ...createService('/v1/approval-delegations'),
};
