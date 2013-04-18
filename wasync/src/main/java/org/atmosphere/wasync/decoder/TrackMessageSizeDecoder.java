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
import org.atmosphere.wasync.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class TrackMessageSizeDecoder implements ReplayDecoder {

    private final Logger logger = LoggerFactory.getLogger(TrackMessageSizeDecoder.class);

    private final String delimiter;
    private final StringBuffer messagesBuffer = new StringBuffer();

    public TrackMessageSizeDecoder() {
        this.delimiter = "|";
    }

    public TrackMessageSizeDecoder(String delimiter) {
        this.delimiter = delimiter;
    }

    @Override
    public List<String> decode(Event type, String message) {
        if (type.equals(Event.MESSAGE)) {
            ArrayList<String> messages = new ArrayList<String>();

            int messageLength = -1;
            int messageStartIndex = 0;
            int delimiterIndex = -1;
            String singleMessage = null;
            while ((delimiterIndex = message.indexOf(delimiter, messageStartIndex)) >= 0) {
                if (delimiterIndex == messageStartIndex) {
                    messageStartIndex = delimiterIndex + 1;
                    continue;
                }
                try {
                    messageLength = Integer.valueOf(message.substring(messageStartIndex, delimiterIndex));
                    if (messageLength <= 0) {
                        throw new Exception();
                    }
                } catch (Exception e) {
                    logger.error("", e);
                    //discard whole message
                    messagesBuffer.setLength(0);
                    throw new Error("Message format is not as expected for tracking message size"); //this error causes invocation of onThrowable of AsyncHandler if not caught in between
                }

                messageStartIndex = delimiterIndex < (message.length() - 1) ? delimiterIndex + 1 : message.length();
                int lenghtOfRemainingMessage = (message.length() - messageStartIndex);
                singleMessage = message.substring(messageStartIndex, messageLength <= lenghtOfRemainingMessage
                        ? messageStartIndex + messageLength : messageStartIndex + lenghtOfRemainingMessage);

                delimiterIndex = message.indexOf(delimiter, messageStartIndex + messageLength);
                if (delimiterIndex >= 0) {
                    messageStartIndex = delimiterIndex < (message.length() - 1) ? delimiterIndex + 1 : (message.length() - 1);
                }

                if (singleMessage.length() == messageLength) {
                    messages.add(singleMessage);
                    if (delimiterIndex < 0) {
                        messagesBuffer.setLength(0);
                    }
                } else {
                    messagesBuffer.setLength(0);
                    messagesBuffer.append(messageLength).append(delimiter).append(singleMessage);
                }
            }
            return messages;
        } else {
            ArrayList<String> l = new ArrayList<String>();
            l.add(message);
            return l;
        }
    }
}
