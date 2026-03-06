package com.amdocs.sanity;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

final class CopyFailedResponses {

    private static final String FAILED_SUFFIX = "~FAILED";

    private CopyFailedResponses() {
    }

    static void copy(Path sourceDir, Path destinationDir) throws IOException {
        if (!Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException("Source path is not a directory: " + sourceDir);
        }

        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString();
                String nameWithoutExtension = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.'))
                        : fileName;

                if (nameWithoutExtension.endsWith(FAILED_SUFFIX)) {
                    Path relativePath = sourceDir.relativize(file);
                    Path targetFile = destinationDir.resolve(relativePath);

                    Files.createDirectories(targetFile.getParent());

                    Files.copy(
                            file,
                            targetFile,
                            StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
