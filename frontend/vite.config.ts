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
  }
});
