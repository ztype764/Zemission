package com.ztype.zemmision.services;

import bt.runtime.BtClient;
import bt.torrent.selector.PieceSelector;
import bt.torrent.selector.SequentialSelector;

import com.ztype.zemmision.models.Playlist;
import com.ztype.zemmision.services.TorrentService.ClientStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class TorrentServiceTest {

    private TorrentService torrentService;

    @Mock
    private BtClient mockClient;

    // Capture selector to verify strategy
    private AtomicReference<PieceSelector> capturedSelector = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        capturedSelector.set(null);

        // Override buildClient directly without Spy to avoid JDK/Mockito issues
        torrentService = new TorrentService() {
            {
                enablePortMapping = false;
            }
            @Override
            protected BtClient buildClient(bt.data.Storage storage, bt.dht.DHTModule dhtModule, Path torrentFile,
                    PieceSelector selector) {
                capturedSelector.set(selector);
                return mockClient;
            }

            // Override 3-arg to forward to 4-arg default logic if called (though we expect
            // 4-arg calls)
            @Override
            protected BtClient buildClient(bt.data.Storage storage, bt.dht.DHTModule dhtModule, Path torrentFile) {
                return mockClient;
            }
        };

        when(mockClient.startAsync(any(), anyLong())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void testStartSeeding() {
        Playlist playlist = new Playlist("Test Playlist", "Desc");
        playlist.setId("playlist-1");
        playlist.setTorrentFilePath("test.torrent");

        torrentService.startSeeding(playlist);

        verify(mockClient, timeout(2000)).startAsync(any(), anyLong());
        // Verify selector is NOT Sequential (RarestFirst is default for seeding)
        assertNotNull(capturedSelector.get());
        assertTrue(!(capturedSelector.get() instanceof SequentialSelector),
                "Seeding should not use Sequential selector");
    }

    @Test
    void testStartStreaming() {
        Playlist playlist = new Playlist("Stream Playlist", "Desc");
        playlist.setId("playlist-2");
        playlist.setTorrentFilePath("test.torrent");

        torrentService.startStreaming(playlist);

        verify(mockClient, timeout(2000)).startAsync(any(), anyLong());
        // Verify selector IS Sequential
        assertNotNull(capturedSelector.get());
        assertTrue(capturedSelector.get() instanceof SequentialSelector, "Streaming MUST use Sequential selector");
    }

    @Test
    void testStopSeeding() {
        Playlist playlist = new Playlist("Test Playlist", "Desc");
        playlist.setId("playlist-1");
        playlist.setTorrentFilePath("test.torrent");

        torrentService.startSeeding(playlist);
        // Wait for start to happen
        verify(mockClient, timeout(2000)).startAsync(any(), anyLong());

        torrentService.stop("playlist-1");

        verify(mockClient).stop();
    }

    @Test
    void testGetClientStatus_Stopped() {
        ClientStatus status = torrentService.getClientStatus("non-existent-id");
        assertNotNull(status);
        assertEquals("Stopped", status.getState());
        assertEquals(0, status.getProgress(), 0.001);
    }

    @Test
    void testRealSeederVerification() throws Exception {
        Path tempStaging = java.nio.file.Files.createTempDirectory("tempStaging");
        Path tempTorrents = java.nio.file.Files.createTempDirectory("tempTorrents");
        
        String playlistId = "test-real-uuid";
        Path playlistDir = tempStaging.resolve("RealPlaylist_" + playlistId);
        java.nio.file.Files.createDirectories(playlistDir);
        
        Path dummyFile = playlistDir.resolve("dummy.txt");
        java.nio.file.Files.write(dummyFile, "Hello World! This is dummy content to test seeding verification.".getBytes());
        Path metaFile = playlistDir.resolve("metadata.json");
        java.nio.file.Files.write(metaFile, "{}".getBytes());
        
        Path torrentFile = tempTorrents.resolve(playlistId + ".torrent");
        java.util.List<File> files = java.util.Arrays.asList(dummyFile.toFile(), metaFile.toFile());
        com.turn.ttorrent.common.Torrent.create(
                playlistDir.toFile(),
                files,
                new java.net.URI("udp://tracker.opentrackr.org:1337/announce"),
                "antigravity-creator").save(new java.io.FileOutputStream(torrentFile.toFile()));
                
        TorrentService realService = new TorrentService() {
            {
                enablePortMapping = false;
            }
            @Override
            public Path getStagingRoot() {
                return tempStaging;
            }
        };
        
        Playlist playlist = new Playlist("RealPlaylist", "Desc");
        playlist.setId(playlistId);
        playlist.setTorrentFilePath(torrentFile.toString());
        
        realService.startSeeding(playlist);
        
        int attempts = 0;
        double progress = 0;
        String stateStr = "";
        while (attempts < 10) {
            Thread.sleep(1000);
            ClientStatus status = realService.getClientStatus(playlistId);
            progress = status.getProgress();
            stateStr = status.getState();
            if (progress >= 1.0) {
                break;
            }
            attempts++;
        }
        
        realService.stop(playlistId);
        
        // Cleanup temp dirs
        try {
            java.nio.file.Files.walk(tempStaging)
                .sorted(java.util.Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
            java.nio.file.Files.walk(tempTorrents)
                .sorted(java.util.Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        } catch (Exception ignored) {}
        
        assertEquals(1.0, progress, "Seeder progress should be 100% (1.0)");
        assertEquals("Seeding", stateStr, "Seeder state should be Seeding");
    }
}
