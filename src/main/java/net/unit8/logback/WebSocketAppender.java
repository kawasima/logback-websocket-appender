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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;

/**
 * @author kawasima
 */
public class WebSocketAppender extends AppenderBase<ILoggingEvent> {
    private static final PreSerializationTransformer<ILoggingEvent> pst =
            new LoggingEventPreSerializationTransformer();

    private URI serverUri;
    private int queueSize = 0;
    private Session session;
    private BlockingQueue<ILoggingEvent> queue;
    private Future<?> task;

    private void started() {
        super.start();
    }

    @Override
    public void start() {
        if (isStarted()) return;
        final WebSocketAppender self = this;

        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        try {
            session = container.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig endpointConfig) {
                    started();
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    System.out.println("close log: " + closeReason);
                }
            }, ClientEndpointConfig.Builder.create().build(), serverUri);
            queue = newBlockingQueue(queueSize);
            task = self.getContext().getExecutorService().submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        while(!Thread.currentThread().isInterrupted()) {
                            dispatchEvents();
                        }
                    } catch (InterruptedException ex) {
                        assert true;
                    } catch (IOException ex) {
                        addError("I/O error while sending logs.", ex);
                    }
                    addInfo("shutting down");
                }
            });
        } catch (Exception ex) {
            addError("Connect server error", ex);
        }
    }

    @Override
    public void stop() {
        if (!isStarted()) return;
        CloseUtil.closeQuietly(session);
        task.cancel(true);
        super.stop();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (event == null || !isStarted()) return;
        addInfo("append: " + event);
        queue.offer(event);
    }

    private void dispatchEvents() throws InterruptedException, IOException {
        RemoteEndpoint.Basic remote = session.getBasicRemote();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);

        while (true) {
            ILoggingEvent event = queue.take();
            Serializable serEvent = pst.transform(event);
            baos.reset();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(serEvent);
            oos.flush();
            remote.sendBinary(ByteBuffer.wrap(baos.toByteArray()));
        }
    }

    BlockingQueue<ILoggingEvent> newBlockingQueue(int queueSize) {
        return queueSize <= 0 ?
                new SynchronousQueue<ILoggingEvent>() : new ArrayBlockingQueue<ILoggingEvent>(queueSize);
    }

    public void setServerUri(String uri) {
        this.serverUri = URI.create(uri);
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }
}
