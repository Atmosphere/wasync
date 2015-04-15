/*
 * Copyright 2015 Async-IO.org
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

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.providers.netty.NettyAsyncHttpProviderConfig;
import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.AtmosphereResourceEventListenerAdapter;
import org.atmosphere.nettosphere.Config;
import org.atmosphere.nettosphere.Nettosphere;
import org.atmosphere.wasync.Client;
import org.atmosphere.wasync.ClientFactory;
import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Encoder;
import org.atmosphere.wasync.Event;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.RequestBuilder;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.impl.AtmosphereClient;
import org.atmosphere.wasync.serial.DefaultSerializedFireStage;
import org.atmosphere.wasync.serial.SerializedClient;
import org.atmosphere.wasync.serial.SerializedOptionsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public abstract class BaseTest {
    public final static String RESUME = "Resume";

    public Nettosphere server;
    public String targetUrl;
    public static final Logger logger = LoggerFactory.getLogger(BaseTest.class);
    public int port;
    public AsyncHttpClient ahc;

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
        if (server != null && server.isStarted()) {
            server.stop();
        }
        ahc.closeAsynchronously();
    }

    abstract Request.TRANSPORT transport();

    abstract int statusCode();

    abstract int notFoundCode();


    abstract int getCount();

    @BeforeMethod(alwaysRun = true)
    public void start() throws IOException {
        if (server != null) server.stop();
        port = findFreePort();
        targetUrl = "http://127.0.0.1:" + port;
        ahc = createDefaultAsyncHttpClient();
    }

    public final static AsyncHttpClient createDefaultAsyncHttpClient(int requestTimeout) {
        NettyAsyncHttpProviderConfig nettyConfig = new NettyAsyncHttpProviderConfig();
        nettyConfig.addProperty("child.tcpNoDelay", "true");
        nettyConfig.addProperty("child.keepAlive", "true");

        AsyncHttpClientConfig.Builder b = new AsyncHttpClientConfig.Builder();
        b.setFollowRedirect(true)
                .setMaxRequestRetry(-1)
                .setConnectTimeout(-1)
                .setReadTimeout(requestTimeout);
        AsyncHttpClientConfig config = b.setAsyncHttpClientProviderConfig(nettyConfig).build();
        return new AsyncHttpClient(config);
    }

    public final static AsyncHttpClient createDefaultAsyncHttpClient() {
        ;
        return createDefaultAsyncHttpClient(-1);
    }

    @Test
    public void closeTest() throws Exception {
        logger.info("\n\ncloseTest\n\n");

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
        Client client = ClientFactory.getDefault().newClient();

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
                logger.error("", t);
                latch.countDown();
            }

        }).open(request.build()).fire("PING");

        latch.await(5, TimeUnit.SECONDS);
        socket.close();
        server.stop();

        assertEquals(socket.status(), Socket.STATUS.CLOSE);
    }

    @Test
    public void closeWithoutOpenTest() throws Exception {
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

        Client client = ClientFactory.getDefault().newClient();
        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());
        socket.close();
        assertEquals(socket.status(), Socket.STATUS.CLOSE);
    }

    @Test
    public void basicWebSocketTest() throws Exception {
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
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());
        ;
        socket.on("message", new Function<String>() {
            @Override
            public void on(String t) {
                logger.info("Function invoked {}", t);
                response.set(t);
                latch.countDown();
            }
        }).on(new Function<Throwable>() {

            @Override
            public void on(Throwable t) {
                logger.error("", t);
                latch.countDown();
            }

        }).open(request.build()).fire("PING");

        latch.await(5, TimeUnit.SECONDS);
        server.stop();
        socket.close();

        assertEquals(response.get(), RESUME);
    }

    @Test
    public void allStringFunctionTest() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!b.getAndSet(true)) {
                            r.suspend(-1);
                            latch.countDown();
                        } else {
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

        final StringBuilder builder = new StringBuilder();

        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());

        socket.on(new Function<String>() {
            @Override
            public void on(String m) {
                builder.append(m);
                latch.countDown();
            }
        }).open(request.build()).fire("PING");

        latch.await(5, TimeUnit.SECONDS);

        assertEquals(builder.toString(), RESUME);
    }

    @Test
    public void statusHeaderFunctionTest() throws Exception {
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

        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicInteger status = new AtomicInteger();
        final AtomicReference<Map> map = new AtomicReference<Map>();

        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());
        socket.on(new Function<Integer>() {
            @Override
            public void on(Integer statusCode) {
                status.set(statusCode);
                latch.countDown();
            }
        }).on(new Function<Map>() {

            @Override
            public void on(Map t) {
                map.set(t);
                latch.countDown();
            }

        }).open(request.build()).fire("PING");

        latch.await(5, TimeUnit.SECONDS);
        socket.close();

        assertEquals(status.get(), statusCode());
        assertNotNull(map.get());
        assertEquals(map.get().getClass().getInterfaces()[0], Map.class);

    }

    @Test
    public void status404FunctionTest() throws Exception {
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
        final AtomicInteger status = new AtomicInteger();

        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/ratata")
                .transport(transport());

        final Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());
        socket.on(new Function<Integer>() {
            @Override
            public void on(Integer statusCode) {
                status.set(statusCode);
                socket.close();
                latch.countDown();
            }
        }).open(request.build());

        latch.await(5, TimeUnit.SECONDS);
        socket.close();

        assertEquals(status.get(), notFoundCode());

    }

    @Test
    public void closedFireTest() throws Exception {
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
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

        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch ioLatch = new CountDownLatch(1);

        final AtomicInteger status = new AtomicInteger();
        final AtomicReference response2 = new AtomicReference();

        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        final Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());
        socket.on(new Function<Integer>() {
            @Override
            public void on(Integer statusCode) {
                status.set(statusCode);
                socket.close();
                latch.countDown();
            }
        }).on(new Function<IOException>() {

            @Override
            public void on(IOException t) {
                response2.set(t);
                ioLatch.countDown();
            }

        }).open(request.build());

        latch.await(5, TimeUnit.SECONDS);

        socket.fire("Yo");

        ioLatch.await(5, TimeUnit.SECONDS);

        assertNotNull(response2.get());
        assertEquals(response2.get().getClass(), IOException.class);

    }

    @Test
    public void basicConnectExceptionTest() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<ConnectException> response = new AtomicReference<ConnectException>();
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());

        IOException ioException = null;
        try {

            socket.on(new Function<ConnectException>() {

                @Override
                public void on(ConnectException t) {
                    response.set(t);
                    latch.countDown();
                }

            }).open(request.build());
        } catch (IOException ex) {
            ioException = ex;
        }

        latch.await(5, TimeUnit.SECONDS);
        socket.close();
        assertEquals(response.get().getClass(), ConnectException.class);
        assertTrue(IOException.class.isAssignableFrom(ioException.getClass()));
    }

    @Test
    public void basicIOExceptionTest() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<IOException> response = new AtomicReference<IOException>();
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());

        IOException ioException = null;
        try {
            socket.on(new Function<IOException>() {

                @Override
                public void on(IOException t) {
                    response.set(t);
                    latch.countDown();
                }

            }).open(request.build());
        } catch (IOException ex) {
            ioException = ex;
        }

        latch.await(5, TimeUnit.SECONDS);
        socket.close();
        assertTrue(IOException.class.isAssignableFrom(response.get().getClass()));
        assertTrue(IOException.class.isAssignableFrom(ioException.getClass()));
    }

    @Test
    public void basicEchoEncoderTest() throws Exception {
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
        final AtomicReference<String> response = new AtomicReference<String>();
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .encoder(new Encoder<String, String>() {
                    @Override
                    public String encode(String s) {
                        return "<-" + s.toString() + "->";
                    }
                })
                .transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());
        ;
        socket.on("message", new Function<String>() {
            @Override
            public void on(String t) {
                response.set(t);
                latch.countDown();
            }
        }).on(new Function<Throwable>() {

            @Override
            public void on(Throwable t) {
                logger.error("", t);
                latch.countDown();
            }

        }).open(request.build()).fire("echo");

        latch.await(5, TimeUnit.SECONDS);
        socket.close();

        assertEquals(response.get(), "<-echo->");
    }

    @Test
    public void requestTimeoutTest() throws Exception {
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

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Class<? extends TimeoutException>> response = new AtomicReference<Class<? extends TimeoutException>>();
        Client client = ClientFactory.getDefault().newClient();

        ahc = createDefaultAsyncHttpClient(5 * 1000);

        Options o = client.newOptionsBuilder().runtime(ahc, false).reconnect(false).build();
        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .encoder(new Encoder<String, String>() {
                    @Override
                    public String encode(String s) {
                        return "<-" + s.toString() + "->";
                    }
                })
                .transport(transport());

        Socket socket = client.create(o);

        socket.on(new Function<TimeoutException>() {

            @Override
            public void on(TimeoutException t) {
                response.set(t.getClass());
                latch.countDown();
            }

        }).open(request.build());

        latch.await(5, TimeUnit.SECONDS);
        socket.close();

        assertEquals(response.get(), TimeoutException.class);
    }

    @Test
    public void basicDecoderTest() throws Exception {
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
                .transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());
        socket.on(Event.MESSAGE, new Function<POJO>() {
            @Override
            public void on(POJO t) {
                response.set(t);
                latch.countDown();
            }
        }).open(request.build()).fire("echo");

        latch.await(5, TimeUnit.SECONDS);

        assertNotNull(response.get());
        assertEquals(response.get().getClass(), POJO.class);
        socket.close();

    }

    @Test
    public void basicTransportTest() throws Exception {
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
        final AtomicReference<Request.TRANSPORT> response = new AtomicReference<Request.TRANSPORT>();
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());
        socket.on(Event.TRANSPORT, new Function<Request.TRANSPORT>() {
            @Override
            public void on(Request.TRANSPORT t) {
                response.set(t);
                latch.countDown();
            }
        }).open(request.build()).fire("yo");

        latch.await(5, TimeUnit.SECONDS);

        assertNotNull(response.get());
        assertEquals(response.get(), transport());

    }

    @Test
    public void encodersChainingTests() throws Exception {
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
        final AtomicReference<String> response = new AtomicReference<String>();
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .encoder(new Encoder<String, POJO>() {
                    @Override
                    public POJO encode(String s) {
                        return new POJO("<-" + s + "->");
                    }
                })
                .encoder(new Encoder<POJO, Reader>() {
                    @Override
                    public Reader encode(POJO s) {
                        return new StringReader(s.message);
                    }
                })
                .transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());
        socket.on(Event.MESSAGE, new Function<String>() {
            @Override
            public void on(String t) {
                response.set(t);
                latch.countDown();
            }
        }).on(new Function<Throwable>() {

            @Override
            public void on(Throwable t) {
                logger.error("", t);
                latch.countDown();
            }

        }).open(request.build()).fire("echo");

        latch.await(5, TimeUnit.SECONDS);
        socket.close();

        assertEquals(response.get(), "<-echo->");
    }

    public final static class POJO {

        public final String message;

        public POJO(String message) {
            this.message = message;
        }
    }

    public final static class POJO2 {

        public final POJO message;

        public POJO2(POJO message) {
            this.message = message;
        }
    }

    @Test
    public void multipleFireBlockingTest() throws Exception {
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);
                    private final AtomicInteger count = new AtomicInteger(2);
                    private final AtomicReference<StringBuffer> response = new AtomicReference<StringBuffer>(new StringBuffer());

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
                        response.get().append(r.getMessage());
                        if (count.decrementAndGet() == 0 && (!r.isResuming() || !r.isCancelled())) {
                            r.getResource().getResponse().write(response.toString());
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
        final AtomicReference<StringBuffer> response = new AtomicReference<StringBuffer>(new StringBuffer());
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());

        socket.on("message", new Function<String>() {
            @Override
            public void on(String t) {
                logger.info("Function invoked {}", t);
                response.get().append(t);
                latch.countDown();
            }
        }).on(new Function<Throwable>() {

            @Override
            public void on(Throwable t) {
                logger.error("", t);
                latch.countDown();
            }

        }).open(request.build())
                .fire("PING").get()
                .fire("PONG").get();

        latch.await(5, TimeUnit.SECONDS);
        socket.close();

        // We can't predict the order of requests send
        try {
            assertEquals(response.get().toString(), "PONGPING");
        } catch (AssertionError e) {
            assertEquals(response.get().toString(), "PINGPONG");
        }
    }

    @Test
    public void multipleFireTest() throws Exception {
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);
                    private final AtomicInteger count = new AtomicInteger(2);
                    private final AtomicReference<StringBuffer> response = new AtomicReference<StringBuffer>(new StringBuffer());

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
                        response.get().append(r.getMessage());
                        if (count.decrementAndGet() == 0 && (!r.isResuming() || !r.isCancelled())) {
                            r.getResource().getResponse().write(response.toString());
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
        final AtomicReference<StringBuffer> response = new AtomicReference<StringBuffer>(new StringBuffer());
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());

        socket.on("message", new Function<String>() {
            @Override
            public void on(String t) {
                logger.info("Function invoked {}", t);
                response.get().append(t);
                latch.countDown();
            }
        }).on(new Function<Throwable>() {

            @Override
            public void on(Throwable t) {
                logger.error("", t);
                latch.countDown();
            }

        }).open(request.build())
                .fire("PING")
                .fire("PONG");

        latch.await(5, TimeUnit.SECONDS);
        socket.close();

        // We can't predict the order of requests send
        try {
            assertEquals(response.get().toString(), "PONGPING");
        } catch (AssertionError e) {
            assertEquals(response.get().toString(), "PINGPONG");
        }
    }

    @Test(enabled = false)
    public void basicLoadTest() throws IOException, InterruptedException {
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/", new AtmosphereHandler() {

                    @Override
                    public void onRequest(final AtmosphereResource r) throws IOException {
                        if (r.getRequest().getMethod().equalsIgnoreCase("GET")) {
                            r.addEventListener(new AtmosphereResourceEventListenerAdapter() {
                                @Override
                                public void onSuspend(AtmosphereResourceEvent event) {
                                    r.removeEventListener(this);
                                    try {
                                        r.getResponse().write("yo!".getBytes()).flushBuffer();
                                    } catch (IOException e) {
                                        logger.error("", e);

                                    }
                                }
                            }).suspend();
                        }
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

        final AsyncHttpClient c = new AsyncHttpClient(new AsyncHttpClientConfig.Builder().setMaxRequestRetry(0).build());
        final CountDownLatch l = new CountDownLatch(getCount());
        Client client = ClientFactory.getDefault().newClient();
        RequestBuilder request = client.newRequestBuilder();
        request.method(Request.METHOD.GET).uri(targetUrl);
        request.transport(transport());
        request.encoder(new Encoder<String, String>() {
            @Override
            public String encode(String s) {
                return s;
            }
        });
        Socket[] sockets = new Socket[getCount()];
        request.decoder(new Decoder<String, String>() {
            @Override
            public String decode(Event evntp, String s) {
                return s;
            }
        });
        for (int i = 0; i < getCount(); i++) {
            sockets[i] = client.create(client.newOptionsBuilder().runtime(c, true).build());
            sockets[i].on(new Function<Integer>() {
                @Override
                public void on(Integer statusCode) {
                }
            });
            sockets[i].on(new Function<String>() {
                @Override
                public void on(String s) {
                    if (s.equalsIgnoreCase("yo!")) {
                        System.out.println("=========> " + l.getCount());
                        l.countDown();
                    }
                }
            });
            sockets[i].on(new Function<Throwable>() {
                @Override
                public void on(Throwable t) {
                    logger.error("", t);
                }
            });

            sockets[i].open(request.build());
            sockets[i].fire("OPEN");
        }

        boolean pass = l.await(60, TimeUnit.SECONDS);
        try {
            for (int i = 0; i < sockets.length; i++) {
                sockets[i].close();
            }
            c.close();
            server.stop();
        } finally {
            assertTrue(pass);
        }
    }

    @Test(enabled = true)
    public void serializeTest() throws Exception {
        System.out.println("=============== STARTING SerializedTest");
        if (server != null) {
            server.stop();
        }

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicInteger count = new AtomicInteger(2);
                    private final AtomicReference<StringBuffer> response = new AtomicReference<StringBuffer>(new StringBuffer());

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (r.getRequest().getMethod().equalsIgnoreCase("GET")) {
                            r.suspend(-1);
                        } else {
                            try {
                                r.getBroadcaster().broadcast(r.getRequest().getReader().readLine()).get();
                            } catch (InterruptedException e) {
                                logger.error("", e);
                            } catch (ExecutionException e) {
                                logger.error("", e);
                            }
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        if (r.getMessage() != null) {
                            response.get().append(r.getMessage());
                            if (count.decrementAndGet() == 0) {
                                r.getResource().getResponse().write(response.toString());
                                r.getResource().resume();
                            }
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
        final AtomicReference<StringBuffer> response = new AtomicReference<StringBuffer>(new StringBuffer());
        SerializedClient client = ClientFactory.getDefault().newClient(SerializedClient.class);

        SerializedOptionsBuilder b = client.newOptionsBuilder();
        b.serializedFireStage(new DefaultSerializedFireStage());

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create(b.build());

        socket.on("message", new Function<String>() {
            @Override
            public void on(String t) {
                logger.info("Serialized Function invoked {}", t);
                response.get().append(t);
                latch.countDown();
            }
        }).on(new Function<Throwable>() {

            @Override
            public void on(Throwable t) {
                System.out.println("=============== ERROR");
                logger.error("", t);
            }

        }).open(request.build())
                .fire("PING")
                .fire("PONG").get();

        latch.await(5, TimeUnit.SECONDS);
        socket.close();

        assertEquals(response.get().toString(), "PINGPONG");
    }

    @Test
    public void postTest() throws Exception {
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
                            r.getResponse().write(r.getRequest().getReader().readLine());
                        }
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

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> response = new AtomicReference<String>();
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());
        socket.on(Event.MESSAGE, new Function<String>() {
            @Override
            public void on(String t) {
                response.set(t);
                latch.countDown();
            }
        }).open(request.build()).fire("yoga");

        latch.await(5, TimeUnit.SECONDS);

        assertNotNull(response.get());
        assertEquals(response.get(), "yoga");

    }

    @Test
    public void serverDownTest() throws Exception {
        final CountDownLatch latch = new CountDownLatch(3);
        final AtomicReference response = new AtomicReference();
        final AtomicReference response2 = new AtomicReference();

        Client client = ClientFactory.getDefault().newClient();

        String unreachableUrl = "http://localhost:815";
        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(unreachableUrl + "/suspend")
                .transport(transport());

        final Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());
        IOException ioException = null;
        try {

            socket.on(new Function<ConnectException>() {

                @Override
                public void on(ConnectException t) {
                    socket.close(); // I close it here, remove this close, issue will not happen
                    response.set(t);
                    latch.countDown();
                }

            }).on(new Function<IOException>() {

                @Override
                public void on(IOException t) {
                    response2.set(t);
                    latch.countDown();
                }

            }).open(request.build());
        } catch (IOException ex) {
            ioException = ex;
        }
        socket.fire("echo");
        latch.await(5, TimeUnit.SECONDS);

        assertEquals(response.get().getClass(), ConnectException.class);
        assertTrue(IOException.class.isAssignableFrom(response2.get().getClass()));
        assertTrue(IOException.class.isAssignableFrom(ioException.getClass()));

    }

    @Test
    public void shutdownServerTest() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
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
                            r.getBroadcaster().broadcast(RESUME);
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        if (!r.isResuming() || !r.isCancelled()) {
                            latch.countDown();

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

        final AtomicReference response = new AtomicReference();
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().reconnect(false).build());
        socket.on(Event.CLOSE, new Function<String>() {
            @Override
            public void on(String t) {
                //Can I receive close message when server is stopped?
                logger.info("Function invoked {}", t);
                response.set(t);
            }
        }).on(new Function<Throwable>() {

            @Override
            public void on(Throwable t) {
                logger.error("", t);
                latch.countDown();
            }

        }).open(request.build()).fire("PING");

        latch.await(5, TimeUnit.SECONDS);

        server.stop();
        socket.close();

        assertEquals(socket.status(), Socket.STATUS.CLOSE);//or ERROR?
    }

    @Test
    public void timeoutTest() throws IOException, InterruptedException {
        logger.info("\n\ntimeoutTest\n\n");
        final AtomicReference<StringBuilder> b = new AtomicReference<StringBuilder>(new StringBuilder());
        final CountDownLatch latch = new CountDownLatch(2);
        final CountDownLatch elatch = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        r.addEventListener(new AtmosphereResourceEventListenerAdapter() {
                            public void onSuspend(AtmosphereResourceEvent event) {
                                latch.countDown();
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

        Client client = ClientFactory.getDefault().newClient();
        RequestBuilder clientRequest = client.newRequestBuilder().method(Request.METHOD.GET).uri(targetUrl)
                .decoder(new Decoder<String, Reader>() {
                    @Override
                    public Reader decode(Event e, String s) {
                        // Fool the decoder mapping
                        return new StringReader(s);
                    }
                }).transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());
        socket.on(Event.CLOSE, new Function<String>() {
            @Override
            public void on(String t) {
                b.get().append(t);
            }
        }).on(new Function<IOException>() {
            @Override
            public void on(IOException ioe) {
                logger.error("", ioe);
                ;
                b.get().append("ERROR");
                elatch.countDown();
            }
        }).on(Event.OPEN, new Function<String>() {
            @Override
            public void on(String t) {
                b.get().append(t);
                latch.countDown();
            }
        }).open(clientRequest.build());

        latch.await(5, TimeUnit.SECONDS);

        server.stop();

        elatch.await(5, TimeUnit.SECONDS);

        assertEquals(b.get().toString(), "OPENCLOSEERROR");
    }

    @Test
    public void closeWriteTest() throws IOException, InterruptedException {
        logger.info("\n\ncloseWriteTest\n\n");
        final AtomicReference<StringBuilder> b = new AtomicReference<StringBuilder>(new StringBuilder());
        final CountDownLatch latch = new CountDownLatch(2);
        final CountDownLatch flatch = new CountDownLatch(1);
        final CountDownLatch elatch = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/", new AtmosphereHandler() {

                    private final AtomicReference<AtmosphereResource> resource = new AtomicReference<AtmosphereResource>();

                    @Override
                    public void onRequest(final AtmosphereResource r) throws IOException {
                        if (r.getRequest().getMethod().equals("GET")) {
                            r.addEventListener(new AtmosphereResourceEventListenerAdapter() {
                                public void onSuspend(AtmosphereResourceEvent event) {
                                    resource.set(r);
                                    for (int i = 0; i < 8192; i++) {
                                        try {
                                            resource.get().getResponse().getOutputStream().write(" ".getBytes());
                                        } catch (IOException e) {
                                            logger.error("", e);
                                            break;
                                        }
                                    }
                                    latch.countDown();
                                }
                            }).suspend();
                        } else {
                            String line = r.getRequest().getReader().readLine();

                            resource.get().write(line).close();
                        }
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

        Client client = ClientFactory.getDefault().newClient();
        RequestBuilder clientRequest = client.newRequestBuilder().method(Request.METHOD.GET).uri(targetUrl)
                .decoder(new Decoder<String, String>() {
                    @Override
                    public String decode(Event e, String s) {
                        // Fool the decoder mapping
                        return s;
                    }
                }).transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());
        socket.on("message", new Function<String>() {
            @Override
            public void on(String t) {
                b.get().append(t);
                flatch.countDown();
            }
        }).on(Event.CLOSE, new Function<String>() {
            @Override
            public void on(String t) {
                b.get().append(t);
            }
        }).on(Event.REOPENED, new Function<String>() {
            @Override
            public void on(String t) {
                b.get().append(t);
            }
        }).on(new Function<IOException>() {
            @Override
            public void on(IOException ioe) {
                logger.error("", ioe);
                b.get().append("ERROR");
                elatch.countDown();
            }
        }).on(Event.OPEN, new Function<String>() {
            @Override
            public void on(String t) {
                b.get().append(t);
                latch.countDown();
            }
        }).open(clientRequest.build());

        latch.await(5, TimeUnit.SECONDS);

        socket.fire("PING");
        flatch.await(5, TimeUnit.SECONDS);

        server.stop();

        elatch.await(5, TimeUnit.SECONDS);

        // TODO: Hacky, but on slow machime the stop operation won't finish on time.
        // The ERRROR will never comes in that case and the client may reconnect.
        assertEquals(b.get().toString(), "OPENPINGCLOSEERROR");
    }

    @Test
    public void reconnectFireTest() throws IOException, InterruptedException {
        logger.info("\n\nreconnectFireTest\n\n");
        final AtomicReference<StringBuilder> b = new AtomicReference<StringBuilder>(new StringBuilder());
        final CountDownLatch latch = new CountDownLatch(2);
        final CountDownLatch flatch = new CountDownLatch(1);
        final CountDownLatch elatch = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);
                    private final AtomicReference<AtmosphereResource> resource = new AtomicReference<AtmosphereResource>();

                    @Override
                    public void onRequest(final AtmosphereResource r) throws IOException {
                        if (r.getRequest().getMethod().equals("GET")) {
                            r.addEventListener(new AtmosphereResourceEventListenerAdapter() {
                                public void onSuspend(AtmosphereResourceEvent event) {
                                    latch.countDown();
                                    resource.set(r);
                                    for (int i = 0; i < 8192; i++) {
                                        resource.get().write(" ");
                                    }
                                }
                            }).suspend();
                        } else {
                            if (!b.getAndSet(true)) {
                                resource.get().write(r.getRequest().getReader().readLine()).close();
                            } else {
                                resource.get().write(r.getRequest().getReader().readLine());
                            }
                        }
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

        Client client = ClientFactory.getDefault().newClient();
        RequestBuilder clientRequest = client.newRequestBuilder().method(Request.METHOD.GET).uri(targetUrl)
                .encoder(new Encoder<String, String>() {
                    @Override
                    public String encode(String s) {
                        System.out.println("[Encode]:" + s);
                        return s;
                    }
                })
                .decoder(new Decoder<String, String>() {
                    @Override
                    public String decode(Event e, String s) {
                        // Fool the decoder mapping
                        return s;
                    }
                }).transport(transport());

        final Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());
        socket.on("message", new Function<String>() {
            @Override
            public void on(String t) {
                //System.out.println("=>" + t);

                b.get().append(t);
                flatch.countDown();
            }
        }).on(Event.CLOSE, new Function<String>() {
            @Override
            public void on(String t) {
                b.get().append(t);
            }
        }).on(Event.REOPENED, new Function<String>() {
            @Override
            public void on(String t) {
                b.get().append(t);
                try {
                    socket.fire("PONG");
                } catch (IOException e) {
                    logger.error("", e);

                }
                latch.countDown();
            }
        }).on(new Function<IOException>() {
            @Override
            public void on(IOException ioe) {
                logger.error("", ioe);

                b.get().append("ERROR");
                elatch.countDown();
            }
        }).on(Event.OPEN, new Function<String>() {
            @Override
            public void on(String t) {
                b.get().append(t);
                latch.countDown();
            }
        }).open(clientRequest.build());

        socket.fire("PING");
        latch.await(10, TimeUnit.SECONDS);
        flatch.await(10, TimeUnit.SECONDS);

        server.stop();

        elatch.await(5, TimeUnit.SECONDS);

        assertEquals(b.get().toString(), "OPENPINGCLOSEERROR");
    }

    @Test
    public void eventDecoderTest() throws Exception {

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

        final CountDownLatch latch = new CountDownLatch(2);
        final CountDownLatch xlatch = new CountDownLatch(1);

        final AtomicReference<POJO> response = new AtomicReference<POJO>();
        final AtomicReference<EventPOJO> open = new AtomicReference<EventPOJO>();
        final AtomicReference<EventPOJO> close = new AtomicReference<EventPOJO>();

        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .decoder(new Decoder<String, POJO>() {
                    @Override
                    public POJO decode(Event e, String s) {
                        if (e.equals(Event.MESSAGE)) {
                            return new POJO(s);
                        } else {
                            return null;
                        }
                    }
                })
                .decoder(new Decoder<String, EventPOJO>() {
                    @Override
                    public EventPOJO decode(Event e, String s) {
                        return new EventPOJO(e, s);
                    }
                })
                .transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());
        socket.on(new Function<POJO>() {
            @Override
            public void on(POJO t) {
                response.set(t);
                latch.countDown();
            }
        }).on(Event.OPEN, new Function<EventPOJO>() {
            @Override
            public void on(EventPOJO t) {
                open.set(t);
                latch.countDown();
            }
        }).on(Event.CLOSE, new Function<EventPOJO>() {
            @Override
            public void on(EventPOJO t) {
                close.set(t);
                xlatch.countDown();
            }
        }).open(request.build()).fire("echo");

        latch.await(5, TimeUnit.SECONDS);

        socket.close();

        xlatch.await(5, TimeUnit.SECONDS);

        assertNotNull(response.get());
        assertNotNull(open.get());
        assertNotNull(close.get());

        assertEquals(response.get().getClass(), POJO.class);
        assertEquals(open.get().getClass(), EventPOJO.class);
        assertEquals(close.get().getClass(), EventPOJO.class);
        assertEquals(open.get().e, Event.OPEN);
        assertEquals(close.get().e, Event.CLOSE);
        assertEquals(open.get().message, Event.OPEN.name());
        assertEquals(close.get().message, Event.CLOSE.name());

    }

    @Test
    public void testTimeoutAtmosphereClient() throws IOException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        AtmosphereClient client = ClientFactory.getDefault().newClient(AtmosphereClient.class);
        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl)
                .trackMessageLength(true)
                .transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());
        IOException ioException = null;
        try {
            socket.on(new Function<ConnectException>() {

                @Override
                public void on(ConnectException t) {
                    latch.countDown();
                }

            }).on(Event.CLOSE.name(), new Function<String>() {
                @Override
                public void on(String t) {
                    logger.info("Connection closed");
                }
            }).open(request.build());
        } catch (IOException ex) {
            ioException = ex;
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(IOException.class.isAssignableFrom(ioException.getClass()));

    }

    @Test
    public void basicHelloTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(2);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(final AtmosphereResource r) throws IOException {

                        r.addEventListener(new AtmosphereResourceEventListenerAdapter() {
                            @Override
                            public void onSuspend(AtmosphereResourceEvent event) {
                                try {
                                    r.getResponse().getWriter().print("HELLO");
                                    r.getResponse().flushBuffer();
                                } catch (IOException e) {
                                    logger.error("", e);
                                }
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

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> response = new AtomicReference<String>();
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());

        socket.on("message", new Function<String>() {
            @Override
            public void on(String t) {
// the status should have been updated to something else than INIT
                response.set(t);
                latch.countDown();
            }
        }).on(new Function<Throwable>() {

            @Override
            public void on(Throwable t) {
                logger.error("", t);
                latch.countDown();
            }

        }).open(request.build());

        latch.await(5, TimeUnit.SECONDS);


        logger.error("SOCKET STATUS [{}]", socket.status());

        assertEquals(response.get(), "HELLO");
        assertEquals(socket.status(), Socket.STATUS.OPEN);

        socket.close();

        assertEquals(socket.status(), Socket.STATUS.CLOSE);
    }

    @Test
    public void customHeaderTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);

        final AtomicReference<String> ref = new AtomicReference<String>();
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {


                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        ref.set(r.getRequest().getHeader("X-Test"));
                        l.countDown();
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

        final CountDownLatch latch = new CountDownLatch(1);
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .header("X-Test", "foo")
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().reconnect(false).build());
        socket.on(Event.CLOSE.name(), new Function<String>() {

            @Override
            public void on(String t) {
                latch.countDown();
            }

        }).open(request.build()).fire("PING");

        l.await(5, TimeUnit.SECONDS);
        server.stop();
        latch.await(5, TimeUnit.SECONDS);

        assertEquals(ref.get(), "foo");
    }

    @Test
    public void testHeadersOnClose() throws IOException, InterruptedException {

        final CountDownLatch l = new CountDownLatch(1);
        final CountDownLatch closedByClient = new CountDownLatch(1);

        final AtomicReference<String> ref = new AtomicReference<String>();
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
                        if (r.isClosedByClient()) {
                            ref.set(r.getResource().getRequest().getHeader("X-Test"));
                            closedByClient.countDown();
                        }
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
                .header("X-Test", "foo")
                .transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());

        socket.open(request.build()).close();

        closedByClient.await(10, TimeUnit.SECONDS);
        assertEquals(ref.get(), "foo");

    }

    @Test
    public void basicFutureBlockingTest() throws Exception {
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
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());

        socket.on("message", new Function<String>() {
            @Override
            public void on(String t) {
                logger.info("Function invoked {}", t);
                response.set(t);
                latch.countDown();
            }
        }).on(new Function<Throwable>() {

            @Override
            public void on(Throwable t) {
                logger.error("", t);
                latch.countDown();
            }

        }).open(request.build()).fire("PING").get();

        latch.await(5, TimeUnit.SECONDS);
        server.stop();
        socket.close();

        assertEquals(response.get(), RESUME);
    }

    @Test(enabled = true)
    public void serializeFutureGetTest() throws Exception {
        final CountDownLatch latch = new CountDownLatch(4);
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicInteger count = new AtomicInteger(1);

                    @Override
                    public void onRequest(final AtmosphereResource r) throws IOException {
                        if (r.getRequest().getMethod().equalsIgnoreCase("GET")) {
                            r.addEventListener(new AtmosphereResourceEventListenerAdapter() {
                                @Override
                                public void onSuspend(AtmosphereResourceEvent event) {
                                    latch.countDown();
                                }
                            }).suspend();
                        } else {
                            try {
                                r.getBroadcaster().broadcast(r.getRequest().getReader().readLine()).get();
                            } catch (InterruptedException e) {
                                logger.error("", e);
                                ;
                            } catch (ExecutionException e) {
                                logger.error("", e);

                            }
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        if (r.getMessage() != null) {
                            r.getResource().getResponse().write(r.getMessage().toString());
                            if (r.getResource().transport().equals(AtmosphereResource.TRANSPORT.LONG_POLLING)) {
                                r.getResource().resume();
                            }
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final AtomicReference<StringBuffer> response = new AtomicReference<StringBuffer>(new StringBuffer());
        SerializedClient client = ClientFactory.getDefault().newClient(SerializedClient.class);

        SerializedOptionsBuilder b = client.newOptionsBuilder().runtime(ahc, false);
        b.serializedFireStage(new DefaultSerializedFireStage());

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create(b.build());

        socket.on("message", new Function<String>() {
            @Override
            public void on(String t) {
                logger.info("Serialized Function invoked {}", t);
                response.get().append(t);
                latch.countDown();
            }
        }).on(Event.OPEN.name(), new Function<Object>() {
                    @Override
                    public void on(Object o) {
                        latch.countDown();
                    }
                }).open(request.build())
                .fire("PING")
                .fire("PONG").get();

        latch.await(10, TimeUnit.SECONDS);
        socket.close();

        assertEquals(response.get().toString(), "PINGPONG");
    }

    @Test
    public void ahcCloseTest() throws IOException, InterruptedException {
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        r.suspend();
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

        final AsyncHttpClient ahc = new AsyncHttpClient(new AsyncHttpClientConfig.Builder().setMaxRequestRetry(0).build());
        AtmosphereClient client = ClientFactory.getDefault().newClient(AtmosphereClient.class);

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(Request.TRANSPORT.WEBSOCKET);

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());
        socket.open(request.build());
        socket.close();

        // AHC is async closed
        Thread.sleep(1000);

        assertTrue(ahc.isClosed());
    }

    @Test(enabled = true)
    public void testCloseWithAtmosphereClient() throws IOException, InterruptedException {
        final CountDownLatch l = new CountDownLatch(1);
        final CountDownLatch closedLatch = new CountDownLatch(1);

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
        socket.on(Event.CLOSE.name(), new Function<Object>() {
            @Override
            public void on(Object o) {
                closedLatch.countDown();
            }
        });

        socket.open(request.build());

        // Wait until the connection is suspended
        assertTrue(l.await(5, TimeUnit.SECONDS));

        // Close the connection
        socket.close();

        // Check if Event.CLOSE was called
        assertTrue(closedLatch.await(5, TimeUnit.SECONDS));
    }

    @Test
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

    @Test
    public void ahcCloseTest2() throws IOException, InterruptedException {
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        r.suspend();
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

        final AsyncHttpClient ahc = new AsyncHttpClient(new AsyncHttpClientConfig.Builder().setMaxRequestRetry(0).build());
        SerializedClient client = ClientFactory.getDefault().newClient(SerializedClient.class);

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(Request.TRANSPORT.WEBSOCKET);

        Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).runtimeShared(false).serializedFireStage(new DefaultSerializedFireStage()).build());
        socket.open(request.build());
        socket.close();

        // AHC is async closed
        Thread.sleep(1000);

        assertTrue(ahc.isClosed());
    }

    public final static class EventPOJO {

        public final String message;
        public final Event e;

        public EventPOJO(String message) {
            this(null, message);
        }

        public EventPOJO(Event e, String message) {
            this.message = message;
            this.e = e;
        }
    }
}

