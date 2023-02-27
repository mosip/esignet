package io.mosip.esignet.entity;

import java.io.Serializable;
import java.util.Objects;

public class RegistryId implements Serializable {


    private String idHash;

    private String authFactor;

    public RegistryId() {
    }

    public RegistryId(String idHash, String authFactor) {
        this.idHash = idHash;
        this.authFactor = authFactor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegistryId registryId = (RegistryId) o;
        return idHash.equals(registryId.idHash) &&
                authFactor.equals(registryId.authFactor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idHash, authFactor);
    }
}
