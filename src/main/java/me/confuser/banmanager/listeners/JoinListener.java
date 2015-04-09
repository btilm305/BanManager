package me.confuser.banmanager.listeners;

import com.j256.ormlite.dao.CloseableIterator;
import me.confuser.banmanager.BanManager;
import me.confuser.banmanager.data.*;
import me.confuser.banmanager.util.DateUtils;
import me.confuser.banmanager.util.IPUtils;
import me.confuser.bukkitutil.Message;
import me.confuser.bukkitutil.listeners.Listeners;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class JoinListener extends Listeners<BanManager> {

  @EventHandler(priority = EventPriority.HIGHEST)
  public void banCheck(final AsyncPlayerPreLoginEvent event) {
    if (plugin.getIpRangeBanStorage().isBanned(event.getAddress())) {
      IpRangeBanData data = plugin.getIpRangeBanStorage().getBan(event.getAddress());

      if (data.hasExpired()) {
        try {
          plugin.getIpRangeBanStorage().unban(data, plugin.getPlayerStorage().getConsole());
        } catch (SQLException e) {
          e.printStackTrace();
        }

        return;
      }

      Message message;

      if (data.getExpires() == 0) {
        message = Message.get("baniprange.ip.disallowed");
      } else {
        message = Message.get("tempbaniprange.ip.disallowed");
        message.set("expires", DateUtils.getDifferenceFormat(data.getExpires()));
      }

      message.set("ip", event.getAddress().toString());
      message.set("reason", data.getReason());
      message.set("actor", data.getActor().getName());

      event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_BANNED);
      event.setKickMessage(message.toString());
      return;
    }

    if (plugin.getIpBanStorage().isBanned(event.getAddress())) {
      IpBanData data = plugin.getIpBanStorage().getBan(event.getAddress());

      if (data.hasExpired()) {
        try {
          plugin.getIpBanStorage().unban(data, plugin.getPlayerStorage().getConsole());
        } catch (SQLException e) {
          e.printStackTrace();
        }

        return;
      }

      Message message;

      if (data.getExpires() == 0) {
        message = Message.get("banip.ip.disallowed");
      } else {
        message = Message.get("tempbanip.ip.disallowed");
        message.set("expires", DateUtils.getDifferenceFormat(data.getExpires()));
      }

      message.set("ip", event.getAddress().toString());
      message.set("reason", data.getReason());
      message.set("actor", data.getActor().getName());

      event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_BANNED);
      event.setKickMessage(message.toString());
      return;
    }

    PlayerBanData data = plugin.getPlayerBanStorage().getBan(event.getUniqueId());

    if (data != null && data.hasExpired()) {
      try {
        plugin.getPlayerBanStorage().unban(data, plugin.getPlayerStorage().getConsole());
      } catch (SQLException e) {
        e.printStackTrace();
      }

      return;
    }

    if (data == null) {
      return;
    }

    Message message;

    if (data.getExpires() == 0) {
      message = Message.get("ban.player.disallowed");
    } else {
      message = Message.get("tempban.player.disallowed");
      message.set("expires", DateUtils.getDifferenceFormat(data.getExpires()));
    }

    message.set("player", data.getPlayer().getName());
    message.set("reason", data.getReason());
    message.set("actor", data.getActor().getName());

    event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_BANNED);
    event.setKickMessage(message.toString());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onJoin(final PlayerJoinEvent event) {
    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {

      @Override
      public void run() {
        PlayerData player = new PlayerData(event.getPlayer());

        try {
          plugin.getPlayerStorage().createOrUpdate(player);
        } catch (SQLException e) {
          e.printStackTrace();
        }

        plugin.getPlayerStorage().addOnline(player);
      }

    });

    plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {

      public void run() {
        // Handle quick disconnects
        if (event.getPlayer() == null || !event.getPlayer().isOnline()) {
          return;
        }

        PlayerData onlinePlayer = plugin.getPlayerStorage().getOnline(event.getPlayer());

        if (onlinePlayer == null) {
          return;
        }

        CloseableIterator<PlayerWarnData> warnings;
        try {
          warnings = plugin.getPlayerWarnStorage().getUnreadWarnings(onlinePlayer);

          while (warnings.hasNext()) {
            PlayerWarnData warning = warnings.next();

            Message.get("warn.player.warned")
                   .set("displayName", event.getPlayer().getDisplayName())
                   .set("player", event.getPlayer().getName())
                   .set("reason", warning.getReason())
                   .set("actor", warning.getActor().getName())
                   .sendTo(event.getPlayer());

            warning.setRead(true);
            // TODO Move to one update query to set all warnings for player to read
            plugin.getPlayerWarnStorage().update(warning);
          }

          warnings.close();
          warnings = null;
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
    }, 20L);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerLogin(final PlayerLoginEvent event) {
    if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
      return;
    }

    if (!plugin.getConfiguration().isDuplicateIpCheckEnabled()) {
      return;
    }

    plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {

      public void run() {
        final long ip = IPUtils.toLong(event.getAddress());
        final UUID uuid = event.getPlayer().getUniqueId();
        List<PlayerData> duplicates = plugin.getPlayerBanStorage().getDuplicates(ip);

        if (duplicates.isEmpty()) {
          return;
        }

        StringBuilder sb = new StringBuilder();

        for (PlayerData player : duplicates) {
          if (player.getUUID().equals(uuid)) {
            continue;
          }

          sb.append(player.getName());
          sb.append(", ");
        }

        if (sb.length() == 0) return;
        if (sb.length() >= 2) sb.setLength(sb.length() - 2);

        Message message = Message.get("duplicateIP");
        message.set("player", event.getPlayer().getName());
        message.set("players", sb.toString());

        plugin.getServer().broadcast(message.toString(), "bm.notify.duplicateips");
      }
    }, 20L);
  }
}
