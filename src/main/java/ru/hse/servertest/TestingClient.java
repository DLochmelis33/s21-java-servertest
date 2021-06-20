package ru.hse.servertest;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static ru.hse.servertest.Util.bytesFromInt;
import static ru.hse.servertest.Util.intFromBytes;

public class TestingClient {

    private final int n, x;
    private final Socket socket;
    private final ExecutorService messageExecutor = Executors.newCachedThreadPool(App.threadFactory);
    public final CountDownLatch clientStoppingCondition = new CountDownLatch(1);
    private final AtomicLong successes = new AtomicLong(0);

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
        messageExecutor.submit(() -> {
            try {
                Log.d("client: submitting new request");
                ArrayToSort.Builder builder = ArrayToSort.newBuilder();
                Random random = new Random();
                IntStream.range(0, n).forEach(i -> builder.addArray(random.nextInt()));
//              builder.addAllArray(List.of(5, 4, 3, 2, 1));

                byte[] messageBytes = builder.build().toByteArray();
                Log.d("client: writing, len=" + messageBytes.length);
                Instant start = Instant.now();
                socket.getOutputStream().write(bytesFromInt(messageBytes.length));
                socket.getOutputStream().write(messageBytes);
                Log.d("client: written");

                int responseLength = intFromBytes(socket.getInputStream().readNBytes(4));
                byte[] response = socket.getInputStream().readNBytes(responseLength);
                Instant end = Instant.now();
                Log.d("client: received");

                if (responseLength != messageBytes.length) {
                    throw new IllegalStateException("wrong response from server");
                }

                Tester.clientCounter.add(Duration.between(start, end).toMillis());
                successes.incrementAndGet();

            } catch (IOException e) {
                // TODO: what if the server is already stopped?
                stop();
                throw new IllegalStateException("failed to send a message", e);
            } finally {
                if(!isConnected() || successes.get() == x) {
                    clientStoppingCondition.countDown();
                }
            }
        });
    }

    public void stop() {
        Log.d("client: stopping");
        try {
            socket.close();
        } catch (IOException e) {
            // ignored
        }
        messageExecutor.shutdownNow();
        clientStoppingCondition.countDown();
    }

    public boolean isConnected() {
        return socket.isConnected();
    }

//    public double getTimesSum() {
//        return avgcnt.getSum();
//    }

    public long getSuccesses() {
        return successes.get();
    }
}
