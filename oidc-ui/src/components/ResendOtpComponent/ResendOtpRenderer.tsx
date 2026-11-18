import type { ComponentRenderContext } from "@thunderid/react";
import ResendOtp from "./ResendOtpComponent";
import type { ResendOtpFlowComponent } from "./ResendOtpModel";

export default function ResendOtpRenderer(
  component: ResendOtpFlowComponent,
  context: ComponentRenderContext,
) {
  return (
    <div className="resend-otp-renderer">
      <ResendOtp key={component.id} component={component} context={context} />
    </div>
  );
}
