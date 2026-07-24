import type { ComponentRenderContext } from "@thunderid/react";
import CaptchaComponent from "./CaptchaComponent";
import type { CaptchaFlowComponent } from "./CaptchaModel";

export default function CaptchaRenderer(
  component: CaptchaFlowComponent,
  context: ComponentRenderContext,
) {
  return (
    <div className="captcha-renderer">
      <CaptchaComponent
        key={component.id}
        component={component}
        context={context}
      />
    </div>
  );
}
