import { MasterTable } from './MasterTable.jsx';
import { designationService } from './designationService.js';

export function Designations() {
  return <MasterTable title="Designations" service={designationService} />;
}
