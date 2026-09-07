package esignet

import (
	"testing"

	"github.com/mosip/esignet/api-test/internal/config"
)

// A scenario's requires_credential is checked against these, so every key
// BuildAnswers can actually supply has to be recognised — otherwise a real
// credential would be rejected as a typo.
func TestKnownAnswerKeyAcceptsEveryKeyBuildAnswersProduces(t *testing.T) {
	built := BuildAnswers(config.Esignet{
		Identity:    config.Identity{IndividualID: "id-1"},
		Credentials: config.Credentials{Username: "u", Password: "p", Biometric: "b"},
		Knowledge:   config.Knowledge{FullName: "n", DOB: "d"},
		OTP:         config.OTP{Source: "static", Value: "111111"},
	})
	for k := range built {
		if !KnownAnswerKey(k) {
			t.Errorf("KnownAnswerKey(%q) = false, but BuildAnswers produced that key", k)
		}
	}
}

// Normalization means case and punctuation must not decide the answer.
func TestKnownAnswerKeyIgnoresCaseAndPunctuation(t *testing.T) {
	for _, k := range []string{"password", "Password", "full_name", "fullName", "captcha-token"} {
		if !KnownAnswerKey(k) {
			t.Errorf("KnownAnswerKey(%q) = false, want true", k)
		}
	}
}

// The point of the check: a typo must be distinguishable from an unconfigured
// credential, so it can fail loudly instead of skipping the scenario.
func TestKnownAnswerKeyRejectsUnknownNames(t *testing.T) {
	for _, k := range []string{"pasword", "fingerprint", ""} {
		if KnownAnswerKey(k) {
			t.Errorf("KnownAnswerKey(%q) = true, want false", k)
		}
	}
}
