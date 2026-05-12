export const LoadingStates = {
  LOADING: 'LOADING',
  LOADED: 'LOADED',
  ERROR: 'ERROR',
} as const;

export type LoadingState = (typeof LoadingStates)[keyof typeof LoadingStates];
