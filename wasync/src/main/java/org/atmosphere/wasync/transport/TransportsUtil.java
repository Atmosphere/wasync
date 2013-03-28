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
package org.atmosphere.wasync.transport;

import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.FunctionResolver;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Transport;
import org.atmosphere.wasync.util.TypeResolver;

import java.util.List;

public class TransportsUtil {

    static void invokeFunction(List<Decoder<? extends Object, ?>> decoders,
                               List<FunctionWrapper> functions,
                               Class<?> implementedType,
                               Object instanceType,
                               String functionName,
                               FunctionResolver resolver) {
        invokeFunction(Transport.EVENT_TYPE.MESSAGE, decoders, functions, implementedType, instanceType, functionName, resolver);
    }

    static void invokeFunction(Transport.EVENT_TYPE e,
                               List<Decoder<? extends Object, ?>> decoders,
                               List<FunctionWrapper> functions,
                               Class<?> implementedType,
                               Object instanceType,
                               String functionName,
                               FunctionResolver resolver) {

        String originalMessage = instanceType.toString();
        for (FunctionWrapper wrapper : functions) {
            Function f = wrapper.function();
            Class<?>[] typeArguments = TypeResolver.resolveArguments(f.getClass(), Function.class);

            if (typeArguments.length > 0 && instanceType != null) {
                instanceType = matchDecoder(e, instanceType, decoders);
                if (instanceType != null) {
                    implementedType = instanceType.getClass();
                }
            }

            if (typeArguments.length > 0 && typeArguments[0].isAssignableFrom(implementedType)) {
                if (resolver.resolve(originalMessage, functionName, wrapper)) {
                    f.on(instanceType);
                }
            }
        }
    }

    static Object matchDecoder(Transport.EVENT_TYPE e, Object instanceType, List<Decoder<? extends Object, ?>> decoders) {
        for (Decoder d : decoders) {
            Class<?>[] typeArguments = TypeResolver.resolveArguments(d.getClass(), Decoder.class);
            if (instanceType != null && typeArguments.length > 0 && typeArguments[0].equals(instanceType.getClass())) {
                // Filter
                instanceType = d.decode(e, instanceType);
            }
        }
        return instanceType;
    }
}
