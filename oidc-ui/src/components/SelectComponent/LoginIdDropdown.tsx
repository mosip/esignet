import {
  useCallback,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent,
  type FocusEvent,
  type MouseEvent as ReactMouseEvent,
} from "react";

export interface DropdownOption {
  label: string;
  value: string;
  /** URL of an SVG used as a mask, so it takes the text colour of its row. */
  icon?: string;
  disabled?: boolean;
}

export interface LoginIdDropdownProps {
  id?: string;
  label?: string;
  /** Accessible name used when there is no visible label. */
  ariaLabel?: string;
  placeholder?: string;
  required?: boolean;
  disabled?: boolean;
  options: readonly DropdownOption[];
  value?: string;
  error?: string;
  helperText?: string;
  onChange?: (value: string) => void;
  onBlur?: () => void;
  className?: string;
}

function OptionIcon({ src }: { src?: string }) {
  if (!src) return null;
  return (
    <span
      aria-hidden="true"
      className="login-id-dropdown__icon"
      style={{
        maskImage: `url("${src}")`,
        WebkitMaskImage: `url("${src}")`,
      }}
    />
  );
}

function ChevronIcon({ open }: { open: boolean }) {
  return (
    <svg
      aria-hidden="true"
      width="16"
      height="16"
      viewBox="0 0 16 16"
      fill="none"
      className={`login-id-dropdown__chevron${open ? " login-id-dropdown__chevron--open" : ""}`}
    >
      <path
        d="M4 6l4 4 4-4"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg
      aria-hidden="true"
      width="20"
      height="20"
      viewBox="0 0 20 20"
      fill="none"
      className="login-id-dropdown__check"
    >
      <path
        d="M4 10.5l4 4 8-9"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

/**
 * A custom dropdown with the ThunderID `Select` contract (label, placeholder,
 * required, options, value, error, onChange, onBlur) rendered as an
 * accessible combobox + listbox, so each option can show an icon and the
 * selected row its highlighted state.
 */
export default function LoginIdDropdown({
  id,
  label,
  ariaLabel,
  placeholder,
  required = false,
  disabled = false,
  options,
  value = "",
  error,
  helperText,
  onChange,
  onBlur,
  className,
}: LoginIdDropdownProps) {
  const generatedId = useId();
  const triggerId = id ?? generatedId;
  const listboxId = `${triggerId}-listbox`;
  const labelId = `${triggerId}-label`;
  const helperTextId = error || helperText ? `${triggerId}-helper-text` : undefined;

  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  const [open, setOpen] = useState(false);
  const selectedIndex = useMemo(
    () => options.findIndex((o) => o.value === value),
    [options, value],
  );
  const [activeIndex, setActiveIndex] = useState<number>(selectedIndex);

  const firstEnabled = options.findIndex((o) => !o.disabled);
  const selected = selectedIndex >= 0 ? options[selectedIndex] : undefined;
  const hasError = Boolean(error);

  const openList = useCallback(() => {
    if (disabled) return;
    setActiveIndex(selectedIndex >= 0 ? selectedIndex : firstEnabled);
    setOpen(true);
  }, [disabled, firstEnabled, selectedIndex]);

  const closeList = useCallback(() => setOpen(false), []);

  const commit = useCallback(
    (index: number) => {
      const option = options[index];
      if (!option || option.disabled) return;
      if (option.value !== value) onChange?.(option.value);
      setOpen(false);
      triggerRef.current?.focus();
    },
    [onChange, options, value],
  );

  // Close on outside click.
  useEffect(() => {
    if (!open) return;
    const handle = (event: MouseEvent | TouchEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) closeList();
    };
    document.addEventListener("mousedown", handle);
    document.addEventListener("touchstart", handle);
    return () => {
      document.removeEventListener("mousedown", handle);
      document.removeEventListener("touchstart", handle);
    };
  }, [open, closeList]);

  // Keep the active row visible while navigating with the keyboard.
  useEffect(() => {
    if (!open || activeIndex < 0) return;
    const row = listRef.current?.children[activeIndex] as
      | HTMLElement
      | undefined;
    row?.scrollIntoView?.({ block: "nearest" });
  }, [open, activeIndex]);

  const move = (from: number, step: 1 | -1) => {
    let i = from;
    for (let n = 0; n < options.length; n++) {
      i = (i + step + options.length) % options.length;
      if (!options[i].disabled) return i;
    }
    return from;
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (disabled) return;
    switch (event.key) {
      case "ArrowDown":
        event.preventDefault();
        if (!open) openList();
        else setActiveIndex((i) => move(i, 1));
        break;
      case "ArrowUp":
        event.preventDefault();
        if (!open) openList();
        else setActiveIndex((i) => move(i, -1));
        break;
      case "Home":
        if (open) {
          event.preventDefault();
          setActiveIndex(firstEnabled);
        }
        break;
      case "End":
        if (open) {
          event.preventDefault();
          setActiveIndex(move(0, -1));
        }
        break;
      // Space is left to the button: its native click (on keyup) reaches
      // handleClick, so the list is toggled exactly once in every browser.
      case "Enter":
        event.preventDefault();
        if (!open) openList();
        else commit(activeIndex);
        break;
      case "Escape":
        if (open) {
          event.preventDefault();
          closeList();
        }
        break;
      case "Tab":
        if (open) closeList();
        break;
      default:
        break;
    }
  };

  const handleClick = (event: ReactMouseEvent<HTMLButtonElement>) => {
    if (!open) openList();
    // detail 0: a keyboard (Space) or assistive-tech activation, which picks
    // the active row like Enter. A real mouse click just closes the list.
    else if (event.detail === 0) commit(activeIndex);
    else closeList();
  };

  const handleBlur = (event: FocusEvent<HTMLDivElement>) => {
    // Only report blur once focus has left the whole control.
    if (rootRef.current?.contains(event.relatedTarget as Node)) return;
    closeList();
    onBlur?.();
  };

  const describedBy = helperTextId;
  const activeId =
    open && activeIndex >= 0 ? `${listboxId}-option-${activeIndex}` : undefined;

  return (
    <div
      ref={rootRef}
      className={`login-id-dropdown${className ? ` ${className}` : ""}`}
      onBlur={handleBlur}
    >
      {label && (
        // Not a <label htmlFor>: clicking a label activates its button,
        // which would toggle the list. The trigger uses aria-labelledby.
        <span
          id={labelId}
          className={`login-id-dropdown__label${hasError ? " login-id-dropdown__label--error" : ""}`}
        >
          {label}
          {required && (
            <span aria-hidden="true" className="login-id-dropdown__required">
              {" *"}
            </span>
          )}
        </span>
      )}

      <button
        ref={triggerRef}
        id={triggerId}
        type="button"
        role="combobox"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listboxId}
        aria-labelledby={label ? labelId : undefined}
        aria-label={label ? undefined : ariaLabel}
        aria-required={required || undefined}
        aria-invalid={hasError || undefined}
        aria-describedby={describedBy}
        aria-activedescendant={activeId}
        disabled={disabled}
        data-open={open ? "true" : undefined}
        data-error={hasError ? "true" : undefined}
        className={[
          "login-id-dropdown__trigger",
          selected ? "login-id-dropdown__trigger--selected" : "",
          hasError ? "login-id-dropdown__trigger--error" : "",
        ]
          .filter(Boolean)
          .join(" ")}
        onClick={handleClick}
        onKeyDown={handleKeyDown}
      >
        {selected ? (
          <>
            <OptionIcon src={selected.icon} />
            <span className="login-id-dropdown__value">{selected.label}</span>
          </>
        ) : (
          <span className="login-id-dropdown__placeholder">{placeholder}</span>
        )}
        <ChevronIcon open={open} />
      </button>

      {open && (
        <ul
          ref={listRef}
          id={listboxId}
          role="listbox"
          aria-labelledby={label ? labelId : undefined}
          aria-label={label ? undefined : ariaLabel}
          className="login-id-dropdown__list"
        >
          {options.map((option, index) => {
            const isSelected = index === selectedIndex;
            const isActive = index === activeIndex;
            return (
              <li
                key={option.value}
                id={`${listboxId}-option-${index}`}
                role="option"
                aria-selected={isSelected}
                aria-disabled={option.disabled || undefined}
                data-value={option.value}
                className={[
                  "login-id-dropdown__option",
                  isSelected ? "login-id-dropdown__option--selected" : "",
                  isActive ? "login-id-dropdown__option--active" : "",
                  option.disabled ? "login-id-dropdown__option--disabled" : "",
                ]
                  .filter(Boolean)
                  .join(" ")}
                onMouseEnter={() => !option.disabled && setActiveIndex(index)}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => commit(index)}
              >
                <OptionIcon src={option.icon} />
                <span className="login-id-dropdown__option-label">
                  {option.label}
                </span>
                {isSelected && <CheckIcon />}
              </li>
            );
          })}
        </ul>
      )}

      {(error || helperText) && (
        <p
          id={helperTextId}
          role={error ? "alert" : undefined}
          className={`login-id-dropdown__helper${hasError ? " login-id-dropdown__helper--error" : ""}`}
        >
          {error || helperText}
        </p>
      )}
    </div>
  );
}
