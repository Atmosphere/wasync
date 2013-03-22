package org.atmosphere.wasync;

import java.util.ArrayList;
import java.util.Collections;
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
		
		if(!atmosphereRequest.isTrackMessageLength()) {
			if(messagesStringBuilder!=null) {
				messages.add(messagesStringBuilder.toString());
				messagesStringBuilder.setLength(0);
			}
			return messages;
		}
		return AbstractAsyncHandler.trackMessageSize(messagesStringBuilder, atmosphereRequest.getTrackMessageLengthDelimiter());
	}
	
	protected final static List<String> trackMessageSize(final StringBuilder messageStringBuilder, String delimiter) {
		
		if(messageStringBuilder==null) { 
			return Collections.emptyList();
		}
		
		String message = messageStringBuilder.toString();
		ArrayList<String> messages = new ArrayList<String>();

		int messageLength = -1;
		int messageStartIndex = 0;
		int delimiterIndex = -1;
		String singleMessage = null;
		while((delimiterIndex = message.indexOf(delimiter, messageStartIndex))>=0) {
			try {
				messageLength =  Integer.valueOf(message.substring(messageStartIndex, delimiterIndex));
				if(messageLength <= 0) {
					continue;
				}
			} catch(Exception e) {
				continue;
			}
			
			messageStartIndex = delimiterIndex<(message.length()-1) ? delimiterIndex+1 : message.length();
			int lenghtOfRemainingMessage = (message.length()-messageStartIndex);
			singleMessage = message.substring(messageStartIndex, messageLength<=lenghtOfRemainingMessage ? messageStartIndex+messageLength : messageStartIndex+lenghtOfRemainingMessage);

			delimiterIndex = message.indexOf(delimiter, messageStartIndex+messageLength);
			if(delimiterIndex>=0) {
				messageStartIndex = delimiterIndex<(message.length()-1) ? delimiterIndex+1 : (message.length()-1);
			}
			
			if(singleMessage.length() == messageLength) {
				messages.add(singleMessage);
				if(delimiterIndex<0) {
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
