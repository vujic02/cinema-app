/**
 * Poster art is a gradient placeholder keyed off `movies.poster_hue`, which the backend serves
 * on every DTO that carries a movie. The only survivor of `mockData.ts`, which Part 7 deleted:
 * this was never mock data, it is how a poster is drawn.
 */
export function posterGradient(hue: number) {
  return { backgroundImage: `linear-gradient(160deg, hsl(${hue} 45% 32%) 0%, hsl(${hue} 40% 18%) 100%)` };
}
