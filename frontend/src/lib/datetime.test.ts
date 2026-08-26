import { dayChips, formatDayAndTime, formatDayLabel, formatDuration, formatMoney, formatShortDate, formatTime, toIsoDate } from './datetime';

/**
 * These functions are the seam where the API's ISO instants become the strings the handoff
 * hardcoded, so what is pinned here is the *agreement with the backend*: the chip values have
 * to be the `yyyy-MM-dd` that `GET /api/showings?date=` buckets on, and the labels have to
 * describe the same UTC day that query does.
 *
 * A fixed `now` is passed in everywhere rather than faking timers — the relative labels are the
 * only clock-dependent behaviour, and they already take `now` as an argument for exactly this.
 */

// A Wednesday, mid-morning UTC.
const NOW = new Date('2026-08-26T10:15:00Z');

describe('toIsoDate', () => {
  it('formats in UTC, not the runner\'s zone', () => {
    // 23:30 UTC is the next day in Sydney and the same day in London. The backend buckets on
    // UTC, so this must be the 26th regardless of where the test runs.
    expect(toIsoDate(new Date('2026-08-26T23:30:00Z'))).toBe('2026-08-26');
    expect(toIsoDate(new Date('2026-08-27T00:30:00Z'))).toBe('2026-08-27');
  });

  it('zero-pads single-digit months and days', () => {
    expect(toIsoDate(new Date('2026-01-05T12:00:00Z'))).toBe('2026-01-05');
  });
});

describe('dayChips', () => {
  it('starts at today and steps one calendar day at a time', () => {
    expect(dayChips(NOW).map(chip => chip.value)).toEqual([
      '2026-08-26',
      '2026-08-27',
      '2026-08-28',
      '2026-08-29',
      '2026-08-30'
    ]);
  });

  it('labels the first two days relatively and the rest by weekday', () => {
    const labels = dayChips(NOW).map(chip => chip.label);

    expect(labels[0]).toBe('Today');
    expect(labels[1]).toBe('Tomorrow');
    // The handoff hardcoded ['Today','Tomorrow','Wed','Thu','Fri'], which was only ever right
    // on a Monday. From a Wednesday the third chip is Friday.
    expect(labels.slice(2)).toEqual(['Fri', 'Sat', 'Sun']);
  });

  it('crosses a month boundary without producing a 32nd', () => {
    expect(dayChips(new Date('2026-08-30T22:00:00Z'), 3).map(chip => chip.value)).toEqual([
      '2026-08-30',
      '2026-08-31',
      '2026-09-01'
    ]);
  });

  it('crosses a leap day', () => {
    expect(dayChips(new Date('2028-02-28T00:00:00Z'), 3).map(chip => chip.value)).toEqual([
      '2028-02-28',
      '2028-02-29',
      '2028-03-01'
    ]);
  });
});

describe('formatDayLabel', () => {
  it('agrees with the chip a showing was filtered under', () => {
    expect(formatDayLabel('2026-08-26T19:00:00Z', NOW)).toBe('Today');
    expect(formatDayLabel('2026-08-27T02:00:00Z', NOW)).toBe('Tomorrow');
  });

  /**
   * The case the UTC decision exists for: a 23:30 screening is still today's for the query that
   * returned it, so the label under it must not say tomorrow just because the browser sits east
   * of Greenwich.
   */
  it('keeps a late-evening showing on the day its query bucketed it', () => {
    expect(formatDayLabel('2026-08-26T23:30:00Z', NOW)).toBe('Today');
  });

  it('falls back to a short date beyond tomorrow', () => {
    expect(formatDayLabel('2026-08-29T19:00:00Z', NOW)).toBe(formatShortDate('2026-08-29T19:00:00Z'));
  });
});

describe('formatTime', () => {
  it('renders the UTC wall-clock time of the instant', () => {
    // Asserted through the same Intl call the app uses, so the test states "19:00 UTC" without
    // hardcoding whether this runner spells it "7:00 PM" or "19:00".
    const expected = new Intl.DateTimeFormat(undefined, {
      timeZone: 'UTC',
      hour: 'numeric',
      minute: '2-digit'
    }).format(new Date('2026-08-26T19:00:00Z'));

    expect(formatTime('2026-08-26T19:00:00Z')).toBe(expected);
  });
});

describe('formatDayAndTime', () => {
  it('joins the day label and the time', () => {
    expect(formatDayAndTime('2026-08-26T19:00:00Z', NOW)).toBe(`Today, ${formatTime('2026-08-26T19:00:00Z')}`);
  });
});

describe('formatDuration', () => {
  it('splits the API\'s flat minute count into hours and minutes', () => {
    expect(formatDuration(118)).toBe('1h 58m');
    expect(formatDuration(45)).toBe('45m');
    expect(formatDuration(120)).toBe('2h');
  });
});

describe('formatMoney', () => {
  it('always shows cents, so $14 and $14.50 line up in a column', () => {
    expect(formatMoney(14)).toBe('$14.00');
    expect(formatMoney(14.5)).toBe('$14.50');
    expect(formatMoney(0)).toBe('$0.00');
  });
});
