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
}
