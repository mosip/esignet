import { IMAGES } from "../constants/public-assets";

export default function PageNotFoundPage() {
  return (
    <div
      className="multipurpose-login-card w-full m-0 sm:shadow py-24 sm:mx-16 sm:my-8 sm:min-h-[80vh] section-background flex flex-col justify-center items-center"
      style={{ boxShadow: "0px 2px 5px #0000001A" }}
    >
      <img
        className="mx-auto my-0"
        src={IMAGES.UNDER_CONSTRUCTION}
        alt="Page not found"
      />
      <div className="error-page-header">Page Not Found</div>
    </div>
  );
}
