import { useEffect } from "react";

const Dummy = () => {
  // Parsing the current URL into a URL object
  const urlObj = new URL(window.location.href);
  const state = urlObj.searchParams.get("state");

  // Extracting the hash part of the URL (excluding the # character)
  const code = urlObj.hash.substring(1);

  useEffect(() => {
    window.onbeforeunload = null;
    window.location.replace(
      `http://localhost:3000/authorize?client_id=mosip-signup-oauth-client&redirect_uri=https://signup.camdgc-dev1.mosip.net/identity-verification&scope=openid&response_type=code&state=${state}&id_token_hint=${code}`
    );
  }, []);

  return <></>;
};

export default Dummy;
