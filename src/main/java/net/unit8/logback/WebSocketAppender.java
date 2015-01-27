package net.unit8.logback;

import ch.qos.logback.classic.net.LoggingEventPreSerializationTransformer;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.spi.PreSerializationTransformer;
import ch.qos.logback.core.util.CloseUtil;

import javax.websocket.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URI;
import java.nio.ByteBuffer;

/**
 * @author kawasima
 */
public class WebSocketAppender extends AppenderBase<ILoggingEvent> {
    private static final PreSerializationTransformer<ILoggingEvent> pst =
            new LoggingEventPreSerializationTransformer();

    private URI serverUri;
    private Session session;

    private void started() {
        super.start();
    }

    @Override
    public void start() {
        if (isStarted()) return;

        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        try {
            session = container.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig endpointConfig) {
                    started();
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                }
            }, ClientEndpointConfig.Builder.create().build(), serverUri);
        } catch (Exception ex) {
            addError("Connect server error", ex);
        }
    }

    @Override
    public void stop() {
        if (!isStarted()) return;
        CloseUtil.closeQuietly(session);
        super.stop();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (event == null || !isStarted()) return;

        ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
        RemoteEndpoint.Async remote = session.getAsyncRemote();
        Serializable serEvent = pst.transform(event);
        try {
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(serEvent);
            oos.flush();
            remote.sendBinary(ByteBuffer.wrap(baos.toByteArray()));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

    }

    public void setServerUri(String uri) {
        this.serverUri = URI.create(uri);
    }
}
