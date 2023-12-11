import React from "react";

const ModalPopup = ({alertIcon, header, body, footer}) => {
  return (
    <div
      className="relative z-50"
      aria-labelledby="modal-title"
      role="dialog"
      aria-modal="true"
    >
      <div className="fixed inset-0 bg-gray-500 bg-opacity-75 transition-opacity"></div>
      <div className="fixed inset-0 z-10 w-screen overflow-y-auto">
        <div className="flex min-h-full items-end justify-center p-4 text-center sm:items-center sm:p-0">
          <div className="relative transform overflow-hidden rounded-[20px] bg-white text-left shadow-xl transition-all duration-300 ease-out sm:my-8 sm:w-full sm:max-w-lg">
            {alertIcon && <div className="flex flex-shrink-0 items-center justify-center rounded-t-md p-4 my-4">
              <img src={alertIcon} />
            </div>}
            {header && <div className="relative text-center text-dark font-semibold text-xl text-[#2B3840]">
              {header}
            </div>}
            {body && <div className="relative px-4 py-3 text-center">
              <p className="text-base text-[#707070]">{body}</p>
            </div>}
            {footer && <div className="flex flex-shrink-0 flex-wrap items-center justify-center rounded-b-md p-4 mb-5 mt-3">
              {footer}
            </div>}
          </div>
        </div>
      </div>
    </div>
  );
};

export default ModalPopup;
