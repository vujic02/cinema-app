import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MOVIES, VENUES, posterGradient } from '../data/mockData';
import { useBooking } from '../context/BookingContext';
import { Showtime, Movie } from '../types';

const DATES = ['Today', 'Tomorrow', 'Wed', 'Thu', 'Fri'];
const ALL_VENUES = 'All venues';

export default function ShowingsPage() {
  const [view, setView] = useState<'grid' | 'list'>('grid');
  const [dateFilter, setDateFilter] = useState('Today');
  const [venueFilter, setVenueFilter] = useState(ALL_VENUES);
  const { selectShowtime } = useBooking();
  const navigate = useNavigate();

  /**
   * Both filters now actually filter. In the handoff `dateFilter` was set by the chips and read
   * by nothing, and the venue `<select>` had no `value` or `onChange` at all — the movie list
   * rendered every showtime regardless of either.
   *
   * A movie drops out entirely once none of its showtimes match, rather than showing up with an
   * empty row of chips.
   */
  const visible = useMemo(() => {
    return MOVIES.map(movie => ({
      movie,
      showtimes: movie.showtimes.filter(
        st => st.date === dateFilter && (venueFilter === ALL_VENUES || st.venue === venueFilter)
      )
    })).filter(entry => entry.showtimes.length > 0);
  }, [dateFilter, venueFilter]);

  function pick(movie: Movie, showtime: Showtime) {
    selectShowtime(movie, showtime);
    navigate('/seats');
  }

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-semibold">Now Showing</h1>
          <p className="mt-1 text-sm text-muted">Pick a movie and showtime</p>
        </div>
        <div className="flex items-center gap-2.5">
          <select
            value={venueFilter}
            onChange={e => setVenueFilter(e.target.value)}
            aria-label="Filter by venue"
            className="rounded-lg border border-line-strong bg-raised px-3 py-2 text-sm"
          >
            <option>{ALL_VENUES}</option>
            {VENUES.map(venue => (
              <option key={venue}>{venue}</option>
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
        {DATES.map(d => (
          <button
            key={d}
            onClick={() => setDateFilter(d)}
            className={`whitespace-nowrap rounded-lg px-4 py-2 text-sm font-semibold ${
              dateFilter === d ? 'bg-accent text-accent-ink' : 'border border-line-strong bg-raised'
            }`}
          >
            {d}
          </button>
        ))}
      </div>

      {visible.length === 0 ? (
        <p className="py-16 text-center text-sm text-muted">
          Nothing showing on {dateFilter}
          {venueFilter === ALL_VENUES ? '' : ` at ${venueFilter}`}.
        </p>
      ) : (
        <div
          className={
            view === 'grid'
              ? 'grid grid-cols-[repeat(auto-fill,minmax(240px,1fr))] gap-5'
              : 'flex flex-col gap-3.5'
          }
        >
          {visible.map(({ movie, showtimes }) => (
            <div
              key={movie.id}
              className={`overflow-hidden rounded-2xl border border-line bg-surface ${
                view === 'grid' ? 'flex flex-col' : 'flex flex-row'
              }`}
            >
              <div
                style={posterGradient(movie.hue)}
                className={`flex items-center justify-center font-mono text-xs tracking-widest text-white/50 ${
                  view === 'grid' ? 'h-32' : 'w-28 flex-shrink-0'
                }`}
              >
                POSTER
              </div>
              <div className="flex flex-1 flex-col justify-between p-4">
                <div>
                  <h3 className="text-lg font-semibold">{movie.title}</h3>
                  <p className="mt-1 text-sm text-muted">
                    {movie.genre} · {movie.rating} · {movie.duration}
                  </p>
                </div>
                <div className="mt-3.5 flex flex-wrap gap-2">
                  {showtimes.map(st => (
                    <button
                      key={st.venue + st.time}
                      onClick={() => pick(movie, st)}
                      className="rounded-lg border border-line-strong bg-raised px-3 py-1.5 text-xs font-semibold hover:border-accent"
                    >
                      {st.time}
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
