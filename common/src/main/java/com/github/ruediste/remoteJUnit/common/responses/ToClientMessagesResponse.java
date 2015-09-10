package com.github.ruediste.remoteJUnit.common.responses;

import java.util.List;

import com.github.ruediste.remoteJUnit.common.messages.RemoteJUnitMessage;

public class ToClientMessagesResponse extends RemoteJUnitResponse {

    public List<RemoteJUnitMessage> messages;

    public ToClientMessagesResponse(List<RemoteJUnitMessage> messages) {
        this.messages = messages;
    }

    @Override
    public <T> T accept(RemoteJUnitResponseVisitor<T> visitor) {
        return visitor.accept(this);
    }

}
