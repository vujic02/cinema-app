import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MOVIES, posterGradient } from '../data/mockData';
import { useBooking } from '../context/BookingContext';
import { Showtime, Movie } from '../types';

const DATES = ['Today', 'Tomorrow', 'Wed', 'Thu', 'Fri'];

export default function ShowingsPage() {
  const [view, setView] = useState<'grid' | 'list'>('grid');
  const [dateFilter, setDateFilter] = useState('Today');
  const { selectShowtime } = useBooking();
  const navigate = useNavigate();

  function pick(movie: Movie, showtime: Showtime) {
    selectShowtime(movie, showtime);
    navigate('/seats');
  }

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-semibold">Now Showing</h1>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">Pick a movie and showtime</p>
        </div>
        <div className="flex items-center gap-2.5">
          <select className="rounded-lg border border-zinc-300 bg-white px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-900">
            <option>All venues</option>
            <option>Downtown 8</option>
            <option>Riverside IMAX</option>
            <option>Uptown Cineplex</option>
          </select>
          <div className="flex overflow-hidden rounded-lg border border-zinc-300 dark:border-zinc-700">
            <button onClick={() => setView('grid')} className={`px-3.5 py-2 text-xs font-semibold ${view === 'grid' ? 'bg-teal-500 text-zinc-950' : 'bg-white dark:bg-zinc-900'}`}>Grid</button>
            <button onClick={() => setView('list')} className={`px-3.5 py-2 text-xs font-semibold ${view === 'list' ? 'bg-teal-500 text-zinc-950' : 'bg-white dark:bg-zinc-900'}`}>List</button>
          </div>
        </div>
      </div>

      <div className="mb-7 flex gap-2 overflow-x-auto pb-1">
        {DATES.map(d => (
          <button key={d} onClick={() => setDateFilter(d)} className={`whitespace-nowrap rounded-lg px-4 py-2 text-sm font-semibold ${dateFilter === d ? 'bg-teal-500 text-zinc-950' : 'border border-zinc-300 bg-white dark:border-zinc-700 dark:bg-zinc-900'}`}>
            {d}
          </button>
        ))}
      </div>

      <div className={view === 'grid' ? 'grid grid-cols-[repeat(auto-fill,minmax(240px,1fr))] gap-5' : 'flex flex-col gap-3.5'}>
        {MOVIES.map(movie => (
          <div key={movie.id} className={`overflow-hidden rounded-2xl border border-zinc-200 bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-900 ${view === 'grid' ? 'flex flex-col' : 'flex flex-row'}`}>
            <div style={posterGradient(movie.hue)} className={`flex items-center justify-center font-mono text-xs tracking-widest text-white/50 ${view === 'grid' ? 'h-32' : 'w-28 flex-shrink-0'}`}>
              POSTER
            </div>
            <div className="flex flex-1 flex-col justify-between p-4">
              <div>
                <h3 className="text-lg font-semibold">{movie.title}</h3>
                <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">{movie.genre} · {movie.rating} · {movie.duration}</p>
              </div>
              <div className="mt-3.5 flex flex-wrap gap-2">
                {movie.showtimes.map(st => (
                  <button key={st.venue + st.time} onClick={() => pick(movie, st)} className="rounded-lg border border-zinc-300 bg-white px-3 py-1.5 text-xs font-semibold dark:border-zinc-700 dark:bg-zinc-800">
                    {st.time}
                  </button>
                ))}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
