package org.atmosphere.tests;

import org.atmosphere.wasync.Request;

public class StreamingTest extends BaseTest {

    @Override
    Request.TRANSPORT transport() {
        return Request.TRANSPORT.STREAMING;
    }

    @Override
    int statusCode() {
        return 200;
    }

    @Override
    int notFoundCode() {
        return 404;
    }

    @Override
    int getCount() {
        return 5;
    }
}
