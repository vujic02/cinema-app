import { fireEvent, render, screen } from '@testing-library/react';
import { Poster } from './Poster';

/**
 * The poster has to survive its own artwork failing.
 *
 * TMDB images come from a third-party CDN the app does not control, and a movie added through
 * the admin API may have no artwork at all. Both cases have to land on the gradient rather than
 * on a broken-image icon, which is the whole reason `posterHue` outlived the arrival of real
 * posters.
 */
describe('Poster', () => {
  it('renders the artwork when there is some', () => {
    render(<Poster posterUrl="https://image.tmdb.org/t/p/w500/a.jpg" posterHue={200} title="Dune" />);

    const image = screen.getByAltText('Dune poster');
    expect(image).toHaveAttribute('src', 'https://image.tmdb.org/t/p/w500/a.jpg');
    expect(screen.queryByText('POSTER')).not.toBeInTheDocument();
  });

  it('falls back to the placeholder when a movie has no artwork', () => {
    render(<Poster posterUrl={null} posterHue={200} title="Hand-entered" />);

    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(screen.getByText('POSTER')).toBeInTheDocument();
  });

  it('falls back when the field is absent rather than null', () => {
    // This is the shape the API actually sends: `default-property-inclusion: non_null` means a
    // movie with no poster has no `posterUrl` key at all. A `!== null` guard passes `undefined`
    // straight through and renders an <img> with no src — a permanently broken frame that never
    // fires onError, so the placeholder below would never get its turn.
    render(<Poster posterUrl={undefined} posterHue={200} title="From the wire" />);

    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(screen.getByText('POSTER')).toBeInTheDocument();
  });

  it('falls back when the image fails to load', () => {
    render(<Poster posterUrl="https://image.tmdb.org/t/p/w500/gone.jpg" posterHue={200} title="Dune" />);

    fireEvent.error(screen.getByAltText('Dune poster'));

    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(screen.getByText('POSTER')).toBeInTheDocument();
  });

  it('paints the gradient underneath either way, so there is no flash while the image loads', () => {
    // jsdom normalises hsl() to rgb() on the way into the style object, so the assertion is that
    // a gradient is present and unchanged by the image, not on the exact colour syntax written.
    const { container, rerender } = render(<Poster posterUrl="/a.jpg" posterHue={120} title="A" />);
    const withArtwork = (container.firstChild as HTMLElement).style.backgroundImage;
    expect(withArtwork).toMatch(/^linear-gradient\(160deg, rgb\(/);

    rerender(<Poster posterUrl={null} posterHue={120} title="A" />);
    expect((container.firstChild as HTMLElement).style.backgroundImage).toBe(withArtwork);
  });

  it('retries for a new movie after a previous one failed', () => {
    // Lists re-render the same component for different movies. Without resetting on the URL, one
    // dead image would leave every later card in the recycled slot showing the placeholder.
    const { rerender } = render(<Poster posterUrl="/gone.jpg" posterHue={200} title="Broken" />);
    fireEvent.error(screen.getByAltText('Broken poster'));
    expect(screen.getByText('POSTER')).toBeInTheDocument();

    rerender(<Poster posterUrl="/good.jpg" posterHue={200} title="Fine" />);
    expect(screen.getByAltText('Fine poster')).toHaveAttribute('src', '/good.jpg');
  });
});
