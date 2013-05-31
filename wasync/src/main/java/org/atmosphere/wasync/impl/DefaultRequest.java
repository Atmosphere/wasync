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
import org.atmosphere.wasync.Encoder;
import org.atmosphere.wasync.FunctionResolver;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.RequestBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of the {@link org.atmosphere.wasync.Request}
 *
 * @author Jeanfrancois Arcand
 */
public class DefaultRequest<T extends RequestBuilder> implements Request {

    protected final T builder;

    protected DefaultRequest(T builder) {
        this.builder = builder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TRANSPORT> transport() {
        return builder.transports();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public METHOD method() {
        return builder.method();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Collection<String>> headers() {
        return builder.headers();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, List<String>> queryString() {
        return builder.queryString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Encoder<?,?>> encoders() {
        return builder.encoders();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Decoder<?,?>> decoders() {
        return builder.decoders();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String uri() {
        return builder.uri();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FunctionResolver functionResolver() {
        return builder.resolver();
    }
}
