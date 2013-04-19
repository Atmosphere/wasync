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

import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.impl.DefaultClient;

/**
 * A {@link org.atmosphere.wasync.Client} that guarantee message delivery order when {@link Socket#fire(Object)} is invoked. Doing:
 * <blockquote><pre>
 * <p/>
 *     socket.fire("message1").fire("message2");
 * </pre></blockquote>
 * means message1 will be send, then message2. By default, wAsync is asynchronous so if you need order use the {@link SerializedClient}
 *
 * @author Christian Bach
 */
public class SerializedClient extends DefaultClient {
	
    protected Socket getSocket(SerializedOptions options) {
    	return new SerializedSocket(options);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SerializedOptionsBuilder newOptionsBuilder() {
        return new SerializedOptionsBuilder();
    }
}
