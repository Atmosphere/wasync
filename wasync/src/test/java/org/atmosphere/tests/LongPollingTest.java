package org.atmosphere.tests;

import org.atmosphere.wasync.Request;

public class LongPollingTest extends StreamingTest {

    @Override
    Request.TRANSPORT transport() {
        return Request.TRANSPORT.LONG_POLLING;
    }

    @Override
    int statusCode() {
        return 200;
    }

    @Override
    int notFoundCode() {
        return 404;
    }
}
