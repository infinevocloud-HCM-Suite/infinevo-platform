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
  { path: '/me/tax-declaration', element: <DeclarationPage /> },
  { path: '/payroll/tax-declaration/settings', element: <TaxWindowScreen /> },
  { path: '/payroll/tax-declaration/officer', element: <OfficerDeclarationView /> },
];

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
  reducer: taxReducer,
};
