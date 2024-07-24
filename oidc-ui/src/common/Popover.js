import React from "react";
import * as Popover from "@radix-ui/react-popover";

const PopoverContainer = ({child, content, position, contentSize, contentClassName}) => {
  return (
    <Popover.Root>
      <Popover.Trigger asChild className="hover: cursor-pointer">
          {child}
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content
          className={`will-change-[transform,opacity] data-[state=open]:data-[side=top]:animate-slideDownAndFade data-[state=open]:data-[side=right]:animate-slideLeftAndFade data-[state=open]:data-[side=bottom]:animate-slideUpAndFade data-[state=open]:data-[side=left]:animate-slideRightAndFade ${contentClassName}`}
          sideOffset={1}
          side={position}
        >
          <span className={contentSize}>
            {content}
          </span>
          <Popover.Arrow stroke="#BCBCBC" className="fill-[#fff]" height={7}/>
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
};

export default PopoverContainer;
