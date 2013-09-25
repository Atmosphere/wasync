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
import org.atmosphere.wasync.Future;
import org.atmosphere.wasync.Socket;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class FutureProxy<T extends java.util.concurrent.Future> implements Future {

    private final Socket serializedSocket;
    private final T proxyiedFuture;
    private IOException ioException;

    public FutureProxy(Socket serializedSocket, T proxyiedFuture) {
        this.serializedSocket = serializedSocket;
        this.proxyiedFuture = proxyiedFuture;
    }

    @Override
    public Future fire(Object data) throws IOException {
        return serializedSocket.fire(data);
    }

    @Override
    public Future finishOrThrowException() throws IOException{
        done();
        if (ioException != null) {
            throw ioException;
        }
        return this;
    }

    @Override
    public Future ioException(IOException t) {
        ioException = t;
        return this;
    }

    @Override
    public void done() {
        if (ListenableFuture.class.isAssignableFrom(proxyiedFuture.getClass())) {
            ListenableFuture.class.cast(proxyiedFuture).done();
        } else {
            Future.class.cast(proxyiedFuture).done();
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return proxyiedFuture.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return proxyiedFuture.isCancelled();
    }

    @Override
    public boolean isDone() {
        return proxyiedFuture.isDone();
    }

    @Override
    public Socket get() throws InterruptedException, ExecutionException {
        proxyiedFuture.get();
        return serializedSocket;
    }

    @Override
    public Socket get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        proxyiedFuture.get(timeout, unit);
        return serializedSocket;
    }
}
