import { CSS_IMAGE_VARIABLES } from '../constants/public-assets';

function setCSSVariables(
  element: HTMLElement,
  variables: Record<string, string>,
): void {
  for (const [variable, fullUrl] of Object.entries(variables)) {
    element.style.setProperty(variable, `url("${fullUrl}")`);
  }
}

/**
 * Injects dynamic CSS image variables into both :root and body,
 * ensuring runtime-correct paths override static CSS defaults.
 */
export function initializeCSSVariables(): void {
  setCSSVariables(document.documentElement, CSS_IMAGE_VARIABLES);
  if (document.body) {
    setCSSVariables(document.body, CSS_IMAGE_VARIABLES);
  }
}
