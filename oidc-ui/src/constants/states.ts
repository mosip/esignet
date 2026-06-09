export const LoadingStates = {
  LOADING: "LOADING",
  LOADED: "LOADED",
  ERROR: "ERROR",
  AUTHENTICATING: "AUTHENTICATING",
} as const;

export type LoadingState = (typeof LoadingStates)[keyof typeof LoadingStates];
