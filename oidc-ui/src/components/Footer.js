import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import configService from "../services/configService";

export default function Footer({ i18nKeyPrefix = "footer" }) {
  const { t } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix,
  });

  const [config, setConfig] = useState(null);

  useEffect(() => {
    let isMounted = true;

    const fetchConfig = async () => {
      try {
        const cfg = await configService();
        if (isMounted) {
          setConfig(cfg);
        }
      } catch (error) {
        console.error("Error fetching footer config:", error);
      }
    };

    fetchConfig();

    return () => {
      isMounted = false;
    };
  }, []);

  if (!config || !config["footer"]) return null;

  return (
    <footer
      className="footer-container flex w-full flex-row flex-wrap items-center justify-center gap-y-6 gap-x-1 border border-blue-gray-50 text-center"
      id="footer"
    >
      {t("powered_by")}
      <img className="footer-brand-logo" alt={t("logo_alt")} />
    </footer>
  );
}
