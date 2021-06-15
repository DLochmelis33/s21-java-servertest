package ru.hse.servertest;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class Util {

    public static int intFromBytes(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
//        if(bytes.length != 4){
//            throw new IllegalArgumentException();
//        }
//        int result = 0;
//        for (int i = 0; i < 4; i++) {
//            result |= (bytes[i] << (i * 8));
//        }
//        return result;) {
    }

    public static byte[] bytesFromInt(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
//        byte[] result = new byte[4];
//        int mask = (1 << 8) - 1;
//        for (int i = 0; i < 4; i++) {
//            System.out.println(Integer.toBinaryString(value));
//            System.out.println(Integer.toBinaryString((value & (mask << (i * 8)))));
//            System.out.println(Integer.toBinaryString(((value & (mask << (i * 8))) >> (i * 8))));
//            System.out.println("---");
//            result[i] = (byte) ((value & (mask << (i * 8))) >> (i * 8));
//        }
//        return result;
    }

    public static <T extends Comparable<T>> void bubbleSort(List<T> list) {
        for (int i = 0; i < list.size(); i++) {
            for (int j = 1; j < list.size(); j++) {
                if (list.get(j - 1).compareTo(list.get(j)) > 0) {
                    // swap
                    T tmp = list.get(j - 1);
                    T tmp2 = list.get(j);
                    list.set(j - 1, tmp2);
                    list.set(j, tmp);
                }
            }
        }
    }

}
