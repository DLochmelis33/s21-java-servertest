package ru.hse.servertest;

import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static ru.hse.servertest.Util.bytesFromInt;
import static ru.hse.servertest.Util.intFromBytes;

public class BlockingServer implements Server {

    private static class ClientHolder {
        public final Socket socket;
        public final ExecutorService receivingExecutor, sendingExecutor;

        public ClientHolder(Socket socket, ExecutorService receivingExecutor, ExecutorService sendingExecutor) {
            this.socket = socket;
            this.receivingExecutor = receivingExecutor;
            this.sendingExecutor = sendingExecutor;
        }
    }

    private ServerSocket serverSocket;
    private final ExecutorService connectionExecutor = Executors.newSingleThreadExecutor(App.threadFactory);
    private final ExecutorService tasksExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), App.threadFactory);
    private final AtomicBoolean isWorking = new AtomicBoolean(false);
    private final Set<ClientHolder> activeClients = ConcurrentHashMap.newKeySet();

    // for non-fatal errors
    // we can set server behavior - either disconnect broken client and continue or stop altogether
    private void error(String msg, Throwable e) {
        if (isWorking.get() && !Tester.isFinishing.get()) {
            Log.e(msg, e);
            throw new RuntimeException(e);
        } // else is part of shutting down
    }

    @Override
    public void start(int port) {
        if (serverSocket != null) {
            throw new IllegalStateException("starting server twice is disallowed");
        }
        try {
            serverSocket = new ServerSocket(port);
            isWorking.set(true);
            Util.submit(this::connectingJob, connectionExecutor);
            Log.d("server: started");
        } catch (IOException e) {
            throw new IllegalStateException("cannot start server", e);
        }
    }

    private void connectingJob() {
        while (isWorking.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                Log.d("server: new client accepted");

                ExecutorService sendingExecutor = Executors.newSingleThreadExecutor();
                ExecutorService receivingExecutor = Executors.newSingleThreadExecutor();

                ClientHolder client = new ClientHolder(clientSocket, receivingExecutor, sendingExecutor);
                activeClients.add(client);
                Util.submit(() -> receivingJob(client),receivingExecutor);

            } catch (IOException e) {
                error("cannot accept new client", e);
            }
        }
    }

    private void receivingJob(ClientHolder client) {
        while (!Thread.interrupted() && isWorking.get()) {
            try {
                InputStream inputStream = client.socket.getInputStream();
                int requestLength = intFromBytes(inputStream.readNBytes(4));
                byte[] requestBytes = inputStream.readNBytes(requestLength);
                Log.d("server: request received");

                ArrayToSort payload = ArrayToSort.parseFrom(requestBytes);
                Util.submit(() -> doTask(client, payload), tasksExecutor);

            } catch (IOException | IllegalArgumentException e) {
                error("server: receiving failed, disconnecting", e);
                disconnect(client);
            }
        }
    }

    private void doTask(ClientHolder client, ArrayToSort payload) {
        SortedArray sortedArray = Tester.process(payload);
        Log.d("server: payload processed");
        Util.submit(() -> writingJob(client, sortedArray), client.sendingExecutor);
    }

    private void writingJob(ClientHolder client, SortedArray sortedArray) {
        try {
            Log.d("server: writing response");
            OutputStream outputStream = client.socket.getOutputStream();
            byte[] data = sortedArray.toByteArray();
            outputStream.write(bytesFromInt(data.length));
            outputStream.write(data);
            Log.d("server: written response");

        } catch (IOException e) {
            error("server: writing failed, disconnecting", e);
            disconnect(client);
        }
    }

    private void disconnect(ClientHolder client) {
        disconnectNoRemove(client);
        activeClients.remove(client);
    }

    private void disconnectNoRemove(ClientHolder client) {
        try {
            client.socket.close();
        } catch (IOException e) {
            // ignored
        }
        client.receivingExecutor.shutdownNow();
        client.sendingExecutor.shutdownNow();
    }

    @Override
    public synchronized void stop() {
        Log.d("server: stopping");
        isWorking.set(false);

        try {
            serverSocket.close();
        } catch (IOException e) {
            // ignored
        }

        connectionExecutor.shutdownNow();
        tasksExecutor.shutdownNow();

        for (Iterator<ClientHolder> iter = activeClients.iterator(); iter.hasNext(); ) {
            disconnectNoRemove(iter.next());
            iter.remove();
        }
        activeClients.clear();

        Log.d("server: stopped");
    }

}
