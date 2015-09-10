package com.github.ruediste.remoteJUnit.common.messages;

public class StoppedByUserMessage implements RemoteJUnitMessage {

    private static final long serialVersionUID = 1L;

    @Override
    public <T> T accept(RemoteJUnitMessageVisitor<T> visitor) {
        return visitor.handle(this);
    }

}
