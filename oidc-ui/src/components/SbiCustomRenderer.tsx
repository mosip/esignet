import type {
  ComponentRenderContext,
  EmbeddedFlowComponent,
} from "@asgardeo/react";
import Sbi from "./SbiComponent";

export default function SbiCustomRenderer(
  component: EmbeddedFlowComponent,
  context: ComponentRenderContext,
) {
  return (
    <div className="sbi-custom-renderer">
      <Sbi key={component.id} component={component} context={context} />
    </div>
  );
}
