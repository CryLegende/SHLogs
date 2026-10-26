package org.Cry_Legende.sHLogs;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class SHLogs extends JavaPlugin implements CommandExecutor {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // Un seul thread d'écriture : les lignes restent dans l'ordre des commandes et le thread principal n'attend pas le disque
    private ExecutorService writer;

    @Override
    public void onEnable() {
        // Crée le dossier data du plugin si besoin
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        writer = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "SHLogs-writer");
            thread.setDaemon(true);
            return thread;
        });
        // Enregistre la commande
        this.getCommand("shlogs").setExecutor(this);
        getLogger().info("SHLogs activé !");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /shlogs <fichier_sans_extention> <message>");
            return true;
        }

        // Le nom vient de la commande : il ne doit pas pouvoir sortir du dossier du plugin (« ../.. », séparateurs)
        if (!isValidFileName(args[0])) {
            sender.sendMessage("Nom de fichier invalide.");
            return true;
        }

        String fileName = args[0] + ".yml";
        // Concatène le reste des arguments comme message
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        File file = new File(getDataFolder(), fileName);
        // Prépare le timestamp au moment de la commande, pas au moment de l'écriture
        String line = String.format("[%s] %s", LocalDateTime.now().format(FORMATTER), message) + System.lineSeparator();

        try {
            writer.execute(() -> {
                try {
                    // Crée le fichier s'il n'existe pas et ajoute la ligne
                    Files.writeString(file.toPath(), line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    sender.sendMessage("Message ajouté dans " + fileName);
                } catch (IOException e) {
                    sender.sendMessage("Erreur lors de l'écriture du fichier: " + e.getMessage());
                    getLogger().severe("Impossible de sauvegarder " + fileName);
                }
            });
        } catch (RejectedExecutionException e) {
            sender.sendMessage("Erreur lors de l'écriture du fichier: plugin en cours d'arrêt");
        }
        return true;
    }

    /** Un simple nom de fichier : ni séparateur de dossier, ni « .. », ni caractère de contrôle. */
    private static boolean isValidFileName(String name) {
        if (name.isEmpty() || name.equals(".") || name.equals("..")) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '/' || c == '\\' || c < 32) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onDisable() {
        // Laisse finir les écritures en attente avant l'arrêt
        if (writer != null) {
            writer.shutdown();
            try {
                if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                    getLogger().warning("Des écritures de log n'ont pas pu être terminées.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        getLogger().info("SHLogs désactivé.");
    }
}