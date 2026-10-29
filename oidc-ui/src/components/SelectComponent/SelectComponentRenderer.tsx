import type { ComponentRenderContext } from "@thunderid/react";
import SelectComponent from "./SelectComponent";
import type { SelectFlowComponent } from "./SelectModel";
import { LOGIN_ID_ICONS } from "../../constants/public-assets";

// Renderer for SELECT_LOGIN_ID: the generic flow select plus the login id
// icons and the "Login Id" label, used when the flow gives no label.
// The SDK calls renderers as plain functions during its own render (and sets
// the returned element's key itself), so this must stay side-effect free.
// Syncing selectedValue into the form happens in SelectComponent's effect.
export default function SelectComponentRenderer(
  component: SelectFlowComponent,
  context: ComponentRenderContext,
) {
  return (
    <div className="select-component-renderer">
      <SelectComponent
        component={component}
        context={context}
        icons={LOGIN_ID_ICONS}
        labelKey="dropdown.login_id_label"
      />
    </div>
  );
}
