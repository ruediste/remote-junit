package com.github.ruediste.remoteJUnit.common.requests;

import com.github.ruediste.remoteJUnit.common.messages.RunTestRequest;

public class RemoteJUnitRequestVisitor<T> {

    public T unHandled(RemoteJUnitRequest request) {
        throw new UnsupportedOperationException();
    }

    public T handle(RunTestRequest runTestRequest) {
        return unHandled(runTestRequest);
    }

    public T handle(GetToClientMessagesRequest getToClientMessagesRequest) {
        return unHandled(getToClientMessagesRequest);
    }

    public T handle(SendToServerMessagesRequest sendToServerMessagesRequest) {
        return unHandled(sendToServerMessagesRequest);
    }

}
