package org.atmosphere.wasync.transport;

import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.FunctionResolver;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.decoder.TrackMessageSizeDecoder;
import org.testng.annotations.Test;

/**
 * @author Sebastian Lövdahl
 */
public class TransportsUtilTest {

    private static final String FUNCTION_NAME_MESSAGE = "MESSAGE";
    private static final String MESSAGE_SIZE_DELIMITER = "|";

    @Test
    public void testInvokeFunctionWithTrackMessageSizeDecoder_NormalMessages() throws Exception {
        List<String> originalMessages = createMessages(3);
        List<String> modifiableMessages = new ArrayList<>(originalMessages);
        CountDownLatch latch = new CountDownLatch(originalMessages.size());
        List<Decoder<?, ?>> decoders = withTrackMessageSizeDecoder();
        List<FunctionWrapper> functions = withMessageFunction(modifiableMessages, latch);

        for (String message : originalMessages) {
            assertTrue(TransportsUtil.invokeFunction(decoders, functions, String.class, withLengthPrefixed(message), FUNCTION_NAME_MESSAGE, FunctionResolver.DEFAULT));
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(modifiableMessages.isEmpty());
    }

    @Test
    public void testInvokeFunctionWithTrackMessageSizeDecoder_CombinedMessages() throws Exception {
        List<String> originalMessages = createMessages(5);
        List<String> modifiableMessages = new ArrayList<>(originalMessages);
        CountDownLatch latch = new CountDownLatch(modifiableMessages.size());
        List<Decoder<?, ?>> decoders = withTrackMessageSizeDecoder();
        List<FunctionWrapper> functions = withMessageFunction(modifiableMessages, latch);

        String firstMessage = originalMessages.get(0);
        String combinedMessages = "";
        for (String message : originalMessages.subList(1, originalMessages.size())) {
            combinedMessages += withLengthPrefixed(message);
        }
        assertTrue(TransportsUtil.invokeFunction(decoders, functions, String.class, withLengthPrefixed(firstMessage), FUNCTION_NAME_MESSAGE, FunctionResolver.DEFAULT));
        assertTrue(TransportsUtil.invokeFunction(decoders, functions, String.class, combinedMessages, FUNCTION_NAME_MESSAGE, FunctionResolver.DEFAULT));

        assertTrue(latch.await(3, TimeUnit.SECONDS), "latch count was " + latch.getCount());
        assertTrue(modifiableMessages.isEmpty());
    }

    private static List<String> createMessages(int n) {
        List<String> messages = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            messages.add("message" + i);
        }
        return Collections.unmodifiableList(messages);
    }

    private static List<Decoder<?, ?>> withTrackMessageSizeDecoder() {
        List<Decoder<?, ?>> decoders = new ArrayList<>(1);
        decoders.add(new TrackMessageSizeDecoder("|", false));
        return decoders;
    }

    private static List<FunctionWrapper> withMessageFunction(final List<String> messages, final CountDownLatch latch) {
        List<FunctionWrapper> functions = new ArrayList<>(1);
        functions.add(new FunctionWrapper(FUNCTION_NAME_MESSAGE, new Function<String>() {
            @Override
            public void on(String s) {
                Iterator<String> iterator = messages.iterator();
                while (iterator.hasNext()) {
                    if (iterator.next().equals(s)) {
                        iterator.remove();
                    }
                }
                latch.countDown();
            }
        }));
        return functions;
    }

    private static String withLengthPrefixed(String message) {
        return message.length() + MESSAGE_SIZE_DELIMITER + message;
    }
}
