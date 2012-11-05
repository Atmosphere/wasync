/*
 * Copyright 2012 Jeanfrancois Arcand
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

import org.atmosphere.wasync.ClientFactory;
import org.atmosphere.wasync.Client;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.RequestBuilder;
import org.atmosphere.wasync.Socket;
import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.nettosphere.Config;
import org.atmosphere.nettosphere.Nettosphere;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class WebSocketsTest extends BaseTest {
    @Override
    Request.TRANSPORT transport() {
        return Request.TRANSPORT.WEBSOCKET;
    }

    @Override
    int statusCode() {
        return 101;
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

          Client client = ClientFactory.getDefault().newclient();

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

}
