import React from "react";
import Modal from 'react-bootstrap/Modal';
 
import { useSelector,useDispatch } from "react-redux";
import { setModalState } from "../../redux/reducers/settingsModalReducer"; // Adjust the import path as necessary
 
 
export default function SettingModal() {
  const dispatch = useDispatch();
  const settingsSelector = useSelector(state => state.settingsModalReducer);
  const handleHideModal = () => {
    dispatch(setModalState(false));
  }
  return (
      <Modal show={settingsSelector.modalState} fullscreen={true} onHide={() => handleHideModal()}>
        <Modal.Header closeButton>
          <Modal.Title>Modal</Modal.Title>
        </Modal.Header>
        <Modal.Body>Modal body content</Modal.Body>
      </Modal>
  );
}