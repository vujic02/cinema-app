/**
 * The UI's own vocabulary. The API's shapes live in `src/api/types.ts`.
 *
 * Part 7 emptied most of this file: `Movie`, `Showtime`, `SeatCell`, `VenueLayout` and `Booking`
 * were the mock data's shapes, invented before there was a server to disagree with. They are
 * the API's records now, generated from the same records the backend returns, so keeping local
 * near-copies would only create two definitions that could drift.
 */

export type Theme = 'light' | 'dark';
