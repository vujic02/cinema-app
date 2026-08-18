import { Movie, VenueLayout, Booking } from '../types';

// Flat price for the mock data only. The backend is authoritative — `showings.price` per
// TECH.md §6 — and this constant goes away when Part 7 swaps mockData for the real API.
export const SEAT_PRICE = 14;

// TODO: replace with useQuery(['movies'], fetchMovies) once wired to the API
export const MOVIES: Movie[] = [
  { id: 'm1', title: 'Midnight Ember', genre: 'Thriller', rating: 'PG-13', duration: '118 min', hue: 12,
    showtimes: [{ venue: 'Downtown 8', date: 'Today', time: '7:00 PM' }, { venue: 'Downtown 8', date: 'Today', time: '9:45 PM' }, { venue: 'Riverside IMAX', date: 'Tomorrow', time: '6:30 PM' }] },
  { id: 'm2', title: 'Salt & Static', genre: 'Drama', rating: 'R', duration: '104 min', hue: 210,
    showtimes: [{ venue: 'Uptown Cineplex', date: 'Today', time: '5:15 PM' }, { venue: 'Downtown 8', date: 'Tomorrow', time: '8:00 PM' }] },
  { id: 'm3', title: 'Comet Line', genre: 'Sci-Fi', rating: 'PG-13', duration: '132 min', hue: 200,
    showtimes: [{ venue: 'Riverside IMAX', date: 'Today', time: '4:00 PM' }, { venue: 'Riverside IMAX', date: 'Today', time: '7:30 PM' }, { venue: 'Uptown Cineplex', date: 'Tomorrow', time: '9:00 PM' }] },
  { id: 'm4', title: 'Paper Tigers', genre: 'Comedy', rating: 'PG-13', duration: '97 min', hue: 140,
    showtimes: [{ venue: 'Uptown Cineplex', date: 'Today', time: '5:45 PM' }, { venue: 'Downtown 8', date: 'Tomorrow', time: '7:15 PM' }] },
  { id: 'm5', title: 'The Long Thaw', genre: 'Drama', rating: 'PG', duration: '121 min', hue: 35,
    showtimes: [{ venue: 'Downtown 8', date: 'Tomorrow', time: '6:00 PM' }, { venue: 'Riverside IMAX', date: 'Tomorrow', time: '8:45 PM' }] }
];

/**
 * Derived from the showtimes rather than typed out again, so the venue filter cannot drift from
 * the data it filters. The handoff hardcoded three venues as <option> elements beside a
 * <select> that was wired to nothing at all.
 */
export const VENUES: string[] = [
  ...new Set(MOVIES.flatMap(movie => movie.showtimes.map(showtime => showtime.venue)))
].sort();

// Configurable per-venue seat map — swap for the layout the Venues admin screen configures.
export const VENUE_LAYOUT: VenueLayout = {
  rows: ['A', 'B', 'C', 'D', 'E', 'F'],
  seatsPerRow: 8,
  aisleAfterSeat: 4,
  reservedSeatIds: ['A2', 'A3', 'B5', 'B6', 'B7', 'C1', 'C8', 'D4', 'D5', 'E2', 'E6', 'F3', 'F4', 'F7']
};

// TODO: replace with useQuery(['bookings'], fetchBookings)
export const INITIAL_BOOKINGS: Booking[] = [
  { id: 'b1', status: 'upcoming', movieTitle: 'Comet Line', hue: 200, venue: 'Riverside IMAX', date: 'Aug 6', time: '7:30 PM', seats: ['C4', 'C5'], total: 28, ref: 'LUM-77291' },
  { id: 'b2', status: 'past', movieTitle: 'Midnight Ember', hue: 12, venue: 'Downtown 8', date: 'Jul 18', time: '9:00 PM', seats: ['D2'], total: 14, ref: 'LUM-40218' },
  { id: 'b3', status: 'past', movieTitle: 'Paper Tigers', hue: 140, venue: 'Uptown Cineplex', date: 'Jul 2', time: '5:15 PM', seats: ['A6', 'A7', 'A8'], total: 42, ref: 'LUM-11029' }
];

/**
 * Stays after Part 7 deletes the rest of this file: poster art is a gradient placeholder keyed
 * off `movies.poster_hue`, which the backend serves and the UI renders.
 */
export function posterGradient(hue: number) {
  return { backgroundImage: `linear-gradient(160deg, hsl(${hue} 45% 32%) 0%, hsl(${hue} 40% 18%) 100%)` };
}
