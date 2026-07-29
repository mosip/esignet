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
}

export interface CaptchaProps {
  captcha: CaptchaComponentType | undefined;
  handleSuccess: (token: string | null) => void;
  handleError: () => void;
  handleExpire: () => void;
}
