package ru.hse.servertest;

import java.util.concurrent.TimeUnit;

public class App {

    public static void main(String[] args) {
        System.out.println("started");
        double clientTime = Tester.doTest(16, 3, 100, 2, BlockingServer::new).avgClientTime;
        System.out.println("client average time: " + clientTime);

        // currently known problems:
        // - CME in server.stop() for no reason
        // - sometimes not stopping
        // - sometimes negative lengths
    }

}
