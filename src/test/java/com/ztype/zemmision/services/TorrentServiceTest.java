package com.ztype.zemmision.services;

import bt.runtime.BtClient;
import com.ztype.zemmision.models.Playlist;
import com.ztype.zemmision.services.TorrentService.ClientStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class TorrentServiceTest {

    private TorrentService torrentService;

    @Mock
    private BtClient mockClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Override buildClient directly without Spy to avoid JDK/Mockito issues
        torrentService = new TorrentService() {
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
        playlist.setTorrentFilePath("test.torrent"); // Dummy path

        torrentService.startSeeding(playlist);

        verify(mockClient).startAsync(any(), anyLong());
    }

    @Test
    void testStopSeeding() {
        Playlist playlist = new Playlist("Test Playlist", "Desc");
        playlist.setId("playlist-1");
        playlist.setTorrentFilePath("test.torrent");

        torrentService.startSeeding(playlist);
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
