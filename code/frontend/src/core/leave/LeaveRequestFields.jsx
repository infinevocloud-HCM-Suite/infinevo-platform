import { useState } from 'react';
import PropTypes from 'prop-types';
import { Form, Select, DatePicker, Switch, Input, Row, Col, Upload, Button, message } from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import { documentService } from '../document/documentService.js';
import { EmployeePicker } from './EmployeePicker.jsx';

const { TextArea } = Input;

export const normalizeDocumentIds = (val) => {
  if (Array.isArray(val)) {
    return val
      .map((item) => {
        if (typeof item === 'string') return item;
        return item?.id || item?.documentId || item?.response?.id || item?.response?.documentId;
      })
      .filter(Boolean);
  }
  if (val && typeof val === 'object') {
    const list = Array.isArray(val.fileList) ? val.fileList : [val.file].filter(Boolean);
    return list
      .map((f) => f?.id || f?.documentId || f?.response?.id || f?.response?.documentId)
      .filter(Boolean);
  }
  return [];
};

export function LeaveRequestFields({
  form,
  isEmployeeView = false,
  leaveTypes = [],
  onEmployeeChange,
  isHalfDay = false,
  onHalfDayChange,
}) {
  const [fileList, setFileList] = useState([]);

  const handleCustomUpload = async ({ file, onSuccess, onError }) => {
    try {
      const res = await documentService.upload(file, 'LEAVE_ATTACHMENT');
      const docId = res?.id || res?.documentId;
      file.documentId = docId;
      file.status = 'done';
      file.response = res;
      onSuccess(res, file);
      if (form && docId) {
        const currentDocIds = normalizeDocumentIds(form.getFieldValue('documentIds'));
        if (!currentDocIds.includes(docId)) {
          form.setFieldsValue({ documentIds: [...currentDocIds, docId] });
        }
      }
    } catch (err) {
      onError(err);
      message.error(err?.response?.data?.message || err?.message || 'Failed to upload supporting document');
    }
  };

  const handleFileChange = ({ fileList: newFileList }) => {
    setFileList(newFileList);
    if (form) {
      const docIds = normalizeDocumentIds({ fileList: newFileList });
      form.setFieldsValue({ documentIds: docIds });
    }
  };

  return (
    <>
      {!isEmployeeView && (
        <Form.Item
          name="employeeId"
          label="Employee"
          rules={[{ required: true, message: 'Please select an employee' }]}
        >
          {/* D-80: search-as-you-type over the whole tenant, not a first page of 100. */}
          <EmployeePicker onChange={onEmployeeChange} />
        </Form.Item>
      )}

      <Form.Item
        name="leaveTypeId"
        label="Leave Type"
        rules={[{ required: true, message: 'Please select a leave type' }]}
      >
        <Select
          placeholder="Select leave type"
          showSearch
          optionFilterProp="label"
          options={leaveTypes.map((t) => ({
            value: t.id,
            label: `${t.name} (${t.code})`,
          }))}
        />
      </Form.Item>

      <Row gutter={16}>
        <Col span={12}>
          <Form.Item
            name="fromDate"
            label="From Date"
            rules={[{ required: true, message: 'Please select start date' }]}
          >
            <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item
            name="toDate"
            label="To Date"
            rules={[{ required: true, message: 'Please select end date' }]}
          >
            <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" />
          </Form.Item>
        </Col>
      </Row>

      <Row gutter={16} align="middle">
        <Col span={8}>
          <Form.Item name="isHalfDay" label="Half Day" valuePropName="checked">
            <Switch onChange={onHalfDayChange} />
          </Form.Item>
        </Col>
        {isHalfDay && (
          <Col span={16}>
            <Form.Item
              name="halfDayPeriod"
              label="Period"
              rules={[{ required: true, message: 'Select half day period' }]}
            >
              <Select
                placeholder="Select period"
                options={[
                  { value: 'FIRST', label: 'First Half' },
                  { value: 'SECOND', label: 'Second Half' },
                ]}
              />
            </Form.Item>
          </Col>
        )}
      </Row>

      <Form.Item
        name="reason"
        label="Reason"
        rules={[{ required: true, message: 'Please enter a reason' }]}
      >
        <TextArea rows={3} placeholder="Enter reason for leave" />
      </Form.Item>

      <Form.Item
        name="documentIds"
        label="Supporting Documents / Attachments"
        extra="Upload supporting documents (PDF, JPG, PNG) if required by the leave policy."
        getValueFromEvent={normalizeDocumentIds}
        rules={[
          {
            validator: () => {
              if (fileList.some((f) => f.status === 'uploading')) {
                return Promise.reject(new Error('Please wait for the document to finish uploading.'));
              }
              if (fileList.some((f) => f.status === 'error')) {
                return Promise.reject(new Error('Document upload failed. Please remove or re-upload the document.'));
              }
              return Promise.resolve();
            },
          },
        ]}
      >
        <Upload
          fileList={fileList}
          customRequest={handleCustomUpload}
          onChange={handleFileChange}
          multiple
        >
          <Button icon={<UploadOutlined />} id="btn-leave-attach-document">Attach Document</Button>
        </Upload>
      </Form.Item>
    </>
  );
}

LeaveRequestFields.propTypes = {
  form: PropTypes.object,
  isEmployeeView: PropTypes.bool,
  leaveTypes: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.string.isRequired,
      name: PropTypes.string.isRequired,
      code: PropTypes.string.isRequired,
    })
  ),
  onEmployeeChange: PropTypes.func,
  isHalfDay: PropTypes.bool,
  onHalfDayChange: PropTypes.func,
};
