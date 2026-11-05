<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHLogs

*Centralise les logs d'actions des plugins du serveur, par plugin et par mois.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Centralise les logs d'actions des plugins du serveur. Les plugins SH écrivent avec `SHLogsAPI`, les plugins externes avec la commande `/shlogs`.

```
plugins/SHLogs/
├── SHCrates/
│   └── 2026-11/
│       ├── Box_Ordinaire.yml
│       └── Box_Rare.yml
└── CrazyVouchers/
    └── 2026-11/
        └── vouchers.yml
```

Une ligne contient la date et le message :

```
[04/11/2026 22:23:28] Nina gagne le titre Farm2Win
```

Les écritures passent par un seul thread : l'appelant n'attend jamais le disque et les lignes restent dans l'ordre. La date est prise au moment de l'appel. Les codes couleur et les retours à la ligne sont retirés des messages.

Utilisé sur : **Opale**.

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/shlogs <dossier> <fichier> <message>` | — | Ajoute une ligne dans un fichier de log | `shlogs.use` |
| `/shlogs reload` | — | Recharge `config.yml` et `messages.yml` | `shlogs.reload` |

Le fichier s'écrit sans extension. Une ligne ajoutée depuis la console ne renvoie aucune réponse.

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shlogs.use` | Autorise l'ajout d'une ligne avec `/shlogs` | `op` |
| `shlogs.reload` | Autorise `/shlogs reload` | `op` |

## API

Dépendance Maven, après un `mvn install` de SHLogs :

```xml
<dependency>
    <groupId>org.Cry_Legende</groupId>
    <artifactId>SHLogs</artifactId>
    <version>2.0</version>
    <scope>provided</scope>
</dependency>
```

Dans `plugin.yml` :

```yaml
softdepend:
  - SHLogs
```

L'appel passe par une petite classe du plugin, qui lui permet de démarrer sur un serveur sans SHLogs :

```java
public final class ActionLog {

    private ActionLog() {
    }

    public static void write(Plugin plugin, String file, String message) {
        if (Bukkit.getPluginManager().isPluginEnabled("SHLogs")) {
            SHLogsAPI.log(plugin, file, message);
        } else {
            plugin.getLogger().info(file + " : " + message);
        }
    }
}
```

`SHLogsAPI.log` s'utilise depuis n'importe quel thread. Le dossier est le nom du plugin appelant.

## Configuration

Fichier `config.yml`, généré au premier lancement.

| Option | Description |
|---|---|
| `line-date-format` | Format de la date en début de ligne |
| `month-folder-format` | Nom du dossier de chaque mois |
| `file-extension` | Extension des fichiers de log |
| `max-pending-lines` | Lignes en attente au-delà desquelles les nouvelles sont ignorées |
| `shutdown-timeout-seconds` | Secondes laissées aux écritures pendant l'arrêt |

## Compilation

Java 25 et Maven :

```bash
mvn clean install
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
