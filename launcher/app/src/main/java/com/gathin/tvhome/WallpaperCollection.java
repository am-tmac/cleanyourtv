package com.gathin.tvhome;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Collection-only storage and display policy. No Android dependencies. */
final class WallpaperCollection {
    private static final Pattern IMAGE = Pattern.compile("[^/\\\\\\x00]+\\.(?i:jpg|jpeg|png|webp)");
    private static final Pattern BING = Pattern.compile("bing-(\\d{8})-(.+)\\.jpg");
    private static final String TITLES = ".titles.properties";
    private WallpaperCollection() { }

    static File resolve(File directory, String name) {
        if (directory == null || name == null || name.startsWith(".") || !IMAGE.matcher(name).matches()) return null;
        try {
            if (Files.isSymbolicLink(directory.toPath())) return null;
            File root = directory.getCanonicalFile();
            File candidate = new File(root, name);
            if (Files.isSymbolicLink(candidate.toPath())) return null;
            File file = candidate.getCanonicalFile();
            return root.equals(file.getParentFile()) ? file : null;
        } catch (IOException | SecurityException e) { return null; }
    }

    /** Include damaged/oversized pictures too: users must be able to clean those up. */
    static List<File> images(File directory) {
        List<File> result = new ArrayList<>();
        File[] files = directory == null ? null : directory.listFiles();
        if (files == null) return result;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File entry : files) {
            File safe = resolve(directory, entry.getName());
            if (safe != null && safe.isFile()) result.add(entry);
        }
        return result;
    }

    static String choiceAfterDelete(String current, String deleted) {
        return deleted.equals(current) ? null : current;
    }

    static String displayName(String name, String title, boolean chinese) {
        Matcher match = BING.matcher(name);
        if (match.matches()) {
            try {
                LocalDate date = LocalDate.parse(match.group(1), DateTimeFormatter.BASIC_ISO_DATE);
                String readable = title == null ? "" : title.trim();
                if (readable.isEmpty() && match.group(2).matches("peapix-[a-fA-F0-9]{32}")) readable = "peapix";
                if (readable.isEmpty()) readable = match.group(2)
                        .replaceFirst("_[A-Za-z]{2}-[A-Za-z]{2}\\d+$", "")
                        .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                        .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                        .replace('_', ' ').trim();
                String day = chinese ? date.getMonthValue() + "月" + date.getDayOfMonth() + "日"
                        : date.getMonthValue() + "/" + date.getDayOfMonth();
                return (chinese ? "必应" : "Bing") + " · " + day + " · " + readable;
            } catch (DateTimeParseException ignored) { /* Treat malformed old names as ordinary files. */ }
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static File metadata(File directory) throws IOException {
        File root = directory.getCanonicalFile();
        File file = new File(root, TITLES);
        if (Files.isSymbolicLink(file.toPath()) || !root.equals(file.getCanonicalFile().getParentFile()))
            throw new IOException("Unsafe title store");
        return file;
    }

    private static Properties read(File directory) throws IOException {
        Properties titles = new Properties();
        File file = metadata(directory);
        if (file.isFile()) try (InputStream stream = Files.newInputStream(file.toPath())) { titles.load(stream); }
        return titles;
    }

    static synchronized String title(File directory, String name) {
        if (resolve(directory, name) == null) return null;
        try { return read(directory).getProperty(name); }
        catch (IOException | IllegalArgumentException e) { return null; }
    }

    private static void write(File directory, Properties titles) throws IOException {
        File target = metadata(directory);
        File temporary = File.createTempFile(".titles-", ".tmp", directory);
        try {
            try (OutputStream stream = Files.newOutputStream(temporary.toPath())) { titles.store(stream, "Wallpaper titles"); }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary.toPath()); }
    }

    static synchronized void setTitle(File directory, String name, String title) throws IOException {
        if (resolve(directory, name) == null) throw new IOException("Unsafe wallpaper name");
        Properties titles = read(directory);
        if (title == null) titles.remove(name); else titles.setProperty(name, title);
        write(directory, titles);
    }

    /** Both manual deletion and gallery pruning use this metadata cleanup. */
    static synchronized boolean delete(File directory, String name) throws IOException {
        File file = resolve(directory, name);
        if (file == null || !file.isFile()) return false;
        Properties titles = read(directory);
        Object old = titles.remove(name);
        if (old != null) write(directory, titles);
        if (file.delete()) return true;
        if (old != null) { titles.put(name, old); write(directory, titles); }
        return false;
    }
}
