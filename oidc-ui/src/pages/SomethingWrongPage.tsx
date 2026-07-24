import { useLocation } from 'react-router-dom';
import { IMAGES } from '../constants/public-assets';
import { useAppTranslation } from '../hooks/useAppTranslation';

export default function SomethingWrongPage() {
  const location = useLocation();
  const { t } = useAppTranslation();
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
        {t("errors.something_went_wrong")} ({statusCode})
      </div>
      <div className="error-page-detail">
        {t("errors.unexpected_error")}
      </div>
    </div>
  );
}
