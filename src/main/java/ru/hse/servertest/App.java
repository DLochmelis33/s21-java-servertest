package ru.hse.servertest;

import java.io.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class App {

    private static class ConfigException extends IllegalStateException {
        public ConfigException() {
        }

        public ConfigException(String s) {
            super(s);
        }

        public ConfigException(String message, Throwable cause) {
            super(message, cause);
        }

        public ConfigException(Throwable cause) {
            super(cause);
        }
    }

    private final static String CONFIG_FILE = "config.txt";

    private enum Param {
        N, M, DELTA, X, SERVER;
    }

    private static final Map<Integer, Supplier<Server>> serverTypeMap = new HashMap<>();

    private static final Map<Param, Set<Integer>> paramValues = new HashMap<>();

    static {
        for (Param p : Param.values()) {
            paramValues.put(p, new HashSet<>());
        }
        serverTypeMap.put(1, BlockingServer::new);
        serverTypeMap.put(2, NonblockingServer::new);
        serverTypeMap.put(3, AsyncServer::new);
    }

    private static void loadParams() {
        try {
            Pattern patternSingle = Pattern.compile("^(\\w+) : (\\w+)$");
            Pattern patternRange = Pattern.compile("^(\\w+) : (\\d+) - (\\d+)$");
            Scanner scanner = new Scanner(new File(CONFIG_FILE));
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine(); // "excluding line separator"
                if (line.equals("")) {
                    // empty line
                    continue;
                }
                if (line.charAt(0) == '#') {
                    // comment
                    continue;
                }
                Matcher matcherRange = patternRange.matcher(line);
                Matcher matcherSingle = patternSingle.matcher(line);

                if (matcherRange.matches()) {
                    MatchResult result = matcherRange.toMatchResult();
                    try {
                        Param param = Param.valueOf(result.group(1).toUpperCase());
                        int from = Integer.parseInt(result.group(2));
                        int to = Integer.parseInt(result.group(3));
                        if (from > to) {
                            throw new ConfigException("invalid range");
                        }
                        for (int i = from; i <= to; i++) {
                            paramValues.get(param).add(i);
                        }
                    } catch (IllegalArgumentException e) {
                        throw new ConfigException(e);
                    }
                } else if (matcherSingle.matches()) {
                    MatchResult result = matcherSingle.toMatchResult();
                    try {
                        int value;
                        Param param = Param.valueOf(result.group(1).toUpperCase());
                        if (param == Param.SERVER) {
                            value = switch (result.group(2)) {
                                case "blocking" -> 1;
                                case "nonblocking" -> 2;
                                case "async" -> 3;
                                default -> throw new ConfigException("unknown server type: " + result.group(2));
                            };
                        } else {
                            value = Integer.parseInt(result.group(2));
                        }
                        paramValues.get(param).add(value);
                    } catch (IllegalArgumentException e) {
                        throw new ConfigException(e);
                    }
                } else {
                    throw new ConfigException("invalid line in config: '" + line + "'");
                }
            }

        } catch (FileNotFoundException e) {
            throw new ConfigException("cannot load config file", e);
        }
    }

    public static void main(String[] args) throws FileNotFoundException {

        loadParams();

        try (CSVPrinter printer = CSVPrinter.builder(new FileOutputStream("results.csv"))
                .addColumn("N", Integer.class)
                .addColumn("M", Integer.class)
                .addColumn("Delta (ms)", Integer.class)
                .addColumn("X", Integer.class)
                .addColumn("Avg client time (ms)", Double.class)
                .addColumn("Avg server time (ms)", Double.class)
                .build()) {

            for (int n : paramValues.get(Param.N)) {
                for (int m : paramValues.get(Param.M)) {
                    for (int deltaMs : paramValues.get(Param.DELTA)) {
                        for (int x : paramValues.get(Param.X)) {
                            Supplier<Server> serverSupplier = serverTypeMap.get(paramValues.get(Param.SERVER).iterator().next());
                            Tester.TestingResult result = Tester.doTest(n, m, deltaMs, x, serverSupplier);
                            printer.printLine(n, m, deltaMs, x, result.avgClientTime, result.avgServerTime);
                        }
                    }
                }
            }

            System.out.println("testing finished");

        } catch (IOException e) {
            e.printStackTrace(); // okay here
        }
    }

}
