# Frontend — Customer Flow

Six customer-facing screens: Log In / Sign Up, Showings (home), Seat Selection, Checkout,
Booking Confirmation, and My Bookings.

Started life as a design handoff bundle — loose React + TypeScript + Tailwind source with no
`package.json`, `index.html` or entry point. Part 6 turned it into a running Vite app, fixed the
defects listed at the bottom, and replaced the raw palette classes with design tokens.

## Run it

```
npm install
npm run dev        # http://localhost:5173
npm run build      # tsc --noEmit && vite build  →  dist/
npm run preview    # serve the production build
```

The dev server proxies `/api` and `/ws` to `http://localhost:8080`, mirroring the Nginx rules in
TECH.md §4. The browser therefore only ever talks to one origin, which is why the backend has no
CORS configuration. Nothing calls the API yet — data is still mocked (Part 7 wires it up).

## Stack

- React 18 + TypeScript, Vite 5
- Tailwind CSS 3 (`darkMode: 'class'`)
- `react-router-dom` v6
- React Context for theme and booking-selection state — no external state library
- TanStack Query, the real API client and the STOMP/SockJS hook arrive in Parts 7–8

## Design tokens

The handoff wrote raw palette names with a `dark:` twin on nearly every element
(`bg-white dark:bg-zinc-950`, `text-zinc-500 dark:text-zinc-400`). That is the same decision
repeated in hundreds of places, each of which can be got wrong independently — which is exactly
how the seat colours ended up contradicting this README.

Colours are now semantic tokens backed by CSS variables in `src/index.css`, mapped to Tailwind
in `tailwind.config.ts`. `bg-surface` is already right in both themes, so `dark:` has
disappeared from the markup entirely.

| Token | Job | Light | Dark |
|---|---|---|---|
| `page` | page background | white | zinc-950 |
| `surface` | cards, nav bar | zinc-50 | zinc-900 |
| `raised` | inputs, chips on a surface | white | zinc-800 |
| `sunken` | inset tab tracks | zinc-200 | zinc-800 |
| `elevated` | the active pill in a track | white | zinc-700 |
| `line` / `line-strong` | borders | zinc-200 / zinc-300 | zinc-800 / zinc-700 |
| `ink` / `muted` / `disabled` | text | zinc-900 / zinc-500 / zinc-400 | zinc-50 / zinc-400 / zinc-600 |
| `accent` / `accent-ink` / `accent-text` | brand fill, text on it, links | teal-500 / zinc-950 / teal-600 | teal-500 / zinc-950 / teal-400 |
| `ok` / `ok-ink` | success states | emerald-500 / emerald-950 | same |
| `seat-available` / `-reserved` / `-selected` / `-ink` | the seat map | white / red-500 / emerald-500 / zinc-900 | zinc-100 / red-500 / emerald-500 / zinc-900 |

A seat stays light in dark mode on purpose: a cinema seat map reads as pale seats in a dark
auditorium, and the seat number needs a light background either way.

`theme.spacing` is extended with `4.5`, `7.5`, `13`, `14.5`, `19` and `21` — the sizes the
handoff was already writing as class names without them existing.

## Screens

1. **LoginPage** — Log In / Sign Up toggle. Still cosmetic; Part 7 wires `AuthContext`.
2. **ShowingsPage** — grid/list toggle, date chips, venue filter, showtime chips → Seat Selection.
3. **SeatSelectionPage** — layout from `mockData.ts` → `VENUE_LAYOUT`. White = available,
   red = reserved, emerald = selected. Proceed disabled until ≥1 seat is picked.
4. **CheckoutPage** — order summary, Confirm Purchase → Confirmation.
5. **ConfirmationPage** — ticket card with QR placeholder and booking reference.
6. **MyBookingsPage** — Upcoming / Past tabs, "View Ticket" reopens Confirmation.

`/seats`, `/checkout` and `/confirmation` are flow steps, not destinations: they are guarded by
`RequireBookingState` and are not in the nav bar. Landing on `/` goes to Showings — browsing the
catalogue needs no account.

## State

`BookingContext` holds `selectedMovie`, `selectedShowtime`, `selectedSeats`, `bookings`,
`confirmation`, plus `selectShowtime`, `toggleSeat`, `confirmPurchase`, `viewBooking`.
`ThemeContext` holds light/dark, toggles the `dark` class on `<html>`, and persists to
`localStorage`; `index.html` applies the stored choice before first paint so there is no flash.

## Assets

Poster art is a gradient placeholder keyed off `movies.poster_hue` (`posterGradient`), which
survives the move to the real API. The QR code is a `.qr-placeholder` block — swap for a real
generator (e.g. `qrcode.react`) keyed by the booking reference.

## Defects fixed from the handoff bundle

- **`CheckoutPage` and `ConfirmationPage` posters were blank.** Each had two `style` attributes
  on one element; JSX keeps only the last, so the gradient lost to an inline width/height.
- **Eight class names did nothing.** `h-7.5`, `w-7.5`, `w-4.5`, `w-13`, `w-14.5`, `h-19`, `h-21`,
  `mb-4.5`, `pt-4.5` are not in Tailwind's default scale, so they silently produced no CSS. They
  are real values in `theme.spacing` now, and the inline overrides are gone.
- **Neither filter on `ShowingsPage` filtered.** `dateFilter` was set by the chips and read by
  nothing; the venue `<select>` had no `value` or `onChange` at all. Both work, the venue list is
  derived from the data, and there is an empty state for a date with no showings.
- **Available seats read as disabled.** `bg-zinc-200 dark:bg-zinc-100` with no border, against a
  README that specifies "white/zinc-100 with border" — the same grey as the disabled button.
- **The nav bar exposed the booking flow as tabs**, so Checkout could be opened cold and would
  confirm a purchase of nothing.
