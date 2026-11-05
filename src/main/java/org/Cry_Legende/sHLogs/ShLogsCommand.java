package org.Cry_Legende.sHLogs;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** /shlogs <dossier> <fichier> <message>, pour les plugins qui n'ont que des commandes. */
final class ShLogsCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION_USE = "shlogs.use";
    private static final String PERMISSION_RELOAD = "shlogs.reload";

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
            plugin.reload();
            sender.sendMessage(plugin.message("reloaded"));
            return true;
        }

        if (!sender.hasPermission(PERMISSION_USE)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.message("usage"));
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission(PERMISSION_USE)) {
            return Collections.emptyList();
        }
        List<String> completions = new ArrayList<>();
        completions.add("reload");
        File[] folders = plugin.getDataFolder().listFiles(File::isDirectory);
        if (folders != null) {
            for (File folder : folders) {
                completions.add(folder.getName());
            }
        }
        String typed = args[0].toLowerCase(Locale.ROOT);
        completions.removeIf(name -> !name.toLowerCase(Locale.ROOT).startsWith(typed));
        Collections.sort(completions);
        return completions;
    }
}
