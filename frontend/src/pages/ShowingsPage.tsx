import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useShowings, useVenues } from '../api/hooks';
import { ShowingResponse } from '../api/types';
import { ErrorNotice, Loading } from '../components/QueryState';
import { useBooking } from '../context/BookingContext';
import { dayChips, formatDuration, formatTime } from '../lib/datetime';
import { Poster } from '../components/Poster';

const ALL_VENUES = 'All venues';

export default function ShowingsPage() {
  const [view, setView] = useState<'grid' | 'list'>('grid');
  // The chips are derived once per mount rather than per render, so the "Today" chip cannot
  // change identity underneath a selection while the page is open.
  const days = useMemo(() => dayChips(), []);
  const [date, setDate] = useState(days[0].value);
  const [venueId, setVenueId] = useState<number | null>(null);
  const { selectShowing } = useBooking();
  const navigate = useNavigate();

  const venues = useVenues();
  const showings = useShowings(date, venueId);

  /**
   * The API returns a flat, time-ordered list of showings; the screen is a list of *movies* with
   * their showtimes as chips. Grouping happens here because `ShowingResponse` already embeds the
   * title, genre, rating and poster hue each card needs — the alternative would be a second
   * request to `/movies` and a join in the browser.
   *
   * Showings that have already started are dropped: the backend refuses to hold a seat for one
   * (`SHOWING_STARTED`, in both `SeatService` and `BookingService`), so offering them would be
   * a button that can only fail. They still come back from the API because a date filter means
   * the whole calendar day, midnight to midnight.
   */
  const moviesWithShowtimes = useMemo(() => {
    const now = Date.now();
    const byMovie = new Map<number, { movie: ShowingResponse['movie']; showings: ShowingResponse[] }>();

    for (const showing of showings.data ?? []) {
      if (new Date(showing.startTime).getTime() <= now) continue;
      const entry = byMovie.get(showing.movie.id);
      if (entry) entry.showings.push(showing);
      else byMovie.set(showing.movie.id, { movie: showing.movie, showings: [showing] });
    }

    return [...byMovie.values()];
  }, [showings.data]);

  function pick(showing: ShowingResponse) {
    selectShowing(showing);
    navigate('/seats');
  }

  const selectedVenueName = venues.data?.find(venue => venue.id === venueId)?.name;

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-semibold">Now Showing</h1>
          <p className="mt-1 text-sm text-muted">Pick a movie and showtime</p>
        </div>
        <div className="flex items-center gap-2.5">
          <select
            value={venueId ?? ''}
            onChange={e => setVenueId(e.target.value === '' ? null : Number(e.target.value))}
            aria-label="Filter by venue"
            className="rounded-lg border border-line-strong bg-raised px-3 py-2 text-sm"
          >
            <option value="">{ALL_VENUES}</option>
            {(venues.data ?? []).map(venue => (
              <option key={venue.id} value={venue.id}>
                {venue.name}
              </option>
            ))}
          </select>
          <div className="flex overflow-hidden rounded-lg border border-line-strong">
            <button
              onClick={() => setView('grid')}
              className={`px-3.5 py-2 text-xs font-semibold ${
                view === 'grid' ? 'bg-accent text-accent-ink' : 'bg-raised'
              }`}
            >
              Grid
            </button>
            <button
              onClick={() => setView('list')}
              className={`px-3.5 py-2 text-xs font-semibold ${
                view === 'list' ? 'bg-accent text-accent-ink' : 'bg-raised'
              }`}
            >
              List
            </button>
          </div>
        </div>
      </div>

      <div className="mb-7 flex gap-2 overflow-x-auto pb-1">
        {days.map(day => (
          <button
            key={day.value}
            onClick={() => setDate(day.value)}
            className={`whitespace-nowrap rounded-lg px-4 py-2 text-sm font-semibold ${
              date === day.value ? 'bg-accent text-accent-ink' : 'border border-line-strong bg-raised'
            }`}
          >
            {day.label}
          </button>
        ))}
      </div>

      {showings.isPending ? (
        <Loading label="Loading showtimes…" />
      ) : showings.isError ? (
        <ErrorNotice
          error={showings.error}
          fallback="Could not load showtimes."
          onRetry={() => showings.refetch()}
        />
      ) : moviesWithShowtimes.length === 0 ? (
        <p className="py-16 text-center text-sm text-muted">
          Nothing showing on {days.find(day => day.value === date)?.label ?? date}
          {selectedVenueName ? ` at ${selectedVenueName}` : ''}.
        </p>
      ) : (
        <div
          className={
            view === 'grid'
              ? 'grid grid-cols-[repeat(auto-fill,minmax(240px,1fr))] gap-5'
              : 'flex flex-col gap-3.5'
          }
        >
          {moviesWithShowtimes.map(({ movie, showings: showtimes }) => (
            <div
              key={movie.id}
              className={`overflow-hidden rounded-2xl border border-line bg-surface ${
                view === 'grid' ? 'flex flex-col' : 'flex flex-row'
              }`}
            >
              {/* Grid cards give the artwork a real 2:3 poster frame. The handoff used a 128px
                  banner, which was the right shape for a gradient and the wrong one for a poster:
                  it cropped every face off the top of the image. */}
              <Poster
                posterUrl={movie.posterUrl}
                posterHue={movie.posterHue}
                title={movie.title}
                className={view === 'grid' ? 'aspect-[2/3] w-full text-lg' : 'w-24 self-stretch text-base'}
              />
              <div className="flex flex-1 flex-col justify-between p-4">
                <div>
                  <h3 className="text-lg font-semibold">{movie.title}</h3>
                  <p className="mt-1 text-sm text-muted">
                    {movie.genre} · {movie.rating} · {formatDuration(movie.durationMinutes)}
                  </p>
                </div>
                <div className="mt-3.5 flex flex-wrap gap-2">
                  {showtimes.map(showing => (
                    <button
                      key={showing.id}
                      onClick={() => pick(showing)}
                      title={`${showing.venue.name} · ${formatTime(showing.startTime)}`}
                      className="rounded-lg border border-line-strong bg-raised px-3 py-1.5 text-xs font-semibold hover:border-accent"
                    >
                      {formatTime(showing.startTime)}
                      {/* With no venue filter one movie can list the same time at two cinemas,
                          so the venue has to be on the chip to tell them apart. */}
                      {venueId === null && (
                        <span className="ml-1.5 font-normal text-muted">{showing.venue.name}</span>
                      )}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
