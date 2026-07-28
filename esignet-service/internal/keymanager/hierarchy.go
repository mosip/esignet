package keymanager

import (
	"context"
	"errors"
)

// RefID sentinels that determine key type and, together with AppID, the
// signing hierarchy (see resolveSignKeyAlias).
const (
	RefIDRSA2048         = "RSA_2048" // Component Master Key ref id
	RefIDECSECP256K1Sign = "EC_SECP256K1_SIGN"
	RefIDECSECP256R1Sign = "EC_SECP256R1_SIGN"
	RefIDED25519Sign     = "ED25519_SIGN"

	AppIDRoot = "ROOT"
)

var (
	// ErrRootKeyNotFound is returned when generating a Component Master Key
	// (RefID=RSA_2048) before the ROOT key exists.
	ErrRootKeyNotFound = errors.New("root key not found: generate the ROOT key first")

	// ErrComponentMasterKeyNotFound is returned when generating a Component
	// Encryption Key before that component's RSA_2048 master key exists.
	ErrComponentMasterKeyNotFound = errors.New("component master key (RSA_2048) not found: generate it before component encryption keys")
)

type keyTypeInfo struct{ AlgoName, CurveName string }

// refIDKeyType maps a reference id to (algoName, curveName), mirroring
// Java's ecRefIdsAlgoNamesMap plus the RSA cases. Any ref id not listed here
// (including "SIGN", "", and any application-specific Component Encryption
// Key ref id) falls through to the RSA default in resolveKeyType.
var refIDKeyType = map[string]keyTypeInfo{
	RefIDRSA2048:         {"RSA", ""},
	RefIDECSECP256K1Sign: {"EC", "SECP256K1"},
	RefIDECSECP256R1Sign: {"EC", "SECP256R1"},
	RefIDED25519Sign:     {"EC", "ED25519"},
}

// resolveKeyType determines the algorithm/curve for a reference id.
func resolveKeyType(refID string) (algoName, curveName string) {
	if kt, ok := refIDKeyType[refID]; ok {
		return kt.AlgoName, kt.CurveName
	}
	return "RSA", ""
}

// resolveSignKeyAlias returns the alias that must sign the key currently
// being generated for (appID, refID), enforcing the key hierarchy:
//
//	ROOT (AppID=ROOT, RefID="")                                          — self-signed
//	  Component Master Key (AppID=<component>, RefID=RSA_2048)            — signed by ROOT
//	  EC sign key (AppID=<component>, RefID=EC_*_SIGN/ED25519_SIGN)        — signed by ROOT
//	    Component Encryption Key (AppID=<component>, RefID=other)         — signed by the Component Master Key
//
// EC sign keys are signed directly by ROOT, the same tier as the Component
// Master Key — generatable as soon as ROOT exists, with no dependency on
// that component's Component Master Key (they're a parallel identity, not
// something the Component Master Key issues).
//
// Returns "" for the self-signed ROOT case (caller signs with its own new
// key). Fails fast with a typed sentinel error if the required parent tier
// doesn't exist yet — no keystore/DB write is attempted for the key being
// generated in that case.
func (s *Service) resolveSignKeyAlias(ctx context.Context, appID, refID string) (string, error) {
	if appID == AppIDRoot {
		return "", nil
	}
	switch refID {
	case RefIDRSA2048, RefIDECSECP256K1Sign, RefIDECSECP256R1Sign, RefIDED25519Sign:
		root, err := s.currentAlias(ctx, AppIDRoot, "")
		if err != nil {
			return "", err
		}
		if root == nil {
			return "", ErrRootKeyNotFound
		}
		return root.ID, nil
	}
	master, err := s.currentAlias(ctx, appID, RefIDRSA2048)
	if err != nil {
		return "", err
	}
	if master == nil {
		return "", ErrComponentMasterKeyNotFound
	}
	return master.ID, nil
}
