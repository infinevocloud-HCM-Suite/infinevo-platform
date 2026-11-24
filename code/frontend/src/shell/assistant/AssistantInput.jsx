import { forwardRef, useState } from 'react';
import PropTypes from 'prop-types';
import { Button, Input } from 'antd';
import { SendOutlined } from '@ant-design/icons';

/** The longest question the server takes (`AiMessageRequest`, W-114.4). */
export const MAX_QUESTION = 2000;

/**
 * The question box (W-114.5): Enter sends, Shift+Enter adds a line, at most 2,000 characters.
 * Disabled while an answer is pending, or when the helper is off or the month's limit is reached.
 */
export const AssistantInput = forwardRef(function AssistantInput({ disabled, onSend }, ref) {
  const [value, setValue] = useState('');
  const question = value.trim();

  const send = () => {
    if (disabled || !question) return;
    onSend(question);
    setValue('');
  };

  const onKeyDown = (event) => {
    if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent?.isComposing) return;
    event.preventDefault();
    send();
  };

  return (
    <div className="assistant-input">
      <Input.TextArea
        ref={ref}
        id="input-ai-question"
        aria-label="Your question"
        placeholder="Ask a question"
        value={value}
        maxLength={MAX_QUESTION}
        autoSize={{ minRows: 1, maxRows: 6 }}
        disabled={disabled}
        onChange={(event) => setValue(event.target.value)}
        onKeyDown={onKeyDown}
      />
      <Button
        type="primary"
        id="btn-ai-send"
        aria-label="Send"
        icon={<SendOutlined />}
        disabled={disabled || !question}
        onClick={send}
      >
        Send
      </Button>
    </div>
  );
});

AssistantInput.propTypes = {
  disabled: PropTypes.bool,
  onSend: PropTypes.func.isRequired,
};
