// Vitest runs the shell in jsdom, which lacks two browser APIs Ant Design's layout code reads.
// Width queries answer against window.innerWidth (jsdom: 1024), so a test is a desktop unless
// it sets a phone width (W-80.1).
function widthMatches(query) {
  const min = /min-width:\s*(\d+(?:\.\d+)?)px/.exec(query);
  const max = /max-width:\s*(\d+(?:\.\d+)?)px/.exec(query);
  if (!min && !max) return false;
  const width = window.innerWidth;
  return (!min || width >= Number(min[1])) && (!max || width <= Number(max[1]));
}

if (typeof window !== 'undefined') {
  if (!window.matchMedia) {
    window.matchMedia = (query) => ({
      matches: widthMatches(query),
      media: query,
      onchange: null,
      addListener() {},
      removeListener() {},
      addEventListener() {},
      removeEventListener() {},
      dispatchEvent() {
        return false;
      },
    });
  }
  if (!window.ResizeObserver) {
    window.ResizeObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  }
}

// Testing Library unmounts after each test only when the runner exposes globals; vitest
// does not here, so it is wired explicitly.
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

afterEach(() => cleanup());
