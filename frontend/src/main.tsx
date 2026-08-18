import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './index.css';

const container = document.getElementById('root');
if (!container) {
  throw new Error('index.html is missing #root');
}

// BrowserRouter, not HashRouter: Nginx rewrites unmatched paths to index.html (TECH.md §4),
// so a refresh on /checkout resolves without the URL carrying a #.
createRoot(container).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>
);
