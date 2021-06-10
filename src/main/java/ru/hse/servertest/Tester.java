package ru.hse.servertest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class Tester {

    public static final String SERVER_IP = "localhost";
    public static final int SERVER_PORT = 5555;

    public static class Result {
        public Result(double avgClientTime) {
            this.avgClientTime = avgClientTime;
        }

        public final double avgClientTime;
    }

    public static Result doTest(int n, int m, long delayMs, int x, Supplier<Server> serverSupplier) {
        if (m <= 0) {
            // stopping condition is never called, thread halts
            throw new IllegalArgumentException("negative argument 'm': " + m);
        }

        Server server = serverSupplier.get();
        server.start(SERVER_PORT);

        ExecutorService clientsExecutor = Executors.newCachedThreadPool();
        CountDownLatch startingLatch = new CountDownLatch(m + 1); // extra 1 for this thread
        ReentrantLock stoppingLock = new ReentrantLock();
        Condition stoppingCondition = stoppingLock.newCondition();
        AtomicBoolean isFinishing = new AtomicBoolean(false);

        TestingClient[] clients = new TestingClient[m];
        IntStream.range(0, m).forEach(clientCnt -> {
            clients[clientCnt] = new TestingClient(n, x, SERVER_IP, SERVER_PORT);
            clientsExecutor.submit(() -> {
                TestingClient client = clients[clientCnt];
                try {
                    // ! TODO: mixed locks
                    synchronized (client) {
                        startingLatch.countDown();
                        startingLatch.await();

                        for (int i = 0; i < x; i++) { // not stream bc InterruptedException
                            client.submitNewRequest();
                            if (!client.isConnected()) {
                                break;
                            }
                            client.wait(delayMs);
                        }

                        while (client.getSuccesses() < x) {
                            client.wait();
                        }
                    }

                } catch (InterruptedException e) {
                    Log.log("interrupted");
                    // ignored, shutting down
                } catch (Throwable hm) {
                    hm.printStackTrace();
                } finally {
                    client.stop();
                    isFinishing.set(true);
                    stoppingLock.lock();
                    try {
                        stoppingCondition.signal();
                    } finally {
                        stoppingLock.unlock();
                    }
                }
            });
        });

        stoppingLock.lock();
        try {
            startingLatch.countDown();

            while (!isFinishing.get()) {
                stoppingCondition.await();
            }
            Log.log("finishing");

            long totalTime = 0;
            long totalSuccesses = 0;
            for (TestingClient client : clients) {
                totalTime += client.getTimesSum();
                totalSuccesses += client.getSuccesses();
            }

            return new Result((double) totalTime / totalSuccesses);

        } catch (InterruptedException e) {
            throw new IllegalStateException("testing interrupted");
        } finally {
            stoppingLock.unlock();
            clientsExecutor.shutdownNow();
            server.stop();
        }

    }

}
