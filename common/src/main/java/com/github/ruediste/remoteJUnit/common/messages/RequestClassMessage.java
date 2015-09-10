package com.github.ruediste.remoteJUnit.common.messages;

public class RequestClassMessage implements RemoteJUnitMessage {

    private static final long serialVersionUID = 1L;
    public String name;

    public RequestClassMessage(String name) {
        this.name = name;
    }

    @Override
    public <T> T accept(RemoteJUnitMessageVisitor<T> visitor) {
        return visitor.handle(this);
    }

}
