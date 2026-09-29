import { useState, useEffect } from 'react';
import ReactDOM from 'react-dom/client';
import { ConfigProvider, Layout, Segmented, Typography, Space, Tag, Alert } from 'antd';
import { Provider } from 'react-redux';
import { BrowserRouter } from 'react-router-dom';
import { store } from '@shell/store';
import { theme } from '@shared/theme';
import { apiClient } from '@shared/api/client';

import { DeclarationPage } from '@payroll/tax/DeclarationPage';
import { TaxWindowScreen } from '@payroll/tax/TaxWindowScreen';
import { OfficerDeclarationView } from '@payroll/tax/OfficerDeclarationView';

const { Header, Content } = Layout;
const { Text } = Typography;

// ── In-Memory Mock Store for Live Interactive Preview ─────────────────────────
const mockStore = {
  settings: {
    '2026-27': {
      financial_year: '2026-27',
      window_opens_on: '2026-04-01',
      window_closes_on: '2026-04-30',
      is_locked: false,
      default_tax_regime: 'NEW',
      can_change_tax_regime: true,
      pan_required_for_rent_over_threshold: 100000,
      updated_at: '2026-04-01T10:00:00Z',
      updated_by: 'admin@infinevo.com',
    },
  },
  header: {
    '2026-27': {
      employee_id: 'EMP-001',
      financial_year: '2026-27',
      regime: 'NEW',
      status: 'DRAFT',
      window_open: true,
      editable: true,
      reopenable: true,
      can_change_tax_regime: true,
      is_staying_in_rented_house: true,
      is_repaying_self_occupied_loan: true,
      has_let_out_property: false,
      pan_required_for_rent_over_threshold: 100000,
      submitted_at: null,
      updated_at: '2026-04-02T11:00:00Z',
    },
  },
  officerHeaders: {
    'EMP-001': {
      employee_id: 'EMP-001',
      financial_year: '2026-27',
      regime: 'NEW',
      status: 'SUBMITTED',
      window_open: true,
      editable: false,
      is_staying_in_rented_house: true,
      is_repaying_self_occupied_loan: true,
      has_let_out_property: false,
      submitted_at: '2026-04-10T14:30:00Z',
      updated_at: '2026-04-10T14:30:00Z',
    },
  },
  housing: {
    '2026-27': {
      house_rent: [
        {
          from_month: '2026-04',
          to_month: '2027-03',
          address: 'Flat 402, Green Meadows, Andheri East, Mumbai',
          landlord_name: 'Suresh Kumar',
          landlord_pan: 'ABCDE1234F',
          is_metro: true,
          amount_per_month: 22000,
        },
      ],
      home_loans: [
        {
          lender_name: 'HDFC Bank',
          lender_pan: 'AAACH1234K',
          principal_paid: 80000,
          interest_paid: 160000,
          is_first_time_buyer: false,
          loan_sanctioned_on: '2023-08-15',
        },
      ],
      let_out_properties: [],
    },
  },
  catalogueItems: [
    {
      id: 'item-80c-lic',
      code: '80C_LIC',
      name: 'Life Insurance Premium',
      group_code: '80C',
      max_limit: 150000,
      description: 'Life insurance premiums paid for self, spouse, or children',
    },
    {
      id: 'item-80c-ppf',
      code: '80C_PPF',
      name: 'Public Provident Fund (PPF)',
      group_code: '80C',
      max_limit: 150000,
      description: 'Contributions to 15-year Public Provident Fund accounts',
    },
    {
      id: 'item-80c-elss',
      code: '80C_ELSS',
      name: 'Equity Linked Savings Scheme (ELSS)',
      group_code: '80C',
      max_limit: 150000,
      description: 'Investment in tax-saving mutual funds (3-year lock-in)',
    },
    {
      id: 'item-80d-self',
      code: '80D_SELF',
      name: 'Medical Insurance (Self / Family)',
      group_code: '80D',
      max_limit: 25000,
      description: 'Health insurance policies covering self, spouse, and dependent children',
    },
    {
      id: 'item-80d-parents',
      code: '80D_PARENTS',
      name: 'Medical Insurance (Parents)',
      group_code: '80D',
      max_limit: 50000,
      description: 'Health insurance policies covering senior citizen parents',
    },
    {
      id: 'item-80ccd1b',
      code: '80CCD_1B',
      name: 'NPS Employee Voluntary Contribution',
      group_code: '80CCD',
      max_limit: 50000,
      description: 'Tier-1 NPS contribution under Section 80CCD(1B) beyond 80C',
    },
  ],
  deductions: {
    '2026-27': {
      section6a: [
        {
          section6a_item_id: 'item-80c-lic',
          item_code: '80C_LIC',
          amount: 60000,
          description: 'HDFC Life Click 2 Protect',
        },
        {
          section6a_item_id: 'item-80d-self',
          item_code: '80D_SELF',
          amount: 22000,
          description: 'Star Health Family Optima',
        },
      ],
      pre_tax_deductions: [
        { kind: 'VPF', amount: 15000 },
        { kind: 'NPS_EMPLOYEE', amount: 35000 },
      ],
      previous_employment: [
        {
          kind: 'INCOME',
          amount: 320000,
          employer_name: 'Previous Tech Enterprises',
          employer_tan: 'MUMB12345A',
        },
        {
          kind: 'INCOME_TAX_DEDUCTED',
          amount: 25000,
          employer_name: 'Previous Tech Enterprises',
          employer_tan: 'MUMB12345A',
        },
      ],
    },
  },
  otherIncome: {
    '2026-27': [
      {
        id: 'oi-1',
        kind: 'SAVINGS_INTEREST',
        description: 'Savings account interest across banks',
        amount: 8500,
      },
      {
        id: 'oi-2',
        kind: 'FD_INTEREST',
        description: 'Fixed deposit interest',
        amount: 18000,
      },
    ],
  },
  summary: {
    '2026-27': {
      financial_year: '2026-27',
      tax_regime: 'NEW',
      status: 'DRAFT',
      declared: {
        house_rent_annual: 264000,
        home_loan_principal: 80000,
        home_loan_interest: 160000,
        let_out_net: 0,
        section6a_by_group: {
          '80C': 60000,
          '80D': 22000,
        },
        section6a_total: 82000,
        pre_tax_total: 50000,
        prev_employment_income: 320000,
        prev_employment_tax: 25000,
        other_income_total: 26500,
      },
      computed: {
        taxable_income: 980000,
        net_taxable_income: 885000,
        tax_on_taxable_income: 44000,
        tax_ytd_amount: 12000,
        tax_to_be_paid: 32000,
        tds_through_payroll: 12000,
        tds_previous_employer: 25000,
        tds_other_income: 0,
        other_sources_income: 26500,
        exemption_under_section10: 45000,
        exemption_under_section6a: 82000,
        remaining_months: 7,
        computed_at: new Date().toISOString(),
      },
    },
  },
};

