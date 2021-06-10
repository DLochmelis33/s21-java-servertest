package ru.hse.servertest;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Log {
    public static void log(String msg) {
        // TODO
//        Logger.getGlobal().log(Level.ALL, msg);
        System.out.println(msg);
    }
}
