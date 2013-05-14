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

public class AtmosphereRequest extends DefaultRequest<AtmosphereRequest.AtmosphereRequestBuilder> {

    public enum CACHE {HEADER_BROADCAST_CACHE, UUID_BROADCASTER_CACHE, SESSION_BROADCAST_CACHE, NO_BROADCAST_CACHE}

    private final static Logger logger = LoggerFactory.getLogger(AtmosphereRequest.class);

    protected AtmosphereRequest(AtmosphereRequestBuilder builder) {
        super(builder);
    }

    public AtmosphereRequest.CACHE getCacheType() {
        return builder.getCacheType();
    }

    public boolean isTrackMessageLength() {
        return builder.isTrackeMessageLength();
    }

    public String getTrackMessageLengthDelimiter() {
        return builder.getTrackMessageLengthDelimiter();
    }

    public static class AtmosphereRequestBuilder extends RequestBuilder<AtmosphereRequestBuilder> {

        private CACHE cacheType = CACHE.NO_BROADCAST_CACHE;
        private boolean trackMessageLength = false;
        private String trackMessageLengthDelimiter = "|";

        public AtmosphereRequestBuilder() {
            super(AtmosphereRequestBuilder.class);
            List<String> l = new ArrayList<String>();
            l.add("1.0");
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

        private CACHE getCacheType() {
            return cacheType;
        }

        public AtmosphereRequestBuilder transport(TRANSPORT t) {
            if (queryString.get("X-Atmosphere-Transport") == null) {
                List<String> l = new ArrayList<String>();
                if (t.equals(TRANSPORT.LONG_POLLING)) {
                    l.add("long-polling");
                } else {
                    l.add(t.name());
                }

                queryString.put("X-Atmosphere-Transport", l);
            }
            transports.add(t);
            return derived.cast(this);
        }

        public AtmosphereRequestBuilder cache(CACHE c) {
            this.cacheType = c;
            return this;
        }

        public AtmosphereRequestBuilder trackMessageLength(boolean trackMessageLength) {
            this.trackMessageLength = trackMessageLength;
            return this;
        }

        public AtmosphereRequestBuilder trackMessageLengthDelimiter(String trackMessageLengthDelimiter) {
            this.trackMessageLengthDelimiter = trackMessageLengthDelimiter;
            return this;
        }

        private boolean isTrackeMessageLength() {
            return trackMessageLength;
        }

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
                            String[] proto = s.trim().split("\\|");
                            List<String> l = new ArrayList<String>();
                            l.add(proto[0]);
                            queryString.put("X-Atmosphere-tracking-id", l);
                            l = new ArrayList<String>();
                            l.add(proto[1]);
                            queryString.put("X-Cache-Date", l);
                            decoders.remove(this);

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
                            String[] proto = new String(b, "UTF-8").trim().split("\\|");
                            List<String> l = new ArrayList<String>();
                            l.add(proto[0]);
                            queryString.put("X-Atmosphere-tracking-id", l);
                            l = new ArrayList<String>();
                            l.add(proto[1]);
                            queryString.put("X-Cache-Date", l);
                            decoders.remove(this);

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
    }


}
