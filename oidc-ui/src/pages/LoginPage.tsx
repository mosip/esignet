import { SignIn } from "@thunderid/react";
import LoadingIndicator from "../components/LoadingIndicator";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
const REQUIRED_PARAMS = ["applicationId", "authId"];

export default function LoginPage() {
  const [isLoading, setIsLoading] = useState(true);
  let searchParams: URLSearchParams | null = null;
  const navigate = useNavigate();

  useEffect(() => {
    // setting search params in useEffect to ensure it runs
    // only on client side and after the component mounts
    searchParams = new URLSearchParams(window.location.search);
  }, []);

  useEffect(() => {
    // If searchParams is not set yet, we cannot validate.
    // Wait for the next effect run.
    if (!searchParams) return;

    // Check if all required params are present
    const allParamsPresent = REQUIRED_PARAMS.every((p: string) =>
      searchParams?.has(p),
    );
    if (allParamsPresent) {
      setIsLoading(false);
      return;
    }

    // If we have search params but are missing
    // required ones, show page not found
    navigate("/something-went-wrong", {
      state: { code: 401 },
      replace: true,
    });
  }, [searchParams]);
  return (
    <div
      className={
        "!rounded-lg w-auto sm:w-3/6 lg:max-w-sm md:z-10 md:m-auto py-4"
      }
    >
      {isLoading ? (
        <LoadingIndicator />
      ) : (
        <SignIn revalidateOnChangeAfterBlur />
      )}
    </div>
  );
}
