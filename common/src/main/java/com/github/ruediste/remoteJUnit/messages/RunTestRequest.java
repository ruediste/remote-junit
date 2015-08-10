package com.github.ruediste.remoteJUnit.messages;

public class RunTestRequest extends RemoteJUnitRequest {

    private static final long serialVersionUID = 1L;
    public String runner;
    public String testClassName;
    public String method;

    @Override
    public void accept(RemoteJUnitRequestVisitor visitor) {
        visitor.handle(this);
    }

}
