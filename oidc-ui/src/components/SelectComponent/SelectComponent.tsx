import { useEffect, useState } from "react";
import {
  useTranslation,
  resolveFlowTemplateLiterals,
  type ComponentRenderContext,
} from "@thunderid/react";
import type { SelectFlowComponent } from "./SelectModel";
import LoginIdDropdown, { type DropdownOption } from "./LoginIdDropdown";

interface RawOption {
  label?: unknown;
  value?: unknown;
  icon?: string;
  disabled?: boolean;
}

export interface SelectComponentProps {
  component: SelectFlowComponent;
  context: ComponentRenderContext;
  /** Icon URL per option value, used when an option has no icon of its own. */
  icons?: Readonly<Record<string, string>>;
  /** i18n key for the label shown above the select when the flow gives none. */
  labelKey?: string;
}

/**
 * A flow select whose options are actions: picking one submits the flow with
 * the option's value as the action id, so the server's step (selectedValue)
 * is always what is shown. Feature-specific bits (icons, default label)
 * come from the renderer that registers it.
 */
export default function SelectComponent({
  component,
  context,
  icons,
  labelKey,
}: SelectComponentProps) {
  const { t } = useTranslation();
  const { onInputChange, onSubmit, resetForm, onInputBlur } = context;
  const identifier = component.ref ?? component.id;
  const selectedValue = component.selectedValue || "";
  const formValue: string | undefined = context.formValues[identifier];

  // Option chosen by the user while its submit is in flight. The form is
  // reset before submitting, so without this the trigger would flash back to
  // the current page's option until the next flow step arrives.
  const [pendingValue, setPendingValue] = useState<string>();

  // Keep the form value in step with the current step's option, so it is
  // sent with the step's other inputs. Done in an effect because the SDK
  // renders us inside its own render.
  useEffect(() => {
    if (selectedValue && formValue !== selectedValue) {
      onInputChange(identifier, selectedValue);
    }
  }, [onInputChange, identifier, selectedValue, formValue]);

  // Resolves "{{t(ns:key)}}" templates; plain text is returned unchanged.
  const resolve = (text: string) =>
    resolveFlowTemplateLiterals(text, { t }) || text;

  const selectOptions: DropdownOption[] = (
    (component.options || []) as readonly (string | RawOption)[]
  ).map((opt) => {
    if (typeof opt === "string") {
      return { label: resolve(opt), value: opt, icon: icons?.[opt] };
    }
    const value = String(opt.value ?? "");
    return {
      label: resolve(String(opt.label ?? opt.value ?? "")),
      value,
      icon: opt.icon || icons?.[value],
      disabled: opt.disabled,
    };
  });

  // Shown above the select: the flow's label, else the renderer's default.
  const label = component.label
    ? resolve(component.label)
    : labelKey
      ? t(labelKey)
      : undefined;
  const placeholder = component.placeholder
    ? resolve(component.placeholder)
    : undefined;

  const value: string = pendingValue || selectedValue;
  const isTouched: boolean = context.touchedFields[identifier] || false;
  const error: string | undefined = isTouched
    ? context.formErrors[identifier]
    : undefined;

  const onSelection = (next: string) => {
    // The SDK always passes onSubmit to sign-in renderers; it is only
    // optional in the shared context type.
    if (!onSubmit) return;
    setPendingValue(next);
    // Mirrors the SDK's handling of a RESET action button (AuthOptionFactory):
    // drop the step's inputs, then submit the option's value as the action id
    // without validation.
    resetForm?.();
    // onSubmit is async though typed as void. The SDK reports submit errors
    // itself, so a rejection is only silenced here; either way the pending
    // choice is cleared so a failed submit shows the real selection.
    Promise.resolve(onSubmit({ ...component, id: next }, {}, true))
      .catch(() => undefined)
      .finally(() => setPendingValue(undefined));
  };

  return (
    <div className="w-full">
      <LoginIdDropdown
        id={component.id}
        label={label}
        placeholder={placeholder}
        required={component.required}
        options={selectOptions}
        value={value}
        error={error}
        onChange={onSelection}
        onBlur={() => onInputBlur?.(identifier)}
      />
    </div>
  );
}
