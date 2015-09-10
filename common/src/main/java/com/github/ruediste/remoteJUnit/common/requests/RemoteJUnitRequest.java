package com.github.ruediste.remoteJUnit.common.requests;

import java.io.Serializable;

public abstract class RemoteJUnitRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    public abstract <T> T accept(RemoteJUnitRequestVisitor<T> visitor);

}
