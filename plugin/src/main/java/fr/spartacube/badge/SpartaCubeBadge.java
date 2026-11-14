package fr.spartacube.badge;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Relaie entre les clients qui utilisent le launcher SpartaCube la liste des joueurs équipés,
 * pour afficher la pastille « S » dans la tab. Aucune donnée n'est stockée sur disque.
 */
public final class SpartaCubeBadge extends JavaPlugin implements PluginMessageListener, Listener {
    static final String HELLO = "spartacube:hello";
    static final String USERS = "spartacube:users";
    static final String TOAST = "spartacube:toast";
    static final String COS_SELECT = "spartacube:cosmetic_select";
    static final String COS_ALLOWED = "spartacube:cosmetic_allowed";
    static final String COS_ACTIVE = "spartacube:cosmetics";
    static final int WINGS = 1, AURA = 2;

    private final Set<UUID> users = ConcurrentHashMap.newKeySet();
    private final java.util.Map<UUID, Integer> cosmetics = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        getServer().getMessenger().registerIncomingPluginChannel(this, HELLO, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, USERS);
        getServer().getMessenger().registerOutgoingPluginChannel(this, TOAST);
        getServer().getMessenger().registerIncomingPluginChannel(this, COS_SELECT, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, COS_ALLOWED);
        getServer().getMessenger().registerOutgoingPluginChannel(this, COS_ACTIVE);
        saveDefaultConfig();
        // Ajoute les nouvelles options (admins, news-api) à un config.yml existant
        getConfig().options().copyDefaults(true);
        saveConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Pastilles du launcher SpartaCube actives.");
        startNewsApi();
    }

    private NewsApi newsApi;

    private void startNewsApi() {
        if (!getConfig().getBoolean("news-api.enabled", true)) return;
        java.util.Set<String> admins = new java.util.HashSet<>();
        for (String a : getConfig().getStringList("admins")) admins.add(a.trim().toLowerCase(java.util.Locale.ROOT));
        newsApi = new NewsApi(getLogger(), getDataFolder().toPath(), admins);
        newsApi.init();
        String mode = getConfig().getString("news-api.mode", "minecraft");
        if ("port".equalsIgnoreCase(mode)) {
            int port = getConfig().getInt("news-api.port", 8765);
            try {
                newsApi.start(getConfig().getString("news-api.bind", "0.0.0.0"), port);
            } catch (Exception e) {
                getLogger().warning("Impossible de démarrer l'API des actualités sur le port " + port + " : " + e.getMessage());
            }
        } else {
            sharedHttp = new SharedPortHttp(newsApi, getLogger());
            if (sharedHttp.register()) getLogger().info("API des actualités du launcher active sur le port du serveur Minecraft.");
            else sharedHttp = null;
        }
    }

    private SharedPortHttp sharedHttp;

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        users.clear();
        if (newsApi != null) newsApi.stop();
        if (sharedHttp != null) sharedHttp.unregister();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (message.length > 256) return;
        if (HELLO.equals(channel)) {
            if (users.add(player.getUniqueId())) {
                getLogger().info(player.getName() + " utilise le launcher SpartaCube.");
                // petit délai : le client doit avoir fini d'enregistrer ses canaux
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    broadcast();
                    player.sendPluginMessage(this, COS_ALLOWED, varInt(allowedFlags(player)));
                    broadcastCosmetics();
                }, 10L);
            }
        } else if (COS_SELECT.equals(channel) && users.contains(player.getUniqueId())) {
            int wanted = readVarInt(message);
            int flags = wanted & allowedFlags(player); // le serveur vérifie les droits
            if (flags == 0) cosmetics.remove(player.getUniqueId()); else cosmetics.put(player.getUniqueId(), flags);
            broadcastCosmetics();
        }
    }

    /** Droits : permissions spartacube.cosmetic.wings / .aura, ou listes UUID/pseudos de config.yml. */
    private int allowedFlags(Player p) {
        int f = 0;
        if (p.hasPermission("spartacube.cosmetic.wings") || listed(p, "cosmetics.wings")) f |= WINGS;
        if (p.hasPermission("spartacube.cosmetic.aura") || listed(p, "cosmetics.aura")) f |= AURA;
        return f;
    }

    private boolean listed(Player p, String path) {
        for (String s : getConfig().getStringList(path)) {
            if (s.equalsIgnoreCase(p.getUniqueId().toString()) || s.equalsIgnoreCase(p.getName())) return true;
        }
        return false;
    }

    private void broadcastCosmetics() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        List<java.util.Map.Entry<UUID, Integer>> list = new ArrayList<>();
        for (java.util.Map.Entry<UUID, Integer> e : cosmetics.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null && p.isOnline()) list.add(e); else cosmetics.remove(e.getKey());
        }
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writeVarInt(out, list.size());
            for (java.util.Map.Entry<UUID, Integer> e : list) {
                out.writeLong(e.getKey().getMostSignificantBits());
                out.writeLong(e.getKey().getLeastSignificantBits());
                writeVarInt(out, e.getValue());
            }
        } catch (IOException ignored) {
            return;
        }
        byte[] data = bytes.toByteArray();
        for (UUID id : users) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendPluginMessage(this, COS_ACTIVE, data);
        }
    }

    private static void writeVarInt(DataOutputStream out, int n) throws IOException {
        while ((n & ~0x7F) != 0) { out.writeByte((n & 0x7F) | 0x80); n >>>= 7; }
        out.writeByte(n);
    }

    private static byte[] varInt(int n) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(b)) { writeVarInt(out, n); } catch (IOException ignored) { }
        return b.toByteArray();
    }

    private static int readVarInt(byte[] data) {
        int value = 0, shift = 0;
        for (byte b : data) {
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
            shift += 7;
            if (shift > 28) break;
        }
        return 0;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cosmetics.remove(e.getPlayer().getUniqueId());
        if (users.remove(e.getPlayer().getUniqueId())) Bukkit.getScheduler().runTask(this, () -> { broadcast(); broadcastCosmetics(); });
    }

    private void broadcast() {
        List<Player> online = new ArrayList<>();
        for (UUID id : users) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) online.add(p); else users.remove(id);
        }
        byte[] data = encode(online);
        for (Player p : online) p.sendPluginMessage(this, USERS, data);
    }

    /** VarInt (nombre) puis chaque UUID en deux longs big-endian (format Uuids.PACKET_CODEC). */
    private static byte[] encode(List<Player> players) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            int n = players.size();
            while ((n & ~0x7F) != 0) { out.writeByte((n & 0x7F) | 0x80); n >>>= 7; }
            out.writeByte(n);
            for (Player p : players) {
                out.writeLong(p.getUniqueId().getMostSignificantBits());
                out.writeLong(p.getUniqueId().getLeastSignificantBits());
            }
        } catch (IOException ignored) {
            // impossible avec un flux en mémoire
        }
        return bytes.toByteArray();
    }

    /** VarInt + UTF-8 (format PacketCodecs.string). */
    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int n = b.length;
        while ((n & ~0x7F) != 0) { out.writeByte((n & 0x7F) | 0x80); n >>>= 7; }
        out.writeByte(n);
        out.write(b);
    }

    /**
     * Notification animée chez les joueurs équipés du launcher.
     * Utilisable par d'autres plugins : Bukkit.getPluginManager().getPlugin("SpartaCubeBadge") puis sendToast(...).
     */
    public int sendToast(Player target, String type, String title, String text) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writeString(out, type.length() > 16 ? type.substring(0, 16) : type);
            writeString(out, title.length() > 64 ? title.substring(0, 64) : title);
            writeString(out, text.length() > 256 ? text.substring(0, 256) : text);
        } catch (IOException ignored) {
            return 0;
        }
        byte[] data = bytes.toByteArray();
        if (target != null) {
            if (!users.contains(target.getUniqueId())) return 0;
            target.sendPluginMessage(this, TOAST, data);
            return 1;
        }
        int n = 0;
        for (UUID id : users) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) { p.sendPluginMessage(this, TOAST, data); n++; }
        }
        return n;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("sctoast")) {
            // /sctoast <joueur|all> <info|event|vote|boss|staff> <titre> | <message>
            if (args.length < 3) {
                sender.sendMessage("§cUsage : /sctoast <joueur|all> <info|event|vote|boss|staff> <titre> | <message>");
                return true;
            }
            String rest = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            String[] parts = rest.split("\\|", 2);
            String title = parts[0].trim();
            String text = parts.length > 1 ? parts[1].trim() : "";
            Player target = args[0].equalsIgnoreCase("all") ? null : Bukkit.getPlayerExact(args[0]);
            if (!args[0].equalsIgnoreCase("all") && target == null) {
                sender.sendMessage("§cJoueur introuvable : " + args[0]);
                return true;
            }
            int n = sendToast(target, args[1].toLowerCase(java.util.Locale.ROOT), title, text);
            if (n == 0) sender.sendMessage("§c[SpartaCube] Aucun joueur avec le launcher n'a reçu la notification (vérifie /launcherusers).");
            else sender.sendMessage("§6[SpartaCube] §fNotification envoyée à §e" + n + "§f joueur(s) du launcher.");
            return true;
        }
        List<String> names = new ArrayList<>();
        for (UUID id : users) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) names.add(p.getName());
        }
        sender.sendMessage("§6[SpartaCube] §f" + names.size() + " joueur(s) avec le launcher : §e" + String.join("§7, §e", names));
        return true;
    }
}
