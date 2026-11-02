package com.digitaleye.database.dao;

import com.digitaleye.database.DBConnection;
import com.digitaleye.database.model.MonitoredSystem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class MonitoredSystemDao {

    public Long insert(MonitoredSystem system) throws SQLException {
        String sql = "INSERT INTO systems (user_id, system_name, status, last_heartbeat) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, system.getUserId());
            ps.setString(2, system.getSystemName());
            ps.setString(3, system.getStatus());
            ps.setTimestamp(4, system.getLastHeartbeat());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return null;
    }

    /** Called whenever the agent sends a heartbeat, so "last seen" stays accurate. */
    public void updateStatus(Long systemId, String status, Timestamp lastHeartbeat) throws SQLException {
        String sql = "UPDATE systems SET status = ?, last_heartbeat = ? WHERE id = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setTimestamp(2, lastHeartbeat);
            ps.setLong(3, systemId);
            ps.executeUpdate();
        }
    }

    /**
     * Changes only the status and deliberately leaves last_heartbeat alone, so an
     * OFFLINE system still shows the real "last seen" time. (Setting the column to
     * itself also stops MySQL from auto-bumping it if the column was declared with
     * ON UPDATE CURRENT_TIMESTAMP.)
     */
    public void setStatusOnly(Long systemId, String status) throws SQLException {
        String sql = "UPDATE systems SET status = ?, last_heartbeat = last_heartbeat WHERE id = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setLong(2, systemId);
            ps.executeUpdate();
        }
    }

    public MonitoredSystem findById(Long id) throws SQLException {
        String sql = "SELECT * FROM systems WHERE id = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /**
     * Looks up a system by the owning user + its name, so the agent registering
     * itself on every startup finds its existing row instead of creating a new
     * "duplicate computer" each time it restarts.
     */
    public MonitoredSystem findByUserIdAndName(Long userId, String systemName) throws SQLException {
        String sql = "SELECT * FROM systems WHERE user_id = ? AND system_name = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.setString(2, systemName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /** Every registered system - this backs the admin dashboard's "Client Systems" table. */
    public List<MonitoredSystem> findAll() throws SQLException {
        String sql = "SELECT * FROM systems ORDER BY system_name";
        List<MonitoredSystem> systems = new ArrayList<>();

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                systems.add(mapRow(rs));
            }
        }
        return systems;
    }

    /**
     * Systems still marked ONLINE whose last heartbeat is older than the cutoff (or
     * missing) - i.e. their agent has gone quiet. The health monitor flips these to OFFLINE.
     */
    public List<MonitoredSystem> findStaleOnline(Timestamp cutoff) throws SQLException {
        String sql = "SELECT * FROM systems WHERE status = 'ONLINE' AND (last_heartbeat IS NULL OR last_heartbeat < ?)";
        List<MonitoredSystem> systems = new ArrayList<>();

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, cutoff);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    systems.add(mapRow(rs));
                }
            }
        }
        return systems;
    }

    private MonitoredSystem mapRow(ResultSet rs) throws SQLException {
        MonitoredSystem system = new MonitoredSystem();
        system.setId(rs.getLong("id"));
        system.setUserId(rs.getLong("user_id"));
        system.setSystemName(rs.getString("system_name"));
        system.setStatus(rs.getString("status"));
        system.setLastHeartbeat(rs.getTimestamp("last_heartbeat"));
        system.setMonitoringSince(rs.getTimestamp("monitoring_since"));
        return system;
    }
}
