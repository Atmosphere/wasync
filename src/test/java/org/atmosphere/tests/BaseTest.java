package org.atmosphere.tests;

import org.atmosphere.wasync.ClientFactory;
import org.atmosphere.wasync.Client;
import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Encoder;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.RequestBuilder;
import org.atmosphere.wasync.Socket;
import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.nettosphere.Config;
import org.atmosphere.nettosphere.Nettosphere;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public abstract class BaseTest {
    public final static String RESUME = "Resume";

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

    abstract Request.TRANSPORT transport();

    abstract int statusCode();

    @BeforeMethod(alwaysRun = true)
    public void start() throws IOException {
        port = findFreePort();
        targetUrl = "http://127.0.0.1:" + port;
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

        Socket socket = client.create();
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
                t.printStackTrace();
                latch.countDown();
            }

        }).open(request.build()).fire("PING");

        latch.await(5, TimeUnit.SECONDS);
        socket.close();

        assertEquals(response.get(), RESUME);
    }

    @Test
    public void allStringFunctionTest() throws Exception {
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
        final StringBuilder builder = new StringBuilder();

        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create();
        socket.on(new Function<String>() {
            @Override
            public void on(String m) {
                builder.append(m);
                latch.countDown();
            }
        }).open(request.build()).fire("PING");

        latch.await(5, TimeUnit.SECONDS);
        socket.close();

        assertEquals(builder.toString(), "Open" + RESUME + "Close");
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

        Socket socket = client.create();
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
    public void basicConnectExceptionTest() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<ConnectException> response = new AtomicReference<ConnectException>();
        Client client = ClientFactory.getDefault().newClient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(transport());

        Socket socket = client.create();
        socket.on(new Function<ConnectException>() {

            @Override
            public void on(ConnectException t) {
                response.set(t);
                latch.countDown();
            }

        }).open(request.build());

        latch.await(5, TimeUnit.SECONDS);
        socket.close();
        assertEquals(response.get().getClass(), ConnectException.class);
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

        Socket socket = client.create();
        socket.on("message", new Function<String>() {
            @Override
            public void on(String t) {
                response.set(t);
                latch.countDown();
            }
        }).on(new Function<Throwable>() {

            @Override
            public void on(Throwable t) {
                t.printStackTrace();
                latch.countDown();
            }

        }).open(request.build()).fire("echo");

        latch.await(5, TimeUnit.SECONDS);
        socket.close();

        assertEquals(response.get(), "<-echo->");
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
                .decoder(new Decoder<String,POJO>() {
                    @Override
                    public POJO decode(String s) {
                        return new POJO(s);
                    }
                })
                .transport(transport());

        Socket socket = client.create();
        socket.on("message", new Function<POJO>() {
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
    public void chainingDecoder() throws Exception {
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
                    public POJO decode(String s) {
                        return new POJO(s);
                    }
                })
                .decoder(new Decoder<POJO, POJO2>() {
                    @Override
                    public POJO2 decode(POJO s) {
                        return new POJO2(s);
                    }
                })
                .transport(transport());

        Socket socket = client.create();
        socket.on("message", new Function<POJO2>() {
            @Override
            public void on(POJO2 t) {
                response.set(t.message);
                latch.countDown();
            }
        }).open(request.build()).fire("echo");

        latch.await(5, TimeUnit.SECONDS);
        socket.close();

        assertNotNull(response.get());
        assertEquals(response.get().getClass(), POJO.class);
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

        Socket socket = client.create();
        socket.on("message", new Function<String>() {
            @Override
            public void on(String t) {
                response.set(t);
                latch.countDown();
            }
        }).on(new Function<Throwable>() {

            @Override
            public void on(Throwable t) {
                t.printStackTrace();
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
}
