/**
 * The model mismatch, resolved in one place.
 *
 * The handoff spoke in display strings — a showtime was literally `{ date: 'Today', time: '7:00
 * PM' }` — while the API returns an ISO instant and a numeric id. Rather than teaching every
 * screen to parse dates, the strings become a formatting concern that lives here and nowhere
 * else, and the state the app carries around is the id.
 *
 * ## Why everything is formatted in UTC
 *
 * The backend buckets showings into days at UTC midnight (`ShowingService.findPublic` →
 * `date.atStartOfDay(ZoneOffset.UTC)`). If the browser rendered `startTime` in its own zone, a
 * customer west of Greenwich would see a 20:00 UTC screening as a 15:00 showing filed under
 * "Today" by a query whose day ran on different hours than the clock next to it — and near
 * midnight the chip and the time printed under it would disagree about which day it is.
 *
 * Formatting in UTC keeps the filter and the label describing the same instant in the same
 * calendar. The real fix is a timezone column on `venues` so a showing renders in its own
 * cinema's local time; that is on the open-items list in `todo.md`, and this constant is the
 * single place it would be replaced.
 */
const DISPLAY_ZONE = 'UTC';

/** Number of day chips on the showings filter. */
const DAY_CHIP_COUNT = 5;

export interface DayChip {
  /** `yyyy-MM-dd`, exactly what `GET /api/showings?date=` expects. */
  value: string;
  /** "Today", "Tomorrow", then the short weekday. */
  label: string;
}

/** `yyyy-MM-dd` for an instant, in the display zone. */
export function toIsoDate(date: Date): string {
  // 'en-CA' is ISO-shaped by definition, which avoids hand-rolling zero padding while still
  // going through Intl for the zone conversion.
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: DISPLAY_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).format(date);
}

/**
 * The next few days starting today, as filter chips.
 *
 * Derived from the clock rather than hardcoded (the handoff shipped a literal
 * `['Today', 'Tomorrow', 'Wed', 'Thu', 'Fri']`, which was wrong on any day that was not a
 * Monday) and stepped in whole days from a UTC midnight so a DST transition cannot skip one.
 */
export function dayChips(now: Date = new Date(), count: number = DAY_CHIP_COUNT): DayChip[] {
  const startOfToday = new Date(`${toIsoDate(now)}T00:00:00Z`);

  return Array.from({ length: count }, (_, offset) => {
    const day = new Date(startOfToday);
    day.setUTCDate(day.getUTCDate() + offset);
    return {
      value: toIsoDate(day),
      label:
        offset === 0
          ? 'Today'
          : offset === 1
            ? 'Tomorrow'
            : new Intl.DateTimeFormat(undefined, { timeZone: DISPLAY_ZONE, weekday: 'short' }).format(day)
    };
  });
}

/** "7:00 PM" */
export function formatTime(iso: string): string {
  return new Intl.DateTimeFormat(undefined, {
    timeZone: DISPLAY_ZONE,
    hour: 'numeric',
    minute: '2-digit'
  }).format(new Date(iso));
}

/** "Aug 6" — the compact form used on booking cards. */
export function formatShortDate(iso: string): string {
  return new Intl.DateTimeFormat(undefined, {
    timeZone: DISPLAY_ZONE,
    month: 'short',
    day: 'numeric'
  }).format(new Date(iso));
}

/**
 * The day label a customer reads: "Today" and "Tomorrow" where they apply, "Aug 6" otherwise.
 * Relative to the same UTC day boundary the filter uses, so a showing under the Today chip is
 * never labelled with a date.
 */
export function formatDayLabel(iso: string, now: Date = new Date()): string {
  const isoDay = toIsoDate(new Date(iso));
  const today = toIsoDate(now);
  if (isoDay === today) return 'Today';

  const tomorrow = new Date(`${today}T00:00:00Z`);
  tomorrow.setUTCDate(tomorrow.getUTCDate() + 1);
  if (isoDay === toIsoDate(tomorrow)) return 'Tomorrow';

  return formatShortDate(iso);
}

/** "Today, 7:00 PM" — the one-line form used under a movie title. */
export function formatDayAndTime(iso: string, now: Date = new Date()): string {
  return `${formatDayLabel(iso, now)}, ${formatTime(iso)}`;
}

/** "1h 58m", from the API's flat minute count. */
export function formatDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return hours === 0 ? `${rest}m` : rest === 0 ? `${hours}h` : `${hours}h ${rest}m`;
}

/** Prices come off `showings.price` as JSON numbers; tickets are whole-cent amounts. */
export function formatMoney(amount: number): string {
  return `$${amount.toFixed(2)}`;
}
