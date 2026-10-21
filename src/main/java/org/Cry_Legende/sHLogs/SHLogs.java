package org.Cry_Legende.sHLogs;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SHLogs extends JavaPlugin implements CommandExecutor {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Override
    public void onEnable() {
        // Crée le dossier data du plugin si besoin
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
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

        String fileName = args[0] + ".yml";
        // Concatène le reste des arguments comme message
        StringBuilder msgBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            msgBuilder.append(args[i]);
            if (i < args.length - 1) msgBuilder.append(" ");
        }
        String message = msgBuilder.toString();

        File file = new File(getDataFolder(), fileName);
        try {
            // Crée le fichier s'il n'existe pas
            if (!file.exists()) {
                file.createNewFile();
            }

            // Prépare le timestamp
            String timestamp = LocalDateTime.now().format(FORMATTER);
            String line = String.format("[%s] %s", timestamp, message);

            // Ajoute la ligne
            FileWriter writer = new FileWriter(file, true);
            writer.write(line);
            writer.write(System.lineSeparator());
            writer.close();

            sender.sendMessage("Message ajouté dans " + fileName);
        } catch (IOException e) {
            sender.sendMessage("Erreur lors de l'écriture du fichier: " + e.getMessage());
            getLogger().severe("Impossible de sauvegarder " + fileName);
        }
        return true;
    }

    @Override
    public void onDisable() {
        getLogger().info("SHLogs désactivé.");
    }
}