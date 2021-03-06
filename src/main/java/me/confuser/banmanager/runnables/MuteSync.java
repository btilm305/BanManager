package me.confuser.banmanager.runnables;

import com.j256.ormlite.dao.CloseableIterator;
import lombok.Getter;
import me.confuser.banmanager.BanManager;
import me.confuser.banmanager.data.PlayerMuteData;
import me.confuser.banmanager.data.PlayerMuteRecord;
import me.confuser.banmanager.storage.PlayerMuteStorage;

import java.sql.SQLException;

public class MuteSync implements Runnable {

  private BanManager plugin = BanManager.getPlugin();
  private PlayerMuteStorage muteStorage = plugin.getPlayerMuteStorage();
  private long lastChecked = 0;
  @Getter
  private boolean isRunning = false;

  public MuteSync() {
    lastChecked = plugin.getSchedulesConfig().getLastChecked("playerMutes");
  }

  @Override
  public void run() {
    isRunning = true;
    // New/updated mutes check
    try {
      newMutes();
    } catch (SQLException e) {
      e.printStackTrace();
    }

    // New unbans
    try {
      newUnmutes();
    } catch (SQLException e) {
      e.printStackTrace();
    }

    lastChecked = System.currentTimeMillis() / 1000L;
    plugin.getSchedulesConfig().setLastChecked("playerMutes", lastChecked);
    isRunning = false;
  }

  private void newMutes() throws SQLException {

    CloseableIterator<PlayerMuteData> itr = muteStorage.findMutes(lastChecked);

    while (itr.hasNext()) {
      final PlayerMuteData mute = itr.next();

      if (muteStorage.isMuted(mute.getPlayer().getUUID()) && mute.getUpdated() < lastChecked) {
        continue;
      }

      muteStorage.addMute(mute);

    }

    itr.close();
  }

  private void newUnmutes() throws SQLException {

    CloseableIterator<PlayerMuteRecord> itr = plugin.getPlayerMuteRecordStorage().findUnmutes(lastChecked);

    while (itr.hasNext()) {
      final PlayerMuteRecord mute = itr.next();

      if (!muteStorage.isMuted(mute.getPlayer().getUUID())) {
        continue;
      }

      if (!mute.equalsMute(muteStorage.getMute(mute.getPlayer().getUUID()))) {
        continue;
      }

      muteStorage.removeMute(mute.getPlayer().getUUID());

    }

    itr.close();
  }
}
