package org.atmosphere.tests;

import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.nettosphere.Config;
import org.atmosphere.nettosphere.Nettosphere;
import org.atmosphere.wasync.ClientFactory;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.RequestBuilder;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.impl.AtmosphereClient;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

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
    public void noMessageLostTest() throws Exception {
    	Config config = new Config.Builder()
        	.port(port)
        	.host("127.0.0.1")
            .broadcasterCache(org.atmosphere.cache.UUIDBroadcasterCache.class)
            .resource("/suspend", new AtmosphereHandler() {

            	@Override
            	public void onRequest(AtmosphereResource resource) throws IOException {
            		if (resource.getRequest().getMethod().equals("GET")) {
            			resource.suspend(-1);
            		} else {
            			String echo = resource.getRequest().getReader().readLine();
            			logger.info("echoing : {}", echo);
            			resource.getBroadcaster().broadcast(echo);
            		}
            	}

            	@Override
            	public void onStateChange(AtmosphereResourceEvent event) throws IOException {
                    if (List.class.isAssignableFrom(event.getMessage().getClass())) {
                        List<String> cached = (List<String>) List.class.cast(event.getMessage());
                        logger.info("cached : {}", cached);
                        for (String m: cached) {
                            event.getResource().getResponse().write(m);
                        }
                    } else {
                        event.getResource().getResponse().write((String) event.getMessage());
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

       Socket socket = client.create(client.newOptionsBuilder().build());

       socket.on("message", new Function<String>() {
    	   @Override
    	   public void on(String message) {
    		   logger.info("received : {}", message);
    		   response.get().add(message);
    		   latch.countDown();
    	   }
       }).on(new Function<Throwable>() {
    	   @Override
    	   public void on(Throwable t) {
    		   t.printStackTrace();
    	   }
       }).open(request.build());

       socket.fire("ECHO1");
       Thread.sleep(1000);
       socket.fire("ECHO2");
       Thread.sleep(2000);
       socket.fire("ECHO3").get();
       socket.fire("ECHO4").get();
       socket.fire("ECHO5").get();
       
       latch.await(10, TimeUnit.SECONDS);
       socket.close();

       assertEquals(response.get().size(), 5);
       
    }
}
