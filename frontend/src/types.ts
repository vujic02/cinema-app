export type Theme = 'light' | 'dark';

export interface Showtime {
  venue: string;
  date: string;
  time: string;
}

export interface Movie {
  id: string;
  title: string;
  genre: string;
  rating: string;
  duration: string;
  hue: number;
  showtimes: Showtime[];
}

export type SeatState = 'available' | 'reserved' | 'selected';

export interface SeatCell {
  id: string;
  row: string;
  num: number;
  isAisleGap?: boolean;
}

export interface VenueLayout {
  rows: string[];
  seatsPerRow: number;
  aisleAfterSeat: number;
  reservedSeatIds: string[];
}

export type BookingStatus = 'upcoming' | 'past';

export interface Booking {
  id: string;
  status: BookingStatus;
  movieTitle: string;
  hue: number;
  venue: string;
  date: string;
  time: string;
  seats: string[];
  total: number;
  ref: string;
}
