package com.github.ruediste.remoteJUnit.common.messages;

import java.io.Serializable;

public interface RemoteJUnitMessage extends Serializable {

    <T> T accept(RemoteJUnitMessageVisitor<T> visitor);
}
