import { SignIn, useAsgardeo } from '@asgardeo/react';
import { useEffect } from 'react';

export default function Login() {
  const { meta } = useAsgardeo();
  const heading = meta?.heading || 'Sign in to your account';
  const subheading = meta?.subHeading || 'This is a subheading';
  const clientName = meta?.application?.name || '';
  const clientLogoPath = meta?.application?.logo_url || '';

  useEffect(() => {
    console.log({ meta });
  }, []);

  return (
    <>
      <div
        className={
          'multipurpose-login-card shadow-sm m-3 !rounded-lg w-auto sm:w-3/6 lg:max-w-sm md:z-10 md:m-auto py-4'
        }
      >
        <div className="flex flex-col flex-grow lg:px-5 md:px-4 sm:px-3 px-3">
          <div className="w-full py-1">
            <h1
              className="flex text-center justify-center mb-3 font-bold text-xl"
              id="login-header"
            >
              {heading}
            </h1>
            {subheading && (
              <h1
                className="text-center justify-center title-font sm:text-base text-base mb-3 py-1 font-small"
                id="login-subheader"
              >
                {clientName}
              </h1>
            )}
          </div>
          <div className="w-full flex mb-4 justify-center items-center pb-2">
            {clientLogoPath && (
              <img
                className="object-contain client-logo-size client-logo-shadow rounded-[25px] border-[0.1px] border-white bg-black"
                src={clientLogoPath}
                alt={clientName}
              />
            )}
            <span className="flex mx-5 alternate-arrow"></span>
            <img
              className="object-contain brand-only-logo client-logo-size"
              alt="logo_alt"
            />
          </div>
          <div className="text-black lg:-mx-5 md:-mx-4 sm:-mx-3 -mx-3 login-card-separator"></div>
          <SignIn />
        </div>
      </div>
    </>
  );
}
