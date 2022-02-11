/*
 * Copyright 2008-2022 Async-IO.org
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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.atmosphere.wasync.Event;
import org.atmosphere.wasync.ReplayDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrackMessageSizeDecoder implements ReplayDecoder<String, String> {

    private final Logger logger = LoggerFactory.getLogger(TrackMessageSizeDecoder.class);

    private final String delimiter;
    private final StringBuffer messagesBuffer = new StringBuffer();
    private final AtomicBoolean skipFirstMessage = new AtomicBoolean();
    private final Decoded<List<String>> empty = new Decoded<List<String>>(Collections.<String>emptyList());

    private final static String charReplacement = "!!&;_!!";

    public TrackMessageSizeDecoder() {
        this.delimiter = "|";
    }

    public TrackMessageSizeDecoder(boolean protocolEnabled) {
        this.delimiter = "|";
        skipFirstMessage.set(protocolEnabled);
    }

    public TrackMessageSizeDecoder(String delimiter, boolean protocolEnabled) {
        this.delimiter = delimiter;
        skipFirstMessage.set(protocolEnabled);
    }

    @Override
    public Decoded<List<String>> decode(Event type, String message) {
        if (type.equals(Event.MESSAGE)) {

            if (skipFirstMessage.getAndSet(false)) return empty;
            Decoded<List<String>> messages = new Decoded<List<String>>(new LinkedList<String>());

            message = messagesBuffer.append(message).toString().replace(delimiter, charReplacement);
            messagesBuffer.setLength(0);

            if (message.indexOf(charReplacement) != -1) {
                String[] tokens = message.split(charReplacement);

                // Skip first.
                int pos = 1;
                int length = Integer.valueOf(tokens[0]);
                String m;
                while (pos < tokens.length) {
                    m = tokens[pos];
                    if (m.length() >= length) {
                    	messages.decoded().add(m.substring(0, length));
                        String t = m.substring(length);
                        if (t.length() > 0) {
                            length = Integer.valueOf(t);
                        }
                    } else {
                        if (pos != 1) {
                            messagesBuffer.append(length).append(delimiter).append(m);
                        } else {
                            messagesBuffer.append(message);
                        }
                        if (messages.decoded().size() > 0) {
                        	return messages;
                        } else {
                        	return new Decoded<List<String>>(new LinkedList<String>(), Decoded.ACTION.ABORT);
                        }
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
