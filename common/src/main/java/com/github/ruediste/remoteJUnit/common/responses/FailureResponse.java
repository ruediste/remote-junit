package com.github.ruediste.remoteJUnit.common.responses;

public class FailureResponse extends RemoteJUnitResponse {

    private static final long serialVersionUID = 1L;
    public Exception exception;

    public FailureResponse(Exception exception) {
        this.exception = exception;
    }

    @Override
    public <T> T accept(RemoteJUnitResponseVisitor<T> visitor) {
        return visitor.accept(this);
    }

}
