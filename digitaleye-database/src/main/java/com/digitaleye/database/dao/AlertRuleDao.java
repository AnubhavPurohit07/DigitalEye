package com.digitaleye.database.dao;

import com.digitaleye.database.DBConnection;
import com.digitaleye.database.model.AlertRule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class AlertRuleDao {

    public Long insert(AlertRule rule) throws SQLException {
        String sql = "INSERT INTO alert_rules (name, rule_type, threshold_minutes, keyword, start_hour, end_hour, " +
                "severity, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            bindFields(ps, rule);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return null;
    }

    /** Returns false if no rule has that id. */
    public boolean update(AlertRule rule) throws SQLException {
        String sql = "UPDATE alert_rules SET name = ?, rule_type = ?, threshold_minutes = ?, keyword = ?, " +
                "start_hour = ?, end_hour = ?, severity = ?, enabled = ? WHERE id = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            bindFields(ps, rule);
            ps.setLong(9, rule.getId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(Long id) throws SQLException {
        String sql = "DELETE FROM alert_rules WHERE id = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public AlertRule findById(Long id) throws SQLException {
        String sql = "SELECT * FROM alert_rules WHERE id = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public List<AlertRule> findAll() throws SQLException {
        return query("SELECT * FROM alert_rules ORDER BY id");
    }

    /** Only the rules the admin has switched on - what the alert engine actually evaluates. */
    public List<AlertRule> findEnabled() throws SQLException {
        return query("SELECT * FROM alert_rules WHERE enabled = 1 ORDER BY id");
    }

    private List<AlertRule> query(String sql) throws SQLException {
        List<AlertRule> rules = new ArrayList<>();

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                rules.add(mapRow(rs));
            }
        }
        return rules;
    }

    private void bindFields(PreparedStatement ps, AlertRule rule) throws SQLException {
        ps.setString(1, rule.getName());
        ps.setString(2, rule.getRuleType());
        setNullableInt(ps, 3, rule.getThresholdMinutes());
        ps.setString(4, rule.getKeyword());
        setNullableInt(ps, 5, rule.getStartHour());
        setNullableInt(ps, 6, rule.getEndHour());
        ps.setString(7, rule.getSeverity());
        ps.setBoolean(8, rule.isEnabled());
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private AlertRule mapRow(ResultSet rs) throws SQLException {
        AlertRule rule = new AlertRule();
        rule.setId(rs.getLong("id"));
        rule.setName(rs.getString("name"));
        rule.setRuleType(rs.getString("rule_type"));
        rule.setThresholdMinutes(getNullableInt(rs, "threshold_minutes"));
        rule.setKeyword(rs.getString("keyword"));
        rule.setStartHour(getNullableInt(rs, "start_hour"));
        rule.setEndHour(getNullableInt(rs, "end_hour"));
        rule.setSeverity(rs.getString("severity"));
        rule.setEnabled(rs.getBoolean("enabled"));
        return rule;
    }
}
