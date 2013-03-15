package org.atmosphere.wasync.impl;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.Request.TRANSPORT;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.Transport;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.FluentCaseInsensitiveStringsMap;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.RequestBuilder;


public class AtmosphereSocket extends DefaultSocket {
	
	private Request request = null;
	private String cacheValue = null;
	
	public AtmosphereSocket(AsyncHttpClient asyncHttpClient, Options options) {
        super(asyncHttpClient, options);
    }
	
	private void setCacheHeaders(RequestBuilder r) {
		
		AtmosphereRequest atmosphereRequest = (AtmosphereRequest)request;
		AtmosphereRequest.CACHE cacheType = atmosphereRequest.getCacheType(); 
	
		if(this.cacheValue == null || (this.cacheValue != null && "".equals(this.cacheValue))) {
			this.cacheValue = "0";
		}

		
		switch(cacheType) {
			case HEADER_BROADCAST_CACHE:
				r.setHeader("X-Cache-Date", this.cacheValue);
				break;
			case UUID_BROADCASTER_CACHE:
				r.setHeader("X-Atmosphere-tracking-id", this.cacheValue);
				break;
			case SESSION_BROADCAST_CACHE:
			case NO_BROADCAST_CACHE:
		}

		
	}
	
	
	private void getCacheHeaders(HttpResponseHeaders headers) {
		FluentCaseInsensitiveStringsMap headersMap = headers.getHeaders();
		AtmosphereRequest atmosphereRequest = (AtmosphereRequest)request;
		AtmosphereRequest.CACHE cacheType = atmosphereRequest.getCacheType(); 

		this.cacheValue = null;
		
		switch(cacheType) {
			case HEADER_BROADCAST_CACHE:
				this.cacheValue = headersMap.getFirstValue("X-Cache-Date");
				break;
			case UUID_BROADCASTER_CACHE:
				this.cacheValue = headersMap.getFirstValue("X-Atmosphere-tracking-id");
				break;
			case SESSION_BROADCAST_CACHE:
			case NO_BROADCAST_CACHE:
		}
		
		if(this.cacheValue == null || (this.cacheValue != null && "".equals(this.cacheValue))) {
			this.cacheValue = "0";
		}
		
	}
	
	@Override
	protected Callable<String> getReconnetCallable(InternalSocket socket, RequestBuilder r, List<Transport> transports) {
		if(this.request instanceof AtmosphereRequest) {
			setCacheHeaders(r);
		} else if (this.request != null) {
			throw new RuntimeException("AtmosphereSocket must be used with only AtmosphereRequest/AtmosphereRequestBuilder/AtmosphereClient");
		}
		return super.getReconnetCallable(socket, r, transports);
	}

	@Override
	public Socket open(Request request) throws IOException {
		this.request = request;
		return super.open(request);
	}
	
	@Override
	protected void onHeaderReceived(HttpResponseHeaders headers, RequestBuilder r) {
		getCacheHeaders(headers);
		setCacheHeaders(r);
		super.onHeaderReceived(headers, r);
		return;
	}
	
	@Override
	protected void processOnThrowable(Throwable t, Options options,
			AsyncHttpClient asyncHttpClient, InternalSocket socket,
			RequestBuilder r, List<Transport> transports) {
		super.processOnThrowable(t, options, asyncHttpClient, socket, r, transports);
		if(t instanceof AsyncReconnectException) {
			return; //dont reconnect
		}
		if (options.reconnect()) {
            asyncHttpClient.getConfig().reaper().schedule(getReconnetCallable(socket, r, transports), options.reconnectInSeconds(), TimeUnit.SECONDS);
        }
	}
	
	//int count = 0;
	@Override
	protected boolean processOnBodyPartReceived(HttpResponseBodyPart bodyPart, boolean isFirstMessage) {
	
		String message = new String(bodyPart.getBodyPartBytes());
		
		if(isFirstMessage) {
			TRANSPORT transport = request.transport().get(0);
			switch (transport) {
				case WEBSOCKET:
				case LONG_POLLING:
					break;
				case SSE:
				case STREAMING:
					return false;
			}
		}
		return true;
		
		
	}
	
}
