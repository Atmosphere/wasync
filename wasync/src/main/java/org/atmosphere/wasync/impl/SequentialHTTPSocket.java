package org.atmosphere.wasync.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.atmosphere.wasync.Encoder;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.ISerializedFireStage;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.Transport;
import org.atmosphere.wasync.transport.LongPollingTransport;
import org.atmosphere.wasync.transport.SSETransport;
import org.atmosphere.wasync.transport.StreamTransport;
import org.atmosphere.wasync.transport.TransportNotSupported;
import org.atmosphere.wasync.transport.WebSocketTransport;
import org.atmosphere.wasync.util.ReaderInputStream;
import org.atmosphere.wasync.util.TypeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.SettableFuture;
import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.FluentStringsMap;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import com.ning.http.client.websocket.WebSocket;

public class SequentialHTTPSocket implements Socket {

	private final static Logger logger = LoggerFactory.getLogger(SequentialHTTPSocket.class);

    private volatile Request request;
    private InternalSocket socket;
    private final List<FunctionWrapper> functions = new ArrayList<FunctionWrapper>();
    protected Transport transportInUse;
    private final Options options;
    
    private ISerializedFireStage serializedFireStage;

    public SequentialHTTPSocket(Options options) {
    	this.serializedFireStage = options.serializedFireStage();
    	this.serializedFireStage.setSocket(this);
        this.options = options;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Response> fire(Object data) throws IOException {
        checkState();
        if (transportInUse.status().equals(STATUS.CLOSE) ||
                transportInUse.status().equals(STATUS.ERROR)) {
            throw new IOException("Invalid Socket Status " + transportInUse.status().name());
        }

        return socket.write(request, data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Socket on(Function<? extends Object> function) {
        return on("", function);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Socket on(String functionName, Function<? extends Object> function) {
        functions.add(new FunctionWrapper(functionName, function));
        return this;
    }

    public Socket open(Request request) throws IOException {
        return open(request, -1, TimeUnit.MILLISECONDS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Socket open(Request request, long timeout, TimeUnit tu) throws IOException {
        this.request = request;
        RequestBuilder r = new RequestBuilder();
        r.setUrl(request.uri())
                .setMethod(request.method().name())
                .setHeaders(request.headers())
                .setQueryParameters(decodeQueryString(request));

        List<Transport> transports = getTransport(r, request);

        return connect(r, transports, timeout, tu);
    }

    static FluentStringsMap decodeQueryString(Request request) {
        Map<String, List<String>> c = request.queryString();
        FluentStringsMap f = new FluentStringsMap();
        f.putAll(c);
        return f;
    }

    protected Socket connect(final RequestBuilder r, final List<Transport> transports) throws IOException {
        return connect(r, transports, -1, TimeUnit.MILLISECONDS);
    }

    protected Socket connect(final RequestBuilder r, final List<Transport> transports, final long timeout, final TimeUnit tu) throws IOException {

        if (transports.size() > 0) {
            transportInUse = transports.get(0);
        } else {
            throw new IOException("No suitable transport supported");
        }
        socket = new InternalSocket(options, new DefaultFuture(this));

        functions.add(new FunctionWrapper("", new Function<TransportNotSupported>() {
            @Override
            public void on(TransportNotSupported transportNotSupported) {
                request.transport().remove(0);
                if (request.transport().size() > 0) {
                    try {
                        if (request.queryString().get("X-Atmosphere-Transport") != null) {
                            Request.TRANSPORT rt = request.transport().get(0);
                            String t = rt == Request.TRANSPORT.LONG_POLLING ? "long-polling" : rt.name();
                            request.queryString().put("X-Atmosphere-Transport", Arrays.asList(new String[]{t}));
                        }
                        open(request, timeout, tu);
                    } catch (IOException e) {
                        logger.error("", e);
                    }
                } else {
                    throw new Error("No suitable transport supported by the server");
                }
            }
        }));

        transportInUse.future(socket.future());
        
        r.setUrl(request.uri().replace("ws", "http"));
        options.runtime().prepareRequest(r.build()).execute((AsyncHandler<String>) transportInUse);

        try {
            // TODO: Give a chance to connect and then unlock. With Atmosphere we will received junk at the
            // beginning for streaming and sse, but nothing for long-polling
            socket.future().get(options.waitBeforeUnlocking(), TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            // Swallow  LOG ME
            logger.trace("", t);
        }

        socket = new InternalSocket(options, new DefaultFuture(this));
        
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        // Not connected, but close the underlying AHC.
        if (transportInUse == null) {
            options.runtime().close();
        } else if (socket != null && !transportInUse.status().equals(STATUS.CLOSE)) {
            transportInUse.close();
            socket.close();
        }
    }

    @Override
    public Socket.STATUS status() {
        if (transportInUse == null) {
            return STATUS.CLOSE;
        } else {
            return transportInUse.status();
        }
    }

    protected InternalSocket internalSocket() {
        return socket;
    }

    protected List<Transport> getTransport(RequestBuilder r, Request request) throws IOException {
        List<Transport> transports = new ArrayList<Transport>();

        if (request.transport().size() == 0) {
            transports.add(new WebSocketTransport(r, options, request, functions));
            transports.add(new LongPollingTransport(r, options, request, functions));
        }

        for (Request.TRANSPORT t : request.transport()) {
        	if (t.equals(Request.TRANSPORT.SSE)) {
                transports.add(new SSETransport(r, options, request, functions));
            } else if (t.equals(Request.TRANSPORT.LONG_POLLING)) {
                transports.add(new LongPollingTransport(r, options, request, functions));
            } else if (t.equals(Request.TRANSPORT.STREAMING)) {
                transports.add(new StreamTransport(r, options, request, functions));
            }
        }
        return transports;
    }


    protected final class InternalSocket {

        private final Options options;
        private final DefaultFuture rootFuture;

        public InternalSocket(WebSocket webSocket, Options options, DefaultFuture rootFuture) {
            this.options = options;
            this.rootFuture = rootFuture;
        }

        public InternalSocket(Options options, DefaultFuture rootFuture) {
            this(null, options, rootFuture);
        }

        public DefaultFuture future() {
            return rootFuture;
        }

        public void close() {
            if (!options.isShared() && !options.runtime().isClosed()) {
                options.runtime().closeAsynchronously();
            } else if (options.isShared()) {
                logger.warn("Cannot close underlying AsyncHttpClient because it is shared. Make sure you close it manually.");
            }
        }

        Object invokeEncoder(List<Encoder<? extends Object, ?>> encoders, Object instanceType) {
            for (Encoder e : encoders) {
                Class<?>[] typeArguments = TypeResolver.resolveArguments(e.getClass(), Encoder.class);

                if (typeArguments.length > 0 && typeArguments[0].isAssignableFrom(instanceType.getClass())) {
                    instanceType = e.encode(instanceType);
                }
            }
            return instanceType;
        }

        public Future<Response> write(Request request, Object data) throws IOException {

        	// Execute encoder
            Object encodedPayload = invokeEncoder(request.encoders(), data);
            if (!
            	(InputStream.class.isAssignableFrom(encodedPayload.getClass()) 
            	|| Reader.class.isAssignableFrom(encodedPayload.getClass()) 
            	|| String.class.isAssignableFrom(encodedPayload.getClass()) 
                || byte[].class.isAssignableFrom(encodedPayload.getClass())
                )
               )
            {
            	throw new IllegalStateException("No Encoder for " + data);
            }	        
            
            if (serializedFireStage != null) {
            	SettableFuture<Response> future = SettableFuture.create();
            	serializedFireStage.enqueue(encodedPayload, future);
            	return future;
            } else {
            	return directWrite(encodedPayload);
            }        
            
        }
        
    }
    
    public ListenableFuture<Response> directWrite(Object encodedPayload) throws IOException {
    	// Only for Atmosphere
        if (AtmosphereRequest.class.isAssignableFrom(request.getClass())) {
            request.queryString().put("X-Atmosphere-Transport", Arrays.asList(new String[]{"polling"}));
            request.queryString().remove("X-atmo-protocol");
        }

        AsyncHttpClient.BoundRequestBuilder b = options.runtime().preparePost(request.uri())
                .setHeaders(request.headers())
                .setQueryParameters(decodeQueryString(request))
                .setMethod(Request.METHOD.POST.name());


        if (InputStream.class.isAssignableFrom(encodedPayload.getClass())) {
        	return b.setBody((InputStream) encodedPayload).execute();
        } else if (Reader.class.isAssignableFrom(encodedPayload.getClass())) {
        	return b.setBody(new ReaderInputStream((Reader) encodedPayload)).execute();	                
        } else if (String.class.isAssignableFrom(encodedPayload.getClass())) {
            return b.setBody((String) encodedPayload).execute();
        } else if (byte[].class.isAssignableFrom(encodedPayload.getClass())) {
            return b.setBody((byte[]) encodedPayload).execute();
        } else {
        	throw new AssertionError();            	
        }
    }

    protected Request request() {
        return request;
    }

    void checkState() {
        if (transportInUse == null) {
            throw new IllegalStateException("Invalid Socket Status : Not Connected");
        }
    }
    
}
