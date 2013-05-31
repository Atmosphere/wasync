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
package org.atmosphere.wasync;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Base class for building {@link Request}
 *
 * @author jeanfrancois Arcand
 */
public abstract class RequestBuilder<T extends RequestBuilder<T>> {

    protected final List<Request.TRANSPORT> transports = new ArrayList<Request.TRANSPORT>();
    protected Request.METHOD method = Request.METHOD.GET;
    protected String uri = "http://localhost:8080";
    protected final List<Encoder<?, ?>> encoders = new CopyOnWriteArrayList<Encoder<?, ?>>();
    protected final List<Decoder<?, ?>> decoders = new CopyOnWriteArrayList<Decoder<?, ?>>();
    protected final Map<String, Collection<String>> headers = new HashMap<String, Collection<String>>();
    protected final Map<String, List<String>> queryString = new HashMap<String, List<String>>();
    protected FunctionResolver resolver = FunctionResolver.DEFAULT;
    protected final Class<T> derived;

    protected RequestBuilder(Class<T> derived) {
        this.derived = derived;
    }

    /**
     * The {@link Request.TRANSPORT} to use. This method can be invoked several time and the library will loop over the list
     * until one {@link Request.TRANSPORT} succeed.
     * @param t
     * @return this
     */
    public T transport(Request.TRANSPORT t) {
        transports.add(t);
        return derived.cast(this);
    }

    /**
     * The method to use for connecting tho the remote server. It is recommended to always use {@link Request.METHOD#GET}
     * @param method
     * @return this
     */
    public T method(Request.METHOD method) {
        this.method = method;
        return derived.cast(this);
    }

    /**
     * The URI to connect to.
     * @param uri  a uri to connect to
     * @return this
     */
    public T uri(String uri) {
        this.uri = uri;
        return derived.cast(this);
    }

    /**
     * Add an {@link Encoder}. Several Encoder can be added and will be invoked the order they were added.
     * @param e an {@link Encoder}
     * @return this
     */
    public T encoder(Encoder e) {
        encoders.add(e);
        return derived.cast(this);
    }

    /**
     * Add a {@link Decoder}. Several Decoder can be added and will be invoked the order they were added.
     * @param d a {@link Decoder}
     * @return this
     */
    public T decoder(Decoder d) {
        decoders.add(d);
        return derived.cast(this);
    }

    /**
     * Add a header.
     * @param name header name
     * @param value header value
     * @return this
     */
    public T header(String name, String value) {
        Collection<String> l = headers.get(name);
        if (l == null) {
            l = new ArrayList<String>();
        }
        l.add(value);
        headers.put(name, l);
        return derived.cast(this);
    }

    /**
     * Add a query param.
     * @param name header name
     * @param value header value
     * @return this
     */
    public T queryString(String name, String value) {
        List<String> l = queryString.get(name);
        if (l == null) {
            l = new ArrayList<String>();
        }
        l.add(value);
        queryString.put(name, l);
        return derived.cast(this);
    }

    /**
     * Add a {@link FunctionResolver}
     * @param resolver  a {@link FunctionResolver}
     * @return this
     */
    public T resolver(FunctionResolver resolver) {
        this.resolver = resolver;
        return derived.cast(this);
    }

    public abstract Request build();

    /**
     * Return the current list of {@link Request.TRANSPORT}
     * @return the current list of {@link Request.TRANSPORT}
     */
    public List<Request.TRANSPORT> transports() {
        return transports;
    }

    /**
     * Return the HTTP method
     * @return  the HTTP method
     */
    public Request.METHOD method() {
        return method;
    }

    /**
     * Return the current tMap of headers
     * @return the current tMap of headers
     */
    public Map<String, Collection<String>> headers() {
        return headers;
    }

    /**
     * Return the current query string/form param
     * @return  the current query string/form param
     */
    public Map<String, List<String>> queryString() {
        return queryString;
    }

    /**
     * Return the current list of {@link Encoder}
     * @return  the current list of {@link Encoder}
     */
    public List<Encoder<?, ?>> encoders() {
        return encoders;
    }

    /**
     * Return the current list of {@link Decoder}
     * @return  the current list of {@link Decoder}
     */
    public List<Decoder<?, ?>> decoders() {
        return decoders;
    }

    /**
     * Return the uri
     * @return the uri
     */
    public String uri() {
        return uri;
    }

    /**
     * Return the current {@link FunctionResolver}
     * @return the current {@link FunctionResolver}
     */
    public FunctionResolver resolver() {
        return resolver;
    }
}
