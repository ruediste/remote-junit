package com.github.ruediste.remoteJUnit.common.responses;

public class RemoteJUnitResponseVisitor<T> {

    public T unHandled(RemoteJUnitResponse response) {
        throw new UnsupportedOperationException();
    }

    public T accept(TestRunStartedResponse testRunStartedResponse) {
        return unHandled(testRunStartedResponse);
    }

    public T accept(ToClientMessagesResponse toClientMessagesResponse) {
        return unHandled(toClientMessagesResponse);
    }

    public T accept(FailureResponse failureResponse) {
        return unHandled(failureResponse);
    }
}
