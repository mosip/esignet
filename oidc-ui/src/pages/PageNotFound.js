import React from 'react';
import { useTranslation } from 'react-i18next';
import { IMAGES } from '../constants/publicAssets';

export default function PageNotFoundPage({ i18nKeyPrefix = 'errors' }) {
  const { t } = useTranslation('translation', { keyPrefix: i18nKeyPrefix });

  return (
    <div
      className="multipurpose-login-card w-full m-0 sm:shadow py-24 sm:mx-16 sm:my-8 sm:min-h-[80vh] section-background flex flex-col justify-center items-center"
      style={{ boxShadow: '0px 2px 5px #0000001A' }}
    >
      <img
        className="mx-auto my-0"
        src={IMAGES.UNDER_CONSTRUCTION}
        alt="page_not_found"
      />
      <div className="error-page-header">{t('page_not_exist')}</div>
    </div>
  );
}
