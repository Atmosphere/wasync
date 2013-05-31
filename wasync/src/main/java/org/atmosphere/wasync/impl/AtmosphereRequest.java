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
package org.atmosphere.wasync.impl;

import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Event;
import org.atmosphere.wasync.RequestBuilder;
import org.atmosphere.wasync.decoder.TrackMessageSizeDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A specialized {@link org.atmosphere.wasync.Request} implementation to use with the Atmosphere Framework. Functionality
 * like track message length, broadcaster cache, etc. can be configured using this object. Make sure your server
 * is properly configured before changing the default.
 *
 * @author Jeanfrancois Arcand
 */
public class AtmosphereRequest extends DefaultRequest<AtmosphereRequest.AtmosphereRequestBuilder> {

    public enum CACHE {HEADER_BROADCAST_CACHE, UUID_BROADCASTER_CACHE, SESSION_BROADCAST_CACHE, NO_BROADCAST_CACHE}

    private final static Logger logger = LoggerFactory.getLogger(AtmosphereRequest.class);

    protected AtmosphereRequest(AtmosphereRequestBuilder builder) {
        super(builder);
    }

    /**
     * Return the {@link AtmosphereRequest.CACHE} used. The value must match the Atmosphere's Broadcaster cache implementation
     * of the server.
     * @return the {@link AtmosphereRequest.CACHE}
     */
    public AtmosphereRequest.CACHE getCacheType() {
        return builder.getCacheType();
    }

    /**
     * Is tracking message's length enabled.
     * @return
     */
    public boolean isTrackMessageLength() {
        return builder.isTrackeMessageLength();
    }

    /**
     * The delimiter used by the Atmosphere Framework when sending message length and message's size.
     * @return delimiter used. Default is '|'
     */
    public String getTrackMessageLengthDelimiter() {
        return builder.getTrackMessageLengthDelimiter();
    }

    /**
     * A builder for {@link AtmosphereRequest}. This builder configure the Atmosphere Protocol on the request object.
     */
    public static class AtmosphereRequestBuilder extends RequestBuilder<AtmosphereRequestBuilder> {

        private CACHE cacheType = CACHE.NO_BROADCAST_CACHE;
        private boolean trackMessageLength = false;
        private String trackMessageLengthDelimiter = "|";

        public AtmosphereRequestBuilder() {
            super(AtmosphereRequestBuilder.class);
            List<String> l = new ArrayList<String>();
            l.add("1.1.0");
            queryString.put("X-Atmosphere-Framework", l);

            l = new ArrayList<String>();
            l.add("0");
            queryString.put("X-Atmosphere-tracking-id", l);

            l = new ArrayList<String>();
            l.add("0");
            queryString.put("X-Cache-Date", l);

            l = new ArrayList<String>();
            l.add("true");
            queryString.put("X-atmo-protocol", l);
        }

        /**
         * Return the {@link AtmosphereRequest.CACHE} used. The value must match the Atmosphere's Broadcaster cache implementation
         * of the server.
         * @return the {@link AtmosphereRequest.CACHE}
         */
        private CACHE getCacheType() {
            return cacheType;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public AtmosphereRequestBuilder transport(TRANSPORT t) {
            if (queryString.get("X-Atmosphere-Transport") == null) {
                List<String> l = new ArrayList<String>();
                if (t.equals(TRANSPORT.LONG_POLLING)) {
                    l.add("long-polling");
                } else {
                    l.add(t.name().toLowerCase());
                }

                queryString.put("X-Atmosphere-Transport", l);
            }
            transports.add(t);
            return derived.cast(this);
        }

        /**
         * Set the {@link CACHE} used by the server side implementation of Atmosphere.
         * @param c the cache type.
         * @return this;
         */
        public AtmosphereRequestBuilder cache(CACHE c) {
            this.cacheType = c;
            return this;
        }

        /**
         * Turn on/off tracking message.
         * @param trackMessageLength  true to enable.
         * @return this
         */
        public AtmosphereRequestBuilder trackMessageLength(boolean trackMessageLength) {
            this.trackMessageLength = trackMessageLength;
            return this;
        }

        /**
         * Set the tracking delimiter.
         * @param trackMessageLengthDelimiter  true to enable.
         * @return this
         */
        public AtmosphereRequestBuilder trackMessageLengthDelimiter(String trackMessageLengthDelimiter) {
            this.trackMessageLengthDelimiter = trackMessageLengthDelimiter;
            return this;
        }

        /**
         * Is track message's length enabled?
         * @return  true if enabled.
         */
        private boolean isTrackeMessageLength() {
            return trackMessageLength;
        }

        /**
         * The delimiter used to track message size.
         * @return
         */
        private String getTrackMessageLengthDelimiter() {
            return trackMessageLengthDelimiter;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public AtmosphereRequest build() {

            if (trackMessageLength) {
                decoders().add(0, new TrackMessageSizeDecoder());
            }

            decoders().add(0, new Decoder<String, String>() {

                private AtomicBoolean protocolReceived = new AtomicBoolean();
                /**
                 * Handle the Atmosphere's Protocol.
                 */
                @Override
                public String decode(Event e, String s) {
                    if (e.equals(Event.MESSAGE) && !protocolReceived.getAndSet(true)) {
                        try {
                            handleProtocol(s);

                            s = null;
                        } catch (Exception ex) {
                            logger.warn("Unable to decode the protocol {}", s);
                            logger.warn("",e);
                        }
                    }
                    return s;
                }
            });

            decoders().add(0, new Decoder<byte[], byte[]>() {

                private AtomicBoolean protocolReceived = new AtomicBoolean();
                /**
                 * Handle the Atmosphere's Protocol.
                 */
                @Override
                public byte[] decode(Event e, byte[] b) {
                    if (e.equals(Event.MESSAGE) && !protocolReceived.getAndSet(true)) {
                        try {
                            handleProtocol(new String(b, "UTF-8"));

                            b = null;
                        } catch (Exception ex) {
                            logger.warn("Unable to decode the protocol {}", new String(b));
                            logger.warn("",e);
                        }
                    }
                    return b;
                }
            });

            return new AtmosphereRequest(this);
        }

        private final void handleProtocol(String s){
            String[] proto = s.trim().split("\\|");
            // Track message size may have been appended
            int pos = proto.length > 2 ? pos = 1 : 0;

            List<String> l = new ArrayList<String>();
            l.add(proto[pos]);
            queryString.put("X-Atmosphere-tracking-id", l);
            l = new ArrayList<String>();
            l.add(proto[pos + 1]);
            queryString.put("X-Cache-Date", l);
            decoders.remove(this);
        }
    }

}
