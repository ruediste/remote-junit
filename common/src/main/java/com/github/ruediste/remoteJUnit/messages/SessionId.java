package com.github.ruediste.remoteJUnit.messages;

import java.io.Serializable;

/**
 * Identifies a session
 */
public class SessionId implements Serializable {
    private int id;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SessionId))
            return false;
        return id == ((SessionId) obj).id;
    }
}
