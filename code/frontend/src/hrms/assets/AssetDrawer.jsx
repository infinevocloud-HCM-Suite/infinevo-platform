import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Drawer, Form, Input, Select, DatePicker, InputNumber, Button, Space, message, Row, Col } from 'antd';
import dayjs from 'dayjs';
import { assetService } from './assetService.js';

const CATEGORY_OPTIONS = [
  { value: 'LAPTOP', label: 'Laptop' },
  { value: 'MONITOR', label: 'Monitor' },
  { value: 'MOBILE_DEVICE', label: 'Mobile Device' },
  { value: 'ACCESS_CARD', label: 'Access Card' },
  { value: 'PERIPHERAL', label: 'Peripheral' },
  { value: 'VEHICLE', label: 'Vehicle' },
  { value: 'FURNITURE', label: 'Furniture' },
  { value: 'OTHER', label: 'Other Equipment' },
];

const CONDITION_OPTIONS = [
  { value: 'NEW', label: 'New' },
  { value: 'GOOD', label: 'Good' },
  { value: 'FAIR', label: 'Fair' },
  { value: 'DAMAGED', label: 'Damaged' },
];

export function AssetDrawer({ open, onClose, asset, onSuccess }) {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const isEdit = Boolean(asset?.id);

  useEffect(() => {
    if (open) {
      if (asset) {
        form.setFieldsValue({
          assetTag: asset.assetTag,
          assetName: asset.assetName,
          category: asset.category || 'LAPTOP',
          brand: asset.brand,
          modelNumber: asset.modelNumber,
          serialNumber: asset.serialNumber,
          purchaseDate: asset.purchaseDate ? dayjs(asset.purchaseDate) : null,
          purchaseCost: asset.purchaseCost ? Number(asset.purchaseCost) : null,
          warrantyExpiry: asset.warrantyExpiry ? dayjs(asset.warrantyExpiry) : null,
          vendorName: asset.vendorName,
          itemCondition: asset.itemCondition || 'GOOD',
          notes: asset.notes,
        });
      } else {
        form.resetFields();
        form.setFieldsValue({
          category: 'LAPTOP',
          itemCondition: 'GOOD',
        });
      }
    }
  }, [open, asset, form]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const payload = {
        ...values,
        purchaseDate: values.purchaseDate ? values.purchaseDate.format('YYYY-MM-DD') : null,
        warrantyExpiry: values.warrantyExpiry ? values.warrantyExpiry.format('YYYY-MM-DD') : null,
      };

      if (isEdit) {
        await assetService.update(asset.id, payload);
        message.success('Asset updated successfully');
      } else {
        await assetService.create(payload);
        message.success('Asset registered successfully');
      }

      onSuccess?.();
      onClose();
    } catch (err) {
      if (err?.errorFields) return;
      message.error(err?.response?.data?.message || err?.message || 'Failed to save asset');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Drawer
      title={isEdit ? `Edit Asset: ${asset?.assetTag}` : 'Register New Asset'}
      width={560}
      open={open}
      onClose={onClose}
      destroyOnHidden
      extra={
        <Space>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="primary" onClick={handleSubmit} loading={submitting}>
            {isEdit ? 'Save Changes' : 'Register Asset'}
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical">
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              name="assetTag"
              label="Asset Tag / Inventory ID"
              rules={[{ required: true, message: 'Please enter unique asset tag' }]}
            >
              <Input placeholder="e.g. LAP-2026-001" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              name="category"
              label="Equipment Category"
              rules={[{ required: true, message: 'Please select category' }]}
            >
              <Select options={CATEGORY_OPTIONS} />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item
          name="assetName"
          label="Asset Name"
          rules={[{ required: true, message: 'Please enter asset name' }]}
        >
          <Input placeholder="e.g. Apple MacBook Pro 14 (M3 Pro)" />
        </Form.Item>

        <Row gutter={16}>
          <Col span={12}>
            <Form.Item name="brand" label="Brand / Manufacturer">
              <Input placeholder="e.g. Apple, Dell, Lenovo" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="modelNumber" label="Model Number">
              <Input placeholder="e.g. A2992 / XPS 15" />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={12}>
            <Form.Item name="serialNumber" label="Serial Number (S/N)">
              <Input placeholder="Hardware serial number" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="itemCondition" label="Initial Condition">
              <Select options={CONDITION_OPTIONS} />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={12}>
            <Form.Item name="purchaseDate" label="Purchase Date">
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="purchaseCost" label="Purchase Cost">
              <InputNumber
                style={{ width: '100%' }}
                min={0}
                precision={2}
                placeholder="0.00"
              />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={12}>
            <Form.Item name="warrantyExpiry" label="Warranty Expiry Date">
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="vendorName" label="Vendor / Supplier">
              <Input placeholder="e.g. Apple Store, Insight" />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item name="notes" label="Specifications & Notes">
          <Input.TextArea rows={3} placeholder="Technical specifications, RAM, storage, accessories included..." />
        </Form.Item>
      </Form>
    </Drawer>
  );
}

AssetDrawer.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  asset: PropTypes.object,
  onSuccess: PropTypes.func,
};

