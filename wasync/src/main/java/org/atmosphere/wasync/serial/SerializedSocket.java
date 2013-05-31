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
package org.atmosphere.wasync.serial;

import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Response;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.impl.DefaultFuture;
import org.atmosphere.wasync.impl.DefaultSocket;
import org.atmosphere.wasync.impl.SocketRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * {@code SerializedSocket} is a {@link Socket} implementation that guarantees ordered message delivery of
 * {@link Socket#fire(Object)} calls, by serializing fire calls over a {@link SerializedFireStage}.
 * <p/>
 * {@code SerializedSocket} guarantees to use only one underlying connection at any moment in time, while still
 * providing an asynchronous fire interface to clients.
 * <p/>
 *
 * @author Christian Bach
 * @author Christian Bach
 */
public class SerializedSocket extends DefaultSocket {

    private final static Logger logger = LoggerFactory.getLogger(SerializedSocket.class);

    private SerializedFireStage serializedFireStage;

    public SerializedSocket(SerializedOptions options) {
        super(options);
        this.serializedFireStage = options.serializedFireStage();
        this.serializedFireStage.setSocket(this);
    }

    @Override
    protected SocketRuntime createSocket() {
        return new SerialSocketRuntime(options, new DefaultFuture(this), this, functions);
    }

    public SerializedFireStage getSerializedFireStage() {
        return serializedFireStage;
    }

    public ListenableFuture<Response> directWrite(Object encodedPayload) throws IOException {
        return socketRuntime.httpWrite(request, encodedPayload, encodedPayload);
    }
}
