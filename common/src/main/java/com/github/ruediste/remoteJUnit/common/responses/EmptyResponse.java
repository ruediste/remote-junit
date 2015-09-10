package com.github.ruediste.remoteJUnit.common.responses;

public class EmptyResponse extends RemoteJUnitResponse {

    private static final long serialVersionUID = 1L;

    @Override
    public <T> T accept(RemoteJUnitResponseVisitor<T> visitor) {
        throw new UnsupportedOperationException();
    }

}
