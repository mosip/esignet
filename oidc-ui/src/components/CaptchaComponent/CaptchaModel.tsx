import type {
  ComponentRenderContext,
  EmbeddedFlowComponent,
} from "@thunderid/react";

export type CaptchaProvider =
  | "google-recaptcha"
  | "cloudflare-turnstile"
  | "hcaptcha";

export interface CaptchaComponent {
  provider?: CaptchaProvider;
  siteKey?: string;
  theme?: "light" | "dark";
  size?: string;
}

export interface CaptchaFlowComponent extends EmbeddedFlowComponent {
  captcha?: CaptchaComponent;
}

export interface CaptchaComponentProps {
  component: CaptchaFlowComponent;
  context: ComponentRenderContext;
}

export interface CaptchaProps {
  captcha: CaptchaComponent | undefined;
  handleSuccess: (token: string | null) => void;
  handleError: () => void;
  handleExpire: () => void;
}
