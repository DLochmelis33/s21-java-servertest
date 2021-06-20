package ru.hse.servertest;

public class Log {
    public static void d(String msg) {
//        System.out.println(msg);
    }

    private static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_RESET = "\u001B[0m";

    public static void w(String msg) {
        System.out.println(ANSI_BLUE + msg + ANSI_RESET);
    }

    public static void e(String msg, Throwable err) {
        System.err.println(msg);
        err.printStackTrace();
    }
}
