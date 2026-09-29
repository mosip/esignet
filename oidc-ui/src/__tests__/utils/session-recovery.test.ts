import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { returnToRelyingParty } from "../../utils/session-recovery";

describe("returnToRelyingParty", () => {
  const originalLocation = window.location;

  beforeEach(() => {
    vi.spyOn(window.history, "back").mockImplementation(() => {});
    window.onbeforeunload = vi.fn() as unknown as typeof window.onbeforeunload;
    Object.defineProperty(window, "location", {
      value: { ...originalLocation, replace: vi.fn() },
      writable: true,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    Object.defineProperty(window, "location", {
      value: originalLocation,
      writable: true,
    });
  });

  const setHistoryLength = (length: number) =>
    Object.defineProperty(window.history, "length", {
      configurable: true,
      get: () => length,
    });

  it("steps back to the relying party when a previous entry exists", () => {
    setHistoryLength(2);
    returnToRelyingParty();
    expect(window.history.back).toHaveBeenCalledTimes(1);
    expect(window.location.replace).not.toHaveBeenCalled();
  });

  it("clears onbeforeunload before navigating", () => {
    setHistoryLength(2);
    returnToRelyingParty();
    expect(window.onbeforeunload).toBeNull();
  });

  it("falls back to the error page when there is no previous entry", () => {
    setHistoryLength(1);
    returnToRelyingParty();
    expect(window.location.replace).toHaveBeenCalledWith("/something-went-wrong");
    expect(window.history.back).not.toHaveBeenCalled();
  });
});
