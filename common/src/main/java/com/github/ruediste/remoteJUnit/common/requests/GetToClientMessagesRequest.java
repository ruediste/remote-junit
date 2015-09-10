package com.github.ruediste.remoteJUnit.common.requests;

public class GetToClientMessagesRequest extends RemoteJUnitRequest {
    private static final long serialVersionUID = 1L;
    public long sessionId;

    public GetToClientMessagesRequest(long sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public <T> T accept(RemoteJUnitRequestVisitor<T> visitor) {
        return visitor.handle(this);
    }

}
