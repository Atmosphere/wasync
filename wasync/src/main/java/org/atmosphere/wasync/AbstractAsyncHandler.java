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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.atmosphere.wasync.impl.AtmosphereRequest;

import com.ning.http.client.AsyncHandler;

public abstract class AbstractAsyncHandler<T> implements AsyncHandler<T>, AtmosphereSpecificAsyncHandler {

    protected List<String> atmosphereClientSideHandler(final StringBuilder messagesStringBuilder, final AtmosphereRequest atmosphereRequest) {
        List<String> messages = AbstractAsyncHandler.trackMessageSize(messagesStringBuilder, atmosphereRequest);
        return messages;
    }

    protected final static List<String> trackMessageSize(final StringBuilder messagesStringBuilder, final AtmosphereRequest atmosphereRequest) {

        List<String> messages = new ArrayList<String>();

        if (!atmosphereRequest.isTrackMessageLength()) {
            if (messagesStringBuilder != null) {
                messages.add(messagesStringBuilder.toString());
                messagesStringBuilder.setLength(0);
            }
            return messages;
        }
        return AbstractAsyncHandler.trackMessageSize(messagesStringBuilder, atmosphereRequest.getTrackMessageLengthDelimiter());
    }

    protected final static List<String> trackMessageSize(final StringBuilder messageStringBuilder, String delimiter) {

        if (messageStringBuilder == null) {
            return Collections.emptyList();
        }

        String message = messageStringBuilder.toString();
        ArrayList<String> messages = new ArrayList<String>();

        int messageLength = -1;
        int messageStartIndex = 0;
        int delimiterIndex = -1;
        String singleMessage = null;
        while ((delimiterIndex = message.indexOf(delimiter, messageStartIndex)) >= 0) {
        	if(delimiterIndex==messageStartIndex) {
        		messageStartIndex = delimiterIndex+1;
        		continue;
        	}
            try {
                messageLength = Integer.valueOf(message.substring(messageStartIndex, delimiterIndex));
                if (messageLength <= 0) {
                    throw new Exception();
                }
            } catch (Exception e) {
            	//discard whole message
    			messageStringBuilder.setLength(0);
    			throw new Error("Message format is not as expected for tracking message size"); //this error causes invocation of onThrowable of AsyncHandler if not caught in between
            }

            messageStartIndex = delimiterIndex < (message.length() - 1) ? delimiterIndex + 1 : message.length();
            int lenghtOfRemainingMessage = (message.length() - messageStartIndex);
            singleMessage = message.substring(messageStartIndex, messageLength <= lenghtOfRemainingMessage ? messageStartIndex + messageLength : messageStartIndex + lenghtOfRemainingMessage);

            delimiterIndex = message.indexOf(delimiter, messageStartIndex + messageLength);
            if (delimiterIndex >= 0) {
                messageStartIndex = delimiterIndex < (message.length() - 1) ? delimiterIndex + 1 : (message.length() - 1);
            }

            if (singleMessage.length() == messageLength) {
                messages.add(singleMessage);
                if (delimiterIndex < 0) {
                    messageStringBuilder.setLength(0);
                }
            } else {
                messageStringBuilder.setLength(0);
                messageStringBuilder.append(messageLength).append(delimiter).append(singleMessage);
            }
        }
        return messages;
    }
    
}
