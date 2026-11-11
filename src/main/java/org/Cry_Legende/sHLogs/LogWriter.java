package org.Cry_Legende.sHLogs;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * File d'écriture des logs. Le thread appelant ne fait que déposer sa ligne : la date, le fichier du mois et
 * l'écriture sont traités par un seul thread, ce qui garde les lignes dans l'ordre.
 */
public final class LogWriter {

    private static final Pattern COLOR_CODES = Pattern.compile("§[0-9a-fA-Fk-oK-OrRxX]");
    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}+");
    private static final int MAX_BATCH = 4096;
    private static final long DROP_WARNING_INTERVAL_MS = 60_000L;

    private record Entry(String folder, String file, LocalDateTime time, String message) {
    }

    private record Settings(DateTimeFormatter lineDate, DateTimeFormatter monthFolder, String extension,
                            int maxPendingLines) {
    }

    private final SHLogs plugin;
    private final LinkedBlockingQueue<Entry> queue = new LinkedBlockingQueue<>();
    private final AtomicLong dropped = new AtomicLong();
    private final Thread thread;
    private volatile Settings settings;
    private volatile boolean running = true;
    private long lastDropWarning;

    LogWriter(SHLogs plugin) {
        this.plugin = plugin;
        configure(plugin.getConfig());
        thread = new Thread(this::run, "SHLogs-writer");
        thread.setDaemon(true);
        thread.start();
    }

    void configure(FileConfiguration config) {
        settings = new Settings(
                formatter(config.getString("line-date-format"), "dd/MM/yyyy HH:mm:ss"),
                formatter(config.getString("month-folder-format"), "yyyy-MM"),
                config.getString("file-extension", "yml"),
                config.getInt("max-pending-lines", 100_000));
    }

    private DateTimeFormatter formatter(String pattern, String fallback) {
        try {
            return DateTimeFormatter.ofPattern(pattern == null ? fallback : pattern);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Format de date invalide : " + pattern + ", utilisation de " + fallback);
            return DateTimeFormatter.ofPattern(fallback);
        }
    }

    /** Dépose une ligne. Utilisable depuis n'importe quel thread, ne touche jamais au disque. */
    public boolean add(String folder, String file, String message) {
        if (!running || !isValidName(folder) || !isValidName(file) || message == null) {
            return false;
        }
        if (queue.size() >= settings.maxPendingLines()) {
            dropped.incrementAndGet();
            return false;
        }
        queue.add(new Entry(folder, file, LocalDateTime.now(), message));
        return true;
    }

    /** Nom du dossier du mois en cours, selon {@code month-folder-format}. */
    String currentMonthName() {
        return settings.monthFolder().format(LocalDateTime.now());
    }

    /** Extension des fichiers de log, sans le point. */
    String extension() {
        return settings.extension();
    }

    /** Dossier d'un plugin pour le mois en cours (il peut ne pas exister encore). */
    Path currentMonthFolder(String folder) {
        return plugin.getDataFolder().toPath().resolve(folder).resolve(currentMonthName());
    }

    /** Un simple nom : il ne doit pas pouvoir sortir du dossier du plugin (« ../.. », séparateurs). */
    public static boolean isValidName(String name) {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '/' || c == '\\' || c < 32) {
                return false;
            }
        }
        return true;
    }

    private void run() {
        List<Entry> batch = new ArrayList<>();
        while (running || !queue.isEmpty()) {
            try {
                Entry first = queue.poll(1, TimeUnit.SECONDS);
                if (first == null) {
                    warnAboutDroppedLines();
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, MAX_BATCH);
                try {
                    write(batch);
                } catch (RuntimeException e) {
                    // Un format de date refusé ne doit pas arrêter le thread : les lignes suivantes restent écrites
                    plugin.getLogger().severe("Écriture de " + batch.size() + " lignes impossible : " + e);
                }
                batch.clear();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void write(List<Entry> batch) {
        Settings current = settings;
        Path root = plugin.getDataFolder().toPath();
        Map<Path, StringBuilder> files = new LinkedHashMap<>();
        for (Entry entry : batch) {
            Path path = root.resolve(entry.folder())
                    .resolve(current.monthFolder().format(entry.time()))
                    .resolve(entry.file() + "." + current.extension());
            files.computeIfAbsent(path, key -> new StringBuilder())
                    .append('[').append(current.lineDate().format(entry.time())).append("] ")
                    .append(clean(entry.message())).append('\n');
        }
        for (Map.Entry<Path, StringBuilder> file : files.entrySet()) {
            try {
                Files.createDirectories(file.getKey().getParent());
                Files.writeString(file.getKey(), file.getValue(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                // Le disque refuse l'écriture : les lignes passent dans la console pour ne pas être perdues
                plugin.getLogger().severe("Impossible d'écrire " + file.getKey() + " : " + e.getMessage());
                plugin.getLogger().severe(file.getValue().toString());
            }
        }
    }

    private static String clean(String message) {
        String withoutColors = COLOR_CODES.matcher(message).replaceAll("");
        return CONTROL_CHARS.matcher(withoutColors).replaceAll(" ").strip();
    }

    private void warnAboutDroppedLines() {
        long now = System.currentTimeMillis();
        if (now - lastDropWarning < DROP_WARNING_INTERVAL_MS) {
            return;
        }
        long count = dropped.getAndSet(0);
        if (count > 0) {
            lastDropWarning = now;
            plugin.getLogger().warning(count + " lignes de log ignorées : file d'écriture pleine.");
        }
    }

    /** Laisse finir les écritures en attente avant l'arrêt. */
    void stop(long timeoutSeconds) {
        running = false;
        try {
            thread.join(TimeUnit.SECONDS.toMillis(timeoutSeconds));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (thread.isAlive()) {
            plugin.getLogger().warning("Des écritures de log n'ont pas pu être terminées.");
        }
    }
}
