import type { EmbeddedFlowComponent } from "@thunderid/react";
import type { CaptchaComponentType } from "../index";

export interface ResendOtpFlowComponent extends EmbeddedFlowComponent {
  timeLeft?: number;
  captcha?: CaptchaComponentType;
}
