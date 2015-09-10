package com.github.ruediste.remoteJUnit.common.requests;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.github.ruediste.remoteJUnit.common.SerializedValue;
import com.github.ruediste.remoteJUnit.common.messages.RemoteJUnitMessage;

public class SendToServerMessagesRequest extends RemoteJUnitRequest {
    private static final long serialVersionUID = 1L;

    public static class Entry implements Serializable {
        public Entry(Class<? extends RemoteJUnitMessage> cls,
                RemoteJUnitMessage message) {
            this.cls = cls;
            this.message = new SerializedValue<>(message);
        }

        private static final long serialVersionUID = 1L;
        public Class<?> cls;
        public SerializedValue<RemoteJUnitMessage> message;
    }

    public List<Entry> messages = new ArrayList<>();
    public long sessionId;

    public SendToServerMessagesRequest(List<RemoteJUnitMessage> messages,
            long sessionId) {
        this.sessionId = sessionId;
        messages.forEach(m -> this.messages.add(new Entry(m.getClass(), m)));
    }

    @Override
    public <T> T accept(RemoteJUnitRequestVisitor<T> visitor) {
        return visitor.handle(this);
    }

}
