/*
 * Copyright 2012 Jeanfrancois Arcand
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
package org.atmosphere.wasync.impl;

import java.util.ArrayList;
import java.util.List;

import org.atmosphere.wasync.RequestBuilder;

public class AtmosphereRequest extends DefaultRequest {
	
	public enum CACHE {HEADER_BROADCAST_CACHE, UUID_BROADCASTER_CACHE, SESSION_BROADCAST_CACHE, NO_BROADCAST_CACHE};

    protected AtmosphereRequest(DefaultRequestBuilder builder) {
        super(builder);
    }
    
    public AtmosphereRequest.CACHE getCacheType() {
		return ((AtmosphereRequestBuilder)builder).getCacheType();
	}
    
    public boolean isTrackMessageLength() {
		return ((AtmosphereRequestBuilder)builder).isTrackeMessageLength();
	}
    
	public String getTrackMessageLengthDelimiter() {
		return ((AtmosphereRequestBuilder)builder).getTrackMessageLengthDelimiter();
	}
    
    public static class AtmosphereRequestBuilder extends DefaultRequestBuilder {
    	
    	private CACHE cacheType = CACHE.NO_BROADCAST_CACHE;	
    	private boolean trackMessageLength = false;
    	private String trackMessageLengthDelimiter = "|";

        public AtmosphereRequestBuilder() {

            List<String> l = new ArrayList<String>();
            l.add("1.0");
            headers.put("X-Atmosphere-Framework", l);

            l = new ArrayList<String>();
            l.add("0");
            headers.put("X-Atmosphere-tracking-id", l);

            l = new ArrayList<String>();
            l.add("0");
            headers.put("X-Cache-Date", l);
        }

        private CACHE getCacheType() {
			return cacheType;
		}
        
        public RequestBuilder transport(TRANSPORT t) {
            List<String> l = new ArrayList<String>();
            if (t.equals(TRANSPORT.LONG_POLLING)) {
                l.add("long-polling");
            } else {
                l.add(t.name());
            }

            headers.put("X-Atmosphere-Transport", l);
            transports.add(t);
            return this;
        }
        
        public AtmosphereRequestBuilder cache(CACHE c) {  
        	this.cacheType = c;
        	return this;
        }
        
        public AtmosphereRequestBuilder trackMessageLength(boolean trackMessageLength) {  
        	this.trackMessageLength = trackMessageLength;
        	return this;
        }
        
        public AtmosphereRequestBuilder trackMessageLengthDelimiter(String trackMessageLengthDelimiter) {  
        	this.trackMessageLengthDelimiter = trackMessageLengthDelimiter;
        	return this;
        }

        private boolean isTrackeMessageLength() {
    		return trackMessageLength;
    	}
        
        private String getTrackMessageLengthDelimiter() {
			return trackMessageLengthDelimiter;
		}

        @Override
        public AtmosphereRequest build() {
            return new AtmosphereRequest(this);
        }
    }


}
