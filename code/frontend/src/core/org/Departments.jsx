import { MasterTable } from './MasterTable.jsx';
import { departmentService } from './departmentService.js';

export function Departments() {
  return <MasterTable title="Departments" service={departmentService} />;
}
