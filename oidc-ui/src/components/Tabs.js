import React from "react";
import { useTranslation } from "react-i18next";

export default function Tabs({ color, tabs, block }) {
  const { t } = useTranslation("tabs");

  const [openTab, setOpenTab] = React.useState(0);
  return (
    <>
      <div className="flex flex-wrap">
        <div className="w-full">
          <ul
            className="flex mb-0 list-none flex-wrap pt-3 pb-4 flex-row"
            role="tablist"
          >
            {tabs.map((tab, index) => (
              <li
                key={tab.name + index}
                className="-mb-px mr-2 last:mr-0 flex-auto text-center"
              >
                <a
                  className={
                    "text-xs font-bold uppercase px-5 py-3 shadow-lg rounded block leading-normal " +
                    (openTab === index
                      ? "text-white bg-gradient-to-r from-" +
                        color +
                        "-500 to-blue-500 hover:bg-gradient-to-bl"
                      : "text-" + color + "-600 bg-white")
                  }
                  onClick={(e) => {
                    e.preventDefault();
                    setOpenTab(index);
                  }}
                  data-toggle="tab"
                  href="#link1"
                  role="tablist"
                >
                  <i className={"fas fa-" + tab.icon + " text-base mr-1"}></i>{" "}
                  {t(tab.name)}
                </a>
              </li>
            ))}
          </ul>
          <div className="relative flex flex-col min-w-0 break-words w-full mb-6 shadow-lg rounded bg-slate-50">
            <div className="px-4 py-5 flex-auto">
              <div className="tab-content tab-space"></div>
              {tabs.map((tab, index) => (
                <div
                  key={tab.comp + index}
                  className={openTab === index ? "block" : "hidden"}
                  id={"link" + index}
                >
                  {block.get(tab.icon)}
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
