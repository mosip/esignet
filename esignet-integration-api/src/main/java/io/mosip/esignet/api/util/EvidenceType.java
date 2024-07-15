package io.mosip.esignet.api.util;

public enum EvidenceType {
    DOCUMENT("document"),
    ELECTRONIC_RECORD("electronic_record"),
    VOUCH("vouch"),
    ELECTRONIC_SIGNATURE("electronic_signature");
    String value;

    EvidenceType(String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

}
