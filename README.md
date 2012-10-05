# Goal
Create a WebSocket/HTTP Client which support the same set of functionality than atmosphere.js. For example, transport fallback should be supported. The API will be asynchronous and will support callbacks and Future. The API will build on top of the [AHC library](https://github.com/sonatype/async-http-client)
# Functionality

## Extensions
Developed under the Atmosphere's umbrella, the client library should also works with with other framework like node.js, socketio protocol, cometd, etc. An Extension/PlugIn API will be defined so framework specific information can be added. For example, The Atmosphere's server needs some specifics headers (X-Cache-Date, X-Atmosphere-Transport, etc.) and that information will be provided as an extension.

## Transports supported
The following transports will be supported. 
* WebSockets
* Server Side Events
* Long-Polling
* JSONP
* Http Streaming
* Normal HTTP

### Response's Decoder
The client will support the concepts of decoder, which will allow an application to write callback that gets invoked with a decoded response. The Callback API will then be invoked using a decoded response.

### Request's Encoder
The client will support the concepts of encoder, which will allow an application to encode message before the get send. 

### Request/Response Interceptor
The client will support the concept of AtmosphereInterceptor, e.g you can intercept/filter the request's message before they get delivered to encoders and then sent to the remove server. You can also intercept/filter response's messages before they are delivered to decoders and callback.

**NEEDS DECISION**: Completely new Filter API (like the one in AHC) or AtmosphereInterceptor

### AtmosphereHandler/Callback (or Handler)
Callbacks will be available for
* receiving message
* transport failure events
* open events
* close events
* connection errors events
* reconnect events
* customizable events
The API could be build around the current [OnMessage](https://github.com/Atmosphere/atmosphere/blob/master/modules/cpr/src/main/java/org/atmosphere/handler/OnMessage.java#L68) API. The idea is to be able to write callback/AtmosphereHandler that can be used on both client and server side.

**NEEDS DECISION:** Completely new Callback API or AtmosphereHandler

### Broadcaster
Broadcasters will works the same way they work on the server side, achieving the same goal.

### Options
Supported options:
    * various timeout: connect, request duration, response time, etc.
    * list of transport to use (start with the first one, try the second in case of failure)
    * etc (probably expose all AHC options?)

### Code Sample
## Basic request
```java
   Options b = AtmosphereClient.options();
   b.transport("ws")
    .fallbackTransport("sse")
    .fallbackTransport("long-polling)
    .url("http://somewhere.com")
    .method("POST")
    .encoder(JacksonEncoder.class)
    .decoder(JacksonDecoder.class);

   AtmosphereClient client = AtmosphereClientFactory.newClient(b.build());
   Future<AtmosphereResource> f = client.send(data)   
    .on("message",new CallBack<Data>() {
            @Override
            public void on(MessageEvent<Data> m) { ... }})
    .on("close", new CloseCallback() {
            @Override
            public void on(CloseEvent e) {...}})
    .on("reconnect", new ReconnectCallBack() {
            @Override
            public void on(ReconnectEvent e) {...}}
    );
```
Encoder
```java
@Encoder
public class JacksonEncoder implements Encoder<Data> {
     final ObjectMapper mapper = new ObjectMapper();
     // Data is from the request's body
     public byte[] encode(Data data) {
         return mapper.writeValueAsBytes(data);
     }
}
```
Decoder
```java
@Decoder
public class JacksonDecoder implements Decoder<Data> {
     final ObjectMapper mapper = new ObjectMapper();
     public Data decode(byte[] responseBody) {
         return mapper.readValue(responseBody, Data.class);
     }
}
```
AtmosphereInterceptor
```java
@AtmosphereInterceptor
public class ClientAtmosphereInterceptor implements AtmosphereInterceptor {

    public Action intercept(AtmosphereResource r) {
        // Do something, add headers, query string etc.
        return Action.CONTINUE;
    }
}
```
```
