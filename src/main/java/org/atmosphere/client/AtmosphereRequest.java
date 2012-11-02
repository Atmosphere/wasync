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
package org.atmosphere.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AtmosphereRequest implements Request {
    private final Builder builder;

    private AtmosphereRequest(Builder builder) {
        this.builder = builder;
    }

    @Override
    public List<TRANSPORT> transport() {
        return builder.transports;
    }

    @Override
    public METHOD method() {
        return builder.method;
    }

    @Override
    public Map<String, Collection<String>> headers() {
        return builder.headers;
    }

    @Override
    public Map<String, Collection<String>> queryString() {
        return builder.queryString;
    }

    @Override
    public Encoder<?> encoder() {
        return builder.encoder;
    }

    @Override
    public Decoder<?> decoder() {
        return builder.decoder;
    }

    @Override
    public String uri() {
        return builder.uri;
    }

    public final static class Builder {

        private final List<TRANSPORT> transports = new ArrayList<TRANSPORT>();
        private METHOD method = METHOD.GET;
        private String uri = "http://localhost:8080";
        private Encoder<?> encoder = null;
        private Decoder<?> decoder = null;
        private final Map<String, Collection<String>> headers = new HashMap<String, Collection<String>>();
        private final Map<String, Collection<String>> queryString = new HashMap<String, Collection<String>>();

        public Builder() {

            List<String> l = new ArrayList<String>();
            l.add("1.0");
            headers.put("X-Atmosphere-Framework", l);

            l = new ArrayList<String>();
            l.add("0");
            headers.put("X-Atmosphere-tracking-id", l);

            l = new ArrayList<String>();
            l.add("0");
            headers.put("X-Cache-Date", l);
        }

        public Builder transport(TRANSPORT t) {
            List<String> l = new ArrayList<String>();
            if (t.equals(TRANSPORT.LONG_POLLING)) {
                l.add("long-polling");
            } else {
                l.add(t.name());
            }

            headers.put("X-Atmosphere-Transport", l);
            transports.add(t);
            return this;
        }

        public Builder method(METHOD method) {
            this.method = method;
            return this;
        }

        public Builder uri(String uri) {
            this.uri = uri;
            return this;
        }

        public Builder encoder(Encoder e) {
            encoder = e;
            return this;
        }

        public Builder decoder(Decoder d) {
            decoder = d;
            return this;
        }

        public Builder header(String name, String value) {
            Collection<String> l = headers.get(name);
            if (l == null) {
                l = new ArrayList<String>();
            }
            l.add(value);
            headers.put(name, l);
            return this;
        }

        public Builder queryString(String name, String value) {
            Collection<String> l = queryString.get(name);
            if (l == null) {
                l = new ArrayList<String>();
            }
            l.add(value);
            queryString.put(name, l);
            return this;
        }

        public AtmosphereRequest build() {
            return new AtmosphereRequest(this);
        }
    }
}
