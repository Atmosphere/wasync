/*
 * Copyright 2008-2025 Async-IO.org
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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.asynchttpclient.AsyncHttpClient;
import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.AtmosphereResourceEventListenerAdapter;
import org.atmosphere.nettosphere.Config;
import org.atmosphere.nettosphere.Nettosphere;
import org.atmosphere.wasync.ClientFactory;
import org.atmosphere.wasync.Event;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.RequestBuilder;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.impl.AtmosphereClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Sebastian Lövdahl <slovdahl@hibox.fi>
 */
public class SSETest {

    private static final Logger logger = LoggerFactory.getLogger(SSETest.class);
    protected final static String RESUME = "Resume";
    protected Nettosphere server;
    protected String targetUrl;
    protected int port;
    protected AsyncHttpClient ahc;

    protected int findFreePort() throws IOException {
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
        if (server != null && server.isStarted()) {
            server.stop();
            ahc.close();
        }
    }

    @BeforeMethod(alwaysRun = true)
    public void start() throws IOException {
        port = findFreePort();
        targetUrl = "http://127.0.0.1:" + port;
        ahc = BaseTest.createDefaultAsyncHttpClient();
    }

    Request.TRANSPORT transport() {
        return Request.TRANSPORT.SSE;
    }

    int statusCode() {
        return 200;
    }

    int notFoundCode() {
        return 404;
    }

    int getCount() {
        return 5;
    }

    @Test(timeOut = 20000)
    public void closeTest() throws Exception {
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {
            private final AtomicBoolean b = new AtomicBoolean(false);

            @Override
            public void onRequest(AtmosphereResource r) throws IOException {
                if (!b.getAndSet(true)) {
                    r.suspend(-1);
                }
                else {
                    r.getBroadcaster().broadcast(RESUME);
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
        final AtomicReference<String> response = new AtomicReference<String>();
        AtmosphereClient client = ClientFactory.getDefault().newClient(AtmosphereClient.class);

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).reconnect(false).build());
        socket.on(Event.MESSAGE, new Function<String>() {
            @Override
            public void on(String t) {
                logger.info("Function invoked {}", t);
                response.set(t);
                latch.countDown();
            }
        }).on(new Function<Throwable>() {
            @Override
            public void on(Throwable t) {
                t.printStackTrace();
                latch.countDown();
            }
        }).open(request.build()).fire("PING");

        latch.await(10, TimeUnit.SECONDS);
        socket.close();
        server.stop();

        assertEquals(socket.status(), Socket.STATUS.CLOSE);
    }

    @Test(enabled = true)
    public void testOpenWithAtmosphereClientAndProtocol() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);
        final CountDownLatch openedLatch = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        r.addEventListener(new AtmosphereResourceEventListenerAdapter() {
                            @Override
                            public void onSuspend(AtmosphereResourceEvent event) {
                                l.countDown();
                            }
                        }).suspend();
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                    }

                    @Override
                    public void destroy() {
                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        AtmosphereClient client = ClientFactory.getDefault().newClient(AtmosphereClient.class);
        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .enableProtocol(true)
                .transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());
        socket.on(Event.OPEN.name(), new Function<Object>() {
            @Override
            public void on(Object o) {
                openedLatch.countDown();
            }
        });

        socket.open(request.build());

        // Wait until the connection is suspended
        assertTrue(l.await(5, TimeUnit.SECONDS));

        // Check if Event.OPEN was called
        assertTrue(openedLatch.await(5, TimeUnit.SECONDS));

        // Cleanup and close the connection
        socket.close();
    }
}
