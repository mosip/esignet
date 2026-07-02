import { useRef, useState, useEffect } from "react";
import {
  type ComponentRenderContext,
  type EmbeddedFlowComponent,
  Button,
} from "@thunderid/react";

interface EmbeddedFlowComponentWithTimeLeft extends EmbeddedFlowComponent {
  timeLeft?: number;
}

export default function ResendOtp({
  component,
  context,
}: {
  component: EmbeddedFlowComponentWithTimeLeft;
  context: ComponentRenderContext;
}) {
  const onInputChangeRef = useRef(context.onInputChange);
  onInputChangeRef.current = context.onInputChange;

  const [remaining, setRemaining] = useState<number>(0);
  const [formattedTime, setFormattedTime] = useState<string>("00:00");
  const [timeLeft, setTimeLeft] = useState<boolean>(true);
  const expiresIn = component?.timeLeft ?? 0;

  useEffect(() => {
    if (expiresIn <= 0) {
      return undefined;
    }

    setRemaining(expiresIn);

    const interval: any = setInterval(() => {
      setRemaining((prev: number) => {
        if (prev <= 1) {
          clearInterval(interval);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [expiresIn]);

  useEffect(() => {
    setTimeLeft(remaining > 0);
    setFormattedTime(formatTime(remaining));
  }, [remaining]);

  const formatTime = (seconds: number): string => {
    if (seconds <= 0) {
      return "Timed out";
    }
    const m: number = Math.floor(seconds / 60);
    const s: number = seconds % 60;
    return `${m}:${s.toString().padStart(2, "0")}`;
  };

  const handleClick = () => {
    if (context.onSubmit) {
      context.onSubmit(component, {}, true);
    }
  };

  return (
    <div className="flex flex-col items-center">
      {timeLeft && (
        <h6 className="thunderid-typography thunderid-typography__h6">
          You can resend the OTP in {formattedTime}
        </h6>
      )}

      <Button
        fullWidth
        id={component.id}
        key={component.id}
        onClick={handleClick}
        disabled={timeLeft}
        // className={options.buttonClassName}
        data-testid="thunderid-resend-otp-button"
        variant={
          component.variant?.toLowerCase() === "primary" ? "solid" : "outline"
        }
        color={
          component.variant?.toLowerCase() === "primary"
            ? "primary"
            : "secondary"
        }
      >
        {component.label || "Submit"}
      </Button>
    </div>
  );
}
