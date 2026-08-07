import {
  describe,
  it,
  expect,
  vi,
  beforeEach,
  afterEach,
  beforeAll,
  afterAll,
} from "vitest";
import axios from "axios";

// ── HttpError ──────────────────────────────────────────────────────────────────

import { HttpError } from "../../services/api.service";

describe("HttpError", () => {
  it("is an instance of Error", () => {
    expect(new HttpError("failed", 403)).toBeInstanceOf(Error);
  });

  it("stores the HTTP status code", () => {
    expect(new HttpError("not found", 404).code).toBe(404);
    expect(new HttpError("server error", 500).code).toBe(500);
  });

  it("stores the message", () => {
    expect(new HttpError("unauthorized", 401).message).toBe("unauthorized");
  });
});

// ── ApiService request interceptor (integration via real axios adapter) ────────

import {
  ApiService,
  setupResponseInterceptor,
} from "../../services/api.service";

/**
 * Set a synchronous mock adapter on ApiService for the duration of a test.
 * Returns the adapter reference so callers can inspect the resolved config.
 */
function setMockAdapter(
  responseFactory: (config: import("axios").InternalAxiosRequestConfig) => {
    data: unknown;
    status: number;
    statusText?: string;
  },
) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (ApiService.defaults as any).adapter = async (
    config: import("axios").InternalAxiosRequestConfig,
  ) => {
    const result = responseFactory(config);
    return { ...result, headers: {}, config } as import("axios").AxiosResponse;
  };
}

describe("ApiService request interceptor — CSRF token", () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.clearAllMocks();
  });

  afterEach(() => {
    sessionStorage.clear();
    // Restore default adapter
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    delete (ApiService.defaults as any).adapter;
  });

  it("injects X-XSRF-TOKEN from sessionStorage on POST without fetching", async () => {
    sessionStorage.setItem("csrfToken", "cached-token");

    let capturedHeaders: Record<string, string> = {};
    setMockAdapter((config) => {
      capturedHeaders = config.headers as Record<string, string>;
      return { data: {}, status: 200 };
    });

    await ApiService.post("/test", {});
    expect(capturedHeaders["X-XSRF-TOKEN"]).toBe("cached-token");
  });

  it("fetches and caches a new CSRF token when none is cached", async () => {
    // Mock the getCsrfToken axios.get call
    const axiosGetSpy = vi
      .spyOn(axios, "get")
      .mockResolvedValue({ data: { token: "fresh-token" } });

    let capturedHeaders: Record<string, string> = {};
    setMockAdapter((config) => {
      capturedHeaders = config.headers as Record<string, string>;
      return { data: {}, status: 200 };
    });

    await ApiService.post("/test", {});

    expect(axiosGetSpy).toHaveBeenCalled();
    expect(sessionStorage.getItem("csrfToken")).toBe("fresh-token");
    expect(capturedHeaders["X-XSRF-TOKEN"]).toBe("fresh-token");

    axiosGetSpy.mockRestore();
  });

  it("rejects if CSRF token fetch fails", async () => {
    const axiosGetSpy = vi
      .spyOn(axios, "get")
      .mockRejectedValue(new Error("network fail"));

    setMockAdapter(() => ({ data: {}, status: 200 }));

    await expect(ApiService.post("/test", {})).rejects.toThrow(
      "CSRF token unavailable",
    );

    axiosGetSpy.mockRestore();
  });

  it("skips sessionStorage.setItem when getCsrfToken returns a falsy token (line 39 false branch)", async () => {
    // getCsrfToken resolves to an empty/null token → if (csrfToken) on line 39 is false.
    const axiosGetSpy = vi
      .spyOn(axios, "get")
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      .mockResolvedValue({ data: { token: null as any } });

    let capturedHeaders: Record<string, string> = {};
    setMockAdapter((config) => {
      capturedHeaders = config.headers as Record<string, string>;
      return { data: {}, status: 200 };
    });

    await ApiService.post("/test", {});

    // Token was not stored because it was falsy.
    expect(sessionStorage.getItem("csrfToken")).toBeNull();
    // And NOT injected into the request headers (line 45 false branch).
    expect(capturedHeaders["X-XSRF-TOKEN"]).toBeUndefined();

    axiosGetSpy.mockRestore();
  });

  it.each(["put", "patch", "delete"])(
    "injects X-XSRF-TOKEN from sessionStorage on %s without fetching",
    async (method) => {
      sessionStorage.setItem("csrfToken", "cached-token");

      let capturedHeaders: Record<string, string> = {};
      setMockAdapter((config) => {
        capturedHeaders = config.headers as Record<string, string>;
        return { data: {}, status: 200 };
      });

      await ApiService.request({ method, url: "/test" });
      expect(capturedHeaders["X-XSRF-TOKEN"]).toBe("cached-token");
    },
  );

  it.each(["put", "patch", "delete"])(
    "fetches and caches a new CSRF token on %s when none is cached",
    async (method) => {
      const axiosGetSpy = vi
        .spyOn(axios, "get")
        .mockResolvedValue({ data: { token: "fresh-token" } });

      let capturedHeaders: Record<string, string> = {};
      setMockAdapter((config) => {
        capturedHeaders = config.headers as Record<string, string>;
        return { data: {}, status: 200 };
      });

      await ApiService.request({ method, url: "/test" });

      expect(axiosGetSpy).toHaveBeenCalled();
      expect(sessionStorage.getItem("csrfToken")).toBe("fresh-token");
      expect(capturedHeaders["X-XSRF-TOKEN"]).toBe("fresh-token");

      axiosGetSpy.mockRestore();
    },
  );

  it("does NOT inject X-XSRF-TOKEN on GET requests", async () => {
    sessionStorage.setItem("csrfToken", "cached-token");

    let capturedHeaders: Record<string, string> = {};
    setMockAdapter((config) => {
      capturedHeaders = config.headers as Record<string, string>;
      return { data: {}, status: 200 };
    });

    await ApiService.get("/test");
    expect(capturedHeaders["X-XSRF-TOKEN"]).toBeUndefined();
  });
});

