package org.atmosphere.tests;

import org.atmosphere.cpr.ApplicationConfig;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    public void BinaryEchoTest() throws Exception {
    	Config config = new Config.Builder()
    		.port(port)
    		.host("127.0.0.1")    	
    		.resource("/suspend", new AtmosphereHandler() {

        	@Override
        	public void onRequest(AtmosphereResource resource) throws IOException {
        		if (resource.getRequest().getMethod().equals("GET")) {
        			resource.suspend(-1);
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
    				event.getResource().getResponse().write((byte[])event.getMessage());        
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
    	
    	final byte[] binaryEcho = new byte[] {1,2,3,4};

    	RequestBuilder request = client.newRequestBuilder()
		   .method(Request.METHOD.GET)
		   .uri(targetUrl + "/suspend")
		   .header("Content-Type", "application/octet-stream")
		   .transport(Request.TRANSPORT.LONG_POLLING);

    	Socket socket = client.create(client.newOptionsBuilder().build());

    	socket.on("message", new Function<byte[]>() {    		
    		@Override
    		public void on(byte[] message) {
    			logger.info("received : {}", message);
    			if (Arrays.equals(message,binaryEcho)) {
    				hasEchoReplied.getAndSet(true);
    			}
    			latch.countDown();
    		}
    	}).on(new Function<Throwable>() {
    		@Override
    		public void on(Throwable t) {
    			t.printStackTrace();
    		}
    	}).open(request.build());

    	socket.fire(binaryEcho).get();
    	
    	latch.await(2, TimeUnit.SECONDS);
    	socket.close();

    	assertEquals(hasEchoReplied.get(), true);
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
       socket.fire("ECHO3");
       socket.fire("ECHO4");
       socket.fire("ECHO5");
       
       latch.await(60, TimeUnit.SECONDS);
       socket.close();

       assertEquals(response.get().size(), 5);
       
    }
}
