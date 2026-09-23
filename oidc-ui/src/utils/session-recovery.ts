import { SOMETHING_WENT_WRONG } from "../constants/routes";

// Step back to the relying party that started the flow. When /signin is the only
// history entry (opened directly), there is nothing to step back to, so fall back
// to the error page instead of leaving a blank screen.
export function returnToRelyingParty(): void {
  window.onbeforeunload = null;
  if (window.history.length <= 1) {
    window.location.replace(SOMETHING_WENT_WRONG);
    return;
  }
  window.history.back();
}
