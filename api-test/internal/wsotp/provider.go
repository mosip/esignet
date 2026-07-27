package wsotp

import "time"

// OTPProvider adapts a Listener to the driver's dynamic-OTP hook: it captures a
// fixed recipient and timeouts so the driver only has to supply the flow's start
// time. It structurally satisfies the driver's OTPProvider interface (no import
// of the esignet package, avoiding a cycle).
type OTPProvider struct {
	l         *Listener
	recipient string
	timeout   time.Duration
	poll      time.Duration
}

// NewOTPProvider builds a provider over a started Listener. recipient may be
// empty (then the newest fresh code from any recipient is used). timeout bounds
// the wait per OTP; poll is the check interval.
func NewOTPProvider(l *Listener, recipient string, timeout, poll time.Duration) *OTPProvider {
	return &OTPProvider{l: l, recipient: recipient, timeout: timeout, poll: poll}
}

// OTP returns the OTP delivered at or after `since` (the moment the login flow
// began), waiting up to the configured timeout.
func (p *OTPProvider) OTP(since time.Time) (string, error) {
	return p.l.WaitOTP(p.recipient, since, p.timeout, p.poll)
}
