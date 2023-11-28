import { useTranslation } from "react-i18next";
import { getBooleanValue } from "../services/utilService";

export default function Footer({ i18nKeyPrefix = "footer" }) {
  const footerCheck = getBooleanValue("REACT_APP_FOOTER");

  const { t } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix,
  });

  return footerCheck && (
    <footer className="footer-container flex w-full flex-row flex-wrap items-center justify-center gap-y-6 gap-x-1 border border-blue-gray-50 text-center">
      {t("powered_by")}
      <img className="footer-brand-logo" alt={t("logo_alt")} />
    </footer>
  );
}
