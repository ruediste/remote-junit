package com.github.ruediste.remoteJUnit.common.messages;

public class NullMessage implements RemoteJUnitMessage {

    private static final long serialVersionUID = 1L;

    @Override
    public <T> T accept(RemoteJUnitMessageVisitor<T> visitor) {
        return null;
    }

}
