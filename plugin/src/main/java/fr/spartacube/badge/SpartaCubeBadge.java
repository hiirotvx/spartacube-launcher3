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

    private final Set<UUID> users = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        getServer().getMessenger().registerIncomingPluginChannel(this, HELLO, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, USERS);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Pastilles du launcher SpartaCube actives.");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        users.clear();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!HELLO.equals(channel) || message.length > 256) return;
        if (users.add(player.getUniqueId())) {
            // petit délai : le client doit avoir fini d'enregistrer ses canaux
            Bukkit.getScheduler().runTaskLater(this, this::broadcast, 10L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (users.remove(e.getPlayer().getUniqueId())) Bukkit.getScheduler().runTask(this, this::broadcast);
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<String> names = new ArrayList<>();
        for (UUID id : users) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) names.add(p.getName());
        }
        sender.sendMessage("§6[SpartaCube] §f" + names.size() + " joueur(s) avec le launcher : §e" + String.join("§7, §e", names));
        return true;
    }
}
