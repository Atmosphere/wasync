/*
 * Copyright 2008-2025 Async-IO.org
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

import java.util.HashMap;
import java.util.Optional;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;

public class TrackMessageSizeDecoder implements ReplayDecoder<String, String> {

    private final Logger logger = LoggerFactory.getLogger(TrackMessageSizeDecoder.class);

    private final String delimiter;
    private final StringBuffer messagesBuffer = new StringBuffer();
    private final AtomicBoolean skipFirstMessage = new AtomicBoolean();
    private final Decoded<List<String>> empty = new Decoded<>(Collections.emptyList());

    public TrackMessageSizeDecoder() {
        this.delimiter = String.format("\\%s", "|");
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
    public Decoded<List<String>> decode(Event eventType, String message) {
        return decodeMessageIfEventIsTypeMessage(eventType, message);
    }

    private Decoded<List<String>> decodeMessageIfEventIsTypeMessage(Event eventType, String message) {
        if (isEventTypeNotMessageOrFirstMessageSkipped(eventType)) {
            return empty;
        }

        return decodeMessage().apply(message);
    }

    private boolean isEventTypeNotMessageOrFirstMessageSkipped(Event eventType) {
        return !isMessageEvent(eventType) || skipFirstMessage.getAndSet(false);
    }

    private Function<String, Decoded<List<String>>> decodeMessage() {
        return constructDecodedListFromMessageList()
                .compose(constructListOfMessages())
                .compose(separateSizeAndPayload())
                .compose(assembleIncompleteMessage());
    }

    private boolean isMessageEvent(Event event) {
        return event.equals(Event.MESSAGE);
    }

    private Function<String, String> assembleIncompleteMessage() {
        return (message) -> {
            message = messagesBuffer.append(message).toString();
            messagesBuffer.setLength(0);
            return message;
        };
    }

    private Function<String, String[]> separateSizeAndPayload() {
        return (message) -> message.split(String.format("\\%s", delimiter), 2);
    }

    private Function<String[], List<String>> constructListOfMessages() {
        return (message) -> {
            if (doesMessageContainMessageLength().and(messageContainDelimiter()).test(message)) {
                return addCompleteMessageToListRecursive()
                        .compose(readMessageContentIntoMap())
                        .apply(message);
            } else {
                messagesBuffer.append(message[0]);
                return new LinkedList<>();
            }
        };
    }

    private Predicate<String[]> doesMessageContainMessageLength() {
        return messageList -> convertPayloadSizeFromStringToInt(messageList[0]).isPresent();
    }

    private Predicate<String[]> messageContainDelimiter() {
        return messageList -> messageList.length > 1;
    }

    private Function<List<String>, Decoded<List<String>>> constructDecodedListFromMessageList() {
        return Decoded::new;
    }

    private Function<Map<String, String>, List<String>> addCompleteMessageToListRecursive() {
        return extractedAndRemainingMessage -> {
            Optional<Integer> payloadSize = convertPayloadSizeFromStringToInt(extractedAndRemainingMessage.get("payloadSize"));
            return payloadSize
                    .map(messageLength -> convertMappedMessagesToMessageList(extractedAndRemainingMessage, messageLength))
                    .orElseGet(LinkedList::new);
        };
    }

    private List<String> convertMappedMessagesToMessageList(Map<String, String> extractedAndRemainingMessage, Integer messageLength) {
        List<String> messageList = new LinkedList<>();

        if (extractedAndRemainingMessage.get("extractedMessage").length() == messageLength) {
            messageList.add(extractedAndRemainingMessage.get("extractedMessage"));
        }

        if (!extractedAndRemainingMessage.get("remainingMessage").isEmpty()) {
            messageList.addAll(
                    constructListOfMessages()
                            .compose(separateSizeAndPayload())
                            .apply(extractedAndRemainingMessage.get("remainingMessage"))
            );
        }
        return messageList;
    }

    private Optional<Integer> convertPayloadSizeFromStringToInt(String message) {
        try {
            return Optional.of(Integer.valueOf(message));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Function<String[], Map<String, String>> readMessageContentIntoMap() {
        return (message) -> {
            Map<String, String> messageMap = new HashMap<>();
            Optional<Integer> payloadSize = convertPayloadSizeFromStringToInt(message[0]);

            payloadSize.ifPresent(messageLength -> {
                messageMap.put("payloadSize", message[0]);
                if (message[1].length() >= messageLength) {
                    messageMap.put("extractedMessage", message[1].substring(0, messageLength));
                    messageMap.put("remainingMessage", message[1].substring(messageLength));
                } else {
                    messagesBuffer.append(messageMap.get("payloadSize")).append(delimiter).append(message[1]);
                    messageMap.put("extractedMessage", message[1]);
                    messageMap.put("remainingMessage", "");
                }
            });

            return messageMap;
        };
    }
}
