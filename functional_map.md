# Zemission: Architectural & Functional Map

Zemission is a peer-to-peer (P2P) music player and playlist sharing application built on JavaFX and the BitTorrent protocol. It allows users to create playlists, package them as torrents, share them over the internet, and stream audio tracks in real-time as they download from seeders.

---

## 1. High-Level Architecture

The software follows a modular design separating the User Interface (JavaFX), Core Domain logic (Playlists/Tracks), Database layer (SQLite), and the P2P networking layer (Bt client, DHT, Trackers, and UPnP Port Mapper).

```mermaid
graph TD
    UI[JavaFX UI: MainController] --> |User Actions| PS[PlaylistService]
    UI --> |Audio Actions| MP[StandaloneMediaPlayer]
    PS --> |Saves/Loads Metadata| DB[DatabaseService]
    PS --> |Commands Transfers| TS[TorrentService]
    MP --> |Reads Bytes| GFIS[GrowingFileInputStream]
    GFIS --> |Polls File Growth| Disk[(Data Folder / Staging Area)]
    TS --> |Seeds / Downloads| Bt[Bt Core Client]
    TS --> |Maps WAN Ports| PM[PortMapper UPnP/NAT-PMP]
    Bt --> |Writes Chunks| Disk
    DB --> |Reads/Writes| SQLite[(SQLite DB)]
```

---

## 2. Core Modules & Responsibilities

### 📂 Presentation Layer (UI)
*   **[Launcher](file:///home/umar/Documents/Zemission/src/main/java/com/ztype/zemmision/Launcher.java) & [Main](file:///home/umar/Documents/Zemission/src/main/java/com/ztype/zemmision/Main.java)**: Bootstraps the JavaFX environment and loads the parent view.
*   **[MainController](file:///home/umar/Documents/Zemission/src/main/java/com/ztype/zemmision/ui/MainController.java)**: 
    *   Glues UI elements (tables, sliders, sidebars) to backing services.
    *   Triggers playlist importing, exports, permanent seeding toggles, and deletion confirmations.
    *   Manages media player controls: Play, Pause, Next, Previous, and Seek Slider bindings.

### 📂 Service Layer (Business Logic)
*   **[PlaylistService](file:///home/umar/Documents/Zemission/src/main/java/com/ztype/zemmision/services/PlaylistService.java)**: 
    *   Acts as the central coordination layer.
    *   Performs file staging (copying/linking files to secure sandbox folders) and updates relative paths.
    *   Generates and merges `metadata.json` for torrent sharing.
    *   Calculates individual track download progress by matching disk size expectations against the active torrent bitfield.
*   **[TorrentService](file:///home/umar/Documents/Zemission/src/main/java/com/ztype/zemmision/services/TorrentService.java)**: 
    *   Manages the BitTorrent core engine runtime.
    *   Allocates free listener/acceptor ports dynamically (avoiding port conflicts when running multiple torrents).
    *   Leverages **DHT (Distributed Hash Table)** and **Public Trackers** for internet-wide peer lookup.
    *   Spawns the port mapper gateways (`NetworkGateway`/`ProcessGateway`) to map external TCP port rules using UPnP/NAT-PMP.
*   **[DatabaseService](file:///home/umar/Documents/Zemission/src/main/java/com/ztype/zemmision/services/DatabaseService.java)**:
    *   Manages the local SQLite database connection.
    *   Persists playlists, tracks, metadata, and custom flags (like permanent seeding).

### 📂 Utils Layer (Media & I/O)
*   **[StandaloneMediaPlayer](file:///home/umar/Documents/Zemission/src/main/java/com/ztype/zemmision/utils/StandaloneMediaPlayer.java)**:
    *   Manages the audio decoding pipeline (MP3 SPI support) and outputs PCM bytes via JavaSound `SourceDataLine`.
    *   Spawns background playback threads to avoid blocking the main UI thread.
*   **[GrowingFileInputStream](file:///home/umar/Documents/Zemission/src/main/java/com/ztype/zemmision/utils/GrowingFileInputStream.java)**:
    *   An override of `InputStream` that wraps a `RandomAccessFile`.
    *   Allows live playback of files *while* they are still downloading. If the reader hits the current EOF, it blocks (sleeping for 100ms) until more torrent chunks are written by the downloader, resuming seamlessly.

---

## 3. Core Workflows & Data Flows

### A. Playlist Creation (Seeding Mode)
When a user drags local MP3s into the app to share a new playlist:

```mermaid
sequenceDiagram
    participant User
    participant Controller as MainController
    participant PS as PlaylistService
    participant TS as TorrentService
    participant DB as DatabaseService

    User->>Controller: Create Playlist (drag songs)
    Controller->>PS: createPlaylist(name, tracks)
    PS->>PS: Copy/Link source files to data/staging/Name_UUID/
    PS->>PS: Write metadata.json to staging folder
    PS->>TS: createPlaylistTorrent(playlist)
    TS->>TS: Generate .torrent file pointing to staging folder
    PS->>DB: Save playlist to SQLite
    PS->>TS: startSeeding(playlist)
    TS->>TS: Map TCP port on router via UPnP/NAT-PMP
    TS->>TS: Start BtClient (RarestFirst piece selector)
    TS->>TS: Announce to tracker & register with DHT
```

---

### B. Track Playback & Live Streaming
When a user plays an imported playlist track that is currently downloading:

```mermaid
sequenceDiagram
    participant User
    participant Controller as MainController
    participant PS as PlaylistService
    participant TS as TorrentService
    participant Player as StandaloneMediaPlayer
    participant GFIS as GrowingFileInputStream

    User->>Controller: Double-click Track
    Controller->>PS: play(playlist)
    PS->>TS: startStreaming(playlist)
    TS->>TS: Switch BitTorrent client to Sequential piece selector
    Controller->>Player: playTrack(trackFilePath)
    Player->>GFIS: Initialize with partial file
    loop While playing
        GFIS->>GFIS: Read bytes
        alt Reached EOF but download is not complete
            GFIS->>GFIS: Sleep 100ms & wait for new torrent chunks
        else New chunks written to disk
            GFIS->>Player: Return new bytes
        end
        Player->>User: Play Audio
    end
```

---

## 4. Key P2P & Networking Features

1.  **Distributed Discovery (Tracker + DHT)**:
    *   Announces torrent structures to `udp://tracker.opentrackr.org:1337/announce`.
    *   Mainline DHT is activated to fetch peer addresses without central tracker servers.
2.  **UPnP/NAT-PMP Port Mapping**:
    *   Integrates `com.offbynull.portmapper` to automatically detect the local gateway router.
    *   Dynamically maps the BitTorrent TCP acceptor port to the router's WAN interface, allowing incoming connections from external peers across the internet.
    *   Releases/unmaps the port rules gracefully when stopping a torrent client.
3.  **Sequential vs. RarestFirst Selection**:
    *   **Seeding Mode**: Uses **Rarest-First** chunk selection, maximizing the distribution of unique playlist blocks to the P2P swarm.
    *   **Streaming Mode**: Switches dynamically to **Sequential** selection to download the beginning of tracks first, ensuring the media player can start playback immediately without buffering delays.
