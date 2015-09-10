package com.github.ruediste.remoteJUnit.common.messages;

public class SendClassMessage implements RemoteJUnitMessage {

    private static final long serialVersionUID = 1L;
    public byte[] data;
    public String name;

    public SendClassMessage(String name, byte[] data) {
        this.name = name;
        this.data = data;
    }

    @Override
    public <T> T accept(RemoteJUnitMessageVisitor<T> visitor) {
        return visitor.handle(this);
    }

}
