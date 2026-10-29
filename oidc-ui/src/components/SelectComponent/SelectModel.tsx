import type { EmbeddedFlowComponent } from "@thunderid/react";

export interface SelectFlowComponent extends EmbeddedFlowComponent {
  selectedValue?: string;
}