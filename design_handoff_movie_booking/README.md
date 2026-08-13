# Handoff: Movie Ticket Booking — Customer Flow

## Overview
Six customer-facing screens for a movie ticket booking app: Login/Sign Up, Showings (home), Seat Selection, Checkout, Booking Confirmation, and My Bookings. Originally prototyped as an HTML design; this bundle is the same flow implemented as real React + TypeScript + Tailwind source, matching your stated stack.

## Fidelity
**High-fidelity.** Layout, copy, seat-map behavior, and screen flow match the approved prototype. Exact colors use a plain Tailwind palette (zinc/teal/red/emerald) rather than the prototype's oklch values — swap in your design tokens / tailwind.config theme.colors if you need pixel-exact parity.

## Stack assumptions
- React 18 + TypeScript, Tailwind CSS (`darkMode: 'class'`)
- `react-router-dom` v6 for navigation between screens
- Local React Context for auth/theme/booking-selection state (per your note: no external state library)
- Data is mocked in `src/data/mockData.ts`. Where you'll wire TanStack Query, the fetch shape is stubbed with a comment (`// TODO: replace with useQuery(...)`) in each page that reads data
- Seat Selection has a `// TODO: subscribe to STOMP/SockJS topic for live seat updates` marker where the WebSocket hook should attach — it should update the same `seatStatus` map used for optimistic local selection

## Screens
1. **LoginPage** — email/password form, tab toggle between Log In / Sign Up. Cosmetic only, no real validation — submit navigates to Showings.
2. **ShowingsPage** — grid/list toggle, date filter chips, venue filter, movie cards with clickable showtime chips → Seat Selection.
3. **SeatSelectionPage** — configurable venue layout (rows/seatsPerRow/aisle position in `mockData.ts` → `VENUE_LAYOUT`), legend: white = available, red = reserved, emerald = selected. Proceed button disabled until ≥1 seat selected.
4. **CheckoutPage** — order summary (movie, showing, seats, total), Confirm Purchase → Confirmation.
5. **ConfirmationPage** — success state, ticket card with QR placeholder and booking reference.
6. **MyBookingsPage** — Upcoming/Past tabs, list of bookings, "View Ticket" reopens Confirmation for that booking.

## State
`BookingContext` holds: `selectedMovie`, `selectedShowtime`, `selectedSeats`, `bookings`, `confirmation`, plus actions (`selectShowtime`, `toggleSeat`, `confirmPurchase`, `viewBooking`). `ThemeContext` holds light/dark and toggles the `dark` class on `<html>`, persisted to `localStorage`.

## Design tokens (Tailwind)
- Background: `bg-white dark:bg-zinc-950`
- Surface: `bg-zinc-50 dark:bg-zinc-900`, border `border-zinc-200 dark:border-zinc-800`
- Text: `text-zinc-900 dark:text-zinc-50`, muted `text-zinc-500 dark:text-zinc-400`
- Accent (brand/buttons): `teal-500` / `teal-600`
- Seat reserved: `red-500`; seat selected: `emerald-500`; seat available: white/zinc-100 with border

## Assets
Poster art is a gradient placeholder (`div` with Tailwind gradient classes) — swap for real poster `<img>`s. QR code is a placeholder block — wire up a real QR generator (e.g. `qrcode.react`) with the booking reference.

## Files
See `src/` — `pages/` (one file per screen), `context/`, `components/NavBar.tsx`, `data/mockData.ts`, `types.ts`, `App.tsx`.
