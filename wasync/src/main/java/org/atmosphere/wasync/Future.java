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

import java.io.IOException;

/**
 * An internal {@link Future} implementation used by {@link Transport} to notify {@link Socket} when the
 * transport has connected and available.
 *
 * @author Jeanfrancois Arcand
 */
public interface Future extends java.util.concurrent.Future<Socket> {
    /**
     * Send data to the remote Server.
     * @param message the message to fire
     * @return a {@link Future}
     * @throws java.io.IOException
     */
    Future fire(Object message) throws IOException;
    /**
     * Mark the future done.
     * @return a {@link Future}
     */
    Future done();
}
