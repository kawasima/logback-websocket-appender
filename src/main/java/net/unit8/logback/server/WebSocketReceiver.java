package net.unit8.logback.server;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.status.ErrorStatus;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusManager;
import ch.qos.logback.core.util.CloseUtil;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * @author kawasima
 */
@ServerEndpoint("/")
public class WebSocketReceiver extends Endpoint {
    @OnOpen
    public void onOpen(final Session session, EndpointConfig config) {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext)) {
            CloseUtil.closeQuietly(session);
            throw new IllegalStateException("LoggerFactory isn't LoggerContext.");
        }
        final LoggerContext context = (LoggerContext) factory;

        session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
            @Override
            public void onMessage(byte[] message) {
                try {
                    ObjectInputStream ois = new ObjectInputStream(
                            new ByteArrayInputStream(message));
                    ILoggingEvent event = (ILoggingEvent) ois.readObject();
                    Logger remoteLogger = context.getLogger(event.getLoggerName());
                    if (remoteLogger.isEnabledFor(event.getLevel())) {
                        remoteLogger.callAppenders(event);
                    }
                } catch (IOException ex) {
                    addStatus(context, new ErrorStatus("IO Error", ex));
                } catch (ClassNotFoundException ex) {
                    addStatus(context, new ErrorStatus("unknown event class", ex));
                }
            }
        });
    }

    public void addStatus(LoggerContext context, Status status) {
        if (context != null) {
            StatusManager sm = context.getStatusManager();
            if (sm != null) {
                sm.add(status);
            }
        }
    }

}
