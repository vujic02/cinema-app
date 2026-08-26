import type { Config } from 'tailwindcss';

/**
 * The handoff bundle wrote raw palette names with a `dark:` twin on almost every element
 * (`bg-white dark:bg-zinc-950`, `text-zinc-500 dark:text-zinc-400`, …). That is 200-odd places
 * where a theme change has to be made twice and can be made inconsistently — which is exactly
 * what happened to the seat colours.
 *
 * These tokens are the same palette named by role instead. They resolve through CSS variables
 * defined in index.css, so `bg-surface` is already correct in both themes and the `dark:` twin
 * disappears from the markup entirely.
 *
 * `<alpha-value>` is what keeps opacity modifiers working, so `border-accent/70` still means
 * something after the switch to variables.
 */
const token = (name: string) => `rgb(var(--${name}) / <alpha-value>)`;

export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        /** Page background, behind everything. */
        page: token('page'),
        /** Cards, the nav bar, anything sitting on the page. */
        surface: token('surface'),
        /** Inputs and chips sitting on a surface. */
        raised: token('raised'),
        /** Inset tracks — the tab bars a pill slides along. */
        sunken: token('sunken'),
        /** The active pill inside a sunken track. */
        elevated: token('elevated'),
        line: token('line'),
        'line-strong': token('line-strong'),
        /** Body text. */
        ink: token('ink'),
        /** Secondary text. */
        muted: token('muted'),
        /** Text on a disabled control. */
        disabled: token('disabled'),
        accent: token('accent'),
        /** Text sitting on top of an accent fill. */
        'accent-ink': token('accent-ink'),
        /** The accent used as text or a link, which needs more contrast than the fill. */
        'accent-text': token('accent-text'),
        /** Success: the confirmation tick, the "upcoming" badge, a selected seat. */
        ok: token('ok'),
        'ok-ink': token('ok-ink'),
        /** Error text, and the tinted panel it sits in. */
        danger: token('danger'),
        'danger-surface': token('danger-surface'),
        seat: {
          available: token('seat-available'),
          /** Sold — a booking row exists in MySQL. */
          reserved: token('seat-reserved'),
          /** Somebody else's live Redis hold, which may still expire. */
          held: token('seat-held'),
          selected: token('seat-selected'),
          /** Seat numbers stay dark in both themes: the seat itself is always light. */
          ink: token('seat-ink')
        }
      },
      /**
       * The handoff used these sizes as Tailwind classes (`h-7.5`, `w-13`, `h-21`, `w-4.5`,
       * `h-19`, `w-14.5`, `mb-4.5`, `pt-4.5`) and then repeated each one as an inline `style`
       * because none of them exist in the default scale — which is where the duplicate `style`
       * attributes on the checkout and confirmation posters came from, silently dropping the
       * poster gradient.
       *
       * Adding them to the scale makes the original class names real and lets the inline
       * overrides go away.
       */
      spacing: {
        '4.5': '1.125rem', // 18px
        '7.5': '1.875rem', // 30px — one seat
        '13': '3.25rem', //   52px — the theme toggle track
        '14.5': '3.625rem', // 58px
        '19': '4.75rem', //   76px
        '21': '5.25rem' //    84px
      }
    }
  },
  plugins: []
} satisfies Config;
