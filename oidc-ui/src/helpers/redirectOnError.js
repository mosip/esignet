import openIDConnectService from "../services/openIDConnectService";

function redirectOnError(errorCode, errorDescription) {

  // Parsing the current URL into a URL object
  const urlObj = new URL(window.location.href);

  // Extracting the value of the 'state' parameter from the URL
  const state = urlObj.searchParams.get("state");

  // Extracting the value of the 'nonce' parameter from the URL
  const nonce = urlObj.searchParams.get("nonce");

  // Extracting the hash part of the URL (excluding the # character)
  const code = urlObj.hash.substring(1);

  // Decoding the Base64-encoded string (excluding the first character)
  const decodedBase64 = atob(code);

  // Creating an instance of the openIDConnectService with decodedBase64, nonce, and state parameters
  const oidcService = new openIDConnectService(
    JSON.parse(decodedBase64),
    nonce,
    state
  );

  // Getting the redirect URI from the openIDConnectService instance
  const redirect_uri = oidcService.getRedirectUri();

  // If no redirect URI is obtained, exit the function
  if (!redirect_uri) return;

  // Creating a new instance of URLSearchParams to store query parameters
  const params = new URLSearchParams();

  // If errorDescription is provided, setting 'error_description' parameter
  if (errorDescription) params.set("error_description", errorDescription);

  // Setting 'state' parameter
  params.set("state", state);
  
  // Setting 'error' parameter
  params.set("error", errorCode);

  // Disabling the beforeunload event handler
  window.onbeforeunload = null;

  // Redirecting the browser to the new URL with the constructed query parameters
  window.location.replace(`${redirect_uri}?${params.toString()}`);
}

export default redirectOnError;
