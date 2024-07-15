import React from "react";
import ClaimDetails from "../components/ClaimDetails";
import { render } from "@testing-library/react";

const hash =
  "#eyJ0cmFuc2FjdGlvbklkIjoiOGNXSFVUWHRaa1hkbC1MS21oX2lMUkMwQmV3MlBrTG5mRkhaQ2pDOVFDWSIsImxvZ29VcmwiOiJodHRwczovL2hlYWx0aHNlcnZpY2VzLmRldi5tb3NpcC5uZXQvaW1hZ2VzL2RvY3Rvcl9sb2dvLnBuZyIsImF1dGhGYWN0b3JzIjpbW3sidHlwZSI6Ik9UUCIsImNvdW50IjowLCJzdWJUeXBlcyI6bnVsbH1dLFt7InR5cGUiOiJLQkEiLCJjb3VudCI6MCwic3ViVHlwZXMiOm51bGx9XSxbeyJ0eXBlIjoiQklPIiwiY291bnQiOjEsInN1YlR5cGVzIjpudWxsfV0sW3sidHlwZSI6IlBJTiIsImNvdW50IjowLCJzdWJUeXBlcyI6bnVsbH1dLFt7InR5cGUiOiJQV0QiLCJjb3VudCI6MCwic3ViVHlwZXMiOm51bGx9XSxbeyJ0eXBlIjoiV0FMTEVUIiwiY291bnQiOjAsInN1YlR5cGVzIjpudWxsfV1dLCJhdXRob3JpemVTY29wZXMiOlsicmVzaWRlbnQtc2VydmljZSJdLCJlc3NlbnRpYWxDbGFpbXMiOlsiYmlydGhkYXRlIiwiYWRkcmVzcyIsImdlbmRlciIsInBob25lX251bWJlciIsImVtYWlsIiwicmVnaXN0cmF0aW9uX3R5cGUiXSwidm9sdW50YXJ5Q2xhaW1zIjpbIm5hbWUiLCJwaWN0dXJlIl0sImNvbmZpZ3MiOnsic2JpLmVudiI6IkRldmVsb3BlciIsInNiaS50aW1lb3V0LkRJU0MiOjMwLCJzYmkudGltZW91dC5ESU5GTyI6MzAsInNiaS50aW1lb3V0LkNBUFRVUkUiOjMwLCJzYmkuY2FwdHVyZS5jb3VudC5mYWNlIjoxLCJzYmkuY2FwdHVyZS5jb3VudC5maW5nZXIiOjEsInNiaS5jYXB0dXJlLmNvdW50LmlyaXMiOjEsInNiaS5jYXB0dXJlLnNjb3JlLmZhY2UiOjcwLCJzYmkuY2FwdHVyZS5zY29yZS5maW5nZXIiOjcwLCJzYmkuY2FwdHVyZS5zY29yZS5pcmlzIjo3MCwicmVzZW5kLm90cC5kZWxheS5zZWNzIjo2MCwic2VuZC5vdHAuY2hhbm5lbHMiOiJlbWFpbCxwaG9uZSIsImNhcHRjaGEuc2l0ZWtleSI6InRlc3QiLCJjYXB0Y2hhLmVuYWJsZSI6InNlbmQtb3RwLG90cCxwd2QiLCJhdXRoLnR4bmlkLmxlbmd0aCI6IjEwIiwiY29uc2VudC5zY3JlZW4udGltZW91dC1pbi1zZWNzIjo2MCwiY29uc2VudC5zY3JlZW4udGltZW91dC1idWZmZXItaW4tc2VjcyI6NSwibGlua2VkLXRyYW5zYWN0aW9uLWV4cGlyZS1pbi1zZWNzIjoyNDAsInNiaS5wb3J0LnJhbmdlIjoiNDUwMS00NjAwIiwic2JpLmJpby5zdWJ0eXBlcy5pcmlzIjoiVU5LTk9XTiIsInNiaS5iaW8uc3VidHlwZXMuZmluZ2VyIjoiVU5LTk9XTiIsIndhbGxldC5xci1jb2RlLWJ1ZmZlci1pbi1zZWNzIjoxMCwib3RwLmxlbmd0aCI6NiwicGFzc3dvcmQucmVnZXgiOiIiLCJwYXNzd29yZC5tYXgtbGVuZ3RoIjoyMCwidXNlcm5hbWUucmVnZXgiOiIiLCJ1c2VybmFtZS5wcmVmaXgiOiIiLCJ1c2VybmFtZS5wb3N0Zml4IjoiIiwidXNlcm5hbWUubWF4LWxlbmd0aCI6MTAsInVzZXJuYW1lLmlucHV0LXR5cGUiOiJudW1iZXIiLCJ3YWxsZXQuY29uZmlnIjp7IndhbGxldC5uYW1lIjoid2FsbGV0TmFtZSIsIndhbGxldC5sb2dvLXVybCI6Ii9pbWFnZXMvcXJfY29kZS5wbmciLCJ3YWxsZXQuZG93bmxvYWQtdXJpIjoiIyIsIndhbGxldC5kZWVwLWxpbmstdXJpIjoiaW5qaTovL2xhbmRpbmctcGFnZS1uYW1lP2xpbmtDb2RlPUxJTktfQ09ERSZsaW5rRXhwaXJlRGF0ZVRpbWU9TElOS19FWFBJUkVfRFQifSwic2lnbnVwLmNvbmZpZyI6eyJzaWdudXAuYmFubmVyIjp0cnVlLCJzaWdudXAudXJsIjoiaHR0cDovL2xvY2FsaG9zdDozMDAwL3NpZ251cCJ9LCJmb3Jnb3QtcGFzc3dvcmQuY29uZmlnIjp7ImZvcmdvdC1wYXNzd29yZCI6dHJ1ZSwiZm9yZ290LXBhc3N3b3JkLnVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6MzAwMC9mb3Jnb3QtcGFzc3dvcmQifSwiZUtZQy1zdGVwcy5jb25maWciOiJodHRwOi8vbG9jYWxob3N0OjMwMDAvaWRlbnRpdHktdmVyaWZpY2F0aW9uIiwiZXJyb3IuYmFubmVyLmNsb3NlLXRpbWVyIjoxMCwiYXV0aC5mYWN0b3Iua2JhLmluZGl2aWR1YWwtaWQtZmllbGQiOiJwb2xpY3lOdW1iZXIiLCJhdXRoLmZhY3Rvci5rYmEuZmllbGQtZGV0YWlscyI6W119LCJyZWRpcmVjdFVyaSI6Imh0dHA6Ly9sb2NhbGhvc3Q6NTAwMC91c2VycHJvZmlsZSIsImNsaWVudE5hbWUiOnsiQG5vbmUiOiJIZWFsdGggc2VydmljZSJ9LCJjcmVkZW50aWFsU2NvcGVzIjpbXX0=";

const url =
  "http://localhost:3000/claim-details?nonce=ere973eieljznge2311&state=eree2311&consentAction=CAPTURE&authenticationTime=1717572654" +
  hash;

jest.mock("react-i18next", () => ({
  // this mock makes sure any components using the translate hook can use it without a warning being shown
  useTranslation: () => {
    return {
      t: (str) => str,
      i18n: {
        changeLanguage: () => new Promise(() => {}),
        // You can include here any property your component may use
      },
    };
  },
  initReactI18next: {
    type: "3rdParty",
    init: () => {},
  },
}));

describe("ClaimDetails Component", () => {
  test("should render ClaimDetails component", async () => {
    Object.defineProperty(window, "location", {
      get() {
        return { href: url };
      },
    });
    render(<ClaimDetails />);
  });
});
