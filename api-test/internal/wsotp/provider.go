package wsotp

import "time"

// OTPProvider adapts a Listener to the driver's dynamic-OTP hook.
type OTPProvider struct {
	l         *Listener
	recipient string
	timeout   time.Duration
	poll      time.Duration
}

// NewOTPProvider builds a provider over a started Listener.
func NewOTPProvider(l *Listener, recipient string, timeout, poll time.Duration) *OTPProvider {
	return &OTPProvider{l: l, recipient: recipient, timeout: timeout, poll: poll}
}

// OTP returns the OTP delivered at or after `since` (the moment the login flow
// began), waiting up to the configured timeout.
func (p *OTPProvider) OTP(since time.Time) (string, error) {
	return p.l.WaitOTP(p.recipient, since, p.timeout, p.poll)
}
