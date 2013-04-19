/*
 * Copyright 2013 Jeanfrancois Arcand
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.tests;

import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.nettosphere.Config;
import org.atmosphere.nettosphere.Nettosphere;
import org.atmosphere.wasync.Client;
import org.atmosphere.wasync.ClientFactory;
import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Event;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.RequestBuilder;
import org.atmosphere.wasync.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TypedTest {
    public Nettosphere server;
    public String targetUrl;
    public static final Logger logger = LoggerFactory.getLogger(BaseTest.class);
    public int port;

    public int findFreePort() throws IOException {
        ServerSocket socket = null;

        try {
            socket = new ServerSocket(0);

            return socket.getLocalPort();
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    @AfterMethod(alwaysRun = true)
    public void tearDownGlobal() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @BeforeMethod(alwaysRun = true)
    public void start() throws IOException {
        port = findFreePort();
        targetUrl = "http://127.0.0.1:" + port;
    }

    @Test
    public void predefinedMessageTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!b.getAndSet(true)) {
                            r.suspend(-1);
                        } else {
                            r.getBroadcaster().broadcast(r.getRequest().getReader().readLine());
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        if (!r.isResuming() || !r.isCancelled()) {
                            r.getResource().getResponse().getWriter().print(r.getMessage());
                            r.getResource().resume();
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<POJO> response = new AtomicReference<POJO>();
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .decoder(new Decoder<String, POJO>() {
                    @Override
                    public POJO decode(Event e, String s) {
                        return new POJO(s);
                    }
                })
                .transport(Request.TRANSPORT.WEBSOCKET);

        Socket socket = client.create();
        socket.on(Event.MESSAGE.name(), new Function<POJO>() {
            @Override
            public void on(POJO t) {
                response.set(t);
                latch.countDown();
            }
        }).open(request.build()).fire("echo");

        latch.await(5, TimeUnit.SECONDS);
        socket.close();

        assertNotNull(response.get());
        assertEquals(response.get().getClass(), POJO.class);
    }

    @Test
    public void defaultTypedTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!b.getAndSet(true)) {
                            r.suspend(-1);
                        } else {
                            r.getBroadcaster().broadcast(r.getRequest().getReader().readLine());
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        if (!r.isResuming() || !r.isCancelled()) {
                            r.getResource().getResponse().getWriter().print(r.getMessage());
                            r.getResource().resume();
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<POJO> response = new AtomicReference<POJO>();
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .decoder(new Decoder<String, POJO>() {
                    @Override
                    public POJO decode(Event e, String s) {
                        return new POJO(s);
                    }
                })
                .transport(Request.TRANSPORT.WEBSOCKET);

        Socket socket = client.create();
        socket.on(new Function<POJO>() {
            @Override
            public void on(POJO t) {
                response.set(t);
                latch.countDown();
            }
        }).open(request.build()).fire("echo");

        latch.await(5, TimeUnit.SECONDS);
        socket.close();

        assertNotNull(response.get());
        assertEquals(response.get().getClass(), POJO.class);
    }


    public final static class POJO {

        public final String message;

        public POJO(String message) {
            this.message = message;
        }
    }
}
