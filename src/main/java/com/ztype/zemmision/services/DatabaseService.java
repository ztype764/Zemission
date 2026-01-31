package com.ztype.zemmision.services;

import com.ztype.zemmision.models.Playlist;
import com.ztype.zemmision.models.Track;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Type;

public class DatabaseService {
    private static final String DB_URL = "jdbc:sqlite:torrentstreamer.db";
    private final Gson gson;

    public DatabaseService() {
        this.gson = new Gson();
        initializeDatabase();
    }

    private void initializeDatabase() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS playlists ("
                + "id TEXT PRIMARY KEY,"
                + "name TEXT NOT NULL,"
                + "description TEXT,"
                + "tracks_json TEXT,"
                + "torrent_hash TEXT,"
                + "torrent_file_path TEXT,"
                + "cover_image_path TEXT,"
                + "author TEXT,"
                + "last_played INTEGER,"
                + "is_permanently_seeded INTEGER"
                + ");";

        try (Connection conn = DriverManager.getConnection(DB_URL);
                Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            try {
                // Migration for existing databases
                stmt.execute("ALTER TABLE playlists ADD COLUMN cover_image_path TEXT");
            } catch (SQLException ignored) {
            }
            try {
                stmt.execute("ALTER TABLE playlists ADD COLUMN author TEXT");
            } catch (SQLException ignored) {
            }
            try {
                stmt.execute("ALTER TABLE playlists ADD COLUMN last_played INTEGER");
            } catch (SQLException ignored) {
            }
            try {
                stmt.execute("ALTER TABLE playlists ADD COLUMN is_permanently_seeded INTEGER");
            } catch (SQLException ignored) {
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void savePlaylist(Playlist playlist) {
        String sql = "INSERT OR REPLACE INTO playlists(id, name, description, tracks_json, torrent_hash, torrent_file_path, cover_image_path, author, last_played, is_permanently_seeded) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL);
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playlist.getId());
            pstmt.setString(2, playlist.getName());
            pstmt.setString(3, playlist.getDescription());
            pstmt.setString(4, gson.toJson(playlist.getTracks()));
            pstmt.setString(5, playlist.getTorrentHash());
            pstmt.setString(6, playlist.getTorrentFilePath());
            pstmt.setString(7, playlist.getCoverImagePath());
            pstmt.setString(8, playlist.getAuthor());
            pstmt.setLong(9, playlist.getLastPlayed());
            pstmt.setInt(10, playlist.isPermanentlySeeded() ? 1 : 0);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deletePlaylist(String playlistId) {
        String sql = "DELETE FROM playlists WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playlistId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Playlist> getAllPlaylists() {
        List<Playlist> playlists = new ArrayList<>();
        String sql = "SELECT * FROM playlists";

        try (Connection conn = DriverManager.getConnection(DB_URL);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Playlist playlist = new Playlist();
                playlist.setId(rs.getString("id"));
                playlist.setName(rs.getString("name"));
                playlist.setDescription(rs.getString("description"));

                String tracksJson = rs.getString("tracks_json");
                Type listType = new TypeToken<ArrayList<Track>>() {
                }.getType();
                List<Track> tracks = gson.fromJson(tracksJson, listType);
                playlist.setTracks(tracks);

                playlist.setTorrentHash(rs.getString("torrent_hash"));
                playlist.setTorrentFilePath(rs.getString("torrent_file_path"));
                playlist.setCoverImagePath(rs.getString("cover_image_path"));
                playlist.setAuthor(rs.getString("author"));
                playlist.setLastPlayed(rs.getLong("last_played"));
                playlist.setPermanentlySeeded(rs.getInt("is_permanently_seeded") == 1);
                playlists.add(playlist);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return playlists;
    }
}
