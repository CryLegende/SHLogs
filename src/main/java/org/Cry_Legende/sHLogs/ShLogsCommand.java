package org.Cry_Legende.sHLogs;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * /shlogs <dossier> <fichier> <message>, pour les plugins qui n'ont que des commandes, et les sous-commandes
 * d'administration reload, list et tail.
 */
final class ShLogsCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION_USE = "shlogs.use";
    private static final String PERMISSION_RELOAD = "shlogs.admin.reload";
    private static final String PERMISSION_LIST = "shlogs.admin.list";
    private static final String PERMISSION_TAIL = "shlogs.admin.tail";

    private static final int DEFAULT_TAIL_LINES = 10;
    private static final int DEFAULT_TAIL_MAX = 20;
    /** Fin de fichier lue pour /shlogs tail : les lignes d'un log sont courtes, 256 Kio couvrent largement le maximum. */
    private static final int TAIL_WINDOW_BYTES = 256 * 1024;

    private final SHLogs plugin;

    ShLogsCommand(SHLogs plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(PERMISSION_RELOAD)) {
                sender.sendMessage(plugin.message("no-permission"));
                return true;
            }
            if (plugin.reload(sender)) {
                sender.sendMessage(plugin.message("reloaded"));
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            if (!sender.hasPermission(PERMISSION_LIST)) {
                sender.sendMessage(plugin.message("no-permission"));
            } else if (args.length > 2) {
                sendUsage(sender);
            } else {
                list(sender, args.length == 2 ? args[1] : null);
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("tail")) {
            if (!sender.hasPermission(PERMISSION_TAIL)) {
                sender.sendMessage(plugin.message("no-permission"));
            } else if (args.length < 3 || args.length > 4) {
                sendUsage(sender);
            } else {
                tail(sender, args[1], args[2], args.length == 4 ? args[3] : null);
            }
            return true;
        }

        if (!sender.hasPermission(PERMISSION_USE)) {
            // Un expéditeur qui n'a que des droits d'administration reçoit l'usage de ce qu'il peut faire
            sendUsage(sender);
            return true;
        }
        if (args.length < 3) {
            sendUsage(sender);
            return true;
        }
        if (!LogWriter.isValidName(args[0]) || !LogWriter.isValidName(args[1])) {
            sender.sendMessage(plugin.message("invalid-name"));
            return true;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (!plugin.writer().add(args[0], args[1], message)) {
            sender.sendMessage(plugin.message("not-written"));
            return true;
        }
        // La console reçoit une ligne par action : seul un joueur a besoin de cette confirmation
        if (sender instanceof Player) {
            sender.sendMessage(plugin.message("line-added").replace("%file%", args[0] + "/" + args[1]));
        }
        return true;
    }

    /** Une seule ligne d'usage, limitée à ce que l'expéditeur a le droit d'utiliser. */
    private void sendUsage(CommandSender sender) {
        List<String> forms = new ArrayList<>();
        if (sender.hasPermission(PERMISSION_USE)) {
            forms.add("<dossier> <fichier> <message>");
        }
        if (sender.hasPermission(PERMISSION_RELOAD)) {
            forms.add("reload");
        }
        if (sender.hasPermission(PERMISSION_LIST)) {
            forms.add("list [dossier]");
        }
        if (sender.hasPermission(PERMISSION_TAIL)) {
            forms.add("tail <dossier> <fichier> [lignes]");
        }
        if (forms.isEmpty()) {
            sender.sendMessage(plugin.message("no-permission"));
            return;
        }
        sender.sendMessage(plugin.message("usage-list").replace("%subcommands%",
                ChatColor.translateAlternateColorCodes('&', String.join(" &8|&7 ", forms))));
    }

    /** /shlogs list [dossier] : dossiers de logs, ou fichiers du mois en cours d'un dossier. */
    private void list(CommandSender sender, String folder) {
        if (folder != null && !LogWriter.isValidName(folder)) {
            sender.sendMessage(plugin.message("invalid-name"));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> lines = new ArrayList<>();
            if (folder == null) {
                File[] folders = plugin.getDataFolder().listFiles(File::isDirectory);
                List<String> names = new ArrayList<>();
                if (folders != null) {
                    for (File entry : folders) {
                        names.add(entry.getName());
                    }
                }
                Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
                if (names.isEmpty()) {
                    lines.add(plugin.message("list-empty"));
                } else {
                    lines.add(plugin.message("list-folders").replace("%count%", String.valueOf(names.size()))
                            .replace("%names%", String.join(", ", names)));
                }
            } else {
                String month = plugin.writer().currentMonthName();
                List<String> files = filesOfMonth(folder);
                if (files.isEmpty()) {
                    lines.add(plugin.message("list-folder-empty").replace("%folder%", folder).replace("%month%", month));
                } else {
                    lines.add(plugin.message("list-files").replace("%folder%", folder).replace("%month%", month)
                            .replace("%count%", String.valueOf(files.size())));
                    lines.addAll(files);
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> lines.forEach(sender::sendMessage));
        });
    }

    /** Lignes « fichier (taille) » du dossier du mois en cours, triées par nom. */
    private List<String> filesOfMonth(String folder) {
        List<String> lines = new ArrayList<>();
        String extension = "." + plugin.writer().extension();
        try (Stream<Path> entries = Files.list(plugin.writer().currentMonthFolder(folder))) {
            entries.filter(Files::isRegularFile)
                    .sorted((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()))
                    .forEach(path -> {
                        String name = path.getFileName().toString();
                        if (name.endsWith(extension)) {
                            name = name.substring(0, name.length() - extension.length());
                        }
                        try {
                            lines.add(plugin.message("list-file-line").replace("%file%", name)
                                    .replace("%size%", size(Files.size(path))));
                        } catch (IOException ignored) {
                            // fichier supprimé entre-temps : il n'apparaît pas
                        }
                    });
        } catch (IOException | RuntimeException e) {
            // dossier absent pour ce mois : liste vide
        }
        return lines;
    }

    private static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " o";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f Kio", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f Mio", bytes / (1024.0 * 1024.0));
    }

    /** /shlogs tail <dossier> <fichier> [lignes] : dernières lignes du fichier du mois en cours. */
    private void tail(CommandSender sender, String folder, String file, String linesArgument) {
        if (!LogWriter.isValidName(folder) || !LogWriter.isValidName(file)) {
            sender.sendMessage(plugin.message("invalid-name"));
            return;
        }
        int max = Math.max(1, plugin.getConfig().getInt("tail-max-lines", DEFAULT_TAIL_MAX));
        int wanted = DEFAULT_TAIL_LINES;
        if (linesArgument != null) {
            try {
                wanted = Integer.parseInt(linesArgument);
            } catch (NumberFormatException e) {
                sendUsage(sender);
                return;
            }
        }
        int count = Math.max(1, Math.min(wanted, max));
        String shown = folder + "/" + file;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> lines = new ArrayList<>();
            Path path = plugin.writer().currentMonthFolder(folder).resolve(file + "." + plugin.writer().extension());
            try {
                if (!Files.isRegularFile(path)) {
                    lines.add(plugin.message("tail-not-found").replace("%file%", shown));
                } else {
                    List<String> last = lastLines(path, count);
                    lines.add(plugin.message("tail-header").replace("%file%", shown)
                            .replace("%count%", String.valueOf(last.size())));
                    for (String line : last) {
                        lines.add(plugin.message("tail-line").replace("%line%", line));
                    }
                }
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().warning("Lecture de " + path + " impossible : " + e.getMessage());
                lines.add(plugin.message("tail-error").replace("%file%", shown));
            }
            Bukkit.getScheduler().runTask(plugin, () -> lines.forEach(sender::sendMessage));
        });
    }

    /** Dernières lignes d'un fichier UTF-8, lues depuis la fin sans charger le fichier entier. */
    static List<String> lastLines(Path path, int count) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            long length = file.length();
            int window = (int) Math.min(length, TAIL_WINDOW_BYTES);
            byte[] buffer = new byte[window];
            file.seek(length - window);
            file.readFully(buffer);
            String text = new String(buffer, StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>(Arrays.asList(text.split("\r?\n")));
            if (length > window && !lines.isEmpty()) {
                lines.remove(0); // première ligne probablement coupée
            }
            lines.removeIf(String::isEmpty);
            return lines.size() <= count ? lines : new ArrayList<>(lines.subList(lines.size() - count, lines.size()));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission(PERMISSION_RELOAD)) {
                completions.add("reload");
            }
            if (sender.hasPermission(PERMISSION_LIST)) {
                completions.add("list");
            }
            if (sender.hasPermission(PERMISSION_TAIL)) {
                completions.add("tail");
            }
            if (sender.hasPermission(PERMISSION_USE)) {
                completions.addAll(folderNames());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("list") && sender.hasPermission(PERMISSION_LIST)) {
            completions.addAll(folderNames());
        } else if (args[0].equalsIgnoreCase("tail") && sender.hasPermission(PERMISSION_TAIL)) {
            if (args.length == 2) {
                completions.addAll(folderNames());
            } else if (args.length == 3 && LogWriter.isValidName(args[1])) {
                completions.addAll(fileNames(args[1]));
            }
        }
        completions.removeIf(name -> !name.toLowerCase(Locale.ROOT).startsWith(typed));
        Collections.sort(completions);
        return completions;
    }

    private List<String> folderNames() {
        List<String> names = new ArrayList<>();
        File[] folders = plugin.getDataFolder().listFiles(File::isDirectory);
        if (folders != null) {
            for (File folder : folders) {
                names.add(folder.getName());
            }
        }
        return names;
    }

    private List<String> fileNames(String folder) {
        List<String> names = new ArrayList<>();
        String extension = "." + plugin.writer().extension();
        File[] files = plugin.writer().currentMonthFolder(folder).toFile().listFiles(File::isFile);
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                names.add(name.endsWith(extension) ? name.substring(0, name.length() - extension.length()) : name);
            }
        }
        return names;
    }
}
