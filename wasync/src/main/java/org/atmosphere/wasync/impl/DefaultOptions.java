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

import com.ning.http.client.AsyncHttpClient;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.OptionsBuilder;
import org.atmosphere.wasync.Transport;

/**
 * Default implementation of the {@link Options}
 *
 * @author Jeanfrancois Arcand
 */
public class DefaultOptions implements Options {

    protected final OptionsBuilder b;

    public DefaultOptions(OptionsBuilder b) {
        this.b = b;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Transport transport() {
        return b.transport();
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean reconnect(){
        return b.reconnect();
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public int reconnectInSeconds(){
        return b.reconnectInSeconds();
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public long waitBeforeUnlocking() {
        return b.waitBeforeUnlocking();
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncHttpClient runtime(){
        return b.runtime();
    }

    @Override
    public void runtime(AsyncHttpClient client) {
        b.runtime(client);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean runtimeShared(){
        return b.runtimeShared();
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public int requestTimeoutInSeconds() {
        return b.requestTimeoutInSeconds();
    }
}
