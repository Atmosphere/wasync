package org.atmosphere.tests;

import org.atmosphere.wasync.Request;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class LongPollingTest extends BaseTest {

    @Override
    Request.TRANSPORT transport() {
        return Request.TRANSPORT.LONG_POLLING;
    }

    @Override
    int statusCode() {
        return 200;
    }
}
