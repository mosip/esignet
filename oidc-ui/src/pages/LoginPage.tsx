import { SignIn } from "@thunderid/react";

// Reached only with a live transaction; main.tsx redirects bare /signin to the RP first.
export default function LoginPage() {
  return (
    <div
      className={
        "!rounded-lg w-auto sm:w-3/6 lg:max-w-sm md:z-10 md:m-auto py-4"
      }
    >
      <SignIn
        revalidateOnChangeAfterBlur
        onSuccess={() => {
          window.onbeforeunload = null;
        }}
        onError={() => {
          window.onbeforeunload = null;
        }}
      />
    </div>
  );
}
