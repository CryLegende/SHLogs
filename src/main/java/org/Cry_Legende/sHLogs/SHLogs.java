package org.Cry_Legende.sHLogs;

import org.Cry_Legende.sHLogs.api.SHLogsAPI;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class SHLogs extends JavaPlugin {

    private static final String MESSAGES_FILE = "messages.yml";

    private LogWriter writer;
    private YamlConfiguration messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), MESSAGES_FILE).exists()) {
            saveResource(MESSAGES_FILE, false);
        }
        loadMessages();

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

    void reload() {
        reloadConfig();
        loadMessages();
        writer.configure(getConfig());
    }

    private void loadMessages() {
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), MESSAGES_FILE));
    }

    LogWriter writer() {
        return writer;
    }

    String message(String key) {
        return ChatColor.translateAlternateColorCodes('&', messages.getString(key, key));
    }
}
