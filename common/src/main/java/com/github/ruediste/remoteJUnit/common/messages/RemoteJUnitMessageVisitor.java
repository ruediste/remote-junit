package com.github.ruediste.remoteJUnit.common.messages;

public class RemoteJUnitMessageVisitor<T> {

    public T unHandled(RemoteJUnitMessage message) {
        throw new UnsupportedOperationException();
    }

    public T handle(RequestClassMessage sendClassMessage) {
        return unHandled(sendClassMessage);
    }

    public T handle(NotifierInvokedMessage notifierInvokedMessage) {
        return unHandled(notifierInvokedMessage);
    }

    public T handle(RunCompletedMessage runCompletedMessage) {
        return unHandled(runCompletedMessage);
    }

    public T handle(SendClassMessage sendClassMessage) {
        return unHandled(sendClassMessage);
    }

    public T handle(StoppedByUserMessage stoppedByUserMessage) {
        return unHandled(stoppedByUserMessage);
    }

}
