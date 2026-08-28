/**
 * The wire shapes, mirroring the backend records one for one.
 *
 * Kept apart from `src/types.ts` on purpose: that file is the UI's own vocabulary, this one is
 * the API's. Where they disagree — the API speaks ISO instants and numeric ids, the screens
 * speak "Today" and "7:00 PM" — the conversion is explicit (`src/lib/datetime.ts`) rather than a
 * shape that quietly means both.
 *
 * `BigDecimal` fields (`price`, `total`) arrive as JSON numbers.
 */

export type Role = 'ADMIN' | 'CUSTOMER';
export type SeatStatus = 'AVAILABLE' | 'HELD' | 'SOLD';
export type SeatType = 'STANDARD' | 'ACCESSIBLE' | 'PREMIUM';
export type BookingState = 'HELD' | 'SOLD';

/** The single error shape every failed request returns (`common/exception/ApiError`). */
export interface ApiError {
  code: string;
  message: string;
  path: string;
  /** Present only on VALIDATION_FAILED: field name -> message. */
  fieldErrors?: Record<string, string>;
  timestamp: string;
}

export interface UserResponse {
  id: number;
  email: string;
  fullName: string;
  role: Role;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  /** Access-token lifetime in seconds. */
  expiresIn: number;
  user: UserResponse;
}

export interface MovieResponse {
  id: number;
  title: string;
  description: string;
  durationMinutes: number;
  genre: string;
  rating: string;
  /** Fallback gradient hue, painted under the artwork and shown alone when there is none. */
  posterHue: number;
  /**
   * TMDB artwork. Optional because the API omits null fields entirely
   * (`default-property-inclusion: non_null`), so a movie with no poster has no key at all.
   */
  posterUrl?: string | null;
}

export interface VenueResponse {
  id: number;
  name: string;
  address: string;
  rowCount: number;
  seatCount: number;
}

export interface ShowingResponse {
  id: number;
  /** ISO-8601 instant, UTC. */
  startTime: string;
  price: number;
  movie: {
    id: number;
    title: string;
    durationMinutes: number;
    genre: string;
    rating: string;
    posterHue: number;
    posterUrl?: string | null;
  };
  venue: { id: number; name: string };
}

export interface SeatView {
  seatId: number;
  seatNumber: number;
  /** Customer-facing identifier, e.g. "C4". */
  label: string;
  seatType: SeatType;
  aisleGap: boolean;
  status: SeatStatus;
  /** True only on an authenticated read, and only for the caller's own holds. */
  heldByYou: boolean;
}

export interface RowView {
  rowIndex: number;
  rowLabel: string;
  seats: SeatView[];
}

export interface SeatMapResponse {
  showingId: number;
  movieTitle: string;
  venueName: string;
  startTime: string;
  price: number;
  /** Server-configured hold lifetime — Part 8's countdown reads this, never a hardcoded 300. */
  holdTtlSeconds: number;
  availableCount: number;
  rows: RowView[];
}

export interface SeatHoldResponse {
  showingId: number;
  seatId: number;
  label: string;
  status: SeatStatus;
  expiresInSeconds: number;
  expiresAt: string;
}

export interface BookingResponse {
  reference: string;
  showingId: number;
  movieTitle: string;
  posterHue: number;
  posterUrl?: string | null;
  venueName: string;
  startTime: string;
  seats: { seatId: number; label: string }[];
  total: number;
  status: BookingState;
  upcoming: boolean;
  bookedAt: string;
  /** Admin views only; Jackson drops it entirely on a customer's own bookings. */
  customer?: { id: number; email: string; fullName: string };
}

export interface MyBookingsResponse {
  upcoming: BookingResponse[];
  past: BookingResponse[];
}

export interface BookingRequest {
  showingId: number;
  seatIds: number[];
}
