import type {
  ComponentRenderContext,
  EmbeddedFlowComponent,
} from "@thunderid/react";

export type CaptchaProvider =
  | "google-recaptcha"
  | "cloudflare-turnstile"
  | "hcaptcha";

export interface CaptchaComponentType {
  provider?: CaptchaProvider;
  siteKey?: string;
  theme?: "light" | "dark";
  size?: string;
}

export interface CaptchaFlowComponent extends EmbeddedFlowComponent {
  captcha?: CaptchaComponentType;
}

export interface CaptchaComponentProps {
  component: CaptchaFlowComponent;
  context: ComponentRenderContext;
  // it is used to notify any changes occur in cpatcha token
  captchaChanged?: (token: string | null | undefined) => void;
}

export interface CaptchaProps {
  captcha: CaptchaComponentType | undefined;
  handleSuccess: (token: string | null) => void;
  handleError: () => void;
  handleExpire: () => void;
}
