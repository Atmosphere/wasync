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

import org.atmosphere.wasync.Client;
import org.atmosphere.wasync.FunctionResolver;
import org.atmosphere.wasync.OptionsBuilder;
import org.atmosphere.wasync.RequestBuilder;
import org.atmosphere.wasync.Socket;

/**
 * The default implementation of the {@link Client}
 *
 * @author Jeanfrancois Arcand
 */
public class DefaultClient implements Client<DefaultOptions, OptionsBuilder,RequestBuilder> {

    public DefaultClient() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Socket create() {
        return ClientUtil.create();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Socket create(DefaultOptions options) {
        return ClientUtil.create(options);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RequestBuilder newRequestBuilder() {
        RequestBuilder b = new DefaultRequestBuilder();
        return b.resolver(FunctionResolver.DEFAULT);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OptionsBuilder newOptionsBuilder() {
        return new DefaultOptionsBuilder();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RequestBuilder newRequestBuilder(Class<RequestBuilder> clazz) {
        RequestBuilder b = null;
        try {
            b = clazz.newInstance();
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return b.resolver(FunctionResolver.DEFAULT);
    }
}
