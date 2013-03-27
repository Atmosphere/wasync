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
package org.atmosphere.wasync;

import org.atmosphere.wasync.impl.DefaultFunctionResolver;
import org.atmosphere.wasync.impl.DefaultFunctionResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Construct a {@link Request}
 *
 * @author jeanfrancois Arcand
 */
public abstract class RequestBuilder {

    public final List<Request.TRANSPORT> transports = new ArrayList<Request.TRANSPORT>();
    public Request.METHOD method = Request.METHOD.GET;
    public String uri = "http://localhost:8080";
    public final List<Encoder<?, ?>> encoders = new ArrayList<Encoder<?, ?>>();
    public final List<Decoder<?, ?>> decoders = new ArrayList<Decoder<?, ?>>();
    public final Map<String, Collection<String>> headers = new HashMap<String, Collection<String>>();
    public final Map<String, Collection<String>> queryString = new HashMap<String, Collection<String>>();
    public FunctionResolver resolver = new DefaultFunctionResolver();

    /**
     * The {@link Request.TRANSPORT} to use. This method can be invoked several time and the library will loop over the list
     * until one {@link Request.TRANSPORT} succeed.
     * @param t
     * @return this
     */
    public RequestBuilder transport(Request.TRANSPORT t) {
        transports.add(t);
        return this;
    }

    /**
     * The method to use for connecting tho the remote server. It is recommended to always use {@link Request.METHOD.GET}
     * @param method
     * @return this
     */
    public RequestBuilder method(Request.METHOD method) {
        this.method = method;
        return this;
    }

    /**
     * The URI to connect to.
     * @param uri  a uri to connect to
     * @return this
     */
    public RequestBuilder uri(String uri) {
        this.uri = uri;
        return this;
    }

    /**
     * Add an {@link Encoder}. Several Encoder can be added and will be invoked the order they were added.
     * @param e an {@link Encoder}
     * @return this
     */
    public RequestBuilder encoder(Encoder e) {
        encoders.add(e);
        return this;
    }

    /**
     * Add a {@link Decoder}. Several Decoder can be added and will be invoked the order they were added.
     * @param d a {@link Decoder}
     * @return this
     */
    public RequestBuilder decoder(Decoder d) {
        decoders.add(d);
        return this;
    }

    /**
     * Add a header.
     * @param name header name
     * @param value header value
     * @return this
     */
    public RequestBuilder header(String name, String value) {
        Collection<String> l = headers.get(name);
        if (l == null) {
            l = new ArrayList<String>();
        }
        l.add(value);
        headers.put(name, l);
        return this;
    }

    /**
     * Add a query param.
     * @param name header name
     * @param value header value
     * @return this
     */
    public RequestBuilder queryString(String name, String value) {
        Collection<String> l = queryString.get(name);
        if (l == null) {
            l = new ArrayList<String>();
        }
        l.add(value);
        queryString.put(name, l);
        return this;
    }

    /**
     * Add a {@link FunctionResolver}
     * @param resolver  a {@link FunctionResolver}
     * @return this
     */
    public RequestBuilder resolver(FunctionResolver resolver) {
        this.resolver = resolver;
        return this;
    }

    public abstract Request build();

}
