import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest';

const windowMock = new EventTarget() as Window & typeof globalThis;
Object.defineProperty(windowMock, 'innerWidth', { value: 1024, writable: true, configurable: true });
Object.defineProperty(globalThis, 'window', { value: windowMock, writable: true, configurable: true });

// Mock Vue lifecycle hooks BEFORE importing useBreakpoint
vi.mock('vue', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue')>();
  return {
    ...actual,
    onMounted: vi.fn((fn: () => void) => fn()), // Execute immediately
    onUnmounted: vi.fn(),
  };
});

import { useBreakpoint } from './useBreakpoint';

describe('useBreakpoint', () => {
  const originalWidth = window.innerWidth;

  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    Object.defineProperty(window, 'innerWidth', { value: originalWidth, writable: true });
  });

  function setWidth(w: number) {
    Object.defineProperty(window, 'innerWidth', { value: w, writable: true });
  }

  function triggerResize() {
    window.dispatchEvent(new Event('resize'));
  }

  it('initializes correctly', () => {
    setWidth(500);
    const { isMobile } = useBreakpoint();
    expect(isMobile.value).toBe(true);
  });

  it('updates state on resize with debounce', () => {
    setWidth(500); // Start as mobile
    const { isMobile, isDesktop } = useBreakpoint();
    expect(isMobile.value).toBe(true);

    // Resize to desktop
    setWidth(1200);
    triggerResize();

    // Should not update immediately due to debounce
    expect(isMobile.value).toBe(true);
    expect(isDesktop.value).toBe(false);

    // Fast forward time by 50ms (less than debounce 100ms)
    vi.advanceTimersByTime(50);
    expect(isMobile.value).toBe(true);

    // Fast forward past debounce time
    vi.advanceTimersByTime(100);
    expect(isMobile.value).toBe(false);
    expect(isDesktop.value).toBe(true);
  });
});
