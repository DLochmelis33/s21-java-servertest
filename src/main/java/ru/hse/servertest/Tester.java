package ru.hse.servertest;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class Tester {

    public static final String SERVER_IP = "localhost";
    public static final int SERVER_PORT = 8888;

    public static class TestingResult {
        public TestingResult(double avgClientTime, double avgServerTime) {
            this.avgClientTime = avgClientTime;
            this.avgServerTime = avgServerTime;
        }

        public final double avgClientTime, avgServerTime;
    }

    public final static AtomicBoolean isFinishing = new AtomicBoolean(false);
    public static AvgCounter clientCounter;

    public static TestingResult doTest(int n, int m, long delayMs, int x, Supplier<Server> serverSupplier) {
        if (m <= 0) {
            // stopping condition is never called, thread halts
            throw new IllegalArgumentException("negative argument 'm': " + m);
        }

        Server server = serverSupplier.get();
        server.start(SERVER_PORT);

//        server.stop();
//        if(true)
//        return new Result(0);

        ExecutorService clientsExecutor = Executors.newCachedThreadPool(App.threadFactory);
        CountDownLatch startingLatch = new CountDownLatch(m + 1); // extra 1 for this thread
        CountDownLatch stoppingLatch = new CountDownLatch(1); // aka notify() without synchronizing / blocking
        isFinishing.set(false);
        clientCounter = new AvgCounter();

        TestingClient[] clients = new TestingClient[m];
        IntStream.range(0, m).forEach(clientCnt -> {
            clients[clientCnt] = new TestingClient(n, x, SERVER_IP, SERVER_PORT);
            clientsExecutor.submit(() -> {
                TestingClient client = clients[clientCnt];
                try {
                    startingLatch.countDown();
                    startingLatch.await();

                    for (int i = 0; i < x; i++) { // not stream bc InterruptedException
                        client.submitNewRequest();
                        if (!client.isConnected() || isFinishing.get()) {
                            break;
                        }
                        Thread.yield();
                        Thread.sleep(delayMs);
                    }

                    while (!isFinishing.get() && client.isConnected() && client.getSuccesses() < x) {
                        client.clientStoppingCondition.await();
                    }

                } catch (InterruptedException e) {
                    Log.d("testing: client interrupted");
                    // ignored, shutting down
                } finally {
                    isFinishing.set(true); // before stopping!
                    stoppingLatch.countDown();
                    client.stop();
                }
            });
        });

        startingLatch.countDown();
        try {
            stoppingLatch.await();
        } catch (InterruptedException e) {
            throw new IllegalStateException("testing: interrupted");
        } finally {
            Log.d("testing: finishing");
            clientsExecutor.shutdownNow();
            server.stop();
        }

        Log.d("testing: calc results");

        long totalSuccesses = 0;
        for (TestingClient client : clients) {
            totalSuccesses += client.getSuccesses();
            client.stop();
        }

        Log.w("total successes: " + totalSuccesses + ", max: " + (m * x) + ", okay: " + (m * (x - 1)));

        return new TestingResult(clientCounter.getAvg(), 0); // TODO: server time
    }

    public static SortedArray process(ArrayToSort arr) {
        ArrayList<Integer> list = new ArrayList<>(arr.getArrayList());
        Util.bubbleSort(list);
        return SortedArray.newBuilder().addAllArray(list).build();
    }

}
