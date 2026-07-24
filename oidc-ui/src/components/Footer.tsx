import { useEffect, useState } from "react";
import { fetchThemeConfig } from "../services/config.service";
import { useAppTranslation } from "../hooks/useAppTranslation";
import type { ThemeConfig } from "../types";

export default function Footer() {
  const [config, setConfig] = useState<ThemeConfig | null>(null);
  const { t } = useAppTranslation();

  useEffect(() => {
    let mounted = true;
    fetchThemeConfig()
      .then((cfg) => {
        if (mounted) setConfig(cfg);
      })
      .catch((error) => {
        console.error("Failed to fetch footer config:", error);
      });
    return () => {
      mounted = false;
    };
  }, []);

  if (!config?.footer) return null;

  return (
    <footer
      className="footer-container flex w-full flex-row flex-wrap items-center justify-center gap-y-6 gap-x-1 border border-blue-gray-50 text-center"
      id="footer"
    >
      {t("app.footer.powered_by")}
      <img className="footer-brand-logo" alt="logo" />
    </footer>
  );
}
