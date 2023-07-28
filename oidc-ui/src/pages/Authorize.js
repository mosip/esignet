import React from "react";
import Authorize from "../components/Authorize";
import authService from "../services/authService";

export default function AuthorizePage() {

  return (
    <>
      <div className="h-5/6 flex items-center justify-center py-12 px-4 sm:px-6 lg:px-8">
        <div className="max-w-md w-full space-y-8">
          <Authorize
            authService={new authService(null)}
          />
        </div>
      </div>
    </>
  );
}
