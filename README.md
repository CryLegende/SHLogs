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
| `/shlogs reload` | — | Recharge `config.yml` et `messages.yml` | `shlogs.admin.reload` |
| `/shlogs list [dossier]` | — | Liste les dossiers de logs, ou les fichiers du mois en cours d'un dossier | `shlogs.admin.list` |
| `/shlogs tail <dossier> <fichier> [lignes]` | — | Affiche les dernières lignes du fichier du mois en cours (10 par défaut, `tail-max-lines` au plus) | `shlogs.admin.tail` |

Le fichier s'écrit sans extension. Une ligne ajoutée depuis la console ne renvoie aucune réponse. `list` et `tail` sont réservés : ils ne peuvent pas servir de nom de dossier avec la première forme. Un reload dont un fichier est invalide ne change rien et donne le fichier et l'erreur. `/shlogs` sans argument affiche une ligne d'usage limitée aux formes autorisées.

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shlogs.use` | Autorise l'ajout d'une ligne avec `/shlogs <dossier> <fichier> <message>` | `op` |
| `shlogs.admin` | Toutes les sous-commandes d'administration (parent de `shlogs.admin.reload`, `.list`, `.tail`) | `op` |
| `shlogs.admin.reload` | Autorise `/shlogs reload` | `op` |
| `shlogs.admin.list` | Autorise `/shlogs list` | `op` |
| `shlogs.admin.tail` | Autorise `/shlogs tail` | `op` |

## API

Dépendance Maven, après un `mvn install` de SHLogs :

```xml
<dependency>
    <groupId>org.Cry_Legende</groupId>
    <artifactId>SHLogs</artifactId>
    <version>2.1</version>
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
| `tail-max-lines` | Nombre maximal de lignes affichées par `/shlogs tail` |

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
