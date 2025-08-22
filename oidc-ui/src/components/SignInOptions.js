import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import LoadingIndicator from '../common/LoadingIndicator';
import {
  configurationKeys,
  walletConfigKeys,
} from '../constants/clientConstants';
import { LoadingStates as states } from '../constants/states';
import { getAllAuthFactors } from '../services/walletService';

export default function SignInOptions({
  openIDConnectService,
  handleSignInOptionClick,
  i18nKeyPrefix = 'signInOption',
  icons,
  authLabel,
}) {
  const { t } = useTranslation('translation', { keyPrefix: i18nKeyPrefix });

  const [status, setStatus] = useState({ state: states.LOADED, msg: '' });
  const [singinOptions, setSinginOptions] = useState(null);
  const [showMoreOptions, setShowMoreOptions] = useState(false);
  const [iconsMap, setIconsMap] = useState({}); // To store preloaded SVGs

  const walletConfig = openIDConnectService.getEsignetConfiguration(
    configurationKeys.walletConfig
  );

  var walletLogoUrl = walletConfig[0][walletConfigKeys.walletLogoUrl];

  // Function to fetch and apply the background color dynamically
  const updateLanguageIconBgColor = (svgText) => {
    // Construct the CSS variable name dynamically
    const cssVariableName = `--primary-color`;

    // Get the computed style for the body element
    const bodyStyles = getComputedStyle(document.body);

    // Fetch the value of the custom property
    const primaryColor = bodyStyles.getPropertyValue(cssVariableName).trim();

    if (primaryColor) {
      // Apply the color to the stroke and fill attributes of the SVG
      svgText = svgText.replace(
        /fill="(?!#fff|white|none)[^"]*"/g,
        `fill="${primaryColor}"`
      );

      svgText = svgText.replace(
        /stroke="(?!#fff|white|none)[^"]*"/g,
        `stroke="${primaryColor}"`
      );
    }
    return svgText;
  };

  const fetchSvg = async (path) => {
    try {
      const response = await fetch(`/${path}`);
      if (!response.ok) {
        throw new Error('Failed to fetch SVG');
      }
      let svgText = await response.text();

      // Call the function to apply the color on page load or dynamically
      return updateLanguageIconBgColor(svgText);
    } catch (error) {
      return ''; // Return empty string if error occurs
    }
  };

  useEffect(() => {
    const preloadIcons = async () => {
      const svgPromises = singinOptions.map(async (option) => {
        if (option.icon !== walletLogoUrl) {
          const svgContent = await fetchSvg(option.icon);
          return { id: option.id, svgContent };
        } else return { id: option.id };
      });

      const svgResults = await Promise.all(svgPromises);
      const svgMap = svgResults.reduce((acc, { id, svgContent }) => {
        acc[id] = svgContent;
        return acc;
      }, {});

      setIconsMap(svgMap); // Store preloaded SVGs
    };

    if (singinOptions) {
      if (
        Object.keys(iconsMap)?.length === 0 &&
        icons &&
        Object.keys(icons)?.length
      ) {
        setIconsMap(icons);
      } else if (!icons) {
        preloadIcons();
      }
    }

    if (iconsMap && Object.keys(iconsMap)?.length > 0) {
      setStatus({ state: states.LOADED, msg: '' });
    } else {
      setStatus({ state: states.LOADING, msg: 'loading_msg' });
    }
  }, [singinOptions]);

  useEffect(() => {
    if (iconsMap && Object.keys(iconsMap)?.length > 0) {
      setStatus({ state: states.LOADED, msg: '' });
    } else {
      setStatus({ state: states.LOADING, msg: 'loading_msg' });
    }
  }, [iconsMap]);

  useEffect(() => {
    setStatus({ state: states.LOADING, msg: 'loading_msg' });

    let oAuthDetails = openIDConnectService.getOAuthDetails();
    let authFactors = oAuthDetails?.authFactors;

    let wlaList =
      openIDConnectService.getEsignetConfiguration(
        configurationKeys.walletConfig
      ) ?? process.env.REACT_APP_WALLET_CONFIG;

    let loginOptions = getAllAuthFactors(authFactors, wlaList);

    if (loginOptions.length === 1) {
      handleSignInOptionClick(loginOptions[0].value, null, authLabel);
    }

    setSinginOptions(loginOptions);
    setShowMoreOptions(loginOptions.length > 4);
    setStatus({ state: states.LOADED, msg: '' });
  }, []);

  return (
    <>
      <h1 className="text-base leading-5 font-sans font-medium my-2">
        {t('preferred_mode_to_continue')}
      </h1>

      {status.state === states.LOADING && (
        <div className="py-6">
          <LoadingIndicator size="medium" message={status.msg} />
        </div>
      )}

      {status.state === states.LOADED &&
        singinOptions &&
        Object.keys(iconsMap)?.length > 0 && (
          <div className="grid grid-rows-4 w-full rounded">
            {singinOptions
              .slice(0, showMoreOptions ? 4 : undefined)
              .map((option, idx) => (
                <div
                  key={idx}
                  className="w-full flex py-[0.6rem] px-1 my-1 cursor-pointer login-list-box-style overflow-hidden"
                  id={option.id}
                  onClick={() =>
                    handleSignInOptionClick(option.value, iconsMap, authLabel)
                  }
                  onKeyDown={() =>
                    handleSignInOptionClick(option.value, iconsMap, authLabel)
                  }
                  role="button"
                  tabIndex={0}
                >
                  {option.icon !== walletLogoUrl ? (
                    <div
                      dangerouslySetInnerHTML={{
                        __html: iconsMap[option.id] || '',
                      }}
                      className="mx-2 relative top-[2.5px] w-6"
                    ></div>
                  ) : (
                    <img
                      className="mx-2 h-6 w-6 relative left-[2px]"
                      src={option.icon}
                      alt={option.id}
                    />
                  )}
                  <div className="font-medium truncate ltr:text-left rtl:text-right ltr:ml-1.5 rtl:mr-1.5 relative bottom-[1px]">
                    {t(authLabel, {
                      option: t(option.label, option.label),
                    })}
                  </div>
                </div>
              ))}
          </div>
        )}

      {showMoreOptions && Object.keys(iconsMap)?.length > 0 && (
        <div
          className="text-center cursor-pointer font-medium text-[#0953FA] mt-3 flex flex-row rtl:flex-row-reverse items-center justify-center"
          onClick={() => setShowMoreOptions(false)}
          onKeyDown={() => setShowMoreOptions(false)}
          id="show-more-options"
          role="button"
          tabIndex={0}
        >
          <span className="mr-2 rtl:ml-2 more-signin-options">
            {t('more_ways_to_sign_in')}
          </span>
          <span>
            <svg
              xmlns="http://www.w3.org/2000/svg"
              height="1em"
              viewBox="0 0 512 512"
            >
              <path
                className="arrow-down"
                d="M233.4 406.6c12.5 12.5 32.8 12.5 45.3 0l192-192c12.5-12.5 12.5-32.8 0-45.3s-32.8-12.5-45.3 0L256 338.7 86.6 169.4c-12.5-12.5-32.8-12.5-45.3 0s-12.5 32.8 0 45.3l192 192z"
              />
            </svg>
          </span>
        </div>
      )}
    </>
  );
}
