import { apiClient } from '@shared/api/client.js';

export const hiringService = {
  openings: {
    async list(status) {
      const res = await apiClient.get('/v1/hrms/job-openings', {
        params: status ? { status } : undefined,
      });
      return res.data?.data || res.data || [];
    },

    async get(id) {
      const res = await apiClient.get(`/v1/hrms/job-openings/${id}`);
      return res.data?.data || res.data;
    },

    async create(payload) {
      const res = await apiClient.post('/v1/hrms/job-openings', payload);
      return res.data?.data || res.data;
    },

    async update(id, payload) {
      const res = await apiClient.put(`/v1/hrms/job-openings/${id}`, payload);
      return res.data?.data || res.data;
    },

    async updateStatus(id, status) {
      const res = await apiClient.patch(`/v1/hrms/job-openings/${id}/status`, { status });
      return res.data?.data || res.data;
    },

    async listApplications(id) {
      const res = await apiClient.get(`/v1/hrms/job-openings/${id}/applications`);
      return res.data?.data || res.data || [];
    },

    async apply(id, candidateId) {
      const res = await apiClient.post(`/v1/hrms/job-openings/${id}/applications`, { candidateId });
      return res.data?.data || res.data;
    },
  },

  candidates: {
    async list(q) {
      const res = await apiClient.get('/v1/hrms/candidates', {
        params: q ? { q } : undefined,
      });
      return res.data?.data || res.data || [];
    },

    async get(id) {
      const res = await apiClient.get(`/v1/hrms/candidates/${id}`);
      return res.data?.data || res.data;
    },

    async create(payload) {
      const res = await apiClient.post('/v1/hrms/candidates', payload);
      return res.data?.data || res.data;
    },
  },

  applications: {
    async get(id) {
      const res = await apiClient.get(`/v1/hrms/applications/${id}`);
      return res.data?.data || res.data;
    },

    async updateStage(id, stage, rejectionReason) {
      const res = await apiClient.patch(`/v1/hrms/applications/${id}/stage`, { stage, rejectionReason });
      return res.data?.data || res.data;
    },

    async scheduleInterview(id, payload) {
      const res = await apiClient.post(`/v1/hrms/applications/${id}/interviews`, payload);
      return res.data?.data || res.data;
    },

    async listInterviews(id) {
      const res = await apiClient.get(`/v1/hrms/applications/${id}/interviews`);
      return res.data?.data || res.data || [];
    },

    async createOffer(id, payload) {
      const res = await apiClient.post(`/v1/hrms/applications/${id}/offers`, payload);
      return res.data?.data || res.data;
    },
  },

  interviews: {
    async list(mine = false) {
      const res = await apiClient.get('/v1/hrms/interviews', {
        params: mine ? { mine: true } : undefined,
      });
      return res.data?.data || res.data || [];
    },

    async get(id) {
      const res = await apiClient.get(`/v1/hrms/interviews/${id}`);
      return res.data?.data || res.data;
    },

    async submitFeedback(id, payload) {
      const res = await apiClient.post(`/v1/hrms/interviews/${id}/feedback`, payload);
      return res.data?.data || res.data;
    },

    async listFeedback(id) {
      const res = await apiClient.get(`/v1/hrms/interviews/${id}/feedback`);
      return res.data?.data || res.data || [];
    },
  },

  offers: {
    async list(status) {
      const res = await apiClient.get('/v1/hrms/offers', {
        params: status ? { status } : undefined,
      });
      return res.data?.data || res.data || [];
    },

    async get(id) {
      const res = await apiClient.get(`/v1/hrms/offers/${id}`);
      return res.data?.data || res.data;
    },

    async send(id) {
      const res = await apiClient.post(`/v1/hrms/offers/${id}/send`);
      return res.data?.data || res.data;
    },

    async accept(id) {
      const res = await apiClient.post(`/v1/hrms/offers/${id}/accept`);
      return res.data?.data || res.data;
    },

    async decline(id, reason) {
      const res = await apiClient.post(`/v1/hrms/offers/${id}/decline`, { reason });
      return res.data?.data || res.data;
    },

    async convertToEmployee(id, payload) {
      const res = await apiClient.post(`/v1/hrms/offers/${id}/convert-to-employee`, payload);
      return res.data?.data || res.data;
    },
  },
};

