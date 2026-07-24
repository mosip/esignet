import { useContext, useState, useRef, useEffect } from "react";
import { I18nContext, LanguageSwitcher } from "@thunderid/react";

/**
 * Renders the language switcher only when inside a ThunderIDProvider context.
 * Uses the LanguageSwitcher render-props API to show language names without
 * country flag icons.
 */
function SafeLanguageSwitcher() {
  const i18n = useContext(I18nContext);
  if (!i18n) return null;

  return (
    <LanguageSwitcher>
      {({ languages, currentLanguage, onLanguageChange, isLoading }) => (
        <LanguageDropdown
          languages={languages}
          currentLanguage={currentLanguage}
          onLanguageChange={onLanguageChange}
          isLoading={isLoading}
        />
      )}
    </LanguageSwitcher>
  );
}

interface LanguageOption {
  code: string;
  displayName: string;
}

function LanguageDropdown({
  languages,
  currentLanguage,
  onLanguageChange,
  isLoading,
}: {
  languages: LanguageOption[];
  currentLanguage: string;
  onLanguageChange: (code: string) => void;
  isLoading: boolean;
}) {
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const currentLang = languages.find((l) => l.code === currentLanguage);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  if (languages.length <= 1) return null;

  return (
    <div ref={dropdownRef} className="relative inline-block text-start">
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        disabled={isLoading}
        className="inline-flex items-center gap-1 px-3 py-1.5 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-1 focus:ring-orange-500 disabled:opacity-50"
        aria-expanded={isOpen}
        aria-haspopup="listbox"
      >
        {currentLang?.displayName || currentLanguage}
        <svg
          className={`w-4 h-4 transition-transform ${isOpen ? "rotate-180" : ""}`}
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M19 9l-7 7-7-7"
          />
        </svg>
      </button>

      {isOpen && (
        <div
          className="absolute end-0 z-50 mt-1 w-40 origin-top-right bg-white border border-gray-200 rounded-md shadow-lg"
          role="listbox"
        >
          {languages.map((lang) => (
            <button
              key={lang.code}
              type="button"
              role="option"
              aria-selected={lang.code === currentLanguage}
              onClick={() => {
                onLanguageChange(lang.code);
                setIsOpen(false);
              }}
              className={`w-full text-start px-4 py-2 text-sm hover:bg-gray-100 ${
                lang.code === currentLanguage
                  ? "font-semibold text-orange-600 bg-orange-50"
                  : "text-gray-700"
              }`}
            >
              {lang.displayName}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export default function NavHeader() {
  return (
    <nav
      className="bg-white border-gray-500 md:px-[4rem] py-2 px-[0.5rem] navbar-header"
      id="navbar-header"
    >
      <div className="flex h-full items-center justify-between">
        <div className="sm:ms-8 ms-1">
          <img className="brand-logo" alt="brand_logo" />
        </div>
        <div className="sm:me-8 me-1">
          <SafeLanguageSwitcher />
        </div>
      </div>
    </nav>
  );
}
