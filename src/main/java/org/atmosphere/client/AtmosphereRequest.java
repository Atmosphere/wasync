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
package org.atmosphere.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AtmosphereRequest extends DefaultRequest {

    protected AtmosphereRequest(DefaultRequest.Builder builder) {
        super(builder);
    }

    public final static class Builder extends DefaultRequest.Builder{

        public Builder() {

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

        public Builder transport(TRANSPORT t) {
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
    }
}
