import { describe, it, expect, vi } from 'vitest';
import Swal from 'sweetalert2';
import { successMsg, errorMsg } from './msgHelper.js';

describe('msgHelper', () => {
  it('successMsg triggers Swal.fire with success icon and title/text', () => {
    const spy = vi.spyOn(Swal, 'fire').mockResolvedValueOnce({ isConfirmed: true });
    successMsg('Operation Complete', 'Record saved successfully');

    expect(spy).toHaveBeenCalledWith(
      expect.objectContaining({
        title: 'Operation Complete',
        text: 'Record saved successfully',
        icon: 'success',
      }),
    );
  });

  it('errorMsg(err) shows err.message and includes traceId', () => {
    const spy = vi.spyOn(Swal, 'fire').mockResolvedValueOnce({ isConfirmed: true });
    const err = {
      code: 'VALIDATION_FAILED',
      message: 'Invalid salary amount',
      traceId: 'trace-98765',
    };

    errorMsg(err);

    expect(spy).toHaveBeenCalledWith(
      expect.objectContaining({
        title: 'Validation Error',
        text: expect.stringContaining('Invalid salary amount'),
        icon: 'error',
      }),
    );

    // Verify traceId is also present in text
    const calledArgs = spy.mock.calls[0][0];
    expect(calledArgs.text).toContain('trace-98765');
  });

  it('errorMsg(title, text) triggers Swal.fire with error icon', () => {
    const spy = vi.spyOn(Swal, 'fire').mockResolvedValueOnce({ isConfirmed: true });
    errorMsg('Custom Error', 'Manual error message');

    expect(spy).toHaveBeenCalledWith(
      expect.objectContaining({
        title: 'Custom Error',
        text: 'Manual error message',
        icon: 'error',
      }),
    );
  });
});
