import PropTypes from 'prop-types';
import { Table, Tag, Button, Space, Popconfirm } from 'antd';
import { formatDateTime as sharedFormatDateTime } from '@shared/ui/format.js';
import { StatusTag } from '@shared/ui/StatusTag.jsx';

/** Kept for callers that import it from here; the shared helper does the work (D-78). */
export function formatDateTime(isoString) {
  return sharedFormatDateTime(isoString);
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
      return <StatusTag status={status} />;
  }
}

/** D-22: the finished states a row may be deleted from. PENDING is revoked first; ACCEPTED stays. */
export const DELETABLE_STATUSES = ['REVOKED', 'DECLINED', 'EXPIRED'];

/** A PENDING row past its expiry: the server flips it to EXPIRED only when its link is opened, so treat it as finished. */
export function isTimeExpired(record) {
  return record?.status === 'PENDING' && !!record?.expiresAt && new Date(record.expiresAt).getTime() < Date.now();
}

/** The Kind column's words (W-73.4): who the invitation is for. */
export const INVITATION_KINDS = { USER: 'User', EMPLOYEE: 'Employee' };

export function InvitationTable({
  data = [],
  showKind = false,
  loading = false,
  onResend,
  onRevoke,
  onDelete,
  resendingId = null,
  revokingId = null,
  deletingId = null,
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
        if (record.status !== 'PENDING' || isTimeExpired(record)) {
          // D-22: a finished invitation can be removed from the list; an accepted one is the account's record.
          if (!onDelete || (!DELETABLE_STATUSES.includes(record.status) && !isTimeExpired(record))) {
            return '—';
          }
          return (
            <Popconfirm
              title="Delete invitation"
              description="Remove this invitation from the list? This cannot be undone."
              okText="Yes, delete"
              cancelText="Cancel"
              okButtonProps={{ danger: true, id: `btn-confirm-delete-${record.id}` }}
              onConfirm={() => onDelete(record.id, record)}
            >
              <Button
                size="small"
                danger
                className="btn-spring"
                id={`btn-delete-${record.id}`}
                loading={deletingId === record.id}
              >
                Delete
              </Button>
            </Popconfirm>
          );
        }
        return (
          <Space size="small">
            <Button
              size="small"
              className="btn-spring"
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
                className="btn-spring"
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
      className="saas-table"
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
  onDelete: PropTypes.func,
  resendingId: PropTypes.string,
  revokingId: PropTypes.string,
  deletingId: PropTypes.string,
  emptyText: PropTypes.string,
};
