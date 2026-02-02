package com.ztype.zemmision.services;

import com.ztype.zemmision.models.Playlist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlaylistServiceTest {

    private MockDatabaseService databaseService;
    private MockTorrentService torrentService;
    private PlaylistService playlistService;

    @BeforeEach
    void setUp() {
        databaseService = new MockDatabaseService();
        torrentService = new MockTorrentService();
        playlistService = new PlaylistService(databaseService, torrentService);
    }

    @Test
    void testCreatePlaylist() throws IOException {
        String name = "Test Playlist";
        String desc = "Description";
        File mockFile = new File("test.mp3");
        List<File> files = Collections.singletonList(mockFile);

        Playlist result = playlistService.createPlaylist(name, desc, files);

        assertNotNull(result);
        assertEquals(name, result.getName());
        assertEquals(desc, result.getDescription());
        assertEquals(1, result.getTracks().size());
        assertEquals("test.mp3", result.getTracks().get(0).getTitle());

        // Verify interactions
        assertTrue(torrentService.createPlaylistTorrentCalled);
        assertTrue(databaseService.savePlaylistCalled);
        assertTrue(torrentService.startSeedingCalled);
    }

    @Test
    void testUpdatePlaylist() {
        Playlist playlist = new Playlist("Updated Name", "Updated Desc");
        playlist.setId("some-id");

        playlistService.updatePlaylist(playlist);

        assertTrue(databaseService.savePlaylistCalled);
        assertEquals("some-id", databaseService.lastSavedPlaylist.getId());
    }

    @Test
    void testDeletePlaylist() {
        String playlistId = "delete-me";

        playlistService.deletePlaylist(playlistId);

        assertTrue(torrentService.stopCalled);
        assertEquals(playlistId, torrentService.lastStoppedId);
        assertTrue(databaseService.deletePlaylistCalled);
        assertEquals(playlistId, databaseService.lastDeletedId);
    }

    // Manual Mocks
    static class MockDatabaseService extends DatabaseService {
        boolean savePlaylistCalled = false;
        boolean deletePlaylistCalled = false;
        Playlist lastSavedPlaylist;
        String lastDeletedId;

        @Override
        public void savePlaylist(Playlist playlist) {
            savePlaylistCalled = true;
            lastSavedPlaylist = playlist;
        }

        @Override
        public void deletePlaylist(String id) {
            deletePlaylistCalled = true;
            lastDeletedId = id;
        }
    }

    static class MockTorrentService extends TorrentService {
        boolean createPlaylistTorrentCalled = false;
        boolean startSeedingCalled = false;
        boolean stopCalled = false;
        String lastStoppedId;

        @Override
        public Path getStagingRoot() {
            return Path.of("temp-staging");
        }

        @Override
        public Path createPlaylistTorrent(Playlist playlist) throws IOException {
            createPlaylistTorrentCalled = true;
            return Path.of("temp.torrent");
        }

        @Override
        public void startSeeding(Playlist playlist) {
            startSeedingCalled = true;
        }

        boolean startStreamingCalled = false;

        @Override
        public void startStreaming(Playlist playlist) {
            startStreamingCalled = true;
        }

        @Override
        public void stop(String playlistId) {
            stopCalled = true;
            lastStoppedId = playlistId;
        }
    }

    @Test
    void testPlay_Local() throws IOException {
        Playlist playlist = new Playlist("Local", "Desc");
        File tempFile = File.createTempFile("test", ".mp3");
        tempFile.deleteOnExit();

        com.ztype.zemmision.models.Track track = new com.ztype.zemmision.models.Track();
        track.setFilePath(tempFile.getAbsolutePath());
        playlist.setTracks(Collections.singletonList(track));

        playlistService.play(playlist);

        assertTrue(torrentService.startSeedingCalled);
        assertFalse(torrentService.startStreamingCalled);
    }

    @Test
    void testPlay_Imported() {
        Playlist playlist = new Playlist("Imported", "Desc");
        com.ztype.zemmision.models.Track track = new com.ztype.zemmision.models.Track();
        track.setFilePath("/path/to/non/existent/file.mp3");
        playlist.setTracks(Collections.singletonList(track));

        playlistService.play(playlist);

        assertFalse(torrentService.startSeedingCalled);
        assertTrue(torrentService.startStreamingCalled);
    }

    @Test
    void testPlaylistPersistence() {
        // This test simulates the edit flow being reported as broken
        Playlist playlist = new Playlist("Persistence Test", "Original Desc");
        playlist.setId("test-persistence-1");
        com.ztype.zemmision.models.Track track = new com.ztype.zemmision.models.Track();
        track.setTitle("Original Title");
        track.setArtist("Original Artist");
        playlist.setTracks(Collections.singletonList(track));

        // 1. Initial Save
        playlistService.updatePlaylist(playlist);
        assertTrue(databaseService.savePlaylistCalled);
        assertEquals("Original Artist", databaseService.lastSavedPlaylist.getTracks().get(0).getArtist());

        // 2. Simulate User Edit (Update object reference)
        playlist.getTracks().get(0).setArtist("New Artist");
        playlistService.updatePlaylist(playlist);

        // 3. Verify Save called again with new data
        assertEquals("New Artist", databaseService.lastSavedPlaylist.getTracks().get(0).getArtist());
    }
}
