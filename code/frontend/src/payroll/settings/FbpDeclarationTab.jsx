import PropTypes from 'prop-types';
import { useParams } from 'react-router-dom';
import { useCan, NotEntitled } from '@shell/screens';
import { FbpDeclarationForm } from './FbpDeclarationForm.jsx';

export function FbpDeclarationTab({ employeeId: propEmployeeId, employee }) {
  const params = useParams();
  const employeeId = propEmployeeId || employee?.id || params?.id || params?.employeeId;

  const canRead = useCan('payroll.fbp.read');
  const canManage = useCan('payroll.salary.manage');

  if (!canRead) {
    return <NotEntitled />;
  }

  if (!employeeId) {
    return null;
  }

  return (
    <div style={{ width: '100%' }}>
      <FbpDeclarationForm
        employeeId={employeeId}
        canManage={canManage}
      />
    </div>
  );
}

FbpDeclarationTab.propTypes = {
  employeeId: PropTypes.string,
  employee: PropTypes.shape({
    id: PropTypes.string,
  }),
};

export default FbpDeclarationTab;
