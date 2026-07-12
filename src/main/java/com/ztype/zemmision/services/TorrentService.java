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
    private final java.util.Map<String, Playlist> activePlaylists;
    private final java.util.Map<String, Boolean> activeModes;
    private final java.util.Map<String, Long> frozenStartTime;
    private final java.util.concurrent.ScheduledExecutorService monitorService;
    private final java.util.Map<String, Long> lastLogTime;

    public TorrentService() {
        this.stagingRoot = Paths.get("data", "staging");
        this.torrentsDir = Paths.get("data", "torrents");
        this.activeClients = new HashMap<>();
        this.clientStates = new java.util.concurrent.ConcurrentHashMap<>();
        this.allocatedPorts = java.util.concurrent.ConcurrentHashMap.newKeySet();
        this.playlistPorts = new java.util.concurrent.ConcurrentHashMap<>();
        this.playlistAcceptorPorts = new java.util.concurrent.ConcurrentHashMap<>();
        this.activePlaylists = new java.util.concurrent.ConcurrentHashMap<>();
        this.activeModes = new java.util.concurrent.ConcurrentHashMap<>();
        this.frozenStartTime = new java.util.concurrent.ConcurrentHashMap<>();
        this.lastLogTime = new java.util.concurrent.ConcurrentHashMap<>();
        this.monitorService = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "torrent-monitor");
            t.setDaemon(true);
            return t;
        });
        this.monitorService.scheduleAtFixedRate(this::checkFrozenTorrents, 5, 5, java.util.concurrent.TimeUnit.SECONDS);
        try {
            Files.createDirectories(stagingRoot);
            Files.createDirectories(torrentsDir);
        } catch (IOException e) {
            logger.error("Failed to create data directories: {}", e.getMessage());
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
        if (!Files.exists(getStagingRoot())) {
            Files.createDirectories(getStagingRoot());
        }
        Path playlistDir = getStagingRoot().resolve(playlist.getName().replaceAll("\\s+", "_") + "_" + playlist.getId());
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
            Boolean currentSequential = activeModes.get(playlist.getId());
            if (currentSequential != null && currentSequential == sequential) {
                logger.info("Playlist {} is already active in the requested mode.", playlist.getName());
                return;
            }
            logger.info("Playlist {} is active in a different mode. Restarting in new mode (sequential={})...", playlist.getName(), sequential);
            stop(playlist.getId());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {}
        }

        activePlaylists.put(playlist.getId(), playlist);
        activeModes.put(playlist.getId(), sequential);

        logger.info("Starting to {} playlist: {}", sequential ? "stream" : "seed", playlist.getName());
        Path torrentFile = Paths.get(playlist.getTorrentFilePath());
        Path dataDir = getStagingRoot().resolve(playlist.getName().replaceAll("\\s+", "_") + "_" + playlist.getId());

        Storage storage = getStorage(dataDir.getParent());

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
                try {
                    logger.info("Client started for: {}", playlist.getName());
                    client.startAsync(state -> {
                        clientStates.put(playlist.getId(), state);
                        java.util.Set<bt.net.ConnectionKey> peers = state.getConnectedPeers();
                        if (peers != null && !peers.isEmpty()) {
                            long now = System.currentTimeMillis();
                            long lastTime = lastLogTime.getOrDefault(playlist.getId(), 0L);
                            if (now - lastTime >= 10000) {
                                lastLogTime.put(playlist.getId(), now);
                                double pct = (state.getPiecesTotal() > 0) ? ((double) state.getPiecesComplete() / state.getPiecesTotal() * 100.0) : 0.0;
                                logger.info("Playlist '{}' ({}) - Active Subscribers/Downloaders: {} peers connected. Uploaded: {} bytes, Downloaded: {} bytes, Progress: {}%", 
                                            playlist.getName(), playlist.getId(), peers.size(), state.getUploaded(), state.getDownloaded(), String.format("%.1f", pct));
                                for (bt.net.ConnectionKey peerKey : peers) {
                                    logger.info("  -> Subscriber: {} (port {})", peerKey.getPeer().getInetAddress(), peerKey.getRemotePort());
                                }
                            }
                        }
                    }, 1000).join();
                } catch (Exception e) {
                    Throwable cause = e;
                    while (cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    if (cause instanceof java.net.BindException || (cause.getMessage() != null && (cause.getMessage().contains("Address already in use") || cause.getMessage().contains("bind")))) {
                        logger.error("Failed to start torrent client for playlist '{}' due to port conflict: Address already in use.", playlist.getName());
                    } else {
                        logger.error("Failed to start torrent client for playlist '{}': {}", playlist.getName(), cause.getMessage());
                    }
                    stop(playlist.getId());
                }
            });
        } catch (java.net.MalformedURLException e) {
            logger.error("Failed to start client: {}", e.getMessage());
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

        return Bt.client()
                .config(config)
                .torrent(torrentFile.toUri().toURL())
                .storage(storage)
                .autoLoadModules()
                .module(dhtModule)
                .selector(selector)
                .afterTorrentFetched(t -> {
                    logger.info("Torrent metadata fetched: {}", t.getName());
                })
                .build();
    }

    public void stop(String playlistId) {
        BtClient client = activeClients.remove(playlistId);
        clientStates.remove(playlistId);
        activePlaylists.remove(playlistId);
        activeModes.remove(playlistId);
        frozenStartTime.remove(playlistId);
        lastLogTime.remove(playlistId);
        if (client != null) {
            client.stop();
        }
        playlistAcceptorPorts.remove(playlistId);
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

        int peers = state.getConnectedPeers().size();

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
            if (!allocatedPorts.contains(port) && (reservedList == null || !reservedList.contains(port))) {
                boolean free = false;
                try (java.net.ServerSocket ss = new java.net.ServerSocket()) {
                    ss.setReuseAddress(true);
                    ss.bind(new java.net.InetSocketAddress(port));
                    
                    try (java.net.DatagramSocket ds = new java.net.DatagramSocket(null)) {
                        ds.setReuseAddress(true);
                        ds.bind(new java.net.InetSocketAddress(port));
                        free = true;
                    }
                } catch (IOException e) {
                    // Port is in use on TCP or UDP
                }
                
                if (free) {
                    allocatedPorts.add(port);
                    if (reservedList != null) {
                        reservedList.add(port);
                    }
                    return port;
                }
            }
            port++;
        }
        return startPort;
    }

    public double getTrackProgress(String playlistId, String trackFileName) {
        BtClient client = activeClients.get(playlistId);
        if (client == null) {
            return 0.0;
        }

        bt.processor.torrent.TorrentContext context = getTorrentContext(client);
        if (context == null) {
            return 0.0;
        }

        Torrent torrent = context.getTorrent().orElse(null);
        bt.data.LocalBitfield bitfield = context.getBitfield();
        if (torrent == null || bitfield == null) {
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
            long chunkSize = torrent.getChunkSize();
            long fileLength = targetFile.getSize();
            
            long startOffset = 0;
            for (TorrentFile f : torrent.getFiles()) {
                if (f == targetFile) {
                    break;
                }
                startOffset += f.getSize();
            }
            long endOffset = startOffset + fileLength;
            
            int firstPiece = (int) (startOffset / chunkSize);
            int lastPiece = (int) ((endOffset - 1) / chunkSize);
            
            int totalPieces = 0;
            int completedPieces = 0;
            for (int i = firstPiece; i <= lastPiece; i++) {
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

    private bt.processor.torrent.TorrentContext getTorrentContext(BtClient client) {
        try {
            bt.runtime.BtClient delegate = client;
            if (delegate.getClass().getName().equals("bt.LazyClient")) {
                java.lang.reflect.Field delegateField = delegate.getClass().getDeclaredField("delegate");
                delegateField.setAccessible(true);
                delegate = (bt.runtime.BtClient) delegateField.get(delegate);
            }
            if (delegate != null && delegate.getClass().getName().equals("bt.DefaultClient")) {
                java.lang.reflect.Field contextField = delegate.getClass().getDeclaredField("context");
                contextField.setAccessible(true);
                Object context = contextField.get(delegate);
                if (context instanceof bt.processor.torrent.TorrentContext) {
                    return (bt.processor.torrent.TorrentContext) context;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to extract TorrentContext using reflection", e);
        }
        return null;
    }

    private void checkFrozenTorrents() {
        for (String playlistId : activeClients.keySet()) {
            ClientStatus status = getClientStatus(playlistId);
            if (status == null) continue;

            if ("Downloading".equals(status.getState()) && status.getProgress() < 1.0 && status.getPeers() == 0) {
                long now = System.currentTimeMillis();
                frozenStartTime.putIfAbsent(playlistId, now);
                long durationSec = (now - frozenStartTime.get(playlistId)) / 1000;

                logger.warn("Playlist ID: {} download is frozen (0 seeders). Frozen duration: {} seconds.", playlistId, durationSec);

                if (durationSec >= 15) {
                    logger.info("Restarting frozen torrent for playlist ID: {} to re-bootstrap peer/seeder discovery.", playlistId);
                    frozenStartTime.remove(playlistId);
                    restartTorrent(playlistId);
                }
            } else {
                frozenStartTime.remove(playlistId);
            }
        }
    }

    private void restartTorrent(String playlistId) {
        Playlist playlist = activePlaylists.get(playlistId);
        Boolean sequential = activeModes.get(playlistId);
        if (playlist != null && sequential != null) {
            CompletableFuture.runAsync(() -> {
                stop(playlistId);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
                startTorrent(playlist, sequential);
            });
        }
    }

    protected Storage getStorage(Path dataDir) {
        return new FileSystemStorage(dataDir);
    }
}