// ── Intercept API calls on apiClient for live in-browser preview ─────────────
apiClient.interceptors.request.use((config) => {
  const url = config.url || '';
  const method = (config.method || 'get').toLowerCase();

  // Settings
  if (url.includes('/tax-declaration/settings')) {
    const fyMatch = url.match(/settings\/([^/]+)/);
    const fy = fyMatch ? decodeURIComponent(fyMatch[1]) : '2026-27';
    if (method === 'get') {
      config.adapter = () =>
        Promise.resolve({
          data: mockStore.settings[fy] || mockStore.settings['2026-27'],
          status: 200,
          statusText: 'OK',
          headers: {},
          config,
        });
    } else if (method === 'put') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data;
      mockStore.settings[fy] = { ...mockStore.settings[fy], ...body };
      config.adapter = () =>
        Promise.resolve({
          data: mockStore.settings[fy],
          status: 200,
          statusText: 'OK',
          headers: {},
          config,
        });
    }
    return config;
  }

  // Officer Header Review
  if (url.includes('/payroll/employees/') && url.includes('/tax-declaration/')) {
    const match = url.match(/employees\/([^/]+)\/tax-declaration\/([^/]+)/);
    const empId = match ? decodeURIComponent(match[1]) : 'EMP-001';
    const fy = match ? decodeURIComponent(match[2]) : '2026-27';
    const found = mockStore.officerHeaders[empId] || {
      employee_id: empId,
      financial_year: fy,
      regime: 'NEW',
      status: 'SUBMITTED',
      window_open: true,
      editable: false,
      is_staying_in_rented_house: true,
      is_repaying_self_occupied_loan: false,
      has_let_out_property: false,
      submitted_at: '2026-04-10T12:00:00Z',
      updated_at: '2026-04-10T12:00:00Z',
    };
    config.adapter = () =>
      Promise.resolve({
        data: found,
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      });
    return config;
  }

  // Section 6A Items
  if (url.includes('/section6a-items')) {
    config.adapter = () =>
      Promise.resolve({
        data: mockStore.catalogueItems,
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      });
    return config;
  }

  // Housing
  if (url.endsWith('/housing')) {
    config.adapter = () =>
      Promise.resolve({
        data: mockStore.housing['2026-27'],
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      });
    return config;
  }

  if (url.endsWith('/house-rent')) {
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data;
    mockStore.housing['2026-27'].house_rent = body;
    config.adapter = () =>
      Promise.resolve({
        data: { success: true },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      });
    return config;
  }

  if (url.endsWith('/home-loan')) {
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data;
    mockStore.housing['2026-27'].home_loans = body;
    config.adapter = () =>
      Promise.resolve({
        data: { success: true },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      });
    return config;
  }

  if (url.endsWith('/let-out-property')) {
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data;
    mockStore.housing['2026-27'].let_out_properties = body;
    config.adapter = () =>
      Promise.resolve({
        data: { success: true },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      });
    return config;
  }

  // Deductions
  if (url.endsWith('/deductions')) {
    config.adapter = () =>
      Promise.resolve({
        data: mockStore.deductions['2026-27'],
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      });
    return config;
  }

  if (url.endsWith('/section6a')) {
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data;
    mockStore.deductions['2026-27'].section6a = body;
    config.adapter = () =>
      Promise.resolve({
        data: { success: true },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      });
    return config;
  }

  if (url.endsWith('/pre-tax-deductions')) {
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data;
    mockStore.deductions['2026-27'].pre_tax_deductions = body;
    config.adapter = () =>
      Promise.resolve({
        data: { success: true },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      });
    return config;
  }

  if (url.endsWith('/previous-employment')) {
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data;
    mockStore.deductions['2026-27'].previous_employment = body;
    config.adapter = () =>
      Promise.resolve({
        data: { success: true },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      });
    return config;
  }

  // Other Income
  if (url.includes('/other-income')) {
    if (method === 'get') {
      config.adapter = () =>
        Promise.resolve({
          data: mockStore.otherIncome['2026-27'],
          status: 200,
          statusText: 'OK',
          headers: {},
          config,
        });
    } else {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data;
      mockStore.otherIncome['2026-27'] = body;
      config.adapter = () =>
        Promise.resolve({
          data: { success: true },
          status: 200,
          statusText: 'OK',
          headers: {},
          config,
        });
    }
    return config;
  }

  // Summary
  if (url.endsWith('/summary')) {
    config.adapter = () =>
      Promise.resolve({
        data: mockStore.summary['2026-27'],
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      });
    return config;
  }

  // Submit / Reopen Lifecycle
  if (url.endsWith('/submit')) {
    mockStore.header['2026-27'].status = 'SUBMITTED';
    mockStore.header['2026-27'].editable = false;
    mockStore.header['2026-27'].submitted_at = new Date().toISOString();
    config.adapter = () =>
      Promise.resolve({
        data: { status: 'SUBMITTED', submitted_at: mockStore.header['2026-27'].submitted_at },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      });
    return config;
  }

  if (url.endsWith('/reopen')) {
    mockStore.header['2026-27'].status = 'DRAFT';
    mockStore.header['2026-27'].editable = true;
    config.adapter = () =>
      Promise.resolve({
        data: { status: 'DRAFT' },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      });
    return config;
  }

  // Header GET / PUT
  if (url.includes('/me/tax-declaration/')) {
    if (method === 'get') {
      config.adapter = () =>
        Promise.resolve({
          data: mockStore.header['2026-27'],
          status: 200,
          statusText: 'OK',
          headers: {},
          config,
        });
    } else if (method === 'put') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data;
      mockStore.header['2026-27'] = { ...mockStore.header['2026-27'], ...body };
      config.adapter = () =>
        Promise.resolve({
          data: mockStore.header['2026-27'],
          status: 200,
          statusText: 'OK',
          headers: {},
          config,
        });
    }
    return config;
  }

  return config;
});

