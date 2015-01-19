logback-websocket-appender
==========================

The appender via websocket for logback.

## Usage

logback.xml

```xml
<appender name="websocket" class="net.unit8.logback.WebSocketAppender">
  <serverUri>ws://[host]:[port]/</serverUri>
</appender>
```