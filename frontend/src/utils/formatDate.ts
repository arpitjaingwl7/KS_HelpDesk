const dateTimeFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
});

const dateFormatter = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' });

const relativeFormatter = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 365 * 24 * 3600],
  ['month', 30 * 24 * 3600],
  ['week', 7 * 24 * 3600],
  ['day', 24 * 3600],
  ['hour', 3600],
  ['minute', 60],
  ['second', 1],
];

function toDate(value: string | Date | null | undefined): Date | null {
  if (!value) return null;
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

/** "Oct 15, 2026, 9:30 AM" in the browser's locale and time zone. */
export function formatDateTime(value: string | Date | null | undefined, fallback = '—'): string {
  const date = toDate(value);
  return date ? dateTimeFormatter.format(date) : fallback;
}

/** "Oct 15, 2026". */
export function formatDate(value: string | Date | null | undefined, fallback = '—'): string {
  const date = toDate(value);
  return date ? dateFormatter.format(date) : fallback;
}

/** "2 hours ago", "yesterday", "just now". */
export function formatRelative(
  value: string | Date | null | undefined,
  now: Date = new Date(),
  fallback = '—',
): string {
  const date = toDate(value);
  if (!date) return fallback;
  const diffSeconds = Math.round((date.getTime() - now.getTime()) / 1000);
  if (Math.abs(diffSeconds) < 45) return 'just now';
  for (const [unit, secondsInUnit] of UNITS) {
    if (Math.abs(diffSeconds) >= secondsInUnit || unit === 'second') {
      return relativeFormatter.format(Math.round(diffSeconds / secondsInUnit), unit);
    }
  }
  return fallback;
}
