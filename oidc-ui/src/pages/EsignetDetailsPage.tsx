import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { LoadingStates } from '../constants/states';
import type { LoadingState } from '../constants/states';

interface WellKnownDetail {
  name: string;
  value: string;
  icon?: string;
}

export default function EsignetDetailsPage() {
  const { t } = useTranslation('translation', {
    keyPrefix: 'esignetDetails',
  });
  const [status, setStatus] = useState<LoadingState>(LoadingStates.LOADING);
  const [details, setDetails] = useState<WellKnownDetail[]>([]);

  useEffect(() => {
    setStatus(LoadingStates.LOADING);

    let detailList: WellKnownDetail[] = [];
    try {
      detailList = JSON.parse(
        decodeURIComponent(window._env_.DEFAULT_WELLKNOWN),
      );
    } catch {
      console.error('Default Wellknown Endpoint is not a valid JSON');
    }

    setDetails(detailList);
    setStatus(LoadingStates.LOADED);
  }, []);

  const openWellKnownEndpoint = (endpoint: string) => {
    window.open(
      `${import.meta.env.BASE_URL}${endpoint.replace(/^\//, '')}`,
      '_blank',
      'noopener,noreferrer',
    );
  };

  return (
    <div className="lg:flex-grow md:w-1/2 flex flex-col md:items-start text-left items-center">
      <div className="w-full flex justify-center">
        <img className="mb-4 h-20 brand-only-logo" alt="brand-logo" />
      </div>
      <div className="w-full">
        <h1 className="flex justify-center title-font sm:text-3xl text-3xl mb-16 font-medium text-gray-900">
          {t('esignet_details_heading', 'eSignet Details')}
        </h1>
      </div>
      <div className="w-full flex justify-center">
        {status === LoadingStates.LOADED && details.length > 0 && (
          <div className="w-3/4 h-min shadow-md rounded bg-[#F8F8F8]">
            <div className="py-3">
              <div className="divide-y-2 gap-2">
                {details.map((detail) => (
                  <div
                    key={detail.name}
                    className="px-2 py-1 grid grid-cols-3"
                  >
                    <div className="col-span-1 flex justify-start">
                      {detail.icon ? (
                        <img src={detail.icon} alt="" />
                      ) : (
                        detail.name
                      )}
                    </div>
                    <div
                      id={`detail-${detail.name.replaceAll(' ', '-')}`}
                      className="col-span-2 flex justify-start cursor-pointer"
                      onClick={() => openWellKnownEndpoint(detail.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          openWellKnownEndpoint(detail.value);
                        }
                      }}
                      role="button"
                      tabIndex={0}
                    >
                      {detail.value}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
