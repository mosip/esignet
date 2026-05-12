import { useTranslation } from 'react-i18next';
import { useLocation } from 'react-router-dom';
import { IMAGES } from '../constants/public-assets';

export default function SomethingWrongPage() {
  const { t } = useTranslation('translation', { keyPrefix: 'errors' });
  const location = useLocation();
  const statusCode =
    (location.state as { code?: number } | null)?.code ?? 'unknown';

  return (
    <div className="multipurpose-login-card w-full m-0 sm:shadow-lg py-24 sm:m-16 section-background flex flex-col items-center justify-center">
      <img
        className="mx-auto my-0"
        src={IMAGES.UNDER_CONSTRUCTION}
        alt="something_went_wrong"
      />
      <div className="error-page-header">
        {t(`statusCodeHeader.${statusCode}`, 'Something went wrong')}
      </div>
      <div className="error-page-detail">
        {t(
          `statusCodeSubHeader.${statusCode}`,
          'An unexpected error occurred. Please try again later.',
        )}
      </div>
    </div>
  );
}
