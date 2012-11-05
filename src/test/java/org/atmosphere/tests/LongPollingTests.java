package org.atmosphere.tests;

import org.atmosphere.client.AtmosphereClientFactory;
import org.atmosphere.client.AtmosphereRequest;
import org.atmosphere.client.Client;
import org.atmosphere.client.Decoder;
import org.atmosphere.client.Encoder;
import org.atmosphere.client.Function;
import org.atmosphere.client.Request;
import org.atmosphere.client.Socket;
import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.nettosphere.Config;
import org.atmosphere.nettosphere.Nettosphere;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class LongPollingTests extends BaseTests{

    @Override
    Request.TRANSPORT transport() {
        return Request.TRANSPORT.LONG_POLLING;
    }

    @Override
    int statusCode() {
        return 200;
    }
}
