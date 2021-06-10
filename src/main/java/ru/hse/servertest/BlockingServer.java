package ru.hse.servertest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static ru.hse.servertest.Util.bytesFromInt;
import static ru.hse.servertest.Util.intFromBytes;

public class BlockingServer implements Server {

    private ServerSocket serverSocket;
    private final ExecutorService connectionExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService tasksExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final AtomicBoolean isWorking = new AtomicBoolean(false);
    private final Map<Socket, Thread> receiverThreads = new HashMap<>();
    private final Map<Socket, ExecutorService> senderExecutors = new HashMap<>();
    private final Set<Socket> activeSockets = new HashSet<>();

    // TODO: fail on staring twice
    @Override
    public void start(int port) {
        try {
            serverSocket = new ServerSocket(port);
            isWorking.set(true);
            connectionExecutor.submit(this::connectingJob);
        } catch (IOException e) {
            throw new IllegalStateException("cannot start server", e);
        }
    }

    private void connectingJob() {
        while (isWorking.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                Log.log("server: new client accepted");

                ExecutorService senderExecutor = Executors.newSingleThreadExecutor();
                senderExecutors.put(clientSocket, senderExecutor);

                Thread receiverThread = new Thread(() -> receivingJob(clientSocket));
                receiverThread.start();
                receiverThreads.put(clientSocket, receiverThread);

                activeSockets.add(clientSocket);

            } catch (IOException e) {
                // TODO: server should not fail here
                throw new IllegalStateException("cannot accept new client", e);
            }
        }
    }

    private void receivingJob(Socket clientSocket) {
        while (!Thread.interrupted() && isWorking.get()) {
            try {
                int requestLength = intFromBytes(clientSocket.getInputStream().readNBytes(4));
                if(requestLength < 0) {
                    Log.log("server: negative len=" + requestLength);
                    return;
                }
                byte[] requestBytes = clientSocket.getInputStream().readNBytes(requestLength);
                Log.log("server: request received");

                ArrayToSort payload = ArrayToSort.parseFrom(requestBytes);
                tasksExecutor.submit(() -> doTask(clientSocket, payload));

            } catch (IOException e) {
                // TODO: server should not fail here
//            throw new IllegalStateException(e);
                disconnect(clientSocket);
            }
        }
    }

    private void doTask(Socket clientSocket, ArrayToSort payload) {
        ArrayList<Integer> array = new ArrayList<>(payload.getArrayList()); // "get 'array' field as list
        Collections.sort(array);
        SortedArray sortedArray = SortedArray.newBuilder().addAllArray(array).build();
        ExecutorService senderExecutor = senderExecutors.get(clientSocket);
        if (senderExecutor != null) {
            senderExecutor.submit(() -> writingJob(clientSocket, sortedArray));
        } else {
            // TODO: server should not fail here
            throw new IllegalStateException("client socket removed before writing");
        }
    }

    private void writingJob(Socket clientSocket, SortedArray sortedArray) {
        try {
            Log.log("server: writing response");
            OutputStream outputStream = clientSocket.getOutputStream();
            byte[] data = sortedArray.toByteArray();
            outputStream.write(bytesFromInt(data.length));
            outputStream.write(data);
            Log.log("server: written response");

        } catch (IOException e) {
            // TODO: server should not fail here
//            throw new IllegalStateException(e);
            disconnect(clientSocket);
        }
    }

    private void disconnect(Socket clientSocket) {
        Log.log("server: disconnect");
        try {
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        receiverThreads.remove(clientSocket);
        senderExecutors.remove(clientSocket);
        activeSockets.remove(clientSocket);
    }

    @Override
    public synchronized void stop() {
        Log.log("server: stopping");
        isWorking.set(false);

        try {
            serverSocket.close();
        } catch (IOException e) {
            // ignored
        }

        connectionExecutor.shutdown();
        tasksExecutor.shutdown();

        for (Thread thread : receiverThreads.values()) {
            thread.interrupt();
        }
        receiverThreads.clear();

        for (ExecutorService executor : senderExecutors.values()) {
            executor.shutdown();
        }
//        senderExecutors.clear();

        for (Iterator<Socket> iter = activeSockets.iterator(); iter.hasNext(); ) {
            iter.next();
//            try {
//                iter.next().close();
//            } catch (IOException e) {
//                // ignored
//            }
        }
        activeSockets.clear();

    }

}
