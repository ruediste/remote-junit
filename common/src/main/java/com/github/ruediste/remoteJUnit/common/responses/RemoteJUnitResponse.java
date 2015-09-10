package com.github.ruediste.remoteJUnit.common.responses;

import java.io.Serializable;

public abstract class RemoteJUnitResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    public abstract <T> T accept(RemoteJUnitResponseVisitor<T> visitor);
}
