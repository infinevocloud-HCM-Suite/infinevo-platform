import PropTypes from 'prop-types';
import { Table, Tag, Button, Space, Popconfirm } from 'antd';

export function formatDateTime(isoString) {
  if (!isoString) return '—';
  try {
    const d = new Date(isoString);
    return isNaN(d.getTime()) ? isoString : d.toLocaleString();
  } catch {
    return isoString;
  }
}

export function getInvitationStatusTag(status) {
  switch (status) {
    case 'PENDING':
      return <Tag color="processing">Pending</Tag>;
    case 'ACCEPTED':
      return <Tag color="success">Accepted</Tag>;
    case 'DECLINED':
      return <Tag color="error">Declined</Tag>;
    case 'REVOKED':
      return <Tag color="default">Revoked</Tag>;
    case 'EXPIRED':
      return <Tag color="warning">Expired</Tag>;
    default:
      return <Tag>{status || 'Unknown'}</Tag>;
  }
}

/** The Kind column's words (W-73.4): who the invitation is for. */
export const INVITATION_KINDS = { USER: 'User', EMPLOYEE: 'Employee' };

export function InvitationTable({
  data = [],
  showKind = false,
  loading = false,
  onResend,
  onRevoke,
  resendingId = null,
  revokingId = null,
  emptyText = 'No invitations found',
}) {
  const columns = [
    {
      title: 'Recipient',
      dataIndex: 'email',
      key: 'email',
      render: (email) => email || '—',
    },
    ...(showKind
      ? [
          {
            title: 'Kind',
            dataIndex: 'kind',
            key: 'kind',
            render: (kind) => (
              <Tag color={kind === 'EMPLOYEE' ? 'blue' : 'purple'}>{INVITATION_KINDS[kind] || kind || '—'}</Tag>
            ),
          },
        ]
      : []),
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status) => getInvitationStatusTag(status),
    },
    {
      title: 'Expires At',
      dataIndex: 'expiresAt',
      key: 'expiresAt',
      render: (expiresAt) => formatDateTime(expiresAt),
    },
    {
      title: 'Created At',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (createdAt) => formatDateTime(createdAt),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record) => {
        if (record.status !== 'PENDING') {
          return '—';
        }
        return (
          <Space size="small">
            <Button
              size="small"
              id={`btn-resend-${record.id}`}
              loading={resendingId === record.id}
              onClick={() => onResend?.(record.id, record)}
            >
              Resend
            </Button>
            <Popconfirm
              title="Revoke invitation"
              description="Are you sure you want to revoke this invitation?"
              okText="Yes, revoke"
              cancelText="Cancel"
              okButtonProps={{ danger: true, id: `btn-confirm-revoke-${record.id}` }}
              onConfirm={() => onRevoke?.(record.id, record)}
            >
              <Button
                size="small"
                danger
                id={`btn-revoke-${record.id}`}
                loading={revokingId === record.id}
              >
                Revoke
              </Button>
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  return (
    <Table
      rowKey="id"
      columns={columns}
      dataSource={data}
      loading={loading}
      pagination={{ pageSize: 10, showSizeChanger: true }}
      locale={{ emptyText }}
    />
  );
}

InvitationTable.propTypes = {
  data: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.string.isRequired,
      email: PropTypes.string,
      kind: PropTypes.oneOf(['USER', 'EMPLOYEE']),
      status: PropTypes.string.isRequired,
      expiresAt: PropTypes.string,
      createdAt: PropTypes.string,
    }),
  ),
  loading: PropTypes.bool,
  showKind: PropTypes.bool,
  onResend: PropTypes.func,
  onRevoke: PropTypes.func,
  resendingId: PropTypes.string,
  revokingId: PropTypes.string,
  emptyText: PropTypes.string,
};
