/*
 * Copyright 2013 Jeanfrancois Arcand
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
import org.atmosphere.wasync.Event;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.FunctionResolver;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.ReplayDecoder;
import org.atmosphere.wasync.util.TypeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TransportsUtil {

    private final static Logger logger = LoggerFactory.getLogger(TransportsUtil.class);

    public static boolean invokeFunction(List<Decoder<? extends Object, ?>> decoders,
                                         List<FunctionWrapper> functions,
                                         Class<?> implementedType,
                                         Object instanceType,
                                         String functionName,
                                         FunctionResolver resolver) {
        return invokeFunction(Event.MESSAGE, decoders, functions, implementedType, instanceType, functionName, resolver);
    }

    public static boolean invokeFunction(Event e,
                                         List<Decoder<? extends Object, ?>> decoders,
                                         List<FunctionWrapper> functions,
                                         Class<?> implementedType,
                                         Object instanceType,
                                         String functionName,
                                         FunctionResolver resolver) {
        boolean hasMatch = false;
        String originalMessage = instanceType == null ? "" : instanceType.toString();

        List<Object> decodedObjects = new CopyOnWriteArrayList<Object>();
        if (instanceType != null) {
            decodedObjects = matchDecoder(e, instanceType, decoders, decodedObjects);
        }

        for (FunctionWrapper wrapper : functions) {
            Function f = wrapper.function();
            Class<?>[] typeArguments = TypeResolver.resolveArguments(f.getClass(), Function.class);
            if (typeArguments.length > 0 && instanceType != null) {
                boolean b = false;
                if (decodedObjects.isEmpty()) {
                    implementedType = instanceType.getClass();
                    b = matchFunction(instanceType, typeArguments, implementedType, resolver, originalMessage, functionName, wrapper, f);
                } else {
                    for (Object o : decodedObjects) {
                        if (!Decoder.Decoded.class.isAssignableFrom(o.getClass())) {
                            b = matchFunction(o, typeArguments, o.getClass(), resolver, originalMessage, functionName, wrapper, f);
                        }
                    }
                }
                if (b) hasMatch = true;
            }
        }

        if (!hasMatch && !e.equals(Event.MESSAGE)) {
            // Since we have no match, most probably because a decoder isn't matching a function or the Event's type, try
            // to match Event type directly with a String.
            // This can happens if a decoder is not behaving properly.
            // instanceType != null because a ReplayDecoder may have interrupted
            for (FunctionWrapper wrapper : functions) {
                Function f = wrapper.function();
                if (wrapper.functionName().equalsIgnoreCase(functionName)) {
                    hasMatch = true;
                    logger.trace("{} .on {}", functionName, instanceType);
                    f.on(originalMessage);
                }
            }
        }

        return hasMatch;
    }

    public static boolean matchFunction(Object instanceType,
                                        Class[] typeArguments,
                                        Class<?> implementedType,
                                        FunctionResolver resolver,
                                        String originalMessage,
                                        Object functionName,
                                        FunctionWrapper wrapper,
                                        Function f) {
        boolean hasMatch = false;
        if (instanceType != null && typeArguments.length > 0 && typeArguments[0].isAssignableFrom(implementedType)) {
            if (resolver.resolve(originalMessage, functionName, wrapper)) {
                hasMatch = true;
                logger.trace("{} .on {}", functionName, instanceType);
                try {
                    f.on(instanceType);
                } catch (Exception e) {
                    logger.warn("Function {} thrown an exception", functionName, e);
                }
            }
        }
        return hasMatch;
    }

    public static List<Object> matchDecoder(Event e, Object instanceType, List<Decoder<? extends Object, ?>> decoders, List<Object> decodedObjects) {
        for (Decoder d : decoders) {
            Class<?>[] typeArguments = TypeResolver.resolveArguments(d.getClass(), Decoder.class);
            if (instanceType != null && typeArguments.length > 0 && typeArguments[0].isAssignableFrom(instanceType.getClass())) {
                boolean replay = ReplayDecoder.class.isAssignableFrom(d.getClass());

                logger.trace("{} is trying to decode {}", d, instanceType);
                Object decoded = d.decode(e, instanceType);

                if (decoded != null && Decoder.Decoded.class.isAssignableFrom(decoded.getClass())) {
                    Decoder.Decoded<?> o = Decoder.Decoded.class.cast(decoded);
                    // The object has been decoded and doesn't need to be dispatched.
                    if (o.action().equals(Decoder.Decoded.ACTION.ABORT)) {
                        logger.trace("Decoder {} fully decoded {}", d, instanceType);
                        decodedObjects.add(o);
                        break;
                    } else {
                        decoded = o.decoded();
                    }
                }

                // The decoded message is a list, so we re-inject.
                if (replay && List.class.isAssignableFrom(decoded.getClass())) {
                    List<Object> l = List.class.cast(decoded);
                    if (l.isEmpty()) {
                        continue;
                    }
                    List<Decoder<? extends Object, ?>> nd = new ArrayList<Decoder<? extends Object, ?>>();
                    boolean add = false;
                    for (Decoder d2 : decoders) {
                        if (d2.equals(d)) {
                            add = true;
                            continue;
                        }

                        if (add) nd.add(d2);
                    }

                    // If no decoder found
                    if (nd.isEmpty()) {
                        return l;
                    }

                    for (Object m : l) {
                        return matchDecoder(e, m, nd, decodedObjects);
                    }
                } else if (decoded != null) {
                    logger.trace("Decoder {} match {}", d, instanceType);
                    decodedObjects.add(decoded);
                }
            }
        }
        return decodedObjects;
    }

}
