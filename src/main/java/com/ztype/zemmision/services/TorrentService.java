package com.ztype.zemmision.services;

import com.ztype.zemmision.models.Playlist;
import com.ztype.zemmision.models.Track;
import bt.Bt;
import bt.data.Storage;
import bt.data.file.FileSystemStorage;
import bt.dht.DHTConfig;
import bt.dht.DHTModule;
import bt.runtime.BtClient;
import bt.torrent.TorrentSessionState;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.data.DataDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import bt.torrent.selector.SequentialSelector;
import bt.torrent.selector.RarestFirstSelector;
import bt.torrent.selector.PieceSelector;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TorrentService {

    private static final Logger logger = LoggerFactory.getLogger(TorrentService.class);

    private final Path stagingRoot;
    private final Path torrentsDir;
    private final Map<String, BtClient> activeClients;
    private final java.util.Map<String, TorrentSessionState> clientStates;
    private final java.util.Set<Integer> allocatedPorts;
    private final java.util.Map<String, java.util.List<Integer>> playlistPorts;
    private final java.util.Map<String, Integer> playlistAcceptorPorts;
    private final java.util.Map<String, DataDescriptor> clientDataDescriptors;
    private final java.util.Map<String, Torrent> clientTorrents;

    public TorrentService() {
        this.stagingRoot = Paths.get("data", "staging");
        this.torrentsDir = Paths.get("data", "torrents");
        this.activeClients = new HashMap<>();
        this.clientStates = new java.util.concurrent.ConcurrentHashMap<>();
        this.allocatedPorts = java.util.concurrent.ConcurrentHashMap.newKeySet();
        this.playlistPorts = new java.util.concurrent.ConcurrentHashMap<>();
        this.playlistAcceptorPorts = new java.util.concurrent.ConcurrentHashMap<>();
        this.clientDataDescriptors = new java.util.concurrent.ConcurrentHashMap<>();
        this.clientTorrents = new java.util.concurrent.ConcurrentHashMap<>();
        try {
            Files.createDirectories(stagingRoot);
            Files.createDirectories(torrentsDir);
        } catch (IOException e) {
            logger.error("Failed to create data directories", e);
            e.printStackTrace();
        }
    }

    public Path getStagingRoot() {
        return stagingRoot;
    }

    /**
     * Creates a torrent for the playlist by linking files to a staging directory.
     * 
     * @return Path to the generated .torrent file
     */
    public Path createPlaylistTorrent(Playlist playlist) throws IOException {
        logger.debug("Creating torrent for playlist: {}", playlist.getName());
        Path playlistDir = stagingRoot.resolve(playlist.getName().replaceAll("\\s+", "_") + "_" + playlist.getId());
        if (!Files.exists(playlistDir)) {
            Files.createDirectories(playlistDir);
        }

        // Ensure files are in staging
        for (Track track : playlist.getTracks()) {
            Path source = Paths.get(track.getFilePath());
            String safeFileName = track.getTitle().replaceAll("[^a-zA-Z0-9.-]", "_") + getExtension(source.toString());
            Path target = playlistDir.resolve(safeFileName);

            if (!Files.exists(target)) {
                try {
                    Files.createLink(target, source);
                } catch (Exception e) {
                    try {
                        Files.copy(source, target);
                    } catch (IOException copyEx) {
                        System.err.println("Failed to copy file: " + source);
                    }
                }
            }
        }

        Path torrentFile = torrentsDir.resolve(playlist.getId() + ".torrent");
        try {
            // TTorrent creation
            // TTorrent creation
            java.util.List<File> files = new java.util.ArrayList<>();
            File[] dirFiles = playlistDir.toFile().listFiles();
            if (dirFiles != null) {
                for (File f : dirFiles) {
                    if (f.isFile() && !f.isHidden()) {
                        files.add(f);
                    }
                }
            }

            if (files.isEmpty()) {
                throw new IOException("No files found in playlist directory to create torrent");
            }

            com.turn.ttorrent.common.Torrent.create(
                    playlistDir.toFile(),
                    files,
                    new java.net.URI("udp://tracker.opentrackr.org:1337/announce"),
                    "antigravity-creator").save(new java.io.FileOutputStream(torrentFile.toFile()));
        } catch (Exception e) {
            throw new IOException("Failed to create torrent", e);
        }

        return torrentFile;
    }

    public void startSeeding(Playlist playlist) {
        startTorrent(playlist, false);
    }

    public void startStreaming(Playlist playlist) {
        startTorrent(playlist, true);
    }

    private void startTorrent(Playlist playlist, boolean sequential) {
        if (activeClients.containsKey(playlist.getId())) {
            // IF switching from random to sequential, we might want to restart?
            // For MVP, just return if already active.
            logger.info("Playlist {} is already active.", playlist.getName());
            return;
        }

        logger.info("Starting to {} playlist: {}", sequential ? "stream" : "seed", playlist.getName());
        Path torrentFile = Paths.get(playlist.getTorrentFilePath());
        Path dataDir = stagingRoot.resolve(playlist.getName().replaceAll("\\s+", "_") + "_" + playlist.getId());

        Storage storage = new FileSystemStorage(dataDir.getParent());

        java.util.List<Integer> ports = new java.util.ArrayList<>();
        int dhtPort = findFreePort(49001, ports);
        DHTModule dhtModule = new DHTModule(new DHTConfig() {
            @Override
            public boolean shouldUseRouterBootstrap() {
                return true;
            }

            @Override
            public int getListeningPort() {
                return dhtPort;
            }
        });

        int acceptorPort = findFreePort(6891, ports);
        playlistPorts.put(playlist.getId(), ports);
        playlistAcceptorPorts.put(playlist.getId(), acceptorPort);

        try {
            BtClient client = buildClient(storage, dhtModule, torrentFile,
                    sequential ? SequentialSelector.sequential() : RarestFirstSelector.randomizedRarest());

            activeClients.put(playlist.getId(), client);

            CompletableFuture.runAsync(() -> {
                logger.info("Client started for: {}", playlist.getName());
                client.startAsync(state -> {
                    clientStates.put(playlist.getId(), state);
                }, 1000).join();
            });
        } catch (java.net.MalformedURLException e) {
            logger.error("Failed to start client", e);
            e.printStackTrace();
        }
    }

    protected BtClient buildClient(Storage storage, DHTModule dhtModule, Path torrentFile)
            throws java.net.MalformedURLException {
        return buildClient(storage, dhtModule, torrentFile, RarestFirstSelector.randomizedRarest());
    }

    protected BtClient buildClient(Storage storage, DHTModule dhtModule, Path torrentFile, PieceSelector selector)
            throws java.net.MalformedURLException {
        String filename = torrentFile.getFileName().toString();
        String playlistId = filename.endsWith(".torrent") ? filename.substring(0, filename.length() - 8) : filename;

        int freePort = playlistAcceptorPorts.getOrDefault(playlistId, 6891);
        logger.info("Selected free acceptor port: {} for torrent client", freePort);

        bt.runtime.Config config = new bt.runtime.Config() {
            @Override
            public int getAcceptorPort() {
                return freePort;
            }
        };

        com.google.inject.Module capturerModule = new com.google.inject.Module() {
            @Override
            public void configure(com.google.inject.Binder binder) {
                binder.bind(String.class).annotatedWith(com.google.inject.name.Names.named("capturer"))
                      .toProvider(new com.google.inject.Provider<String>() {
                          @com.google.inject.Inject
                          private DataDescriptor dataDescriptor;

                          @Override
                          public String get() {
                              if (dataDescriptor != null) {
                                  clientDataDescriptors.put(playlistId, dataDescriptor);
                              }
                              return "captured";
                          }
                      }).asEagerSingleton();
            }
        };

        return Bt.client()
                .config(config)
                .torrent(torrentFile.toUri().toURL())
                .storage(storage)
                .autoLoadModules()
                .module(dhtModule)
                .module(capturerModule)
                .selector(selector)
                .afterTorrentFetched(t -> {
                    logger.info("Torrent metadata fetched: {}", t.getName());
                    clientTorrents.put(playlistId, t);
                })
                .build();
    }

    public void stop(String playlistId) {
        BtClient client = activeClients.remove(playlistId);
        clientStates.remove(playlistId);
        if (client != null) {
            client.stop();
        }
        playlistAcceptorPorts.remove(playlistId);
        clientDataDescriptors.remove(playlistId);
        clientTorrents.remove(playlistId);
        java.util.List<Integer> ports = playlistPorts.remove(playlistId);
        if (ports != null) {
            allocatedPorts.removeAll(ports);
            logger.info("Released ports {} for playlist ID: {}", ports, playlistId);
        }
    }

    public ClientStatus getClientStatus(String playlistId) {
        TorrentSessionState state = clientStates.get(playlistId);

        if (state == null) {
            BtClient client = activeClients.get(playlistId);
            if (client == null) {
                return new ClientStatus(0, 0, 0, "Stopped");
            }
            return new ClientStatus(0, 0, 0, "Initializing");
        }

        int total = state.getPiecesTotal();
        int complete = state.getPiecesComplete();
        double progress = (total > 0) ? (double) complete / total : 0.0;

        // Peer count is not directly available in standard TorrentSessionState in all
        // versions without casting.
        // We will assume 0 or implement a separate peer listener later if needed.
        int peers = 0;

        return new ClientStatus(progress, peers, 0, (complete == total && total > 0) ? "Seeding" : "Downloading");
    }

    public static class ClientStatus {
        private final double progress;
        private final int peers;
        private final double downloadSpeed; // bytes/sec
        private final String state;

        public ClientStatus(double progress, int peers, double downloadSpeed, String state) {
            this.progress = progress;
            this.peers = peers;
            this.downloadSpeed = downloadSpeed;
            this.state = state;
        }

        public double getProgress() {
            return progress;
        }

        public int getPeers() {
            return peers;
        }

        public double getDownloadSpeed() {
            return downloadSpeed;
        }

        public String getState() {
            return state;
        }
    }

    private String getExtension(String path) {
        int i = path.lastIndexOf('.');
        return (i > 0) ? path.substring(i) : "";
    }

    private int findFreePort(int startPort) {
        return findFreePort(startPort, null);
    }

    private int findFreePort(int startPort, java.util.List<Integer> reservedList) {
        int port = startPort;
        while (port < 65535) {
            if (!allocatedPorts.contains(port)) {
                try (java.net.ServerSocket socket = new java.net.ServerSocket(port)) {
                    allocatedPorts.add(port);
                    if (reservedList != null) {
                        reservedList.add(port);
                    }
                    return port;
                } catch (IOException e) {
                    // Port is in use
                }
            }
            port++;
        }
        return startPort;
    }

    public double getTrackProgress(String playlistId, String trackFileName) {
        DataDescriptor dataDescriptor = clientDataDescriptors.get(playlistId);
        Torrent torrent = clientTorrents.get(playlistId);
        if (dataDescriptor == null || torrent == null) {
            return 0.0;
        }

        TorrentFile targetFile = null;
        for (TorrentFile f : torrent.getFiles()) {
            java.util.List<String> pathElements = f.getPathElements();
            String name = pathElements.isEmpty() ? "" : pathElements.get(pathElements.size() - 1);
            if (name.equals(trackFileName)) {
                targetFile = f;
                break;
            }
        }

        if (targetFile == null) {
            return 0.0;
        }

        try {
            java.util.BitSet filePieces = dataDescriptor.getAllPiecesForFiles(java.util.Collections.singleton(targetFile));
            bt.data.LocalBitfield bitfield = dataDescriptor.getBitfield();
            int totalPieces = 0;
            int completedPieces = 0;
            for (int i = filePieces.nextSetBit(0); i >= 0; i = filePieces.nextSetBit(i + 1)) {
                totalPieces++;
                if (bitfield.isComplete(i)) {
                    completedPieces++;
                }
            }
            return (totalPieces > 0) ? (double) completedPieces / totalPieces : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
}
