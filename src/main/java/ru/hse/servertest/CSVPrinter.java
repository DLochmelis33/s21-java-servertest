package ru.hse.servertest;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class CSVPrinter implements Closeable {

    private final List<Class<?>> colTypes;
    private final PrintWriter writer;

    private CSVPrinter(OutputStream output, List<Class<?>> colTypes, List<String> colNames) throws FileNotFoundException {
        this.colTypes = colTypes;
        writer = new PrintWriter(output);
        for (int i = 0; i < colNames.size(); i++) {
            if(!checkEntry(colNames.get(i))) {
                throw new IllegalArgumentException("column " + i + ": in CSV format entries cannot have commas or newlines");
            }
            writer.print(colNames.get(i));
            if (i != colNames.size() - 1) {
                writer.print(", ");
            }
        }
        writer.println();
    }

    public static class CSVPrinterBuilder {
        private CSVPrinterBuilder(OutputStream output) {
            this.output = output;
        }

        private final OutputStream output;
        private final List<Class<?>> colTypes = new LinkedList<>();
        private final List<String> colNames = new LinkedList<>();

        public CSVPrinterBuilder addColumn(String name, Class<?> type) {
            colTypes.add(type);
            colNames.add(name);
            return this;
        }

        public CSVPrinter build() throws FileNotFoundException {
            return new CSVPrinter(output, colTypes, colNames);
        }
    }

    public static CSVPrinterBuilder builder(OutputStream output) {
        return new CSVPrinterBuilder(output);
    }

    private boolean checkEntry(String entry) {
        return !entry.contains(",") && !entry.contains("\n");
    }

    public void printLine(Object... objects) {
        if (objects.length != colTypes.size()) {
            throw new IllegalArgumentException("passed " + objects.length + " objects into " + colTypes.size() + " columns");
        }
        for (int i = 0; i < objects.length; i++) {
            Object o = objects[i];
            if (!colTypes.get(i).isInstance(o)) {
                throw new IllegalArgumentException("column " + i + ": expected type " + colTypes.get(i).getCanonicalName() +
                        ", but got " + o.getClass().getCanonicalName());
            }
            String str = o.toString();
            if (!checkEntry(str)) {
                throw new IllegalArgumentException("column " + i + ": in CSV format entries cannot have commas or newlines");
            }
            writer.print(str);
            if (i != objects.length - 1) {
                writer.print(", ");
            }
        }
        writer.println();
    }

    @Override
    public void close() throws IOException {
        writer.flush();
        writer.close();
    }

}
