package com.github.ruediste.remoteJUnit.messages;

import java.io.Serializable;

public abstract class RemoteJUnitRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    public abstract void accept(RemoteJUnitRequestVisitor visitor);
}
