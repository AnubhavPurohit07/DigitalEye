package com.digitaleye.database.dao;

import com.digitaleye.database.DBConnection;
import com.digitaleye.database.model.ActivityLog;
import com.digitaleye.database.model.ActivityLogFilter;
import com.digitaleye.database.model.ActivityLogView;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ActivityLogDao {

    /** Opens a new session (app started, or user went idle). Returns the new row's id. */
    public Long insert(ActivityLog log) throws SQLException {
        String sql = "INSERT INTO activity_logs (system_id, application_name, status, category, start_time) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, log.getSystemId());
            ps.setString(2, log.getApplicationName());
            ps.setString(3, log.getStatus());
            ps.setString(4, log.getCategory());
            ps.setTimestamp(5, log.getStartTime());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return null;
    }

    /** Closes a session once the app loses focus (or the user becomes active again). */
    public void closeSession(Long logId, Timestamp endTime) throws SQLException {
        String sql = "UPDATE activity_logs SET end_time = ? WHERE id = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, endTime);
            ps.setLong(2, logId);
            ps.executeUpdate();
        }
    }

    /**
     * The one still-open session for a system (end_time IS NULL), if any - this IS
     * "what the system is currently doing": whatever app/idle session hasn't been
     * closed yet. Used both to show "Current Application" on the dashboard and to
     * know what to close when a new activity report comes in with a different app.
     */
    public ActivityLog findOpenSession(Long systemId) throws SQLException {
        String sql = "SELECT * FROM activity_logs WHERE system_id = ? AND end_time IS NULL " +
                "ORDER BY start_time DESC LIMIT 1";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, systemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /** Every still-open session across all systems - the idle-time alert check scans these. */
    public List<ActivityLog> findOpenSessions() throws SQLException {
        String sql = "SELECT * FROM activity_logs WHERE end_time IS NULL";
        List<ActivityLog> logs = new ArrayList<>();

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                logs.add(mapRow(rs));
            }
        }
        return logs;
    }

    /** Most recent sessions for one system, newest first - backs the "Recent Activity Logs" table. */
    public List<ActivityLog> findRecentBySystem(Long systemId, int limit) throws SQLException {
        String sql = "SELECT * FROM activity_logs WHERE system_id = ? ORDER BY start_time DESC LIMIT ?";
        List<ActivityLog> logs = new ArrayList<>();

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, systemId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapRow(rs));
                }
            }
        }
        return logs;
    }

    /** Every session for one system that STARTED within a date range. */
    public List<ActivityLog> findByDateRange(Long systemId, Timestamp from, Timestamp to) throws SQLException {
        String sql = "SELECT * FROM activity_logs WHERE system_id = ? AND start_time >= ? AND start_time <= ? " +
                "ORDER BY start_time";
        List<ActivityLog> logs = new ArrayList<>();

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, systemId);
            ps.setTimestamp(2, from);
            ps.setTimestamp(3, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapRow(rs));
                }
            }
        }
        return logs;
    }

    /**
     * Every session for one system that OVERLAPS [from, to) - including one that began
     * yesterday and is still running, or one that is still open right now. The analytics
     * code clips each session to the window, so this is what the timeline, usage totals
     * and activity score are computed from.
     */
    public List<ActivityLog> findOverlapping(Long systemId, Timestamp from, Timestamp to) throws SQLException {
        String sql = "SELECT * FROM activity_logs WHERE system_id = ? AND start_time < ? " +
                "AND (end_time IS NULL OR end_time > ?) ORDER BY start_time";
        List<ActivityLog> logs = new ArrayList<>();

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, systemId);
            ps.setTimestamp(2, to);
            ps.setTimestamp(3, from);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapRow(rs));
                }
            }
        }
        return logs;
    }

    /** Same as findOverlapping, but for every system at once (dashboard overview). */
    public List<ActivityLog> findOverlappingAll(Timestamp from, Timestamp to) throws SQLException {
        String sql = "SELECT * FROM activity_logs WHERE start_time < ? " +
                "AND (end_time IS NULL OR end_time > ?) ORDER BY system_id, start_time";
        List<ActivityLog> logs = new ArrayList<>();

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, to);
            ps.setTimestamp(2, from);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapRow(rs));
                }
            }
        }
        return logs;
    }

    /** How many sessions started in [from, to) - the "Activity Logs Today" card. */
    public long countStartedBetween(Timestamp from, Timestamp to) throws SQLException {
        String sql = "SELECT COUNT(*) FROM activity_logs WHERE start_time >= ? AND start_time < ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, from);
            ps.setTimestamp(2, to);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    /** Filtered, paged list for the Activity Logs page (newest first), joined with each system's name. */
    public List<ActivityLogView> search(ActivityLogFilter filter, int limit, int offset) throws SQLException {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(filter, params);

        String sql = "SELECT l.id, l.system_id, s.system_name, l.application_name, l.status, l.category, " +
                "l.start_time, l.end_time FROM activity_logs l JOIN systems s ON s.id = l.system_id" +
                where + " ORDER BY l.start_time DESC LIMIT ? OFFSET ?";
        List<ActivityLogView> rows = new ArrayList<>();

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int i = bindAll(ps, params);
            ps.setInt(i++, limit);
            ps.setInt(i, offset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ActivityLogView row = new ActivityLogView();
                    row.setId(rs.getLong("id"));
                    row.setSystemId(rs.getLong("system_id"));
                    row.setSystemName(rs.getString("system_name"));
                    row.setApplicationName(rs.getString("application_name"));
                    row.setStatus(rs.getString("status"));
                    row.setCategory(rs.getString("category"));
                    row.setStartTime(rs.getTimestamp("start_time"));
                    row.setEndTime(rs.getTimestamp("end_time"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /** Total rows matching the same filter as search() - needed for the page count. */
    public long countSearch(ActivityLogFilter filter) throws SQLException {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(filter, params);

        String sql = "SELECT COUNT(*) FROM activity_logs l JOIN systems s ON s.id = l.system_id" + where;

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            bindAll(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private String buildWhere(ActivityLogFilter f, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (f == null) {
            return where.toString();
        }
        if (f.getSystemId() != null) {
            where.append(" AND l.system_id = ?");
            params.add(f.getSystemId());
        }
        if (f.getCategory() != null && !f.getCategory().isBlank()) {
            String wanted = f.getCategory().trim().toUpperCase().replace('-', '_').replace(' ', '_');
            if ("NON_PRODUCTIVE".equals(wanted)) {
                // rows written before the classification upgrade say ENTERTAINMENT
                where.append(" AND l.category IN ('NON_PRODUCTIVE', 'ENTERTAINMENT')");
            } else if ("OTHER".equals(wanted)) {
                where.append(" AND (l.category = 'OTHER' OR l.category IS NULL OR l.category = '')");
            } else {
                where.append(" AND l.category = ?");
                params.add(wanted);
            }
        }
        if (f.getStatus() != null && !f.getStatus().isBlank()) {
            where.append(" AND l.status = ?");
            params.add(f.getStatus());
        }
        if (f.getApplicationQuery() != null && !f.getApplicationQuery().isBlank()) {
            where.append(" AND l.application_name LIKE ?");
            params.add("%" + escapeLike(f.getApplicationQuery().trim()) + "%");
        }
        if (f.getFrom() != null) {
            where.append(" AND l.start_time >= ?");
            params.add(f.getFrom());
        }
        if (f.getTo() != null) {
            where.append(" AND l.start_time < ?");
            params.add(f.getTo());
        }
        return where.toString();
    }

    /**
     * A page of rows in id order, starting after the given id (pass 0 for the first page).
     * Used to walk the whole table without loading it all at once, e.g. when the classification
     * rules are re-applied to old activity.
     */
    public List<ActivityLog> findPageAfterId(long afterId, int limit) throws SQLException {
        String sql = "SELECT * FROM activity_logs WHERE id > ? ORDER BY id ASC LIMIT ?";
        List<ActivityLog> rows = new ArrayList<>();

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, afterId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapRow(rs));
                }
            }
        }
        return rows;
    }

    /** Sets a new category on each listed row (key = row id) in one batch. Returns how many rows were sent. */
    public int updateCategories(Map<Long, String> categoryById) throws SQLException {
        if (categoryById.isEmpty()) {
            return 0;
        }
        String sql = "UPDATE activity_logs SET category = ? WHERE id = ?";

        try (Connection conn = DBConnection.get()) {
            boolean oldAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Map.Entry<Long, String> e : categoryById.entrySet()) {
                    ps.setString(1, e.getValue());
                    ps.setLong(2, e.getKey());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(oldAutoCommit);
            }
        }
        return categoryById.size();
    }

    /** Stops a search for "100%" from treating % and _ as wildcards. */
    private String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private int bindAll(PreparedStatement ps, List<Object> params) throws SQLException {
        int i = 1;
        for (Object p : params) {
            ps.setObject(i++, p);
        }
        return i;
    }

    private ActivityLog mapRow(ResultSet rs) throws SQLException {
        ActivityLog log = new ActivityLog();
        log.setId(rs.getLong("id"));
        log.setSystemId(rs.getLong("system_id"));
        log.setApplicationName(rs.getString("application_name"));
        log.setStatus(rs.getString("status"));
        log.setCategory(rs.getString("category"));
        log.setStartTime(rs.getTimestamp("start_time"));
        log.setEndTime(rs.getTimestamp("end_time"));
        return log;
    }
}
