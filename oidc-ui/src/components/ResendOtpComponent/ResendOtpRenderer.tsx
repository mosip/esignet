import type {
  ComponentRenderContext,
  EmbeddedFlowComponent,
} from "@thunderid/react";
import ResendOtp from "./ResendOtpComponent";

interface EmbeddedFlowComponentWithTimeLeft extends EmbeddedFlowComponent {
  timeLeft?: number;
}

export default function ResendOtpRenderer(
  component: EmbeddedFlowComponentWithTimeLeft,
  context: ComponentRenderContext,
) {
  return (
    <div className="back-button-renderer">
      <ResendOtp key={component.id} component={component} context={context} />
    </div>
  );
}
