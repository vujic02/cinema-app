import React from 'react';
import { useNavigate } from 'react-router-dom';
import { posterGradient } from '../data/mockData';
import { useBooking } from '../context/BookingContext';

export default function ConfirmationPage() {
  const { confirmation } = useBooking();
  const navigate = useNavigate();

  if (!confirmation) {
    return <p className="text-center text-sm text-zinc-500 dark:text-zinc-400">No booking to show yet — complete a checkout first.</p>;
  }

  return (
    <div className="mx-auto max-w-md text-center">
      <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-emerald-500">
        <div className="h-2.5 w-4.5 -translate-y-0.5 translate-x-0.5 rotate-[-45deg] border-b-4 border-l-4 border-emerald-950" style={{ width: 18, height: 10 }} />
      </div>
      <h1 className="mb-1 text-xl font-semibold">You're all set!</h1>
      <p className="mb-7 text-sm text-zinc-500 dark:text-zinc-400">Your tickets have been booked</p>

      <div className="rounded-2xl border border-dashed border-zinc-300 bg-zinc-50 p-6 text-left dark:border-zinc-700 dark:bg-zinc-900">
        <div className="mb-4.5 flex gap-3.5">
          <div style={posterGradient(confirmation.hue)} className="flex h-19 w-14.5 flex-shrink-0 items-center justify-center rounded-lg font-mono text-[8px] text-white/50" style={{ width: 58, height: 76 }}>
            POSTER
          </div>
          <div>
            <h3 className="text-lg font-semibold">{confirmation.movieTitle}</h3>
            <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">{confirmation.venue} · {confirmation.date}, {confirmation.time}</p>
            <p className="text-sm text-zinc-500 dark:text-zinc-400">Seats {confirmation.seats.join(', ')} · ${confirmation.total}</p>
          </div>
        </div>
        <div className="flex items-center gap-4 border-t border-zinc-200 pt-4.5 dark:border-zinc-800">
          {/* TODO: replace with a real QR code (e.g. qrcode.react) encoding the booking ref */}
          <div className="flex h-19 w-19 flex-shrink-0 items-center justify-center rounded-lg bg-[repeating-linear-gradient(45deg,theme(colors.zinc.200),theme(colors.zinc.200)_6px,theme(colors.zinc.300)_6px,theme(colors.zinc.300)_12px)] dark:bg-[repeating-linear-gradient(45deg,theme(colors.zinc.800),theme(colors.zinc.800)_6px,theme(colors.zinc.700)_6px,theme(colors.zinc.700)_12px)]" style={{ width: 76, height: 76 }}>
            <span className="rounded bg-zinc-50 px-1 py-0.5 font-mono text-[9px] text-zinc-500 dark:bg-zinc-900 dark:text-zinc-400">QR</span>
          </div>
          <div>
            <p className="text-[11px] tracking-wide text-zinc-500 dark:text-zinc-400">BOOKING REF</p>
            <p className="mt-0.5 font-mono text-base font-bold">{confirmation.ref}</p>
          </div>
        </div>
      </div>

      <div className="mt-6 flex gap-2.5">
        <button onClick={() => navigate('/bookings')} className="flex-1 rounded-lg border border-zinc-300 py-3 text-sm font-semibold dark:border-zinc-700">My Bookings</button>
        <button onClick={() => navigate('/showings')} className="flex-1 rounded-lg bg-teal-500 py-3 text-sm font-bold text-zinc-950">Back to Showings</button>
      </div>
    </div>
  );
}
