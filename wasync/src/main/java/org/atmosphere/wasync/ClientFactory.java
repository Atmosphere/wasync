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

import org.atmosphere.wasync.impl.DefaultClient;

/**
 * Create a {@link Client}. By default, the {@link DefaultClient} will be returned. To override the {@link DefaultClient},
 * just specify the provider using the -Dwasync.client property.
 *
 * @author Jeanfrancois Arcand
 */
public class ClientFactory {

    private final static ClientFactory factory = new ClientFactory();
    private final String clientClassName;

    public ClientFactory() {
        clientClassName = System.getProperty("wasync.client");
    }

    /**
     * Return the default Factory.
     *
     * @return this
     */
    public final static ClientFactory getDefault() {
        return factory;
    }

    /**
     * Return a new {@link Client} instance
     *
     * @return a new {@link Client} instance
     */
    public Client<? extends Options, ? extends OptionsBuilder, ? extends RequestBuilder> newClient() {
        if (clientClassName == null) {
            return new DefaultClient();
        } else {
            try {
                return (Client) Thread.currentThread().getContextClassLoader().loadClass(clientClassName).newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Return a new {@link Client} instance
     *
     * @param clientClass the runtime instance class of {@link Client} instance that is returned
     * @return a new {@link Client} instance
     */
    public <T extends Client<? extends Options, ? extends OptionsBuilder, ? extends RequestBuilder>> T newClient(Class<? extends Client> clientClass) {
        Client client;
        try {
            client = clientClass.newInstance();
            return (T) client;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
