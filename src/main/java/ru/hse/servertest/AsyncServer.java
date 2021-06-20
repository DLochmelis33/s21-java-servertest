package ru.hse.servertest;

import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncServer implements Server {

    public final static int BUFFER_CAPACITY = 1 << 18; // 256 KB

    private static class ClientWrapper {
        private final AsynchronousSocketChannel channel;
        private final ByteBuffer readingBuffer;
        private final ByteBuffer writingBuffer;
        private int readingByteCnt;
        private int msgLength;
        private final AtomicInteger writingByteCnt = new AtomicInteger(0); // this can be modified from different threads

        private enum IOState {
            LENGTH, DATA
        }

        private IOState readingState = IOState.LENGTH;

        public ClientWrapper(AsynchronousSocketChannel channel) {
            this.channel = channel;
            readingBuffer = ByteBuffer.allocate(BUFFER_CAPACITY);
            writingBuffer = ByteBuffer.allocate(BUFFER_CAPACITY);
        }
    }

    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), App.threadFactory);
    private final AsynchronousChannelGroup channelGroup;
    private final AsynchronousServerSocketChannel serverSocketChannel;

    public AsyncServer() {
        try {
            channelGroup = AsynchronousChannelGroup.withThreadPool(executor);
            serverSocketChannel = AsynchronousServerSocketChannel.open(channelGroup);
        } catch (IOException e) {
            throw new IllegalStateException("cannot create server object", e);
        }
    }

    private final AtomicBoolean isWorking = new AtomicBoolean(false);
    private final Set<ClientWrapper> activeClients = ConcurrentHashMap.newKeySet();

    private void error(String msg, Throwable err) {
        if (isWorking.get()) {
            Log.e(msg, err);
        } // else ignore
    }

    @Override
    public void start(int port) {
        try {
            serverSocketChannel.bind(new InetSocketAddress(Tester.SERVER_IP, Tester.SERVER_PORT));
            serverSocketChannel.accept(null, new ConnectionHandler());
        } catch (IOException e) {
            throw new IllegalStateException("cannot start server", e);
        }
    }

    private class ConnectionHandler implements CompletionHandler<AsynchronousSocketChannel, Object> {
        @Override
        public void completed(AsynchronousSocketChannel result, Object attachment) {
            ClientWrapper client = new ClientWrapper(result);
            result.read(client.readingBuffer, client, new ReadingHandler());
            Log.d("server: new client accepted");
            serverSocketChannel.accept(attachment, this);
        }

        @Override
        public void failed(Throwable exc, Object attachment) {
            error("server: cannot connect new client", exc);
            serverSocketChannel.accept(attachment, this);
        }
    }

    private class ReadingHandler implements CompletionHandler<Integer, ClientWrapper> {
        @Override
        public void completed(Integer result, ClientWrapper client) {
            if (result < 0) {
                // end of stream
                disconnect(client);
                return;
            }
            Log.d("server: read something, len=" + result);
            client.readingByteCnt += result;
            client.readingBuffer.flip();
            handleReading(client);
            client.channel.read(client.readingBuffer, client, this);
        }

        private void handleReading(ClientWrapper client) {
            switch (client.readingState) {
                case LENGTH:
                    if (client.readingByteCnt >= 4) {
                        client.msgLength = client.readingBuffer.getInt(); // flip?
                        client.readingBuffer.compact();
                        Log.d("server: read msgLength=" + client.msgLength);

                        client.readingState = ClientWrapper.IOState.DATA;
                        client.readingByteCnt -= 4;
                        if (client.readingByteCnt > 0) {
                            handleReading(client); // recursively read data
                        }
                    }
                    break;
                case DATA:
                    if (client.readingByteCnt >= client.msgLength) {
                        byte[] msgBytes = new byte[client.msgLength];
                        client.readingBuffer.get(msgBytes).compact(); // flip?
                        Log.d("server: read entire request");

                        executor.submit(() -> {
                            try {
                                Log.d("server: submitted task");
                                ArrayToSort payload = ArrayToSort.parseFrom(msgBytes);
                                doTask(client, payload);
                            } catch (InvalidProtocolBufferException e) {
                                error("server: cannot parse protobuf message, disconnecting client", e);
                                disconnect(client);
                            }
                        });

                        client.readingState = ClientWrapper.IOState.LENGTH;
                        client.readingByteCnt -= client.msgLength;
                        if (client.readingByteCnt > 0) {
                            handleReading(client);
                        }
                    }
                    break;
            }
        }

        @Override
        public void failed(Throwable exc, ClientWrapper client) {
            error("server: reading failed, disconnecting client", exc);
            disconnect(client);
        }
    }

    private void doTask(ClientWrapper client, ArrayToSort payload) {
        Log.d("server: before processing");
        byte[] responseBytes = Tester.process(payload).toByteArray();
        Log.d("server: processed payload");
        client.writingBuffer.putInt(responseBytes.length);
        client.writingBuffer.put(responseBytes);
        client.writingByteCnt.addAndGet(responseBytes.length + 4);
        client.writingBuffer.flip(); // flip?
        client.channel.write(client.writingBuffer, client, new WritingHandler());
    }

    private class WritingHandler implements CompletionHandler<Integer, ClientWrapper> {
        @Override
        public void completed(Integer result, ClientWrapper client) {
            Log.d("server: written something, len=" + result);
            client.writingByteCnt.addAndGet(-result); // subtract?...
            if (client.writingByteCnt.get() < 0) {
                failed(new IllegalStateException("written more bytes than there were messages?"), client);
                return;
            }
            if (client.writingByteCnt.get() == 0) {
                // successfully written
                Log.d("server: written entire response(s)");
                return;
            }
            client.writingBuffer.compact();
            client.channel.write(client.writingBuffer, client, this);
        }

        @Override
        public void failed(Throwable exc, ClientWrapper client) {
            error("server: writing failed, disconnecting client", exc);
        }
    }

    private void disconnect(ClientWrapper client) {
        disconnectNoRemove(client);
        activeClients.remove(client);
    }

    private void disconnectNoRemove(ClientWrapper client) {
        try {
            client.channel.close();
        } catch (IOException e) {
            // ignored
        }
    }

    @Override
    public void stop() {
        isWorking.set(false);
        try {
            serverSocketChannel.close();
        } catch (IOException e) {
            // ignored
        }
        try {
            channelGroup.shutdownNow();
        } catch (IOException e) {
            // ignored
        }
        executor.shutdownNow();

        for (Iterator<ClientWrapper> iter = activeClients.iterator(); iter.hasNext(); ) {
            disconnectNoRemove(iter.next());
            iter.remove();
        }
    }
}
