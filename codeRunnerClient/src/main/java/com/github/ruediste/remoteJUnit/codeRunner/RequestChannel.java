package com.github.ruediste.remoteJUnit.codeRunner;

import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.ruediste.remoteJUnit.codeRunner.RemoteCodeRunnerRequestsAndResponses.CustomResponse;

/**
 * Channel to send requests to remote running {@link RequestHandlingServerCode}
 */
public class RequestChannel {
    static final Logger log = LoggerFactory.getLogger(RequestChannel.class);
    private long runId;
    private Function<byte[], byte[]> requestSender;

    public RequestChannel(long runId, Function<byte[], byte[]> requestSender) {
        this.runId = runId;
        this.requestSender = requestSender;
    }

    public Object sendRequest(Object request) {
        log.debug("sending custom request {}",
                request == null ? null : request.getClass().getName());
        RemoteCodeRunnerRequestsAndResponses.CustomResponse resp = (CustomResponse) CodeRunnerClient
                .toResponse(requestSender.apply(SerializationHelper.toByteArray(
                        new RemoteCodeRunnerRequestsAndResponses.CustomRequest(
                                runId,
                                SerializationHelper.toByteArray(request)))));
        return SerializationHelper.toObject(resp.payload);
    }
}