package com.github.ruediste.remoteJUnit.common.responses;

public class TestRunStartedResponse extends RemoteJUnitResponse {

    public long sessionId;

    public TestRunStartedResponse(long sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public <T> T accept(RemoteJUnitResponseVisitor<T> visitor) {
        return visitor.accept(this);
    }

}
