import PropTypes from 'prop-types';
import { Card, Tag, Typography, Progress } from 'antd';
import { ClockCircleOutlined } from '@ant-design/icons';

const { Text } = Typography;

function formatDayNumber(val) {
  const n = Number(val);
  if (!Number.isFinite(n)) return '0';
  return Number.isInteger(n) ? String(n) : n.toFixed(1);
}

/**
 * Mobile Leave Balance Card (W-98.3 §4.2)
 * Horizontal-scroll swipeable card displaying category balances and consumption progress.
 */
export function LeaveBalanceCard({ balance, onClick = null, isSelected = false }) {
  if (!balance) return null;

  const available = Number(balance.availableDays ?? 0);
  const consumed = Number(balance.consumedDays ?? 0);
  const total = Number(balance.totalDays ?? (consumed + Number(balance.remainingDays ?? 0)));
  const pending = Number(balance.pendingDays ?? 0);
  const isExpired = !!balance.expired;

  const percent = total > 0 ? Math.min(100, Math.round((consumed / total) * 100)) : 0;

  return (
    <Card
      className={`mobile-leave-balance-card ${isSelected ? 'selected' : ''}`}
      data-testid="leave-balance-card"
      onClick={onClick}
      hoverable={!!onClick}
      style={{
        borderRadius: 14,
        minWidth: 200,
        maxWidth: 240,
        flex: '0 0 auto',
        cursor: onClick ? 'pointer' : 'default',
        boxShadow: isSelected ? '0 0 0 2px #1677ff' : '0 2px 8px rgba(0,0,0,0.06)',
        border: isSelected ? '1px solid #1677ff' : '1px solid #f0f0f0',
        background: isExpired
          ? '#fafafa'
          : 'linear-gradient(145deg, #ffffff 0%, #f6faff 100%)',
      }}
      styles={{ body: { padding: '14px 16px' } }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <Text strong style={{ fontSize: 15, color: '#1f2937' }} ellipsis={{ tooltip: true }}>
          {balance.leaveTypeName || balance.leaveTypeCode || 'Leave'}
        </Text>
        {isExpired ? (
          <Tag color="error">Expired</Tag>
        ) : (
          <Tag color="blue" style={{ borderRadius: 6, fontWeight: 600 }}>
            {balance.leaveTypeCode || 'LEAVE'}
          </Tag>
        )}
      </div>

      <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, marginBottom: 8 }}>
        <span
          data-testid="balance-available-days"
          style={{
            fontSize: 28,
            fontWeight: 700,
            lineHeight: 1,
            color: isExpired ? '#8c8c8c' : '#1677ff',
          }}
        >
          {formatDayNumber(available)}
        </span>
        <Text type="secondary" style={{ fontSize: 13 }}>
          {isExpired ? 'days (expired)' : 'days available'}
        </Text>
      </div>

      <div style={{ marginBottom: 8 }}>
        <Progress
          percent={percent}
          size="small"
          showInfo={false}
          strokeColor={isExpired ? '#d9d9d9' : '#1677ff'}
          trailColor="#e5e7eb"
        />
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: '#6b7280', marginTop: 4 }}>
          <span>Used: {formatDayNumber(consumed)}d</span>
          <span>Total: {formatDayNumber(total)}d</span>
        </div>
      </div>

      {pending > 0 && (
        <div style={{ marginTop: 6 }}>
          <Tag color="warning" icon={<ClockCircleOutlined />} style={{ borderRadius: 6 }}>
            {formatDayNumber(pending)} pending approval
          </Tag>
        </div>
      )}
    </Card>
  );
}

LeaveBalanceCard.propTypes = {
  balance: PropTypes.shape({
    leaveTypeId: PropTypes.string,
    leaveTypeCode: PropTypes.string,
    leaveTypeName: PropTypes.string,
    availableDays: PropTypes.number,
    remainingDays: PropTypes.number,
    consumedDays: PropTypes.number,
    totalDays: PropTypes.number,
    pendingDays: PropTypes.number,
    expired: PropTypes.bool,
  }).isRequired,
  onClick: PropTypes.func,
  isSelected: PropTypes.bool,
};

