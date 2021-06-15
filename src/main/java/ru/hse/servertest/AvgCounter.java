package ru.hse.servertest;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AvgCounter {

    private final AtomicLong sum = new AtomicLong(0);
    private final AtomicInteger cnt = new AtomicInteger(0);

    public void add(long value) {
        sum.addAndGet(value);
        cnt.incrementAndGet();
    }

    public double getAvg() {
        return (double) sum.get() / cnt.get();
    }

    public int getCnt() {
        return cnt.get();
    }

    public long getSum() {
        return sum.get();
    }

}
