import PropTypes from 'prop-types';
import { useParams } from 'react-router-dom';
import { useCan, NotEntitled } from '@shell/screens';
import { FbpDeclarationForm } from './FbpDeclarationForm.jsx';

export function FbpDeclarationTab({ employeeId: propEmployeeId, employee }) {
  const params = useParams();
  const employeeId = propEmployeeId || employee?.id || params?.id || params?.employeeId;
  const isOwn = !employeeId;

  const canReadOwn = useCan('payroll.fbp.read_own');
  const canDeclareOwn = useCan('payroll.fbp.declare_own');
  const canReadAdmin = useCan('payroll.fbp.read');
  const canManageAdmin = useCan('payroll.salary.manage');

  const canRead = isOwn ? canReadOwn : canReadAdmin;
  const canManage = isOwn ? canDeclareOwn : canManageAdmin;

  if (!canRead) {
    return <NotEntitled />;
  }

  return (
    <div style={{ width: '100%' }}>
      <FbpDeclarationForm
        employeeId={employeeId}
        isOwn={isOwn}
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
