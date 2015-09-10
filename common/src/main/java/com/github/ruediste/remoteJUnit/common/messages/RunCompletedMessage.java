package com.github.ruediste.remoteJUnit.common.messages;

public class RunCompletedMessage implements RemoteJUnitMessage {
    private static final long serialVersionUID = 1L;
    public Throwable failure;

    public RunCompletedMessage(Throwable failure) {
        this.failure = failure;
    }

    @Override
    public <T> T accept(RemoteJUnitMessageVisitor<T> visitor) {
        return visitor.handle(this);
    }

}
