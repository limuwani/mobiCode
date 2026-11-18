package dev.mobicode.app;

import android.os.SystemClock;
import android.system.Os;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

final class WorkspaceFiles {
    static final long MIB = 1024L * 1024L;
    private static final LinkOption NO_LINKS = LinkOption.NOFOLLOW_LINKS;

    static final class Download {
        final String url, sha256;
        final long bytes;

        Download(String url, String sha256, long bytes) {
            this.url = url;
            this.sha256 = sha256;
            this.bytes = bytes;
        }
    }

    interface Progress {
        void update(long savedBytes);
    }

    static void checkRunning() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Setup was stopped.");
    }

    static void download(Download source, Path destination, Progress progress) throws Exception {
        long saved = Files.exists(destination) ? Files.size(destination) : 0;
        if (saved > source.bytes) {
            Files.delete(destination);
            saved = 0;
        }
        progress.update(saved);
        if (saved < source.bytes) {
            HttpURLConnection connection = connect(source.url, saved);
            try {
                int status = connection.getResponseCode();
                if (status == HttpURLConnection.HTTP_OK) saved = 0;
                else if (status == HttpURLConnection.HTTP_PARTIAL) {
                    String range = connection.getHeaderField("Content-Range");
                    if (range == null || !range.startsWith("bytes " + saved + "-")) {
                        Files.deleteIfExists(destination);
                        throw new IOException("The download could not resume. Tap Try again.");
                    }
                } else if (status == 416 && saved > 0) {
                    Files.deleteIfExists(destination);
                    throw new IOException("The saved download expired. Tap Try again.");
                } else throw new IOException("The download server returned HTTP " + status + ".");

                StandardOpenOption mode = saved > 0 ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING;
                try (InputStream input = connection.getInputStream();
                     OutputStream output = Files.newOutputStream(destination, StandardOpenOption.CREATE, StandardOpenOption.WRITE, mode)) {
                    byte[] buffer = new byte[65536];
                    long lastUpdate = 0;
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        checkRunning();
                        saved += count;
                        if (saved > source.bytes) throw new IOException("The download is larger than expected.");
                        output.write(buffer, 0, count);
                        long now = SystemClock.elapsedRealtime();
                        if (now - lastUpdate >= 300) {
                            lastUpdate = now;
                            progress.update(saved);
                        }
                    }
                }
                if (saved != source.bytes) throw new IOException("The download was interrupted. Tap Try again to resume.");
            } finally {
                connection.disconnect();
            }
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(destination)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) {
                checkRunning();
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder hash = new StringBuilder(64);
        for (byte value : digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", value & 255));
        if (!source.sha256.contentEquals(hash)) {
            Files.delete(destination);
            throw new IOException("Downloaded files failed verification. Tap Try again.");
        }
        progress.update(saved);
    }

    private static HttpURLConnection connect(String address, long offset) throws IOException {
        URL url = new URL(address);
        for (int redirects = 0; redirects < 6; redirects++) {
            if (!"https".equals(url.getProtocol())) throw new IOException("Download must use HTTPS.");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(25000);
            connection.setRequestProperty("Accept-Encoding", "identity");
            if (offset > 0) connection.setRequestProperty("Range", "bytes=" + offset + "-");
            int status;
            try { status = connection.getResponseCode(); }
            catch (IOException error) { connection.disconnect(); throw error; }
            if (status < 300 || status > 399) return connection;
            String next = connection.getHeaderField("Location");
            connection.disconnect();
            if (next == null) throw new IOException("Download redirect is missing its destination.");
            url = new URL(url, next);
        }
        throw new IOException("Too many download redirects.");
    }

    static void unpack(InputStream zipped, Path destination, String prefix, boolean keepExisting) throws Exception {
        Files.createDirectories(destination);
        long bytes = 0;
        int entries = 0;
        List<Path> directoryModes = new ArrayList<>();
        try (TarArchiveInputStream archive = new TarArchiveInputStream(new GZIPInputStream(new BufferedInputStream(zipped), 65536))) {
            TarArchiveEntry entry;
            byte[] buffer = new byte[65536];
            while ((entry = archive.getNextEntry()) != null) {
                checkRunning();
                if (++entries > 200000) throw new IOException("Tool archive has too many files.");
                String name = nameInside(entry.getName(), prefix);
                if (name.isEmpty()) continue;
                Path output = pathIn(destination, name);
                checkedParents(destination, output.getParent());
                if (entry.isDirectory()) {
                    if (!Files.exists(output, NO_LINKS)) Files.createDirectory(output);
                    if (!Files.isDirectory(output, NO_LINKS)) throw new IOException("Archive directory is blocked by another file.");
                    directoryModes.add(output);
                    continue;
                }
                if (Files.exists(output, NO_LINKS)) {
                    if (keepExisting) continue;
                    throw new IOException("Duplicate file in tools archive.");
                }
                if (entry.isSymbolicLink()) {
                    // Absolute targets are paths in the guest. Extraction never follows a link.
                    Os.symlink(entry.getLinkName(), output.toString());
                } else if (entry.isLink()) {
                    Path target = pathIn(destination, nameInside(entry.getLinkName(), prefix));
                    Os.symlink(output.getParent().relativize(target).toString(), output.toString());
                } else if (entry.isFile()) {
                    bytes += entry.getSize();
                    if (bytes > 1800 * MIB) throw new IOException("Tools archive is larger than expected.");
                    try (OutputStream file = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                        int count;
                        while ((count = archive.read(buffer)) != -1) {
                            checkRunning();
                            file.write(buffer, 0, count);
                        }
                    }
                    Os.chmod(output.toString(), entry.getMode() & 0777);
                }
                // The guest receives standard terminal devices from Android at launch.
            }
        }
        // The app UID owns the files. Keep directories writable for guest package tools.
        Collections.reverse(directoryModes);
        for (Path path : directoryModes) Os.chmod(path.toString(), 0755);
    }

    private static String nameInside(String raw, String prefix) throws IOException {
        while (raw.startsWith("./")) raw = raw.substring(2);
        if (raw.equals(".")) return "";
        if (prefix.isEmpty()) return raw;
        if (raw.equals(prefix)) return "";
        if (!raw.startsWith(prefix + "/")) throw new IOException("Unexpected tools archive layout.");
        return raw.substring(prefix.length() + 1);
    }

    private static Path pathIn(Path root, String name) throws IOException {
        Path path = root.resolve(name).normalize();
        if (name.startsWith("/") || !path.startsWith(root) || path.equals(root)) throw new IOException("Invalid path in tools archive.");
        return path;
    }

    private static void checkedParents(Path root, Path parent) throws IOException {
        Path path = root;
        for (Path part : root.relativize(parent)) {
            path = path.resolve(part);
            if (!Files.exists(path, NO_LINKS)) Files.createDirectory(path);
            if (!Files.isDirectory(path, NO_LINKS)) throw new IOException("Unexpected link in tools archive path.");
        }
    }

    static void removeTree(Path directory) throws IOException {
        if (!Files.exists(directory, NO_LINKS)) return;
        if (Files.isDirectory(directory, NO_LINKS)) {
            try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
                for (Path file : files) removeTree(file);
            }
        }
        Files.delete(directory);
    }

    private WorkspaceFiles() {}
}
