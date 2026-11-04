package com.digitaleye.database.dao;

import com.digitaleye.database.DBConnection;
import com.digitaleye.database.model.Screenshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class ScreenshotDao {

    public Long insert(Screenshot shot) throws SQLException {
        String sql = "INSERT INTO screenshots (system_id, taken_at, application_name, file_path, file_size) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, shot.getSystemId());
            ps.setTimestamp(2, shot.getTakenAt());
            ps.setString(3, shot.getApplicationName());
            ps.setString(4, shot.getFilePath());
            ps.setInt(5, shot.getFileSize());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return null;
    }

    public Screenshot findById(Long id) throws SQLException {
        String sql = "SELECT * FROM screenshots WHERE id = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /** Screenshots for one system within [from, to), oldest first (replay order) or newest first. */
    public List<Screenshot> findBySystemBetween(Long systemId, Timestamp from, Timestamp to,
                                                int limit, boolean oldestFirst) throws SQLException {
        String sql = "SELECT * FROM screenshots WHERE system_id = ? AND taken_at >= ? AND taken_at < ? " +
                "ORDER BY taken_at " + (oldestFirst ? "ASC" : "DESC") + ", id " + (oldestFirst ? "ASC" : "DESC") +
                " LIMIT ?";
        List<Screenshot> shots = new ArrayList<>();

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, systemId);
            ps.setTimestamp(2, from);
            ps.setTimestamp(3, to);
            ps.setInt(4, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    shots.add(mapRow(rs));
                }
            }
        }
        return shots;
    }

    /** The most recent screenshots for one system, whatever day they are from. */
    public List<Screenshot> findLatestBySystem(Long systemId, int limit) throws SQLException {
        String sql = "SELECT * FROM screenshots WHERE system_id = ? ORDER BY taken_at DESC, id DESC LIMIT ?";
        List<Screenshot> shots = new ArrayList<>();

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, systemId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    shots.add(mapRow(rs));
                }
            }
        }
        return shots;
    }

    private Screenshot mapRow(ResultSet rs) throws SQLException {
        Screenshot shot = new Screenshot();
        shot.setId(rs.getLong("id"));
        shot.setSystemId(rs.getLong("system_id"));
        shot.setTakenAt(rs.getTimestamp("taken_at"));
        shot.setApplicationName(rs.getString("application_name"));
        shot.setFilePath(rs.getString("file_path"));
        shot.setFileSize(rs.getInt("file_size"));
        return shot;
    }
}
