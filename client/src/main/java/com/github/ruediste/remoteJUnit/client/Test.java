package com.github.ruediste.remoteJUnit.client;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.github.ruediste.remoteJUnit.messages.RemoteJUnitRequest;
import com.github.ruediste.remoteJUnit.messages.RemoteJUnitResponse;

public class Test {

    @org.junit.Test
    public void test() throws Throwable {
        RemoteJUnitRequest request = new RemoteJUnitRequest();
        request.message = "Hello from the client";

        URL serverUrl = new URL("http://localhost:9081/");
        HttpURLConnection conn = (HttpURLConnection) serverUrl.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/octet-stream");

        ObjectOutputStream os = new ObjectOutputStream(conn.getOutputStream());
        os.writeObject(request);
        os.close();

        ObjectInputStream in = new ObjectInputStream(conn.getInputStream());
        RemoteJUnitResponse resp = (RemoteJUnitResponse) in
                .readObject();
        in.close();

        System.out.println(resp.response);
    }
}
