import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios';
import { describe, expect, it, vi } from 'vitest';
import {
  applyFieldErrors,
  getProblem,
  hasCode,
  isProblemDetail,
  problemMessage,
  toFieldErrors,
  type ProblemDetail,
} from './problem';

const validationProblem: ProblemDetail = {
  type: 'https://helpdesk.local/problems/validation-failed',
  title: 'Validation failed',
  status: 400,
  detail: 'Request has 2 invalid fields',
  instance: '/api/v1/tickets',
  code: 'VALIDATION_FAILED',
  traceId: '6f1c2a9e4b7d4c1a',
  timestamp: '2026-10-15T09:30:00Z',
  errors: [
    { field: 'subject', message: 'size must be between 3 and 200', rejectedValue: 'Hi' },
    { field: 'priority', message: 'must be one of LOW, MEDIUM, HIGH, URGENT' },
    { field: 'subject', message: 'second message is ignored' },
  ],
};

function axiosErrorWith(status: number, data: unknown): AxiosError {
  const config = { headers: new AxiosHeaders() };
  const response = { status, statusText: 'Err', data, headers: {}, config } as AxiosResponse;
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', config, {}, response);
}

describe('toFieldErrors', () => {
  it('maps errors[] to a field → message record, first message wins', () => {
    expect(toFieldErrors(validationProblem)).toEqual({
      subject: 'size must be between 3 and 200',
      priority: 'must be one of LOW, MEDIUM, HIGH, URGENT',
    });
  });

  it('accepts a raw AxiosError carrying a problem+json body', () => {
    const err = axiosErrorWith(400, validationProblem);
    expect(toFieldErrors(err)).toHaveProperty('subject', 'size must be between 3 and 200');
  });

  it('returns an empty object when there are no field errors', () => {
    expect(toFieldErrors({ ...validationProblem, errors: undefined })).toEqual({});
    expect(toFieldErrors(new Error('boom'))).toEqual({});
    expect(toFieldErrors(null)).toEqual({});
  });

  it('skips malformed entries', () => {
    const problem = {
      ...validationProblem,
      errors: [
        { field: '', message: 'x' },
        { field: 'email', message: 'must be a well-formed email address' },
      ],
    };
    expect(toFieldErrors(problem)).toEqual({ email: 'must be a well-formed email address' });
  });
});

describe('getProblem / problemMessage', () => {
  it('extracts the ProblemDetail from an axios error', () => {
    const conflict = {
      ...validationProblem,
      status: 409,
      code: 'CONCURRENT_MODIFICATION',
      detail: 'Stale version',
      errors: undefined,
    };
    const err = axiosErrorWith(409, conflict);
    expect(isProblemDetail(conflict)).toBe(true);
    expect(getProblem(err)?.code).toBe('CONCURRENT_MODIFICATION');
    expect(hasCode(err, 'CONCURRENT_MODIFICATION')).toBe(true);
    expect(problemMessage(err)).toBe('Stale version');
  });

  it('synthesises a problem for network errors', () => {
    const err = new AxiosError('Network Error', 'ERR_NETWORK');
    expect(getProblem(err)).toMatchObject({ status: 0, code: 'NETWORK_ERROR' });
  });

  it('synthesises a problem for non-RFC7807 responses', () => {
    expect(getProblem(axiosErrorWith(502, '<html>Bad gateway</html>'))).toMatchObject({
      status: 502,
      code: 'INTERNAL_ERROR',
    });
  });
});

describe('applyFieldErrors', () => {
  it('sets known fields on the form and reports the rest', () => {
    const setError = vi.fn();
    const result = applyFieldErrors(validationProblem, setError, ['subject']);
    expect(setError).toHaveBeenCalledWith('subject', {
      type: 'server',
      message: 'size must be between 3 and 200',
    });
    expect(result).toEqual({
      mapped: 1,
      unmapped: ['priority: must be one of LOW, MEDIUM, HIGH, URGENT'],
    });
  });
});
