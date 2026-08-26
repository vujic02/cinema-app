import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, get } from './client';
import {
  BookingRequest,
  BookingResponse,
  MovieResponse,
  MyBookingsResponse,
  SeatHoldResponse,
  SeatMapResponse,
  ShowingResponse,
  VenueResponse
} from './types';

/**
 * Every server read the customer flow makes, plus the three writes that change seats.
 *
 * These replace the `// TODO: replace with useQuery(...)` markers the handoff left in
 * `mockData.ts`. Nothing here is user-scoped by cache key: `AuthContext` clears the whole cache
 * on sign-in and sign-out, which is the only thing that can change whose data these return.
 */

export const queryKeys = {
  movies: (search?: string) => ['movies', search ?? null] as const,
  venues: () => ['venues'] as const,
  showings: (date: string, venueId: number | null) => ['showings', date, venueId] as const,
  seatMap: (showingId: number) => ['seat-map', showingId] as const,
  myBookings: () => ['bookings', 'me'] as const
};

export function useMovies(search?: string) {
  return useQuery({
    queryKey: queryKeys.movies(search),
    queryFn: () => get<MovieResponse[]>('/movies', { params: search ? { q: search } : undefined })
  });
}

export function useVenues() {
  return useQuery({
    queryKey: queryKeys.venues(),
    // The venue list changes about as often as a cinema chain opens a building.
    staleTime: 5 * 60 * 1000,
    queryFn: () => get<VenueResponse[]>('/venues')
  });
}

/**
 * One day's schedule. The listing groups these by movie itself rather than asking for
 * `/movies` too — `ShowingResponse` already carries the title, rating and poster hue the cards
 * need, which is why the backend embeds them.
 */
export function useShowings(date: string, venueId: number | null) {
  return useQuery({
    queryKey: queryKeys.showings(date, venueId),
    queryFn: () =>
      get<ShowingResponse[]>('/showings', {
        params: venueId === null ? { date } : { date, venueId }
      })
  });
}

/**
 * The seat picker's whole payload. Refetched on window focus and never cached stale, because
 * between opening the tab and coming back to it somebody else may have taken a seat — Part 8
 * replaces the polling gap with live frames on `/topic/showings/{id}`.
 */
export function useSeatMap(showingId: number | null) {
  return useQuery({
    queryKey: queryKeys.seatMap(showingId ?? 0),
    enabled: showingId !== null,
    staleTime: 0,
    refetchOnWindowFocus: true,
    queryFn: () => get<SeatMapResponse>(`/showings/${showingId}/seat-map`)
  });
}

export function useMyBookings() {
  return useQuery({
    queryKey: queryKeys.myBookings(),
    queryFn: () => get<MyBookingsResponse>('/bookings/me')
  });
}

/**
 * Claim a seat. A 409 (SEAT_HELD / SEAT_SOLD / HOLD_LIMIT_REACHED / SHOWING_STARTED) is the
 * expected losing case, not an exception — the caller reverts its optimistic selection and
 * shows the reason.
 */
export function useHoldSeat(showingId: number) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (seatId: number) => {
      const { data } = await api.post<SeatHoldResponse>(`/showings/${showingId}/seats/${seatId}/hold`);
      return data;
    },
    onSuccess: hold => patchSeat(queryClient, showingId, hold.seatId, 'HELD', true),
    // Losing the race means the map is out of date by definition; go and get the truth.
    onError: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.seatMap(showingId) });
    }
  });
}

/** Give a seat back. Idempotent server-side, so a double-click cannot fail the second time. */
export function useReleaseSeat(showingId: number) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (seatId: number) => {
      await api.delete(`/showings/${showingId}/seats/${seatId}/hold`);
      return seatId;
    },
    onSuccess: seatId => patchSeat(queryClient, showingId, seatId, 'AVAILABLE', false),
    onError: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.seatMap(showingId) });
    }
  });
}

/**
 * Checkout. The request carries no price and no total — the server reads `showings.price` and
 * snapshots it (TECH.md §6), so the amount on the confirmation is the server's number.
 */
export function useConfirmBooking() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (request: BookingRequest) => {
      const { data } = await api.post<BookingResponse>('/bookings', request);
      return data;
    },
    onSuccess: booking => {
      queryClient.invalidateQueries({ queryKey: queryKeys.myBookings() });
      queryClient.invalidateQueries({ queryKey: queryKeys.seatMap(booking.showingId) });
    }
  });
}

/**
 * Writes one seat's new status into the cached map instead of refetching it.
 *
 * A seat click would otherwise cost a full seat-map round trip, and the map is the largest
 * payload the customer flow fetches. The server has already confirmed the change by the time
 * this runs, so the local edit is a shortcut, not a guess.
 */
function patchSeat(
  queryClient: ReturnType<typeof useQueryClient>,
  showingId: number,
  seatId: number,
  status: SeatMapResponse['rows'][number]['seats'][number]['status'],
  heldByYou: boolean
) {
  queryClient.setQueryData<SeatMapResponse>(queryKeys.seatMap(showingId), previous => {
    if (!previous) return previous;

    const rows = previous.rows.map(row => ({
      ...row,
      seats: row.seats.map(seat => (seat.seatId === seatId ? { ...seat, status, heldByYou } : seat))
    }));

    return {
      ...previous,
      rows,
      // Recounted rather than nudged by ±1, so the header cannot drift out of step with the
      // grid it is counting if a patch is ever applied twice.
      availableCount: rows.reduce(
        (total, row) => total + row.seats.filter(seat => seat.status === 'AVAILABLE').length,
        0
      )
    };
  });
}
