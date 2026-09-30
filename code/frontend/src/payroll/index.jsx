import { routeGroups } from '@shell/routes';
import { TaxWindowScreen } from './tax/TaxWindowScreen';
import { OfficerDeclarationView } from './tax/OfficerDeclarationView';
import { DeclarationPage } from './tax/DeclarationPage';
import { HousingSection } from './tax/HousingSection';
import { DeductionsSection } from './tax/DeductionsSection';
import { OtherIncomeSection } from './tax/OtherIncomeSection';
import { SummarySection } from './tax/SummarySection';
import { declarationService } from './tax/declarationService';
import { taxSettingsService } from './tax/taxSettingsService';
import { currentFy, formatFy, formatFyDisplay, fyOptions } from './tax/financialYear';
import taxReducer, {
  setFy,
  setHeader,
  setSectionData,
  setItems,
  setLoading,
  setError,
  resetTaxState,
} from './tax/taxSlice';

export const payrollRoutes = [
  { path: '/payroll/settings/tax-declaration', element: <TaxWindowScreen /> },
  { path: '/payroll/tax-declarations/:employeeId/:fy', element: <OfficerDeclarationView /> },
];

export const portalPanels = [
  { code: 'taxDeclaration', element: <DeclarationPage /> },
];

routeGroups.payroll.splice(0, routeGroups.payroll.length, ...payrollRoutes);

export {
  TaxWindowScreen,
  OfficerDeclarationView,
  DeclarationPage,
  HousingSection,
  DeductionsSection,
  OtherIncomeSection,
  SummarySection,
  declarationService,
  taxSettingsService,
  currentFy,
  formatFy,
  formatFyDisplay,
  fyOptions,
  taxReducer,
  setFy,
  setHeader,
  setSectionData,
  setItems,
  setLoading,
  setError,
  resetTaxState,
};

export default {
  routes: payrollRoutes,
  portalPanels,
  reducer: taxReducer,
};
