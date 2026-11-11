package org.Cry_Legende.sHLogs;

import org.Cry_Legende.sHLogs.api.SHLogsAPI;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class SHLogs extends JavaPlugin {

    private static final String CONFIG_FILE = "config.yml";
    private static final String MESSAGES_FILE = "messages.yml";

    private LogWriter writer;
    private volatile YamlConfiguration messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), MESSAGES_FILE).exists()) {
            saveResource(MESSAGES_FILE, false);
        }
        messages = readMessages(new File(getDataFolder(), MESSAGES_FILE));

        writer = new LogWriter(this);
        SHLogsAPI.bind(writer);

        ShLogsCommand command = new ShLogsCommand(this);
        PluginCommand shlogs = getCommand("shlogs");
        shlogs.setExecutor(command);
        shlogs.setTabCompleter(command);
    }

    @Override
    public void onDisable() {
        SHLogsAPI.bind(null);
        if (writer != null) {
            writer.stop(getConfig().getLong("shutdown-timeout-seconds", 5L));
        }
    }

    /**
     * Relit config.yml et messages.yml. Un fichier au YAML invalide est refusé avant toute application : l'ancienne
     * configuration reste en mémoire et l'expéditeur reçoit le nom du fichier et l'erreur. Les fichiers de logs ne
     * sont jamais touchés.
     *
     * @return vrai si tout a été rechargé
     */
    boolean reload(CommandSender sender) {
        YamlConfiguration newMessages = parse(sender, CONFIG_FILE);
        if (newMessages == null) {
            return false;
        }
        newMessages = parse(sender, MESSAGES_FILE);
        if (newMessages == null) {
            return false;
        }
        reloadConfig();
        messages = withDefaults(newMessages);
        writer.configure(getConfig());
        return true;
    }

    /** Lit un fichier du plugin sans rien appliquer ; en cas d'erreur, prévient l'expéditeur et renvoie null. */
    private YamlConfiguration parse(CommandSender sender, String name) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(new File(getDataFolder(), name));
            return yaml;
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().severe("Rechargement annulé, " + name + " est invalide : " + e.getMessage());
            sender.sendMessage(message("reload-failed").replace("%file%", name).replace("%error%", firstLine(e)));
            return null;
        }
    }

    private static String firstLine(Exception e) {
        String text = String.valueOf(e.getMessage()).strip();
        int end = text.indexOf('\n');
        String line = end < 0 ? text : text.substring(0, end).strip();
        return line.length() > 160 ? line.substring(0, 160) + "..." : line;
    }

    private YamlConfiguration readMessages(File file) {
        return withDefaults(YamlConfiguration.loadConfiguration(file));
    }

    /** Les clés absentes du messages.yml du serveur reprennent le texte du jar. */
    private YamlConfiguration withDefaults(YamlConfiguration loaded) {
        try (Reader defaults = new InputStreamReader(getResource(MESSAGES_FILE), StandardCharsets.UTF_8)) {
            loaded.setDefaults(YamlConfiguration.loadConfiguration(defaults));
        } catch (IOException | RuntimeException e) {
            getLogger().warning("messages.yml du jar illisible : " + e.getMessage());
        }
        return loaded;
    }

    LogWriter writer() {
        return writer;
    }

    String message(String key) {
        return ChatColor.translateAlternateColorCodes('&', messages.getString(key, key));
    }
}