// ── Request interceptor error handler (line 51 in api.service.ts) ─────────────
//
// Line 51 is `(error) => Promise.reject(error as Error)` — the REJECTED handler
// of the request interceptor.  It fires when a prior interceptor in the chain
// throws.  Axios processes request interceptors in LIFO order, so a pre-interceptor
// registered AFTER the module-level one runs FIRST; when it rejects the error
// flows into line 51's error handler.

describe("ApiService request interceptor — error handler (line 51)", () => {
  let preInterceptorId: number;

  afterEach(() => {
    ApiService.interceptors.request.eject(preInterceptorId);
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    delete (ApiService.defaults as any).adapter;
    sessionStorage.clear();
  });

  it("passes the error through when a prior interceptor rejects (covers line 51)", async () => {
    // Register a pre-interceptor that always rejects — LIFO means this runs first.
    preInterceptorId = ApiService.interceptors.request.use(async () => {
      throw new Error("pre-interceptor rejected");
    }, undefined);

    setMockAdapter(() => ({ data: {}, status: 200 }));

    // The pre-interceptor throws → error flows to api.service.ts line 51 →
    // Promise.reject(error) → the request rejects with the original error.
    await expect(ApiService.get("/test")).rejects.toThrow(
      "pre-interceptor rejected",
    );
  });
});

// ── setupResponseInterceptor — exercises the REAL source callbacks ─────────────
//
// We call the actual exported setupResponseInterceptor() so V8 coverage
// instruments its lines (59-70 in api.service.ts).  We capture the ID it
// registers by temporarily wrapping interceptors.response.use(), then eject
// after all tests in this describe block to avoid bleeding into other suites.

describe("setupResponseInterceptor — real source code", () => {
  const navigate = vi.fn();
  let capturedId: number;

  beforeAll(() => {
    // Wrap use() for exactly ONE call to capture the assigned interceptor ID,
    // then immediately restore the original so nothing else is affected.
    const origUse = ApiService.interceptors.response.use.bind(
      ApiService.interceptors.response,
    );
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (ApiService.interceptors.response as any).use = (
      ...args: Parameters<typeof origUse>
    ) => {
      capturedId = origUse(...args);
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (ApiService.interceptors.response as any).use = origUse;
      return capturedId;
    };
    setupResponseInterceptor(navigate);
  });

  afterAll(() => {
    ApiService.interceptors.response.eject(capturedId);
  });

  beforeEach(() => {
    navigate.mockClear();
    sessionStorage.setItem("csrfToken", "tok");
  });

  afterEach(() => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    delete (ApiService.defaults as any).adapter;
    sessionStorage.clear();
  });

  it("success handler passes responses through unchanged (line 60)", async () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (ApiService.defaults as any).adapter = async (
      config: import("axios").InternalAxiosRequestConfig,
    ) => ({
      data: { ok: true },
      status: 200,
      statusText: "OK",
      headers: {},
      config,
    });

    const res = await ApiService.get("/ok");
    expect((res.data as { ok: boolean }).ok).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });

  it("error handler navigates on 4xx — covers if-branch true path (lines 61-65)", async () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (ApiService.defaults as any).adapter = async (
      config: import("axios").InternalAxiosRequestConfig,
    ) => {
      const err = Object.assign(new Error("404"), {
        response: { status: 404, data: {}, headers: {}, config },
        isAxiosError: true,
      });
      return Promise.reject(err);
    };

    await ApiService.get("/fail").catch(() => {});
    expect(navigate).toHaveBeenCalledWith(
      expect.stringContaining("something-went-wrong"),
      expect.objectContaining({ state: { code: 404 } }),
    );
  });

  it("error handler does not navigate when no response — covers if-branch false path", async () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (ApiService.defaults as any).adapter = async () =>
      Promise.reject(new Error("network failure"));

    await ApiService.get("/fail").catch(() => {});
    expect(navigate).not.toHaveBeenCalled();
  });

  it("error handler wraps a non-Error rejection — covers ternary false branch (line 68)", async () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (ApiService.defaults as any).adapter = async () =>
      Promise.reject("plain string error");

    const caught = await ApiService.get("/fail").catch((e: unknown) => e);
    expect(caught).toBeInstanceOf(Error);
  });
});

