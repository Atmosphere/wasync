## wAsync: A WebSockets/HTTP Client Library for Asynchronous Communication.

wAsync is a Java based library allowing asynchronous communication with any WebServer supporting the WebSocket or Http Protocol.
As simple as:

```java

        Client client = AtmosphereClientFactory.getDefault().newclient();

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(targetUrl + "/suspend")
                .encoder(new Encoder<String, Reader>() {        // Stream the request body
                    @Override
                    public Reader encode(String s) {
                        return new StringReader(s);
                    }
                })
                .decoder(new Decoder<String, Reader>() {
                    @Override
                    public Reader decode(String s) {
                        return new StringReader(s);
                    }
                })
                .transport(WEBSOCKET)                        // Try WebSocket
                .transport(LONG_POLLING);                    // Fallback to Long-Polling

        Socket socket = client.create();
        socket.on("message", new Function<String>() {
            @Override
            public void on(Reader r) {
                // Read the response
            }
        }).on(new Function<IOException>() {

            @Override
            public void on(Throwable t) {
                // Some IOException occurred
            }

        }).open(request.build()).fire("echo");
```
You can download the jar or use Maven
```xml
          <dependency>
              <groupId>org.atmosphere</groupId>
              <artifactId>wasync</artifactId>
              <version>1.0.0</version>
          </dependency>

```