# Zemmision - Decentralized Audio Streaming Platform

**Zemmision** is a next-generation, decentralized audio streaming application built on the BitTorrent protocol. It allows users to create, share, and stream customized playlists directly from peers without relying on central servers. By leveraging the power of P2P technology, Zemmision ensures high availability, censorship resistance, and user autonomy.

> [!CAUTION]
> **LEGAL DISCLAIMER: STRICTLY PROHIBITED USE**
>
> This platform is engineered for the **legal sharing of authorized content** only (e.g., Creative Commons music, independent artist releases with permission, public domain audio).
>
> **The use of Zemmision for the distribution, downloading, or streaming of copyrighted material without the explicit permission of the rights holder is STRICTLY PROHIBITED and ILLEGAL.**
>
> - **Zero Tolerance**: The developers of Zemmision do not condone, solicit, or support piracy in any form.
> - **User Responsibility**: You are solely responsible for ensuring that any content you share or download complies with your local intellectual property laws.
> - **No Liability**: The creators and contributors of this open-source project accept **no liability** for any misuse of this software for illegal activities.
>
> **By using this software, you agree to these terms and affirm that you will not use this platform to infringe upon the rights of others.**

---

## 🚀 Key Features

- **Decentralized Streaming**: Stream audio directly from other peers using the BitTorrent protocol. No central servers required.
- **Playlist Management**: Create custom playlists from your local audio files.
- **Metadata Editing**: Easily edit track details (Artist, Album) and playlist information (Title, Description, Cover Art) directly within the app. Changes are locally persisted.
- **Seamless Sharing**: Export playlists as `.torrent` files to share with friends or the community.
- **Smart Seeding**: Automatically seeds your playlists to ensure content availability for others. Includes intelligent seeding policies (e.g., permanent seeding options).
- **Modern UI**: A clean, light-themed interface built with JavaFX, featuring intuitive controls and responsive design.
- **Cross-Platform**: Runs on Linux and Windows (and macOS via JVM).

---

## 🛠️ Technology Stack

Zemmision is built with a robust, modern Java stack:

- **Language**: Java 21 LTS
- **UI Framework**: JavaFX 23 (Controls, FXML, Media)
- **P2P Core**:
  - `atomashpolskiy/bt` (BitTorrent client implementation)
  - `ttorrent` (Torrent file creation)
- **Database**: SQLite (via `sqlite-jdbc`) for local persistence of playlists and metadata.
- **Build Tool**: Maven
- **Packaging**: `maven-shade-plugin` (Fat JAR) and `launch4j` (Windows EXE).

---

## ⚙️ Prerequisites

Before you begin, ensure you have the following installed:

1.  **Java Development Kit (JDK) 21** or higher.
    - Verify with: `java -version`
2.  **Maven 3.8+**.
    - Verify with: `mvn -version`

---

## 📥 Installation & Build

1.  **Clone the Repository**:

    ```bash
    git clone https://github.com/your-username/zemmision.git
    cd zemmision
    ```

2.  **Build the Project**:
    Run the following Maven command to clean and package the application into a runnable JAR:

    ```bash
    mvn clean package -DskipTests
    ```

    _Note: Tests are skipped for speed; run without `-DskipTests` to execute the suite._

3.  **Locate the Artifact**:
    The build will generate two key files in the `target/` directory:
    - `zemmision-0.0.1-SNAPSHOT-shaded.jar` (The runnable Fat JAR for Linux/Mac/Windows)
    - `zemmision.exe` (Windows Executable, if built on/for Windows targets)

---

## ▶️ How to Run

### Linux / macOS

Run the shaded JAR directly using Java:

```bash
java -jar target/zemmision-0.0.1-SNAPSHOT-shaded.jar
```

### Windows

You can run the `.exe` generated in the `target/` folder, or use the Java command:

```cmd
java -jar target\zemmision-0.0.1-SNAPSHOT-shaded.jar
```

---

## 📖 Usage Guide

### 1. Creating a Playlist

1.  Click the **`+`** button in the sidebar "PLAYLISTS" section.
2.  Enter a **Name** and **Description**.
3.  Select audio files from your computer to add.
4.  Click **Create**. The playlist will immediately start seeding.

### 2. Importing a Playlist

1.  Click the **`Import`** button in the sidebar.
2.  Select a valid `.torrent` file associated with a Zemmision playlist.
3.  The playlist will appear in your sidebar and begin downloading/streaming metadata and audio.

### 3. Editing Metadata

- **Playlist Details**: Select a playlist and click the **"Edit Details"** button in the header. You can update the Name, Description, and Cover Image.
- **Track Details**: In the playlist track table, double-click the **Artist** or **Album** cells to inline-edit the metadata. Press **Enter** to save.

### 4. Player Controls

- **Play/Pause**: Middle button.
- **Skip 10s**: Inner arrow buttons (`<<`, `>>`).
- **Prev/Next Track**: Outer arrow buttons (`|<<`, `>>|`).
- **Mute**: Click the volume speaker icon.
- **Seek**: Drag the slider to jump to a specific time.

---

## 🔧 Troubleshooting

### Linux: Media Playback Issues

If audio fails to play, you may be missing GStreamer codecs required by JavaFX Media. install them with:

```bash
sudo apt-get install libavcodec-extra gstreamer1.0-libav gstreamer1.0-plugins-base gstreamer1.0-plugins-good gstreamer1.0-plugins-bad gstreamer1.0-plugins-ugly
```

### Windows: Defender Warning

Since the binary is not signed (requires a paid certificate), Windows Defender might flag the `.exe`. You can safely assume it is false positive if you built it from source, or proceed by clicking "Run Anyway".

---

## 🤝 Contributing

Contributions are welcome! Please fork the repository and submit a Pull Request. ensure you follow the existing code style and include unit tests for new logic.

---

## 📜 License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

Copyright © 2026 ZType.
