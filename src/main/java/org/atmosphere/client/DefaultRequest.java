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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultRequest implements Request {

    protected final Builder builder;

    protected DefaultRequest(Builder builder) {
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
    public List<Encoder<?,?>> encoders() {
        return builder.encoders;
    }

    @Override
    public List<Decoder<?,?>> decoders() {
        return builder.decoders;
    }

    @Override
    public String uri() {
        return builder.uri;
    }

    public static class Builder {

        protected final List<TRANSPORT> transports = new ArrayList<TRANSPORT>();
        protected METHOD method = METHOD.GET;
        protected String uri = "http://localhost:8080";
        protected final List<Encoder<?,?>> encoders = new ArrayList<Encoder<?,?>>();
        protected final List<Decoder<?, ?>> decoders = new ArrayList<Decoder<?,?>>();
        protected final Map<String, Collection<String>> headers = new HashMap<String, Collection<String>>();
        protected final Map<String, Collection<String>> queryString = new HashMap<String, Collection<String>>();

        public Builder() {
        }

        public Builder transport(TRANSPORT t) {
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
            encoders.add(e);
            return this;
        }

        public Builder decoder(Decoder d) {
            decoders.add(d);
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

        public DefaultRequest build(){
            return new DefaultRequest(this);
        }
    }

}
