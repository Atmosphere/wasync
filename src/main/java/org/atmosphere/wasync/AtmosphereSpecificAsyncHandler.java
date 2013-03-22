package org.atmosphere.wasync;

import org.atmosphere.wasync.impl.AtmosphereRequest;

import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.AsyncHandler.STATE;

/**
 * Any transport that supports atmosphere must implement {@link AtmosphereSpecificAsyncHandler} interface.
 * {@link AtmosphereSpecificAsyncHandler} methods are not thread safe.
 *
 */
public interface AtmosphereSpecificAsyncHandler {
	public abstract STATE onBodyPartReceived(HttpResponseBodyPart bodyPart, final StringBuilder messagesStringBuilder, final AtmosphereRequest atmosphereRequest) throws Exception;

}
