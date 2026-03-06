package com.amdocs.sanity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

final class ProcessAPIData {
    private ProcessAPIData() {
    }

    static void processFiles(Path inputRoot, Path outputRoot) throws IOException {
        Files.walk(inputRoot)
                .filter(Files::isDirectory)
                .forEach(dir -> processDirectory(dir, inputRoot, outputRoot));
    }

    private static void processDirectory(Path dir, Path inputRoot, Path outputRoot) {
        try {
            Path relative = inputRoot.relativize(dir);
            Path targetDir = outputRoot.resolve(relative);

            Files.createDirectories(targetDir);

            // check if testcase folder contains txt files
            try (Stream<Path> files = Files.list(dir)) {
                boolean containsTxt = files.anyMatch(f -> f.toString().endsWith(".txt"));

                if (containsTxt) {
                    Path requestDir = targetDir.resolve("request");
                    Path responseDir = targetDir.resolve("response");
                    Path metaDir = targetDir.resolve("meta-data");

                    Files.createDirectories(requestDir);
                    Files.createDirectories(responseDir);
                    Files.createDirectories(metaDir);

                    try (Stream<Path> txtFiles = Files.list(dir)) {
                        txtFiles.filter(f -> f.toString().endsWith(".txt"))
                                .forEach(f -> processFile(f, requestDir, responseDir, metaDir));
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void processFile(Path file,
            Path requestDir,
            Path responseDir,
            Path metaDir) {

        try {
            String content = new String(Files.readAllBytes(file), "UTF-8");

            String baseName = file.getFileName().toString().replace(".txt", "");

            String requestJson = extractJson(content, "---------------- Request ---------------------------");
            String responseJson = extractJson(content, "---------------- Response --------------------------");
            String status = extractStatus(content);
            String messages = extractMessages(content);

            Files.write(requestDir.resolve(baseName + ".json"), requestJson.getBytes("UTF-8"));
            Files.write(responseDir.resolve(baseName + ".json"), responseJson.getBytes("UTF-8"));

            String metaContent = messages.isEmpty() ? "Status: " + status + System.lineSeparator()
                    : "Status: " + status + System.lineSeparator() + System.lineSeparator() + messages;

            Files.write(metaDir.resolve(baseName + ".txt"), metaContent.getBytes("UTF-8"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String extractStatus(String content) {
        StringBuilder status = new StringBuilder();

        for (String line : content.split("\n")) {
            if (line.trim().startsWith("Status:")) {
                status.append(line.split(":")[1].trim());
            }
        }

        String statusCode = extractStatusCode(content);
        if (!statusCode.equals("UNKNOWN")) {
            status.append(" (" + statusCode + ")");
        }

        return status.toString();
    }

    private static String extractStatusCode(String content) {
        for (String line : content.split("\n")) {
            if (line.trim().startsWith("StatusCode:")) {
                return line.split(":")[1].trim();
            }
        }
        return "UNKNOWN";
    }

    private static String extractMessages(String content) {
        String msgMarker = "----------------- Messages ------------------------------";
        int start = content.indexOf(msgMarker);
        int end = content.indexOf("----------------- Properties ------------------------------");
        if (end == -1) {
            end = content.length();
        }

        if (start != -1 && start < end) {
            start += msgMarker.length();
            String result = content.substring(start, end).trim();
            return result.isEmpty() ? "" : msgMarker + System.lineSeparator() + result;
        }

        return "";
    }

    private static String extractJson(String content, String marker) {
        int sectionStart = content.indexOf(marker);
        if (sectionStart == -1) {
            return "";
        }

        String sub = content.substring(sectionStart);

        int firstBrace = sub.indexOf("{");
        if (firstBrace == -1) {
            return "";
        }

        int braceCount = 1;
        StringBuilder json = new StringBuilder("{");

        for (int i = firstBrace + 1; i < sub.length() && braceCount > 0; i++) {
            char c = sub.charAt(i);

            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
            }
            json.append(c);
        }

        return json.toString();
    }
}
