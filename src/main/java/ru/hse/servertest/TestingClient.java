package ru.hse.servertest;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static ru.hse.servertest.Util.bytesFromInt;
import static ru.hse.servertest.Util.intFromBytes;

public class TestingClient {

    private final int n, x;
    private final Socket socket;
    private final ExecutorService messageExecutor = Executors.newCachedThreadPool();
    private long timesSum = 0;
    private int successes = 0;

    public TestingClient(int n, int x, String serverIp, int serverPort) {
        this.n = n;
        this.x = x;
        try {
            socket = new Socket(serverIp, serverPort);
        } catch (IOException e) {
            throw new IllegalStateException("could not create a client", e);
        }
    }

    // non-blocking
    public void submitNewRequest() {
        if (!isConnected()) {
            throw new IllegalStateException("client: disconnected, cannot submit request");
        }
        Log.log("client: submitting new request");
        ArrayToSort.Builder builder = ArrayToSort.newBuilder();
//        Random random = new Random();
//        IntStream.range(0, n).forEach(i -> {
//            builder.addArray(random.nextInt());
//        });
        builder.addAllArray(List.of(5, 4, 3, 2, 1));

        byte[] messageBytes = builder.build().toByteArray();
        messageExecutor.execute(() -> {
            try {
                Log.log("client: writing, len=" + messageBytes.length);
                Instant start = Instant.now();
                socket.getOutputStream().write(bytesFromInt(messageBytes.length));
                socket.getOutputStream().write(messageBytes);
                Log.log("client: written");

                int responseLength = intFromBytes(socket.getInputStream().readNBytes(4));
                byte[] response = socket.getInputStream().readNBytes(responseLength);
                Instant end = Instant.now();
                Log.log("client: received");

                if (responseLength != messageBytes.length) {
                    throw new IllegalStateException("wrong response from server");
                }

                timesSum += Duration.between(start, end).toMillis();
                successes++;

            } catch (IOException e) {
                // TODO: what if the server is already stopped?
                throw new IllegalStateException("failed to send a message", e);
            }
        });
    }

    public void stop() {
        Log.log("client: stopping");
//        try {
//            socket.close();
//        } catch (IOException e) {
//            // ignored
//        }
        messageExecutor.shutdown();
    }

    public boolean isConnected() {
        return socket.isConnected();
    }

    public long getTimesSum() {
        return timesSum;
    }

    public synchronized long getSuccesses() {
        notify(); // ! TODO: mixed locks
        return successes;
    }
}