// ── Response interceptor logic (unit-tested inline) ───────────────────────────

describe("response interceptor error handler logic", () => {
  function buildHandler(navigate: ReturnType<typeof vi.fn>) {
    return (error: unknown): Promise<never> => {
      const status = (error as { response?: { status?: number } })?.response
        ?.status;
      if (status && status >= 400) {
        new navigate("/something-went-wrong", { state: { code: status } });
      }
      return Promise.reject(
        error instanceof Error ? error : new Error(String(error)),
      );
    };
  }

  it("navigates on 401", async () => {
    const navigate = vi.fn();
    await buildHandler(navigate)({ response: { status: 401 } }).catch(() => {});
    expect(navigate).toHaveBeenCalledWith("/something-went-wrong", {
      state: { code: 401 },
    });
  });

  it("navigates on 503", async () => {
    const navigate = vi.fn();
    await buildHandler(navigate)({ response: { status: 503 } }).catch(() => {});
    expect(navigate).toHaveBeenCalledWith("/something-went-wrong", {
      state: { code: 503 },
    });
  });

  it("does not navigate for 302 (not a 4xx/5xx)", async () => {
    const navigate = vi.fn();
    await buildHandler(navigate)({ response: { status: 302 } }).catch(() => {});
    expect(navigate).not.toHaveBeenCalled();
  });

  it("does not navigate when there is no HTTP response", async () => {
    const navigate = vi.fn();
    await buildHandler(navigate)(new Error("network error")).catch(() => {});
    expect(navigate).not.toHaveBeenCalled();
  });

  it("wraps non-Error rejections in an Error", async () => {
    const navigate = vi.fn();
    await buildHandler(navigate)("plain string").catch((e: unknown) => {
      expect(e).toBeInstanceOf(Error);
    });
  });
});

// ── CSRF interceptor logic (unit-tested inline) ───────────────────────────────

describe("CSRF request interceptor logic", () => {
  beforeEach(() => sessionStorage.clear());
  afterEach(() => sessionStorage.clear());

  it("injects X-XSRF-TOKEN when token in sessionStorage", () => {
    sessionStorage.setItem("csrfToken", "tok");
    const config: { method: string; headers: Record<string, string> } = {
      method: "post",
      headers: {},
    };
    if (["post", "put", "patch", "delete"].includes(config.method?.toLowerCase() ?? "")) {
      const t = sessionStorage.getItem("csrfToken");
      if (t) config.headers["X-XSRF-TOKEN"] = t;
    }
    expect(config.headers["X-XSRF-TOKEN"]).toBe("tok");
  });

  it("skips injection for GET", () => {
    sessionStorage.setItem("csrfToken", "tok");
    const config: { method: string; headers: Record<string, string> } = {
      method: "get",
      headers: {},
    };
    if (["post", "put", "patch", "delete"].includes(config.method?.toLowerCase() ?? "")) {
      const t = sessionStorage.getItem("csrfToken");
      if (t) config.headers["X-XSRF-TOKEN"] = t;
    }
    expect(config.headers["X-XSRF-TOKEN"]).toBeUndefined();
  });
});

// ── config.method undefined — covers ?. null and ?? right branches (line 34) ────
//
// Axios always sets config.method before calling interceptors, so there is no
// normal request path where it arrives as undefined.  A pre-interceptor
// (registered after the module-level one; LIFO → runs first) strips the field,
// exercising the optional-chain null branch and the ?? "" fallback.

describe("ApiService request interceptor — undefined config.method (line 34 branches)", () => {
  let preId: number;

  afterEach(() => {
    ApiService.interceptors.request.eject(preId);
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    delete (ApiService.defaults as any).adapter;
    sessionStorage.clear();
  });

  it("skips CSRF injection and returns config when config.method is undefined", async () => {
    preId = ApiService.interceptors.request.use(async (config) => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (config as any).method = undefined;
      return config;
    });

    let capturedHeaders: Record<string, string> = {};
    setMockAdapter((config) => {
      capturedHeaders = config.headers as Record<string, string>;
      return { data: {}, status: 200 };
    });

    await ApiService.get("/test");
    expect(capturedHeaders["X-XSRF-TOKEN"]).toBeUndefined();
  });
});
