// Registers jest-dom's matchers (toBeInTheDocument, toHaveTextContent, …) on Vitest's `expect`.
// The `/vitest` entry point is the one that wires them into Vitest rather than Jest.
import '@testing-library/jest-dom/vitest';
