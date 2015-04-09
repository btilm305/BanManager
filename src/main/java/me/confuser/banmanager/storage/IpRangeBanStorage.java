package me.confuser.banmanager.storage;

import com.j256.ormlite.dao.BaseDaoImpl;
import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.DatabaseTableConfig;
import com.j256.ormlite.table.TableUtils;
import me.confuser.banmanager.BanManager;
import me.confuser.banmanager.data.IpRangeBanData;
import me.confuser.banmanager.data.PlayerData;
import me.confuser.banmanager.events.IpRangeBanEvent;
import me.confuser.banmanager.events.IpRangeUnbanEvent;
import me.confuser.banmanager.util.DateUtils;
import me.confuser.banmanager.util.IPUtils;
import net.minecraft.util.com.google.common.collect.Range;
import net.minecraft.util.com.google.common.collect.RangeSet;
import net.minecraft.util.com.google.common.collect.TreeRangeSet;
import org.bukkit.Bukkit;

import java.net.InetAddress;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

public class IpRangeBanStorage extends BaseDaoImpl<IpRangeBanData, Integer> {

  private BanManager plugin = BanManager.getPlugin();
  private RangeSet<Long> ranges = TreeRangeSet.create();
  private ConcurrentHashMap<Range, IpRangeBanData> bans = new ConcurrentHashMap<>();

  public IpRangeBanStorage(ConnectionSource connection) throws SQLException {
    super(connection, (DatabaseTableConfig<IpRangeBanData>) BanManager.getPlugin().getConfiguration().getLocalDb()
                                                                      .getTable("ipRangeBans"));

    if (!this.isTableExists()) {
      TableUtils.createTable(connection, tableConfig);
      return;
    }

    CloseableIterator<IpRangeBanData> itr = iterator();

    while (itr.hasNext()) {
      IpRangeBanData ban = itr.next();
      Range<Long> range = Range.closed(ban.getFromIp(), ban.getToIp());

      bans.put(range, ban);
      ranges.add(range);
    }

    itr.close();

    plugin.getLogger().info("Loaded " + bans.size() + " ip range bans into memory");
  }

  public ConcurrentHashMap<Range, IpRangeBanData> getBans() {
    return bans;
  }

  public boolean isBanned(long ip) {
    return ranges.contains(ip);
  }

  public boolean isBanned(InetAddress address) {
    return isBanned(IPUtils.toLong(address));
  }

  public IpRangeBanData retrieveBan(long fromIp, long toIp) throws SQLException {
    QueryBuilder<IpRangeBanData, Integer> query = this.queryBuilder();
    Where<IpRangeBanData, Integer> where = queryBuilder().where();

    where.eq("fromIp", fromIp).eq("toIp", toIp);

    query.setWhere(where);

    IpRangeBanData ban = query.queryForFirst();

    return ban;
  }

  public IpRangeBanData getBan(long ip) {
    Range range = ranges.rangeContaining(ip);

    if (range == null) return null;

    return bans.get(range);
  }

  public IpRangeBanData getBan(InetAddress address) {
    return getBan(IPUtils.toLong(address));
  }

  public void addBan(IpRangeBanData ban) {
    Range range = Range.closed(ban.getFromIp(), ban.getToIp());

    ranges.add(range);
    bans.put(range, ban);
  }

  public void removeBan(IpRangeBanData ban) {
    removeBan(Range.closed(ban.getFromIp(), ban.getToIp()));
  }

  public void removeBan(Range range) {
    ranges.remove(range);
    bans.remove(range);
  }

  public boolean ban(IpRangeBanData ban) throws SQLException {
    IpRangeBanEvent event = new IpRangeBanEvent(ban);
    Bukkit.getServer().getPluginManager().callEvent(event);

    if (event.isCancelled()) {
      return false;
    }

    create(ban);
    Range range = Range.closed(ban.getFromIp(), ban.getToIp());

    bans.put(range, ban);
    ranges.add(range);

    return true;
  }

  public boolean unban(IpRangeBanData ban, PlayerData actor) throws SQLException {
    IpRangeUnbanEvent event = new IpRangeUnbanEvent(ban);
    Bukkit.getServer().getPluginManager().callEvent(event);

    if (event.isCancelled()) {
      return false;
    }

    delete(ban);
    Range range = Range.closed(ban.getFromIp(), ban.getToIp());

    bans.remove(range);
    ranges.remove(range);

    plugin.getIpRangeBanRecordStorage().addRecord(ban, actor);

    return true;
  }

  public CloseableIterator<IpRangeBanData> findBans(long fromTime) throws SQLException {
    if (fromTime == 0) {
      return iterator();
    }

    long checkTime = fromTime + DateUtils.getTimeDiff();

    QueryBuilder<IpRangeBanData, Integer> query = queryBuilder();
    Where<IpRangeBanData, Integer> where = query.where();
    where
            .ge("created", checkTime)
            .or()
            .ge("updated", checkTime);

    query.setWhere(where);

    return query.iterator();

  }

}
