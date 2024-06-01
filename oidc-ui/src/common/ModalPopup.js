import React from "react";

const ModalPopup = ({
  alertIcon,
  alertClassname,
  header,
  headerClassname,
  body,
  bodyClassname,
  footer,
  footerClassname,
}) => {
  return (
    <>
      <img
        className="top_left_bg_logo hidden md:block"
        alt="top left background"
      />
      <img
        className="bottom_left_bg_logo hidden md:block"
        alt="bottom left background"
      />
      <div
        className="relative z-50"
        aria-labelledby="modal-title"
        role="dialog"
        aria-modal="true"
      >
        <div className="fixed inset-0 bg-gray-500 bg-opacity-75 transition-opacity"></div>
        <div className="fixed inset-0 z-10 w-screen overflow-y-auto">
          <div className="flex min-h-full items-end justify-center p-4 text-center sm:items-center sm:p-0">
            <div className="relative transform overflow-hidden rounded-[20px] bg-white text-left shadow-xl transition-all duration-300 ease-out sm:my-8 sm:w-full sm:max-w-[28rem] m-auto">
              {alertIcon && (
                <div className={alertClassname}>
                  <img src={alertIcon} />
                </div>
              )}
              {header && <div className={headerClassname}>{header}</div>}
              {body && (
                <div className={bodyClassname}>
                  <p className="text-base text-[#707070]">{body}</p>
                </div>
              )}
              {footer && <div className={footerClassname}>{footer}</div>}
            </div>
          </div>
        </div>
      </div>
    </>
  );
};

export default ModalPopup;
