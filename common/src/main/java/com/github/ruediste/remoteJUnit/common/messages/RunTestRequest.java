package com.github.ruediste.remoteJUnit.common.messages;

import org.junit.runner.Description;

import com.github.ruediste.remoteJUnit.common.SerializedValue;
import com.github.ruediste.remoteJUnit.common.requests.RemoteJUnitRequest;
import com.github.ruediste.remoteJUnit.common.requests.RemoteJUnitRequestVisitor;

public class RunTestRequest extends RemoteJUnitRequest {

    private static final long serialVersionUID = 1L;
    public String runner;
    public String testClassName;
    public SerializedValue<Description> description = new SerializedValue<>();

    public RunTestRequest(String runner, String testClassName,
            Description description) {
        super();
        this.runner = runner;
        this.testClassName = testClassName;
        this.description.set(description);
    }

    @Override
    public <T> T accept(RemoteJUnitRequestVisitor<T> visitor) {
        return visitor.handle(this);
    }

}
