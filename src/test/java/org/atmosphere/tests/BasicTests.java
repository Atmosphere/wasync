package org.atmosphere.tests;

import org.atmosphere.client.AtmosphereClientFactory;
import org.atmosphere.client.Client;
import org.atmosphere.client.DefaultRequest;
import org.atmosphere.client.Function;
import org.atmosphere.client.Request;
import org.atmosphere.client.Socket;
import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterFactory;
import org.atmosphere.cpr.BroadcasterListener;
import org.atmosphere.cpr.DefaultBroadcasterFactory;
import org.atmosphere.nettosphere.Config;
import org.atmosphere.nettosphere.Nettosphere;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class BasicTests {
    private final static String RESUME = "Resume";

    protected Nettosphere server;
    private String targetUrl;
    private String wsUrl;
    protected static final Logger logger = LoggerFactory.getLogger(BasicTests.class);
    public String urlTarget;
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
        server.stop();
    }

    @BeforeMethod(alwaysRun = true)
    public void start() throws IOException {
        port = 8080;
        targetUrl = "http://127.0.0.1:" + port;
        wsUrl = "ws://127.0.0.1:" + port;
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
        Client client = AtmosphereClientFactory.getDefault().newclient();

        DefaultRequest.Builder request = new DefaultRequest.Builder()
                .method(Request.METHOD.GET)
                .uri(wsUrl + "/suspend")
                .transport(Request.TRANSPORT.WEBSOCKET);

        Socket socket = client.create();
        socket.on(new Function<String>() {
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

        }).open(request).fire("PING");

        latch.await(5, TimeUnit.SECONDS);
        assertEquals(response.get(), RESUME);
        socket.close();
    }

    @Test
    public void basicConnectExceptionTest() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<ConnectException> response = new AtomicReference<ConnectException>();
        Client client = AtmosphereClientFactory.getDefault().newclient();

        DefaultRequest.Builder request = new DefaultRequest.Builder()
                .method(Request.METHOD.GET)
                .uri(wsUrl + "/suspend")
                .transport(Request.TRANSPORT.WEBSOCKET);

        Socket socket = client.create();
        socket.on(new Function<ConnectException>() {

            @Override
            public void on(ConnectException t) {
                response.set(t);
                latch.countDown();
            }

        }).open(request);

        latch.await(5, TimeUnit.SECONDS);
        assertEquals(response.get().getClass(), ConnectException.class);
        socket.close();
    }

}
