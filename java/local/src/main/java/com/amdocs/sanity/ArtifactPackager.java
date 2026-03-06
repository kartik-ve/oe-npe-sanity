package com.amdocs.sanity;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

final class ArtifactPackager {

    private ArtifactPackager() {
    }

    static void zipConfiguredArtifacts(Path buildDir, Properties config) throws Exception {
        String[] targets = config.getProperty("zip.targets").split(",");

        for (String target : targets) {
            String dirName = target.trim();
            Path dir = buildDir.resolve(dirName);

            if (!Files.exists(dir)) {
                continue;
            }

            String zipName = config.getProperty("zip." + dirName);
            Path zipPath = buildDir.resolve(zipName);

            zipDirectory(dir, zipPath);
        }
    }

    private static void zipDirectory(Path sourceDir, Path zipFile) throws Exception {
        if (Files.exists(zipFile)) {
            Files.delete(zipFile);
        }

        Map<String, String> env = new HashMap<>();
        env.put("create", "true");

        try (FileSystem zipFs = FileSystems.newFileSystem(
                URI.create("jar:" + zipFile.toUri()),
                env)) {

            Files.walk(sourceDir).forEach(path -> {
                try {
                    if (!Files.isDirectory(path)) {
                        Path relative = sourceDir.relativize(path);
                        Path zipEntry = zipFs.getPath(relative.toString());

                        if (zipEntry.getParent() != null) {
                            Files.createDirectories(zipEntry.getParent());
                        }

                        Files.copy(path, zipEntry, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}