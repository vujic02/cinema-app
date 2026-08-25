/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Dev mirror of the Nginx rules in TECH.md §4: the browser only ever talks to one origin,
    // so CORS never enters the picture — which is why the backend still has no CORS config.
    // `ws: true` matters for the seat-hold socket; without it the SockJS upgrade 404s.
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'http://localhost:8080', changeOrigin: true, ws: true }
    }
  },
  // Vitest reuses everything above — same React plugin, same resolution — so tests compile
  // through the identical pipeline the app builds with. Keep Vitest on a major that accepts
  // Vite 5 (v3 does; v4 demands Vite 6+ and silently installs a second Vite alongside this one).
  test: {
    environment: 'jsdom',
    // `describe`/`it`/`expect` without imports, and it is also what lets Testing Library
    // register its automatic cleanup between tests.
    globals: true,
    setupFiles: './src/test/setup.ts',
    css: false
  }
});
