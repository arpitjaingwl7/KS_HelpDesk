import { isAxiosError } from 'axios';
import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';

export type ProblemCode =
  | 'VALIDATION_FAILED'
  | 'MALFORMED_REQUEST'
  | 'INVALID_SORT'
  | 'INVALID_ASSIGNEE'
  | 'INVALID_REQUESTER'
  | 'UNAUTHENTICATED'
  | 'INVALID_CREDENTIALS'
  | 'ACCESS_DENIED'
  | 'RESOURCE_NOT_FOUND'
  | 'INVALID_STATUS_TRANSITION'
  | 'TICKET_CLOSED'
  | 'CONCURRENT_MODIFICATION'
  | 'EMAIL_ALREADY_EXISTS'
  | 'SLUG_ALREADY_EXISTS'
  | 'LAST_ADMIN'
  | 'SELF_DEACTIVATION'
  | 'UNSUPPORTED_MEDIA_TYPE'
  | 'METHOD_NOT_ALLOWED'
  | 'INTERNAL_ERROR';

export interface FieldErrorItem {
  field: string;
  message: string;
  rejectedValue?: unknown;
}

/** RFC 7807 Problem Details as returned by the backend (`application/problem+json`). */
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  /** Typed as string so unknown future codes don't break parsing. */
  code: ProblemCode | (string & {});
  traceId?: string;
  timestamp?: string;
  errors?: FieldErrorItem[];
}

export function isProblemDetail(value: unknown): value is ProblemDetail {
  return (
    typeof value === 'object' &&
    value !== null &&
    !(value instanceof Error) && // AxiosError also has numeric `status` and string `code`
    typeof (value as { status?: unknown }).status === 'number' &&
    typeof (value as { code?: unknown }).code === 'string'
  );
}

/**
 * Extracts a ProblemDetail from anything thrown by the API layer.
 * Network failures and non-RFC7807 responses are normalised into a synthetic problem
 * so callers can always rely on `status` / `code` / `detail`.
 */
export function getProblem(error: unknown): ProblemDetail | null {
  if (!error) return null;
  if (isAxiosError(error)) {
    const data: unknown = error.response?.data;
    if (isProblemDetail(data)) return data;
    if (error.response) {
      return {
        type: 'about:blank',
        title: error.response.statusText || 'Request failed',
        status: error.response.status,
        detail: `The server responded with status ${error.response.status}.`,
        code: error.response.status >= 500 ? 'INTERNAL_ERROR' : 'UNKNOWN',
      };
    }
    return {
      type: 'about:blank',
      title: 'Network error',
      status: 0,
      detail: 'Could not reach the server. Check your connection and try again.',
      code: 'NETWORK_ERROR',
    };
  }
  if (isProblemDetail(error)) return error;
  return null;
}

/** Human-readable message for an error alert or toast. */
export function problemMessage(error: unknown, fallback = 'Something went wrong.'): string {
  const problem = getProblem(error);
  if (problem) return problem.detail || problem.title || fallback;
  if (error instanceof Error && error.message) return error.message;
  return fallback;
}

export function hasCode(error: unknown, code: ProblemCode): boolean {
  return getProblem(error)?.code === code;
}

/**
 * Turns `errors[]` from a 400 VALIDATION_FAILED problem into a `{ field: message }` map.
 * The first message wins when a field has several violations.
 * Accepts either a ProblemDetail or a raw thrown error.
 */
export function toFieldErrors(source: ProblemDetail | unknown): Record<string, string> {
  const problem = isProblemDetail(source) ? source : getProblem(source);
  const result: Record<string, string> = {};
  if (!problem?.errors) return result;
  for (const item of problem.errors) {
    if (!item || typeof item.field !== 'string' || !item.field) continue;
    if (!(item.field in result)) result[item.field] = item.message;
  }
  return result;
}

/**
 * Applies server field errors to a react-hook-form instance.
 * Only fields listed in `knownFields` are mapped; returns the messages for fields that
 * could not be mapped (so the caller can show them in a general alert).
 */
export function applyFieldErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  knownFields: readonly Path<T>[],
): { mapped: number; unmapped: string[] } {
  const fieldErrors = toFieldErrors(error);
  let mapped = 0;
  const unmapped: string[] = [];
  for (const [field, message] of Object.entries(fieldErrors)) {
    if ((knownFields as readonly string[]).includes(field)) {
      setError(field as Path<T>, { type: 'server', message });
      mapped += 1;
    } else {
      unmapped.push(`${field}: ${message}`);
    }
  }
  return { mapped, unmapped };
}

/**
 * 401 (session expired → redirect) and 403 (permission toast) are surfaced globally,
 * so forms can skip showing them inline.
 */
export function isGloballyHandled(error: unknown): boolean {
  const status = getProblem(error)?.status;
  return status === 401 || status === 403;
}
