package com.ztype.zemmision.services;

import com.ztype.zemmision.models.Playlist;
import com.ztype.zemmision.models.Track;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PlaylistService {

    private static final Logger logger = LoggerFactory.getLogger(PlaylistService.class);

    private final DatabaseService databaseService;
    private final TorrentService torrentService;

    public PlaylistService() {
        this(new DatabaseService(), new TorrentService());
    }

    public PlaylistService(DatabaseService databaseService, TorrentService torrentService) {
        this.databaseService = databaseService;
        this.torrentService = torrentService;
    }

    public Playlist createPlaylist(String name, String description, List<File> files) {
        logger.info("Creating playlist '{}' with {} files.", name, files.size());
        Playlist playlist = new Playlist(name, description);
        List<Track> tracks = new ArrayList<>();

        for (File file : files) {
            Track track = new Track();
            track.setTitle(file.getName());
            track.setFilePath(file.getAbsolutePath());
            track.setSizeBytes(file.length());
            // Default metadata
            track.setArtist("Unknown Artist");
            track.setAlbum("Unknown Album");

            tracks.add(track);
        }
        playlist.setTracks(tracks);

        try {
            // Create metadata.json for sync
            Path playlistDir = torrentService.getStagingRoot()
                    .resolve(playlist.getName().replaceAll("\\s+", "_") + "_" + playlist.getId());
            if (!java.nio.file.Files.exists(playlistDir)) {
                java.nio.file.Files.createDirectories(playlistDir);
            }

            // 1. Copy/link original files to staging directory first!
            for (Track track : playlist.getTracks()) {
                Path source = java.nio.file.Paths.get(track.getFilePath());
                String safeFileName = track.getTitle().replaceAll("[^a-zA-Z0-9.-]", "_") + getExtension(source.toString());
                Path target = playlistDir.resolve(safeFileName);
                if (!java.nio.file.Files.exists(target)) {
                    try {
                        java.nio.file.Files.createLink(target, source);
                    } catch (Exception e) {
                        try {
                            java.nio.file.Files.copy(source, target);
                        } catch (IOException copyEx) {
                            logger.error("Failed to copy file: " + source, copyEx);
                        }
                    }
                }
            }

            // 2. Update the track paths in the playlist object to point to the staging files
            for (Track track : playlist.getTracks()) {
                Path source = java.nio.file.Paths.get(track.getFilePath());
                String safeFileName = track.getTitle().replaceAll("[^a-zA-Z0-9.-]", "_") + getExtension(source.toString());
                Path target = playlistDir.resolve(safeFileName);
                track.setFilePath(target.toAbsolutePath().toString());
            }

            // 3. Create metadata.json with the updated staging paths
            File metadataFile = playlistDir.resolve("metadata.json").toFile();
            com.google.gson.Gson gson = new com.google.gson.Gson();
            String json = gson.toJson(playlist);
            java.nio.file.Files.write(metadataFile.toPath(), json.getBytes());

            // 4. Create the .torrent file
            Path torrentPath = torrentService.createPlaylistTorrent(playlist);
            playlist.setTorrentFilePath(torrentPath.toString());

            databaseService.savePlaylist(playlist);
            torrentService.startSeeding(playlist);
            logger.info("Playlist '{}' created and seeding started.", name);

        } catch (IOException e) {
            logger.error("Failed to create/seed playlist '{}'", name, e);
            e.printStackTrace();
        }

        return playlist;
    }

    public Playlist importPlaylist(File torrentFile) throws IOException {
        logger.info("Importing playlist from torrent: {}", torrentFile.getName());
        String name = torrentFile.getName().replace(".torrent", "");
        String playlistId = null;

        try {
            com.turn.ttorrent.common.Torrent torrent = com.turn.ttorrent.common.Torrent.load(torrentFile);
            String torrentName = torrent.getName();
            if (torrentName != null) {
                int lastUnderscore = torrentName.lastIndexOf('_');
                if (lastUnderscore != -1 && lastUnderscore < torrentName.length() - 1) {
                    String potentialUuid = torrentName.substring(lastUnderscore + 1);
                    if (potentialUuid.length() == 36) { // standard UUID length
                        playlistId = potentialUuid;
                        name = torrentName.substring(0, lastUnderscore).replace('_', ' ');
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load torrent metadata from: {}", torrentFile.getName(), e);
        }

        Playlist playlist = new Playlist(name, "Imported from " + torrentFile.getName());
        if (playlistId != null) {
            playlist.setId(playlistId);
        }
        playlist.setTracks(new ArrayList<>());
        playlist.setTorrentFilePath(torrentFile.getAbsolutePath());

        // Save initial state
        databaseService.savePlaylist(playlist);

        // Try to refresh metadata immediately if files already exist
        refreshMetadata(playlist.getId());

        // Start downloading/seeding
        torrentService.startSeeding(playlist);

        return playlist;
    }

    // Method to refresh metadata from downloaded files
    public void refreshMetadata(String playlistId) {
        try {
            Playlist playlist = databaseService.getAllPlaylists().stream()
                    .filter(p -> p.getId().equals(playlistId))
                    .findFirst()
                    .orElse(null);

            if (playlist == null)
                return;

            Path playlistDir = torrentService.getStagingRoot()
                    .resolve(playlist.getName().replaceAll("\\s+", "_") + "_" + playlist.getId());
            File metadataFile = playlistDir.resolve("metadata.json").toFile();

            if (metadataFile.exists()) {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                Playlist meta = gson.fromJson(new java.io.FileReader(metadataFile), Playlist.class);

                boolean changed = false;

                // Handle missing tracks (Imported case)
                if (playlist.getTracks() == null || playlist.getTracks().isEmpty()) {
                    if (meta.getTracks() != null) {
                        List<Track> newTracks = new ArrayList<>();
                        for (Track t : meta.getTracks()) {
                            // Fix path: point to local staging file
                            String safeName = new File(t.getFilePath()).getName();
                            File localFile = playlistDir.resolve(safeName).toFile();
                            t.setFilePath(localFile.getAbsolutePath());
                            newTracks.add(t);
                        }
                        playlist.setTracks(newTracks);
                        changed = true;
                    }
                } else if (meta.getTracks() != null) {
                    // Existing tracks: Merge metadata
                    for (Track localTrack : playlist.getTracks()) {
                        for (Track remoteTrack : meta.getTracks()) {
                            if (localTrack.getTitle().equals(remoteTrack.getTitle())) {
                                localTrack.setArtist(remoteTrack.getArtist());
                                localTrack.setAlbum(remoteTrack.getAlbum());
                                changed = true;
                            }
                        }
                    }
                }

                if (meta.getCoverImagePath() != null) {
                    String coverName = new File(meta.getCoverImagePath()).getName();
                    File localCover = playlistDir.resolve(coverName).toFile();
                    if (localCover.exists()) {
                        playlist.setCoverImagePath(localCover.getAbsolutePath());
                        changed = true;
                    }
                }

                if (meta.getAuthor() != null && playlist.getAuthor() == null) {
                    playlist.setAuthor(meta.getAuthor());
                    changed = true;
                }

                if (changed) {
                    databaseService.savePlaylist(playlist);
                    logger.info("Metadata updated from sync for playlist: {}", playlist.getName());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to refresh metadata for " + playlistId, e);
        }
    }

    public List<Playlist> getAllPlaylists() {
        return databaseService.getAllPlaylists();
    }

    public com.ztype.zemmision.services.TorrentService.ClientStatus getTransferStatus(String playlistId) {
        return torrentService.getClientStatus(playlistId);
    }

    public void play(Playlist playlist) {
        boolean isImported = playlist.getDescription() != null && playlist.getDescription().startsWith("Imported from");
        
        boolean isComplete = false;
        com.ztype.zemmision.services.TorrentService.ClientStatus status = torrentService.getClientStatus(playlist.getId());
        if (status != null && status.getProgress() >= 1.0) {
            isComplete = true;
        }

        logger.info("Playing playlist {}: Mode={}", playlist.getName(),
                (!isImported || isComplete) ? "Local/Seeding" : "Streaming/Downloading");

        if (!isImported || isComplete) {
            // Owner or fully downloaded: Seed normally
            torrentService.startSeeding(playlist);
        } else {
            // Imported/Incomplete: Stream (Sequential download)
            torrentService.startStreaming(playlist);
        }
    }

    public void restartSeeding(String playlistId) {
        logger.info("Restarting seeding for playlist ID: {}", playlistId);
        // Find playlist details
        Playlist playlist = databaseService.getAllPlaylists().stream()
                .filter(p -> p.getId().equals(playlistId))
                .findFirst()
                .orElse(null);

        if (playlist != null) {
            torrentService.startSeeding(playlist);
        } else {
            logger.warn("Playlist not found for restart: {}", playlistId);
        }
    }

    public void exportTorrent(String playlistId, File destination) throws IOException {
        Playlist playlist = databaseService.getAllPlaylists().stream()
                .filter(p -> p.getId().equals(playlistId))
                .findFirst()
                .orElseThrow(() -> new IOException("Playlist not found: " + playlistId));

        File sourceFile = new File(playlist.getTorrentFilePath());
        if (!sourceFile.exists()) {
            throw new IOException("Torrent file not found for playlist: " + playlist.getName());
        }

        java.nio.file.Files.copy(sourceFile.toPath(), destination.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        logger.info("Exported torrent for playlist '{}' to {}", playlist.getName(), destination.getAbsolutePath());
    }

    public void enforceSeedingPolicy() {
        logger.info("Enforcing seeding policy...");
        long threeDaysAgo = System.currentTimeMillis() - (3L * 24 * 60 * 60 * 1000);
        List<Playlist> allPlaylists = databaseService.getAllPlaylists();

        for (Playlist playlist : allPlaylists) {
            boolean shouldSeed = playlist.isPermanentlySeeded() || playlist.getLastPlayed() > threeDaysAgo;
            com.ztype.zemmision.services.TorrentService.ClientStatus status = torrentService
                    .getClientStatus(playlist.getId());
            boolean isSeeding = !status.getState().equals("Stopped");

            if (shouldSeed && !isSeeding) {
                logger.info("Starting seeding for playlist '{}' (Last Played: {}, Permanent: {})",
                        playlist.getName(), new java.util.Date(playlist.getLastPlayed()),
                        playlist.isPermanentlySeeded());
                torrentService.startSeeding(playlist);
            } else if (!shouldSeed && isSeeding) {
                logger.info("Stopping seeding for playlist '{}' (Last Played: {}, Permanent: {})",
                        playlist.getName(), new java.util.Date(playlist.getLastPlayed()),
                        playlist.isPermanentlySeeded());
                torrentService.stop(playlist.getId());
            }
        }
    }

    public void updateLastPlayed(String playlistId) {
        Playlist playlist = databaseService.getAllPlaylists().stream()
                .filter(p -> p.getId().equals(playlistId))
                .findFirst()
                .orElse(null);

        if (playlist != null) {
            playlist.setLastPlayed(System.currentTimeMillis());
            databaseService.savePlaylist(playlist);
            // Ensure it is seeding since it is now active
            if (torrentService.getClientStatus(playlistId).getState().equals("Stopped")) {
                torrentService.startSeeding(playlist);
            }
        }
    }

    public void setPermanentSeeding(String playlistId, boolean isPermanent) {
        Playlist playlist = databaseService.getAllPlaylists().stream()
                .filter(p -> p.getId().equals(playlistId))
                .findFirst()
                .orElse(null);

        if (playlist != null) {
            playlist.setPermanentlySeeded(isPermanent);
            databaseService.savePlaylist(playlist);
        }
    }

    public void updatePlaylist(Playlist playlist) {
        databaseService.savePlaylist(playlist);
    }

    public void deletePlaylist(String playlistId) {
        logger.info("Deleting playlist {}", playlistId);
        
        Playlist playlist = databaseService.getAllPlaylists().stream()
                .filter(p -> p.getId().equals(playlistId))
                .findFirst()
                .orElse(null);

        // Stop seeding
        torrentService.stop(playlistId);
        // Remove from DB
        databaseService.deletePlaylist(playlistId);

        if (playlist != null) {
            boolean isImported = playlist.getDescription() != null && playlist.getDescription().startsWith("Imported from");
            if (isImported) {
                Path playlistDir = torrentService.getStagingRoot()
                        .resolve(playlist.getName().replaceAll("\\s+", "_") + "_" + playlist.getId());
                deleteDirectory(playlistDir.toFile());
                logger.info("Deleted staging directory for imported playlist: {}", playlistDir);

                if (playlist.getTorrentFilePath() != null) {
                    File torrentFile = new File(playlist.getTorrentFilePath());
                    if (torrentFile.exists()) {
                        torrentFile.delete();
                        logger.info("Deleted torrent file: {}", torrentFile);
                    }
                }
            }
        }
    }

    private void deleteDirectory(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDirectory(f);
            }
        }
        file.delete();
    }

    public double getTrackProgress(String playlistId, Track track) {
        String safeName = new File(track.getFilePath()).getName();
        return torrentService.getTrackProgress(playlistId, safeName);
    }

    private String getExtension(String path) {
        int i = path.lastIndexOf('.');
        return (i > 0) ? path.substring(i) : "";
    }
}
