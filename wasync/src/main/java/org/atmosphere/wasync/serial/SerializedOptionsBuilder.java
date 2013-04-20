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
package org.atmosphere.wasync.serial;

import org.atmosphere.wasync.OptionsBuilder;

public class SerializedOptionsBuilder extends OptionsBuilder<SerializedOptionsBuilder> {

    private SerializedFireStage serializedFireStage;

    protected SerializedOptionsBuilder() {
        super(SerializedOptionsBuilder.class);
    }

    /**
     * Build an {@link org.atmosphere.wasync.Options}
     *
     * @return {@link org.atmosphere.wasync.Options}
     */
    @Override
    public SerializedOptions build() {
        return new SerializedOptions(this);
    }

    public SerializedFireStage serializedFireStage() {
    	return serializedFireStage;
    }

    public SerializedOptionsBuilder serializedFireStage(SerializedFireStage serializedFireStage) {
        this.serializedFireStage = serializedFireStage;
        return this;
    }
}
