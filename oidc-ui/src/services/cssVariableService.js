import { CSS_IMAGE_VARIABLES } from '../constants/imageAssets';

/**
 * Injects dynamic CSS variables into the document root
 * This overrides the static CSS variables with runtime-correct paths
 * Also handles theme classes (orange, green) that may override root variables
 */
export const initializeCSSVariables = () => {
  const root = document.documentElement;

  // Inject image variables with proper PUBLIC_URL prefix to :root
  Object.entries(CSS_IMAGE_VARIABLES).forEach(([variable, fullUrl]) => {
    const cssUrl = `url("${fullUrl}")`;
    root.style.setProperty(variable, cssUrl);
  });

  // Also inject to body element to handle theme classes
  // This ensures theme-specific CSS doesn't override with hardcoded paths
  const body = document.body;
  if (body) {
    Object.entries(CSS_IMAGE_VARIABLES).forEach(([variable, fullUrl]) => {
      const cssUrl = `url("${fullUrl}")`;
      body.style.setProperty(variable, cssUrl);
    });
  }
};
