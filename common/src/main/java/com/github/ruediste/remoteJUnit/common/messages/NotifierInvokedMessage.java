package com.github.ruediste.remoteJUnit.common.messages;

public class NotifierInvokedMessage implements RemoteJUnitMessage {

    private static final long serialVersionUID = 1L;
    public String methodName;
    public String argTypeClass;
    public Object arg;

    public NotifierInvokedMessage(String methodName, String argTypeClass,
            Object arg) {
        this.methodName = methodName;
        this.argTypeClass = argTypeClass;
        this.arg = arg;
    }

    @Override
    public <T> T accept(RemoteJUnitMessageVisitor<T> visitor) {
        return visitor.handle(this);
    }

}
