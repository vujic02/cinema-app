import { useEffect, useState } from 'react';
import { posterGradient } from '../lib/poster';

/**
 * A movie's artwork, with the gradient underneath it.
 *
 * The backend serves both halves on every DTO that carries a movie: `posterUrl` from TMDB, and
 * `posterHue` as the gradient that predates it. The gradient is not dead weight — it is what
 * fills the frame while a 500px JPEG crosses the network, what a movie entered by hand through
 * the admin API has instead of artwork, and what is left if image.tmdb.org is unreachable.
 *
 * So the gradient is always painted as the element's background and the image sits on top of it.
 * There is no loading flash to manage and no layout shift: the box is sized by its container
 * either way, and a failed image simply reveals what was already behind it.
 */
export function Poster({
  posterUrl,
  posterHue,
  title,
  className = ''
}: {
  /**
   * Absent as often as null: the API sets `default-property-inclusion: non_null`, so a movie
   * with no artwork simply has no `posterUrl` key at all rather than one holding null.
   */
  posterUrl?: string | null;
  posterHue: number;
  /** Used for the alt text — a poster's meaning is entirely "which film is this". */
  title: string;
  className?: string;
}) {
  // Reset per URL, or a card recycled by a re-render would inherit the previous movie's failure
  // and never try to load its own artwork.
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [posterUrl]);

  // Truthiness, not `!== null`. `undefined` passes a null check and would render an <img> with
  // no src — a broken frame that never fires onError, so the fallback below would never run.
  // Narrowing to a `string | null` here also gives `src` a type it can accept.
  const artwork = failed || !posterUrl ? null : posterUrl;

  return (
    <div
      style={posterGradient(posterHue)}
      className={`relative flex flex-shrink-0 items-center justify-center overflow-hidden ${className}`}
    >
      {artwork ? (
        <img
          src={artwork}
          alt={`${title} poster`}
          loading="lazy"
          onError={() => setFailed(true)}
          className="h-full w-full object-cover"
        />
      ) : (
        // The handoff's placeholder, kept for exactly the case it was designed for.
        <span className="font-mono text-[0.6em] tracking-widest text-white/50">POSTER</span>
      )}
    </div>
  );
}