// ── Showcase App Shell ────────────────────────────────────────────────────────
function PreviewShowcase() {
  const [selectedScreen, setSelectedScreen] = useState('declaration');

  useEffect(() => {
    // Read route or hash if any
    const hash = window.location.hash.replace('#', '');
    if (hash === 'settings' || hash === 'officer' || hash === 'declaration') {
      setSelectedScreen(hash);
    }
  }, []);

  const handleScreenChange = (val) => {
    setSelectedScreen(val);
    window.location.hash = val;
  };

  return (
    <Layout style={{ minHeight: '100vh', background: '#f5f5f5' }}>
      <Header
        style={{
          background: '#001529',
          padding: '0 24px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
          position: 'sticky',
          top: 0,
          zIndex: 1000,
        }}
      >
        <Space align="center" size={16}>
          <Text strong style={{ color: '#fff', fontSize: 18, letterSpacing: 0.5 }}>
            Infinevo
          </Text>
          <Tag color="cyan">Ticket W-47.3 Showcase</Tag>
        </Space>

        <Segmented
          value={selectedScreen}
          onChange={handleScreenChange}
          options={[
            { label: 'Employee Tax Declaration', value: 'declaration' },
            { label: 'Admin Window Configuration', value: 'settings' },
            { label: 'Officer Review View', value: 'officer' },
          ]}
          style={{ background: 'rgba(255,255,255,0.15)' }}
        />

        <Tag color="green">Live Interactive Mode</Tag>
      </Header>

      <Content style={{ padding: '20px 24px' }}>
        <Alert
          message="Interactive Preview Mode Active"
          description="You can switch tabs, enter numbers, save declarations, change regimes, and test submit/reopen flows live in your browser."
          type="info"
          showIcon
          closable
          style={{ maxWidth: 1100, margin: '0 auto 20px auto' }}
        />

        {selectedScreen === 'declaration' && <DeclarationPage initialFy="2026-27" />}
        {selectedScreen === 'settings' && <TaxWindowScreen initialFy="2026-27" />}
        {selectedScreen === 'officer' && <OfficerDeclarationView employeeId="EMP-001" fy="2026-27" />}
      </Content>
    </Layout>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <Provider store={store}>
    <ConfigProvider theme={theme}>
      <BrowserRouter>
        <PreviewShowcase />
      </BrowserRouter>
    </ConfigProvider>
  </Provider>,
);
