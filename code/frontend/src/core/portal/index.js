import { ApplyLeave } from './ApplyLeave.jsx';
import { ApplyWfh } from './ApplyWfh.jsx';
import { MyRequests } from './MyRequests.jsx';
import { EditOwnSection } from './EditOwnSection.jsx';
import { MyDocumentsPanel } from './MyDocumentsPanel.jsx';

export { ApplyLeave, ApplyWfh, MyRequests, EditOwnSection, MyDocumentsPanel };

export const leaveActions = {
  ApplyLeave,
  MyRequests,
};

export const wfhActions = {
  ApplyWfh,
};

export const profileActions = {
  EditOwnSection,
};
