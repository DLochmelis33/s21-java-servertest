package ru.hse.servertest;

public class Util {

    public static int intFromBytes(byte[] bytes) {
        int result = 0;
        for (int i = 0; i < 4; i++) {
            result += bytes[i] << (i * 8);
        }
        return result;
    }

    public static byte[] bytesFromInt(int value) {
        byte[] result = new byte[4];
        int mask = (1 << 8) - 1;
        for (int i = 0; i < 4; i++) {
            result[i] = (byte) ((value & (mask << (i * 8))) >> (i * 8));
        }
        return result;
    }

}
