package org.Cry_Legende.sHLogs.api;

import org.Cry_Legende.sHLogs.LogWriter;
import org.bukkit.plugin.Plugin;

/**
 * Point d'entrée pour les plugins SH. Les appels sont utilisables depuis n'importe quel thread
 * et n'attendent jamais le disque.
 *
 * <pre>SHLogsAPI.log(plugin, "ventes", "Nina -> 1 Diamant vendu pour 500 SHCoins");</pre>
 */
public final class SHLogsAPI {

    private static volatile LogWriter writer;

    private SHLogsAPI() {
    }

    /** Écrit dans le dossier du plugin appelant. */
    public static boolean log(Plugin plugin, String file, String message) {
        return log(plugin.getName(), file, message);
    }

    /** Écrit dans un dossier choisi, pour un log qui n'appartient pas au plugin appelant. */
    public static boolean log(String folder, String file, String message) {
        LogWriter current = writer;
        return current != null && current.add(folder, file, message);
    }

    /** Réservé à SHLogs : branche ou débranche l'écriture. */
    public static void bind(LogWriter logWriter) {
        writer = logWriter;
    }
}
