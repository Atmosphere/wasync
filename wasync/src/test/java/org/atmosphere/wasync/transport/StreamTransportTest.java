package org.atmosphere.wasync.transport;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.Collections;
import org.atmosphere.wasync.Client;
import org.atmosphere.wasync.ClientFactory;
import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Event;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.Socket;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class StreamTransportTest {

    private StreamTransport streamTransport;

    private TestDecoder decoder;

    @BeforeMethod
    void setup() {
        Client client = ClientFactory.getDefault().newClient();

        Options options = client.newOptionsBuilder().runtime(null, false).reconnect(false).build();

        decoder = new TestDecoder();

        Request request = client.newRequestBuilder()
            .method(Request.METHOD.GET)
            .uri("https://www.example.com")
            .transport(Request.TRANSPORT.STREAMING)
            .decoder(decoder)
            .build();
        streamTransport = new StreamTransport(null, options, request, Collections.emptyList());
    }

    @Test
    public void testTriggerOpen_socketInit() {
        streamTransport.status = Socket.STATUS.INIT;

        streamTransport.triggerOpen();

        assertEquals(streamTransport.status, Socket.STATUS.OPEN);
        assertEquals(decoder.getEvent(), Event.OPEN);
    }

    @Test
    public void testTriggerOpen_socketOpen() {
        streamTransport.status = Socket.STATUS.OPEN;

        streamTransport.triggerOpen();

        assertEquals(streamTransport.status, Socket.STATUS.OPEN);
        assertNull(decoder.getEvent());
    }

    @Test
    public void testTriggerOpen_socketClose() {
        streamTransport.status = Socket.STATUS.CLOSE;

        streamTransport.triggerOpen();

        assertEquals(streamTransport.status, Socket.STATUS.OPEN);
        assertEquals(decoder.getEvent(), Event.REOPENED);
    }

    private static class TestDecoder implements Decoder<String, Object> {
        private Event event;

        @Override
        public Object decode(Event e, String s) {
            setEvent(e);
            return s;
        }

        public Event getEvent() {
            return event;
        }

        public void setEvent(Event e) {
            this.event = e;
        }
    }
}
