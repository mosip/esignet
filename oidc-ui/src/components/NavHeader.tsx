import { useContext } from "react";
import { I18nContext, LanguageSwitcher } from "@thunderid/react";

/**
 * Renders the language switcher only when inside a ThunderIDProvider context.
 * I18nContext defaults to null, so this safely returns null on error pages
 * and any route rendered without ThunderIDProvider — avoiding hook throws.
 */
function SafeLanguageSwitcher() {
  const i18n = useContext(I18nContext);
  if (!i18n) return null;
  return <LanguageSwitcher />;
}

export default function NavHeader() {
  return (
    <nav
      className="bg-white border-gray-500 md:px-[4rem] py-2 px-[0.5rem] navbar-header"
      id="navbar-header"
    >
      <div className="flex h-full items-center justify-between">
        <div className="sm:ml-8 ml-1">
          <img className="brand-logo" alt="brand_logo" />
        </div>
        <div className="sm:mr-8 mr-1">
          <SafeLanguageSwitcher />
        </div>
      </div>
    </nav>
  );
}
