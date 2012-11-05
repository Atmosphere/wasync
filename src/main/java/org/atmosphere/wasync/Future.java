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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class Future implements java.util.concurrent.Future<Socket> {

    private final Socket socket;
    private final CountDownLatch latch = new CountDownLatch(1);
    private final AtomicBoolean done = new AtomicBoolean(false);

    public Future(Socket socket) {
        this.socket = socket;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        latch.countDown();
        return true;
    }

    @Override
    public boolean isCancelled() {
        return latch.getCount() == 0;
    }

    @Override
    public boolean isDone() {
        return done.get();
    }

    // TODO: Not public
    public Future done(){
        done.set(true);
        latch.countDown();
        return this;
    }

    @Override
    public Socket get() throws InterruptedException, ExecutionException {
        latch.await();
        return socket;
    }

    @Override
    public Socket get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        latch.await(timeout, unit);
        return socket;
    }

    protected Socket socket() {
        return socket;
    }
}
