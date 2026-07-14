import type {
  ComponentRenderContext,
  EmbeddedFlowComponent,
} from "@thunderid/react";
import BackButton from "./BackButtonComponent";

export default function BackButtonRenderer(
  component: EmbeddedFlowComponent,
  context: ComponentRenderContext,
) {
  return (
    <div className="back-button-renderer">
      <BackButton key={component.id} component={component} context={context} />
    </div>
  );
}
