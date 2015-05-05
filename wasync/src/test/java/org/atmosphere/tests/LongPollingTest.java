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
import org.atmosphere.cpr.ApplicationConfig;
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
import org.atmosphere.wasync.serial.DefaultSerializedFireStage;
import org.atmosphere.wasync.serial.SerializedClient;
import org.atmosphere.wasync.serial.SerializedOptionsBuilder;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class LongPollingTest extends StreamingTest {

    @Override
    Request.TRANSPORT transport() {
        return Request.TRANSPORT.LONG_POLLING;
    }

    @Override
    int statusCode() {
        return 200;
    }

    @Override
    int notFoundCode() {
        return 404;
    }

    @Test
    public void BinaryEchoTest() throws Exception {

        logger.info("\n\nBinaryEchoTest\n\n");
        final CountDownLatch suspendedLatch = new CountDownLatch(2);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    @Override
                    public void onRequest(AtmosphereResource resource) throws IOException {
                        if (resource.getRequest().getMethod().equals("GET")) {
                            resource.addEventListener(new AtmosphereResourceEventListenerAdapter.OnSuspend() {
                                @Override
                                public void onSuspend(AtmosphereResourceEvent event) {
                                    suspendedLatch.countDown();
                                }
                            }).suspend(-1);
                        } else {
                            int payloadSize = resource.getRequest().getContentLength();
                            byte[] payload = new byte[payloadSize];
                            try {
                                resource.getRequest().getInputStream().read(payload);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            logger.info("echoing : {}", payload);
                            resource.getBroadcaster().broadcast(payload);
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent event) throws IOException {
                        if (!(event.isResuming()) || event.isResumedOnTimeout() || event.isSuspended()) {
                            // make the GET reply have binary content type
                            event.getResource().getResponse().setContentType("application/octet-stream");
                            // make it use the OutputStream directly in writing, prevent any String conversions.
                            event.getResource().getRequest().setAttribute(ApplicationConfig.PROPERTY_USE_STREAM, true);
                            // do the actual write
                            event.getResource().getResponse().write((byte[]) event.getMessage());
                            event.getResource().resume();
                        }
                    }

                    @Override
                    public void destroy() {

                    }

                }).build();

        Nettosphere server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean hasEchoReplied = new AtomicBoolean(false);
        AtmosphereClient client = ClientFactory.getDefault().newClient(AtmosphereClient.class);

        final byte[] binaryEcho = new byte[]{1, 2, 3, 4};

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .header("Content-Type", "application/octet-stream")
                .transport(Request.TRANSPORT.LONG_POLLING);

        final Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());


        socket.on(new Function<Integer>() {
            @Override
            public void on(Integer statusCode) {
                suspendedLatch.countDown();
            }
        }).on("message", new Function<byte[]>() {
            @Override
            public void on(byte[] message) {
                logger.info("===Received : {}", message);
                if (Arrays.equals(message, binaryEcho) && !hasEchoReplied.get()) {
                    hasEchoReplied.getAndSet(true);
                    socket.close();
                    latch.countDown();
                }
            }
        }).on(new Function<Throwable>() {
            @Override
            public void on(Throwable t) {
                t.printStackTrace();
            }
        }).open(request.build());

        suspendedLatch.await(5, TimeUnit.SECONDS);

        socket.fire(binaryEcho).get();

        latch.await(10, TimeUnit.SECONDS);

        assertEquals(hasEchoReplied.get(), true);
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
                .fire("PONG").get(5, TimeUnit.SECONDS);

        latch.await(5, TimeUnit.SECONDS);
        socket.close();

        assertEquals(response.get().toString(), "PINGPONG");
    }

    /**
     * Due to the reconnection cycle, this test may or may not work on Jenkins due ti the connection latency. Better to disable it.
     * @throws Exception
     */
    @Test(enabled = false)
    public void noMessageLostTest() throws Exception {

        logger.info("\n\nnoMessageLostTest\n\n");
        final CountDownLatch suspendedLatch = new CountDownLatch(2);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .broadcasterCache(org.atmosphere.cache.UUIDBroadcasterCache.class)
                .resource("/suspend", new AtmosphereHandler() {

                    @Override
                    public void onRequest(AtmosphereResource resource) throws IOException {
                        if (resource.getRequest().getMethod().equals("GET")) {
                            logger.info("Suspending : {}", resource.uuid());
                            resource.addEventListener(new AtmosphereResourceEventListenerAdapter.OnSuspend() {
                                @Override
                                public void onSuspend(AtmosphereResourceEvent event) {
                                    suspendedLatch.countDown();
                                }
                            }).suspend(-1);
                        } else {
                            String echo = resource.getRequest().getReader().readLine();
                            logger.info("echoing : {}", echo);
                            try {
                                resource.getBroadcaster().broadcast(echo).get();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            } catch (ExecutionException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent event) throws IOException {
                        logger.info("cached : {}", event.getMessage());

                        if (event.getMessage() != null && List.class.isAssignableFrom(event.getMessage().getClass())) {
                            List<String> cached = (List<String>) List.class.cast(event.getMessage());
                            StringBuilder b = new StringBuilder();
                            for (String m : cached) {
                                b.append(m).append("-");
                            }
                            // Write message in a single IO operation to prevent the connection from resuming.
                            event.getResource().getResponse().write(b.toString());
                        } else {
                            event.getResource().getResponse().write((String) event.getMessage() + "-");
                        }
                        event.getResource().resume();
                    }

                    @Override
                    public void destroy() {

                    }

                }).build();

        Nettosphere server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final CountDownLatch latch = new CountDownLatch(5);
        final AtomicReference<Set> response = new AtomicReference<Set>(new HashSet());
        AtmosphereClient client = ClientFactory.getDefault().newClient(AtmosphereClient.class);

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .transport(Request.TRANSPORT.LONG_POLLING);

        final Socket socket = client.create(client.newOptionsBuilder().runtime(ahc, false).build());


        socket.on(new Function<Integer>() {
            @Override
            public void on(Integer statusCode) {
                suspendedLatch.countDown();
            }
        }).on("message", new Function<String>() {
            @Override
            public void on(String message) {
                logger.info("received : {}", message);

                for (String m : message.split("-")) {
                    response.get().add(m);
                    latch.countDown();
                }
            }
        }).on(new Function<Throwable>() {
            @Override
            public void on(Throwable t) {
                t.printStackTrace();
            }
        }).open(request.build());

        suspendedLatch.await(5, TimeUnit.SECONDS);

        socket.fire("ECHO1");
        socket.fire("ECHO2");
        socket.fire("ECHO3");
        socket.fire("ECHO4");
        socket.fire("ECHO5");

        latch.await(10, TimeUnit.SECONDS);

        logger.info("RESPONSE {}", response.get());
        assertEquals(response.get().size(), 5);
        socket.close();
        server.stop();

    }

    @Test(enabled = true)
    public void serializeFutureGetTest() throws Exception {
        logger.info("\n\nserializeFutureGetTest\n\n");

        final CountDownLatch latch = new CountDownLatch(4);
        final AtomicInteger allMessagesReceived = new AtomicInteger();
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

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
                                String msg = r.getRequest().getReader().readLine();
                                // In case the message arrive in a single chunk.
                                if (msg.equalsIgnoreCase("PINGPONG")) {
                                    r.getBroadcaster().broadcast("PING").get();
                                    r.getBroadcaster().broadcast("PONG").get();
                                } else {
                                    r.getBroadcaster().broadcast(msg).get();
                                }
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

                            // If the connection is about to resume we will loose the message so we must make sure we got 2 messages
                            // before resuming.
                            if (allMessagesReceived.incrementAndGet() == 2
                                    && r.getResource().transport().equals(AtmosphereResource.TRANSPORT.LONG_POLLING)) {
                                r.getResource().getResponse().flushBuffer();
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
                logger.info("Serialized Function get invoked {}", t);
                response.get().append(t);
                latch.countDown();
                // If we received the message in a single packet
                if (t.equals("PINGPONG")) latch.countDown();
            }
        }).on(Event.OPEN.name(), new Function<Object>() {
                    @Override
                    public void on(Object o) {
                        latch.countDown();
                    }
                }).open(request.build())
                .fire("PING")
                .fire("PONG").get(5, TimeUnit.SECONDS);

        latch.await(10, TimeUnit.SECONDS);

        socket.close();

        assertEquals(response.get().toString(), "PINGPONG");
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
}
