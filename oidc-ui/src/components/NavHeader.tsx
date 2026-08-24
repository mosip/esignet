import { useContext, useState, useRef, useEffect } from "react";
import { I18nContext, LanguageSwitcher } from "@thunderid/react";

const LANGUAGE_NATIVE_NAMES: Record<string, string> = {
  ar: "العربية",
  en: "English",
  es: "español",
  fr: "français",
  hi: "हिन्दी",
  km: "ខ្មែរ",
  kn: "ಕನ್ನಡ",
  si: "සිංහල",
  ta: "தமிழ்",
};

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

  const resolveLabel = (code: string, fallback: string) =>
    LANGUAGE_NATIVE_NAMES[code] || fallback;

  return (
    <div ref={dropdownRef} className="relative flex items-center" id="language_dropdown">
      <div className="mx-2 rtl:scale-x-[-1]">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          width="39"
          height="39"
          viewBox="0 0 39 39"
        >
          <g transform="translate(-1211.5 -10.5)">
            <path
              d="M19,0A19,19,0,1,1,0,19,19,19,0,0,1,19,0Z"
              transform="translate(1212 11)"
              stroke="rgba(0,0,0,0)"
              strokeWidth="1"
              className="language-icon-bg-color"
            />
            <path
              d="M298.862,206.5h-9.284A2.582,2.582,0,0,0,287,209.079v6.189a2.582,2.582,0,0,0,2.579,2.579h5.2l2.246,2.245a.774.774,0,0,0,1.32-.547v-1.7h.516a2.582,2.582,0,0,0,2.579-2.579v-6.189a2.582,2.582,0,0,0-2.579-2.579Zm-2.063,4.9h-.288a4.63,4.63,0,0,1-1.372,2.8,3.585,3.585,0,0,0,1.4.291.532.532,0,0,1,.534.516.5.5,0,0,1-.5.516,4.708,4.708,0,0,1-2.347-.633,4.606,4.606,0,0,1-2.331.633.531.531,0,0,1-.534-.516.5.5,0,0,1,.5-.516,3.681,3.681,0,0,0,1.45-.3,4.581,4.581,0,0,1-.967-1.327.516.516,0,1,1,.932-.442,3.6,3.6,0,0,0,.94,1.213,3.592,3.592,0,0,0,1.251-2.243h-3.827a.516.516,0,1,1,0-1.032H293.7v-1.032a.516.516,0,0,1,1.032,0v1.032H296.8a.516.516,0,0,1,0,1.032Z"
              transform="translate(941.304 -179.827)"
              className="language-icon-color"
            />
            <path
              d="M130.862,91h-9.284A2.582,2.582,0,0,0,119,93.578v6.189a2.582,2.582,0,0,0,2.579,2.579h.516v1.7a.773.773,0,0,0,1.32.547l2.246-2.245h.56V99.251a3.585,3.585,0,0,1,.152-1.032h-1.461l-.8,1.76a.516.516,0,1,1-.939-.427l2.579-5.673a.516.516,0,0,1,.939,0l1.092,2.4a3.59,3.59,0,0,1,2.049-.641h3.61V93.577A2.582,2.582,0,0,0,130.863,91Z"
              transform="translate(1101.051 -69.999)"
              className="language-icon-color"
            />
          </g>
        </svg>
      </div>

      <div>
        <button
          type="button"
          onClick={() => setIsOpen(!isOpen)}
          disabled={isLoading}
          className="inline-flex items-center bg-white outline-none focus-visible:ring-2 focus-visible:ring-orange-500 focus-visible:rounded cursor-pointer disabled:opacity-50"
          aria-expanded={isOpen}
          aria-haspopup="listbox"
          id="language_selection"
        >
          {resolveLabel(currentLanguage, currentLang?.displayName || currentLanguage)}
          <svg
            width="11"
            height="7"
            viewBox="0 0 11 7"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
            className="mx-[5px] relative top-[1px]"
          >
            <path
              d="M6.32475 6.11822C6.27602 6.1693 6.21679 6.21007 6.1508 6.23797C6.08481 6.26587 6.01351 6.28027 5.94142 6.28027C5.86934 6.28027 5.79803 6.26587 5.73205 6.23797C5.66606 6.21007 5.60682 6.1693 5.5581 6.11822L1.01957 1.35833C0.950664 1.28692 0.904963 1.19771 0.888077 1.10166C0.871191 1.00562 0.883855 0.906926 0.924515 0.817707C0.965175 0.728487 1.03206 0.652628 1.11695 0.599448C1.20184 0.546268 1.30105 0.518082 1.40237 0.518354L10.4794 0.518354C10.5804 0.518247 10.6793 0.546417 10.7639 0.599424C10.8486 0.652431 10.9153 0.727982 10.956 0.816849C10.9967 0.905717 11.0096 1.00406 10.9932 1.09986C10.9767 1.19566 10.9316 1.28478 10.8633 1.35633L6.32475 6.11822Z"
              className="language-icon-bg-color"
            />
          </svg>
        </button>

        {isOpen && (
          <div className="absolute end-0 z-50 mt-2" role="listbox">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 30 10"
              stroke="#BCBCBC"
              className="h-[7px] ms-auto me-4 block"
            >
              <polygon points="15,0 30,10 0,10" fill="#fff" />
              <line x1="15" y1="0" x2="0" y2="10" strokeWidth="1" />
              <line x1="15" y1="0" x2="30" y2="10" strokeWidth="1" />
            </svg>
            <div className="min-w-[190px] bg-white rounded-md shadow-sm border border-[#BCBCBC] px-3 py-1">
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
                  className={`w-full text-start text-[14px] leading-none flex items-center py-3 cursor-pointer select-none outline-none focus-visible:ring-2 focus-visible:ring-orange-500 focus-visible:rounded [&:not(:last-child)]:border-b-[1px] langDropdown${lang.code === currentLanguage ? " selectedLang" : ""
                    }`}
                >
                  {resolveLabel(lang.code, lang.displayName)}
                  {lang.code === currentLanguage && (
                    <div className="ms-auto">
                      <svg
                        xmlns="http://www.w3.org/2000/svg"
                        width="16"
                        height="16"
                        viewBox="0 0 24 24"
                        fill="none"
                        strokeWidth="2"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        className="checkIcon"
                      >
                        <path d="M20 6 9 17l-5-5" />
                      </svg>
                    </div>
                  )}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
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
