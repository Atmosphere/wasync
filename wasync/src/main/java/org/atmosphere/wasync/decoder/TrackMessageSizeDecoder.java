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
package org.atmosphere.wasync.decoder;

import org.atmosphere.wasync.Event;
import org.atmosphere.wasync.ReplayDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class TrackMessageSizeDecoder implements ReplayDecoder {

    private final Logger logger = LoggerFactory.getLogger(TrackMessageSizeDecoder.class);

    private final String delimiter;
    private final StringBuffer messagesBuffer = new StringBuffer();
    private final AtomicBoolean skipFirstMessage = new AtomicBoolean();
    private final List<String> empty = Collections.<String>emptyList();

    public TrackMessageSizeDecoder() {
        this.delimiter = "\\|";
    }

    public TrackMessageSizeDecoder(boolean protocolEnabled) {
        this.delimiter = "\\|";
        skipFirstMessage.set(protocolEnabled);
    }

    public TrackMessageSizeDecoder(String delimiter, boolean protocolEnabled) {
        this.delimiter = delimiter;
        skipFirstMessage.set(protocolEnabled);
    }

    @Override
    public List<String> decode(Event type, String message) {
        if (type.equals(Event.MESSAGE)) {

            if (skipFirstMessage.getAndSet(false)) return empty;
            LinkedList<String> messages = new LinkedList<String>();

            message = messagesBuffer.append(message).toString().replace(delimiter, "__");
            messagesBuffer.setLength(0);

            if (message.indexOf("__") != -1) {
                String[] tokens = message.split("__");

                // Skip first.
                int pos = 1;
                int length = Integer.valueOf(tokens[0]);
                String m;
                while (pos < tokens.length) {
                    m = tokens[pos];
                    if (m.length() >= length) {
                        messages.addLast(m.substring(0, length));
                        String t = m.substring(length);
                        if (!t.isEmpty()) {
                            length = Integer.valueOf(t);
                        }
                    } else {
                        if (pos != 1) {
                            messagesBuffer.append(length).append(delimiter).append(m);
                        } else {
                            messagesBuffer.append(message);
                        }
                        break;
                    }
                    pos++;
                }
            }

            return messages;
        } else {
            return empty;
        }
    }
}
