import { CSS_IMAGE_VARIABLES } from '../constants/publicAssets';

const setCSSVariables = (element, variables) => {
  Object.entries(variables).forEach(([variable, fullUrl]) => {
    const cssUrl = `url("${fullUrl}")`;
    element.style.setProperty(variable, cssUrl);
  });
};

/**
 * Injects dynamic CSS variables into the document root
 * This overrides the static CSS variables with runtime-correct paths
 */
export const initializeCSSVariables = () => {
  const root = document.documentElement;

  // Inject image variables with proper PUBLIC_URL prefix to :root
  setCSSVariables(root, CSS_IMAGE_VARIABLES);

  // Also inject to body element to handle theme classes
  const body = document.body;
  if (body) {
    setCSSVariables(body, CSS_IMAGE_VARIABLES);
  }
};
