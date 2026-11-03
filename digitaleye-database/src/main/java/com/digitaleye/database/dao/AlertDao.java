package com.digitaleye.database.dao;

import com.digitaleye.database.DBConnection;
import com.digitaleye.database.model.Alert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class AlertDao {

    /**
     * Saves a new alert and returns its id, or null if the unique key (rule + activity
     * session) says this exact alert was already raised - which is not an error, it just
     * means "already alerted for this".
     */
    public Long insert(Alert alert) throws SQLException {
        String sql = "INSERT INTO alerts (system_id, rule_id, activity_log_id, alert_type, severity, message, " +
                "application_name) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, alert.getSystemId());
            setNullableLong(ps, 2, alert.getRuleId());
            setNullableLong(ps, 3, alert.getActivityLogId());
            ps.setString(4, alert.getAlertType());
            ps.setString(5, alert.getSeverity());
            ps.setString(6, alert.getMessage());
            ps.setString(7, alert.getApplicationName());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (SQLIntegrityConstraintViolationException duplicate) {
            return null;
        }
        return null;
    }

    /** True if this rule has already raised an alert for this exact activity session. */
    public boolean existsForRuleAndLog(Long ruleId, Long activityLogId) throws SQLException {
        String sql = "SELECT 1 FROM alerts WHERE rule_id = ? AND activity_log_id = ? LIMIT 1";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, ruleId);
            ps.setLong(2, activityLogId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** True if this rule already alerted for this system since the given time (used as a cooldown). */
    public boolean existsForRuleSystemSince(Long ruleId, Long systemId, Timestamp since) throws SQLException {
        String sql = "SELECT 1 FROM alerts WHERE rule_id = ? AND system_id = ? AND created_at >= ? LIMIT 1";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, ruleId);
            ps.setLong(2, systemId);
            ps.setTimestamp(3, since);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Newest first. Any filter left null is ignored. */
    public List<Alert> search(Long systemId, String severity, Boolean acknowledged, int limit, int offset)
            throws SQLException {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(systemId, severity, acknowledged, params);

        String sql = "SELECT a.*, s.system_name FROM alerts a JOIN systems s ON s.id = a.system_id" + where +
                " ORDER BY a.created_at DESC, a.id DESC LIMIT ? OFFSET ?";
        List<Alert> alerts = new ArrayList<>();

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int i = 1;
            for (Object p : params) {
                ps.setObject(i++, p);
            }
            ps.setInt(i++, limit);
            ps.setInt(i, offset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    alerts.add(mapRow(rs));
                }
            }
        }
        return alerts;
    }

    public long count(Long systemId, String severity, Boolean acknowledged) throws SQLException {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(systemId, severity, acknowledged, params);

        String sql = "SELECT COUNT(*) FROM alerts a" + where;

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int i = 1;
            for (Object p : params) {
                ps.setObject(i++, p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    public long countSince(Timestamp since) throws SQLException {
        String sql = "SELECT COUNT(*) FROM alerts WHERE created_at >= ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, since);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    /** Alerts raised for one system within [from, to) - the "alerts triggered" figure on the detail page. */
    public long countBySystemBetween(Long systemId, Timestamp from, Timestamp to) throws SQLException {
        String sql = "SELECT COUNT(*) FROM alerts WHERE system_id = ? AND created_at >= ? AND created_at < ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, systemId);
            ps.setTimestamp(2, from);
            ps.setTimestamp(3, to);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    public boolean acknowledge(Long id) throws SQLException {
        String sql = "UPDATE alerts SET acknowledged = 1 WHERE id = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public int acknowledgeAll() throws SQLException {
        String sql = "UPDATE alerts SET acknowledged = 1 WHERE acknowledged = 0";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            return ps.executeUpdate();
        }
    }

    private String buildWhere(Long systemId, String severity, Boolean acknowledged, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (systemId != null) {
            where.append(" AND a.system_id = ?");
            params.add(systemId);
        }
        if (severity != null && !severity.isBlank()) {
            where.append(" AND a.severity = ?");
            params.add(severity);
        }
        if (acknowledged != null) {
            where.append(" AND a.acknowledged = ?");
            params.add(acknowledged ? 1 : 0);
        }
        return where.toString();
    }

    private void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    private Alert mapRow(ResultSet rs) throws SQLException {
        Alert alert = new Alert();
        alert.setId(rs.getLong("id"));
        alert.setSystemId(rs.getLong("system_id"));
        alert.setSystemName(rs.getString("system_name"));

        long ruleId = rs.getLong("rule_id");
        alert.setRuleId(rs.wasNull() ? null : ruleId);
        long logId = rs.getLong("activity_log_id");
        alert.setActivityLogId(rs.wasNull() ? null : logId);

        alert.setAlertType(rs.getString("alert_type"));
        alert.setSeverity(rs.getString("severity"));
        alert.setMessage(rs.getString("message"));
        alert.setApplicationName(rs.getString("application_name"));
        alert.setAcknowledged(rs.getBoolean("acknowledged"));
        alert.setCreatedAt(rs.getTimestamp("created_at"));
        return alert;
    }
}
