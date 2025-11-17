import { CSS_IMAGE_VARIABLES } from '../constants/imageAssets';

const setCSSVariables = (element, variables) => {
  Object.entries(variables).forEach(([variable, fullUrl]) => {
    const cssUrl = `url("${fullUrl}")`;
    element.style.setProperty(variable, cssUrl);
  });
};

/**
 * Injects dynamic CSS variables into the document root
 * This overrides the static CSS variables with runtime-correct paths
 * Also handles theme classes (orange, green) that may override root variables
 */
export const initializeCSSVariables = () => {
  const root = document.documentElement;

  // Inject image variables with proper PUBLIC_URL prefix to :root
  setCSSVariables(root, CSS_IMAGE_VARIABLES);

  // Also inject to body element to handle theme classes
  // This ensures theme-specific CSS doesn't override with hardcoded paths
  const body = document.body;
  if (body) {
    setCSSVariables(body, CSS_IMAGE_VARIABLES);
  }
};
