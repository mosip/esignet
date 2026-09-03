package esignet

import (
	"strings"

	"github.com/mosip/esignet/api-test/internal/config"
)

// answerKeys are every key BuildAnswers can put into the answers map. It has to
// be listed separately because put() drops empty values, so an unconfigured
// credential is simply absent — indistinguishable by lookup from a key that
// was never a credential at all.
var answerKeys = []string{
	"username", "individualId", "otp", "password", "biometric",
	"fullName", "name", "dob", "captchaToken",
}

// KnownAnswerKey reports whether k names an answer BuildAnswers can supply.
// A scenario naming anything else has a typo in its spec rather than an
// unconfigured credential, and the difference matters: the first must be a
// loud failure, while the second is a legitimate skip.
func KnownAnswerKey(k string) bool {
	n := Normalize(k)
	for _, key := range answerKeys {
		if Normalize(key) == n {
			return true
		}
	}
	return false
}

// BuildAnswers turns the eSignet login config into the driver's answers map.
func BuildAnswers(c config.Esignet) map[string]string {
	// The flow's username_input is the subject identifier, not a console login.
	username := c.Identity.IndividualID
	if username == "" {
		username = c.Credentials.Username
	}
	m := map[string]string{}
	put := func(k, v string) {
		if v != "" {
			m[Normalize(k)] = v
		}
	}
	put("username", username)
	put("individualId", c.Identity.IndividualID)
	// In dynamic mode the OTP is fetched from the mock-SMTP listener at flow time;
	// leaving "otp" unset here lets the driver's OTPProvider fallback supply it.
	if c.OTP.Source != "dynamic" {
		put("otp", c.OTP.Value)
	}
	put("password", c.Credentials.Password)
	put("biometric", c.Credentials.Biometric)
	put("fullName", c.Knowledge.FullName)
	put("name", c.Knowledge.FullName)
	put("dob", c.Knowledge.DOB)
	// The login flow requires a captcha_token input on nearly every step (flow-esignet.yaml's captcha_box).
	put("captchaToken", "harness-captcha-bypass")
	return m
}

// AuthFactorTokens returns preferred action tokens for the ACR (auth-factor)
// selection step.
func AuthFactorTokens(factor string) []string {
	switch strings.ToLower(strings.TrimSpace(factor)) {
	case "otp", "":
		return []string{"otp", "generated-code", "generated_code"}
	case "password", "pwd", "static":
		return []string{"password", "static-code", "static_code", "pwd"}
	case "bio", "biometric", "biometrics":
		return []string{"bio", "biometric"}
	case "kbi", "knowledge":
		return []string{"kbi", "knowledge"}
	default:
		return []string{strings.ToLower(strings.TrimSpace(factor))}
	}
}

// IDTypeTokens returns preferred action tokens for the login-id-type selection
// (uin / vid / phone / email).
func IDTypeTokens(idType string) []string {
	switch strings.ToLower(strings.TrimSpace(idType)) {
	case "uin":
		return []string{"uin"}
	case "vid":
		return []string{"vid"}
	case "phone", "mobile":
		return []string{"mobile", "phone"}
	case "email":
		return []string{"email"}
	case "":
		return nil
	default:
		return []string{strings.ToLower(strings.TrimSpace(idType))}
	}
}
