package esignet

import (
	"strings"

	"github.com/mosip/esignet/api-test/internal/config"
)

// BuildAnswers turns the eSignet login config into the driver's answers map
// (normalized flow-input identifier -> value). The flow response decides which
// input is actually requested; this only supplies candidate answers.
func BuildAnswers(c config.Esignet) map[string]string {
	username := c.Credentials.Username
	if c.Provider == "mosip" || c.Provider == "sunbird" || username == "" {
		if c.Identity.IndividualID != "" {
			username = c.Identity.IndividualID
		}
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
	put("fullName", c.Knowledge.FullName)
	put("name", c.Knowledge.FullName)
	put("dob", c.Knowledge.DOB)
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
