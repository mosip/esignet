import { useTranslation } from "react-i18next";
import configService from "../services/configService";

const config = await configService();

export default function Footer({ i18nKeyPrefix = "footer" }) {

  const { t } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix,
  });

  return config["footer"] && (
    <footer className="footer-container flex w-full flex-row flex-wrap items-center justify-center gap-y-6 gap-x-1 border border-blue-gray-50 text-center" id="footer">
      {t("powered_by")}
      <img className="footer-brand-logo" alt={t("logo_alt")} />
    </footer>
  );
}
