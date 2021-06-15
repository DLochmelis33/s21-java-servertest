package ru.hse.servertest;

public class Log {
    public static void d(String msg) {
        System.out.println(msg);
    }

    public static void e(String msg, Throwable err) {
        System.err.println(msg);
        err.printStackTrace();
    }
}
