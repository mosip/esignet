import { SignIn } from "@thunderid/react";

// Reached only with a live transaction; main.tsx redirects bare /signin to the RP first.
export default function LoginPage() {
  return (
    // Fluid width, capped at the SDK's 420px card width.
    <div
      className={
        "!rounded-lg w-full max-w-[420px] mx-auto md:z-10 md:m-auto py-4"
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
