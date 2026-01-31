package com.ztype.zemmision.ui;

import com.ztype.zemmision.models.Playlist;
import com.ztype.zemmision.models.Track;
import com.ztype.zemmision.services.PlaylistService;
import com.ztype.zemmision.services.TorrentService;
// import com.ztype.zemmision.utils.SvgLoader; // Removed SVG loader
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainController {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // Sidebar
    @FXML
    private Button createPlaylistButton;
    @FXML
    private ListView<String> playlistListView;

    // Center Views
    @FXML
    private VBox playlistView;
    @FXML
    private VBox seedingStatusView;
    @FXML
    private VBox allSongsView;

    // Playlist View Components
    @FXML
    private Label playlistTitleLabel;
    @FXML
    private Label playlistDescriptionLabel;
    @FXML
    private Label playlistAuthorLabel;
    @FXML
    private Label playlistStatusLabel;
    @FXML
    private javafx.scene.image.ImageView playlistCoverImage;
    @FXML
    private Button playlistPlayButton;
    @FXML
    private TableView<Track> tracksTableView;
    @FXML
    private TableColumn<Track, String> trackNumberColumn;
    @FXML
    private TableColumn<Track, String> trackTitleColumn;
    @FXML
    private TableColumn<Track, String> trackArtistColumn;
    @FXML
    private TableColumn<Track, String> trackAlbumColumn;
    @FXML
    private TableColumn<Track, String> trackSizeColumn;

    // All Songs View Components
    @FXML
    private TableView<Track> allTracksTableView;
    @FXML
    private TableColumn<Track, String> allTracksTitleColumn;
    @FXML
    private TableColumn<Track, String> allTracksArtistColumn;
    @FXML
    private TableColumn<Track, String> allTracksAlbumColumn;

    // Seeding Status Components
    @FXML
    private TableView<StatusModel> statusTable;
    @FXML
    private TableColumn<StatusModel, String> nameColumn;
    @FXML
    private TableColumn<StatusModel, String> statusColumn;
    @FXML
    private TableColumn<StatusModel, String> progressColumn;
    @FXML
    private TableColumn<StatusModel, String> peersColumn;
    @FXML
    private TableColumn<StatusModel, String> speedColumn;

    // Player Bar Components
    @FXML
    private Label nowPlayingTitle;
    @FXML
    private Label nowPlayingArtist;
    @FXML
    private Button prevTrackButton;
    @FXML
    private Button nextTrackButton;
    @FXML
    private Button skipBackButton;
    @FXML
    private Button skipFwdButton;
    @FXML
    private Button playPauseButton;
    @FXML
    private Label currentTimeLabel;
    @FXML
    private Label totalTimeLabel;
    @FXML
    private Slider seekSlider;
    @FXML
    private Slider volumeSlider;
    @FXML
    private Label volumeLabel;

    // Services
    private PlaylistService playlistService;

    // Player State
    private MediaPlayer mediaPlayer;
    private Playlist currentPlaylist; // The one currently VIEWED
    private Playlist playingPlaylist; // The one currently PLAYING audio
    private int currentTrackIndex = -1;
    private boolean isPlaying = false;
    private boolean isSeeking = false;
    private boolean isMuted = false;
    private double previousVolume = 70;

    public void initialize() {
        logger.info("Initializing MainController...");
        playlistService = new PlaylistService();

        setupPlaylistList();
        setupTracksTable();
        setupAllTracksTable();
        setupStatusTable();
        setupPlayerControls();
        setupIcons();

        refreshPlaylistList();
        startStatusPoller();

        // Show generic welcome or status by default
        showSeedingStatus();

        // Enforce seeding policy on startup
        playlistService.enforceSeedingPolicy();
    }

    private void setupIcons() {
        try {
            // Load PNG Icons - Resized to 32px for bottom bar
            setPngIcon(playPauseButton, "/icons/play-button.png", 32);

            if (playlistPlayButton != null) {
                // Larger icon for playlist header - 64px
                setPngIcon(playlistPlayButton, "/icons/play-button.png", 64);
            }

            // Create Playlist Button (Still using SVG or finding PNG if available? User
            // asked for symbols replacement)
            // Available PNGs: next-button, previous, volume-up, back, repeat, pause, mute,
            // next, play-button
            // No "add" PNG found in list. Falling back to text or keeping SVG for ADD only
            // if acceptable?
            // Task says "use the png symbols...". I will try to use a relevant one or keep
            // text if none fits.
            // For now, I'll keep the SVG for add/create since no PNG match, OR revert to
            // text "+"
            // But let's check if I can use a generic one. I'll stick to SVG for 'add' as it
            // wasn't explicitly provided as PNG.
            // Actually user said "use the png symbols as replacement".
            // I will use what I have.

            if (volumeLabel != null) {
                // Use ImageView for label graphic
                ImageView iv = new ImageView(
                        new Image(getClass().getResourceAsStream("/icons/volume-up-interface-symbol.png")));
                iv.setFitWidth(24);
                iv.setFitHeight(24);
                volumeLabel.setGraphic(iv);
                volumeLabel.setText("");
            }

            setPngIcon(prevTrackButton, "/icons/previous.png", 24);
            setPngIcon(nextTrackButton, "/icons/next-button.png", 24);
            setPngIcon(skipBackButton, "/icons/back.png", 24); // Assuming back.png is suitable
            setPngIcon(skipFwdButton, "/icons/next.png", 24); // Assuming next.png is suitable

        } catch (Exception e) {
            logger.error("Failed to load icons", e);
        }
    }

    private void setPngIcon(Button button, String resourcePath, double size) {
        try {
            ImageView iv = new ImageView(new Image(getClass().getResourceAsStream(resourcePath)));
            iv.setFitWidth(size);
            iv.setFitHeight(size);
            button.setGraphic(iv);
            button.setText("");
        } catch (Exception e) {
            logger.error("Failed to set icon: " + resourcePath, e);
        }
    }

    private void setupPlaylistList() {
        playlistListView.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                showPlaylist(newVal);
            }
        });

        playlistListView.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    String displayName = item;
                    Optional<Playlist> pOpt = findPlaylist(item);
                    if (pOpt.isPresent() && pOpt.get().isPermanentlySeeded()) {
                        displayName += " (∞)";
                    }

                    // Check if playing
                    if (playingPlaylist != null && playingPlaylist.getName().equals(item) && isPlaying) {
                        // Add indicator
                        setText(displayName + " 🔊");
                        setStyle("-fx-text-fill: #22c55e; -fx-font-weight: bold;");
                    } else if (playingPlaylist != null && playingPlaylist.getName().equals(item)) {
                        setText(displayName + " (Paused)");
                        setStyle("-fx-text-fill: #22c55e;");
                    } else {
                        setText(displayName);
                        setStyle("");
                    }
                }
            }
        });

        // Context Menu
        ContextMenu contextMenu = new ContextMenu();
        MenuItem exportItem = new MenuItem("Export Torrent");
        exportItem.setOnAction(e -> {
            String selected = playlistListView.getSelectionModel().getSelectedItem();
            if (selected != null)
                handleExportTorrent(selected);
        });

        MenuItem toggleSeedItem = new MenuItem("Toggle Permanent Seeding");
        toggleSeedItem.setOnAction(e -> {
            String selected = playlistListView.getSelectionModel().getSelectedItem();
            if (selected != null)
                handleTogglePermanentSeeding(selected);
        });

        MenuItem deleteItem = new MenuItem("Delete Playlist");
        deleteItem.setOnAction(e -> {
            String selected = playlistListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                handleDeletePlaylist(selected);
            }
        });

        contextMenu.getItems().addAll(exportItem, toggleSeedItem, new SeparatorMenuItem(), deleteItem);
        playlistListView.setContextMenu(contextMenu);
    }

    private void handleDeletePlaylist(String playlistName) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Playlist");
        alert.setHeaderText("Delete " + playlistName + "?");
        alert.setContentText("Are you sure you want to delete this playlist? This cannot be undone.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                findPlaylist(playlistName).ifPresent(p -> {
                    playlistService.deletePlaylist(p.getId());
                    if (currentPlaylist != null && currentPlaylist.getId().equals(p.getId())) {
                        currentPlaylist = null;
                        playlistView.setVisible(false);
                    }
                    refreshPlaylistList();
                });
            }
        });
    }

    private void setupTracksTable() {
        tracksTableView.setEditable(true);

        trackNumberColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                String.valueOf(tracksTableView.getItems().indexOf(cellData.getValue()) + 1)));
        trackTitleColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getTitle()));
        trackTitleColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        trackTitleColumn.setOnEditCommit(event -> {
            Track track = event.getRowValue();
            track.setTitle(event.getNewValue());
            // Save changes
            if (currentPlaylist != null) {
                playlistService.updatePlaylist(currentPlaylist);
            }
        });

        trackArtistColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getArtist()));
        trackArtistColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        trackArtistColumn.setOnEditCommit(event -> {
            Track track = event.getRowValue();
            track.setArtist(event.getNewValue());
            if (currentPlaylist != null) {
                playlistService.updatePlaylist(currentPlaylist);
            }
        });

        trackAlbumColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getAlbum()));
        trackAlbumColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        trackAlbumColumn.setOnEditCommit(event -> {
            Track track = event.getRowValue();
            track.setAlbum(event.getNewValue());
            if (currentPlaylist != null) {
                playlistService.updatePlaylist(currentPlaylist);
            }
        });

        trackSizeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                String.format("%.2f MB", cellData.getValue().getSizeBytes() / (1024.0 * 1024.0))));

        tracksTableView.setRowFactory(tv -> {
            TableRow<Track> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    Track rowData = row.getItem();
                    playTrack(rowData);
                }
            });
            return row;
        });
    }

    private void setupStatusTable() {
        nameColumn.setCellValueFactory(cell -> cell.getValue().nameProperty());
        statusColumn.setCellValueFactory(cell -> cell.getValue().statusProperty());
        progressColumn.setCellValueFactory(cell -> cell.getValue().progressProperty());
        peersColumn.setCellValueFactory(cell -> cell.getValue().peersProperty());
        speedColumn.setCellValueFactory(cell -> cell.getValue().speedProperty());

        statusTable.setRowFactory(tv -> {
            TableRow<StatusModel> row = new TableRow<>();
            ContextMenu contextMenu = new ContextMenu();
            MenuItem restartItem = new MenuItem("Restart Seeding");
            restartItem.setOnAction(e -> {
                StatusModel item = row.getItem();
                if (item != null)
                    handleRestartSeeding(item.nameProperty().get());
            });
            contextMenu.getItems().add(restartItem);
            row.setContextMenu(contextMenu);
            return row;
        });
    }

    private void setupPlayerControls() {
        volumeSlider.valueProperty().addListener((obs, old, p) -> {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(p.doubleValue() / 100.0);
            }
        });

        seekSlider.setOnMousePressed(e -> isSeeking = true);
        seekSlider.setOnMouseReleased(e -> {
            isSeeking = false;
            if (mediaPlayer != null) {
                mediaPlayer.seek(Duration.seconds(seekSlider.getValue()));
            }
        });
    }

    @FXML
    private void showSeedingStatus() {
        seedingStatusView.setVisible(true);
        playlistView.setVisible(false);
        allSongsView.setVisible(false);
        playlistListView.getSelectionModel().clearSelection();
    }

    private void setupAllTracksTable() {
        allTracksTitleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        allTracksArtistColumn.setCellValueFactory(new PropertyValueFactory<>("artist"));
        allTracksAlbumColumn.setCellValueFactory(new PropertyValueFactory<>("album"));

        allTracksTableView.setRowFactory(tv -> {
            TableRow<Track> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    Track rowData = row.getItem();
                    // For now, play track logic might need adjustment if we don't know the parent
                    // playlist
                    // ideally playTrack should rely on the track file path which is absolute or
                    // relative
                    if (rowData != null)
                        playTrack(rowData);
                }
            });
            return row;
        });
    }

    @FXML
    private void showAllSongs() {
        seedingStatusView.setVisible(false);
        playlistView.setVisible(false);
        allSongsView.setVisible(true);
        playlistListView.getSelectionModel().clearSelection();

        List<Playlist> allPlaylists = playlistService.getAllPlaylists();
        ObservableList<Track> allTracks = FXCollections.observableArrayList();
        for (Playlist p : allPlaylists) {
            allTracks.addAll(p.getTracks());
        }
        allTracksTableView.setItems(allTracks);
    }

    @FXML
    private void handleEditPlaylist() {
        if (currentPlaylist == null)
            return;

        EditPlaylistDialog dialog = new EditPlaylistDialog(currentPlaylist);
        Optional<Playlist> result = dialog.showAndWait();

        result.ifPresent(updatedPlaylist -> {
            // Save changes
            // Note: In a real P2P app, changing metadata of a seeding active torrent is
            // complex.
            // We would need to stop seeding, regenerate metadata.json, recreate torrent,
            // and save.

            // Update existing playlist
            playlistService.updatePlaylist(updatedPlaylist);

            // Actually, the plan said "Update Method: Add updatePlaylistMetadata(Playlist
            // p)".
            // I missed adding that explicitly to Service in previous step, so I will do a
            // quick DB save here
            // or call savePlaylist (which is INSERT OR REPLACE).

            // Update UI details
            playlistTitleLabel.setText(updatedPlaylist.getName());
            playlistDescriptionLabel.setText(updatedPlaylist.getDescription());
            if (updatedPlaylist.getAuthor() != null)
                playlistAuthorLabel.setText("Created by " + updatedPlaylist.getAuthor());
            else
                playlistAuthorLabel.setText("Created by Unknown");

            try {
                if (updatedPlaylist.getCoverImagePath() != null) {
                    playlistCoverImage
                            .setImage(new javafx.scene.image.Image("file:" + updatedPlaylist.getCoverImagePath()));
                }
            } catch (Exception ignored) {
            }

            refreshPlaylistList();
        });
    }

    private void showPlaylist(String playlistName) {
        seedingStatusView.setVisible(false);
        playlistView.setVisible(true);
        allSongsView.setVisible(false);

        Optional<Playlist> pOpt = playlistService.getAllPlaylists().stream()
                .filter(p -> p.getName().equals(playlistName))
                .findFirst();

        if (pOpt.isPresent()) {
            currentPlaylist = pOpt.get(); // Update VIEWED state

            updatePlaylistHeaderState(); // Update icons/labels to match

            if (currentPlaylist.isPermanentlySeeded()) {
                playlistTitleLabel.setText(currentPlaylist.getName() + " (∞)");
            } else {
                playlistTitleLabel.setText(currentPlaylist.getName());
            }
            playlistDescriptionLabel.setText(currentPlaylist.getDescription());

            if (currentPlaylist.getAuthor() != null && !currentPlaylist.getAuthor().isEmpty()) {
                playlistAuthorLabel.setText("Created by " + currentPlaylist.getAuthor());
            } else {
                playlistAuthorLabel.setText("Created by Unknown");
            }

            if (currentPlaylist.getCoverImagePath() != null) {
                try {
                    playlistCoverImage
                            .setImage(new javafx.scene.image.Image("file:" + currentPlaylist.getCoverImagePath()));
                } catch (Exception e) {
                    playlistCoverImage.setImage(null);
                }
            } else {
                playlistCoverImage.setImage(null);
            }

            ObservableList<Track> tracks = FXCollections.observableArrayList();
            if (currentPlaylist.getTracks() != null) {
                tracks.addAll(currentPlaylist.getTracks());
            }
            tracksTableView.setItems(tracks);
        }
    }

    private void updatePlaylistHeaderState() {
        if (currentPlaylist == null)
            return;

        boolean isThisPlaylistPlaying = (playingPlaylist != null
                && playingPlaylist.getId().equals(currentPlaylist.getId()));

        if (isThisPlaylistPlaying && isPlaying) {
            playlistStatusLabel.setText("Currently Playing");
            setPngIcon(playlistPlayButton, "/icons/pause.png", 64);
        } else if (isThisPlaylistPlaying && !isPlaying) {
            playlistStatusLabel.setText("Paused");
            setPngIcon(playlistPlayButton, "/icons/play-button.png", 64);
        } else {
            playlistStatusLabel.setText("");
            setPngIcon(playlistPlayButton, "/icons/play-button.png", 64);
        }

        // Also refresh sidebar to show playing status
        playlistListView.refresh();
    }

    // --- Player Logic ---

    private void playTrack(Track track) {
        // Stop previous
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }

        // Identify playlist and index
        Optional<Playlist> pOpt = playlistService.getAllPlaylists().stream()
                .filter(p -> p.getName().equals(playlistTitleLabel.getText()))
                .findFirst();

        if (pOpt.isPresent()) {
            currentPlaylist = pOpt.get();
            currentTrackIndex = currentPlaylist.getTracks().indexOf(track);
            // Update last played
            playlistService.updateLastPlayed(currentPlaylist.getId());
        } else if (currentPlaylist == null) {
            // Fallback if user somehow clicked a track without a valid playlist context?
            return;
        }

        try {
            File mediaFile = new File(track.getFilePath());
            if (!mediaFile.exists()) {
                showAlert("Error", "File not found: " + track.getFilePath());
                return;
            }

            Media media = new Media(mediaFile.toURI().toString());
            mediaPlayer = new MediaPlayer(media);

            mediaPlayer.currentTimeProperty().addListener((obs, old, time) -> {
                if (!isSeeking) {
                    seekSlider.setValue(time.toSeconds());
                    currentTimeLabel.setText(formatTime(time));
                }
            });

            mediaPlayer.totalDurationProperty().addListener((obs, old, duration) -> {
                seekSlider.setMax(duration.toSeconds());
                totalTimeLabel.setText(formatTime(duration));
            });

            mediaPlayer.setOnEndOfMedia(this::handleNext);

            mediaPlayer.setVolume(volumeSlider.getValue() / 100.0);
            mediaPlayer.setMute(isMuted); // Apply mute state
            mediaPlayer.play();
            isPlaying = true;
            playingPlaylist = currentPlaylist; // Set PLAYING playlist

            updateNowPlaying(track);
            updatePlayPauseIcon();
            updatePlaylistHeaderState(); // Refresh header

        } catch (javafx.scene.media.MediaException e) {
            logger.error("Failed to initialize media player", e);
            String os = System.getProperty("os.name").toLowerCase();
            String msg = "Could not play track: " + e.getMessage();
            if (os.contains("linux")) {
                msg += "\n\nOn Linux, this often means missing GStreamer codecs.\n" +
                        "Run: sudo apt-get install libavcodec-extra gstreamer1.0-libav gstreamer1.0-plugins-base gstreamer1.0-plugins-good gstreamer1.0-plugins-bad gstreamer1.0-plugins-ugly";
            }
            showAlert("Media Error", msg);
        } catch (Exception e) {
            logger.error("Failed to play track: {}", track.getTitle(), e);
            showAlert("Error", "Could not play track: " + e.getMessage());
        }
    }

    private void updateNowPlaying(Track track) {
        nowPlayingTitle.setText(track.getTitle());
        nowPlayingArtist.setText("Local File"); // Placeholder for metadata
    }

    private void updatePlayPauseIcon() {
        if (isPlaying) {
            setPngIcon(playPauseButton, "/icons/pause.png", 32);
        } else {
            setPngIcon(playPauseButton, "/icons/play-button.png", 32);
        }
    }

    private String formatTime(Duration duration) {
        int minutes = (int) duration.toMinutes();
        int seconds = (int) duration.toSeconds() % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    @FXML
    private void handlePlayPause() {
        if (mediaPlayer == null)
            return;

        if (isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
        } else {
            mediaPlayer.play();
            isPlaying = true;
        }
        updatePlayPauseIcon();
        updatePlaylistHeaderState(); // Sync header if visible
    }

    @FXML
    private void handleNext() {
        if (currentPlaylist == null || currentTrackIndex == -1)
            return;

        if (currentTrackIndex < currentPlaylist.getTracks().size() - 1) {
            playTrack(currentPlaylist.getTracks().get(currentTrackIndex + 1));
        } else {
            // Loop or stop? For now stop.
            mediaPlayer.stop();
            isPlaying = false;
            updatePlayPauseIcon();
            updatePlaylistHeaderState();
            mediaPlayer.seek(Duration.ZERO);
        }
    }

    @FXML
    private void handlePrevious() {
        if (currentPlaylist == null || currentTrackIndex == -1)
            return;

        // If > 3 seconds in, restart track
        if (mediaPlayer.getCurrentTime().toSeconds() > 3) {
            mediaPlayer.seek(Duration.ZERO);
        } else {
            if (currentTrackIndex > 0) {
                playTrack(currentPlaylist.getTracks().get(currentTrackIndex - 1));
            }
        }
    }

    @FXML
    private void handlePlay() {
        // "Play" button in playlist header
        if (currentPlaylist != null && playingPlaylist != null
                && currentPlaylist.getId().equals(playingPlaylist.getId())) {
            // If checking the active playlist, toggle play/pause
            handlePlayPause();
            return;
        }

        if (!tracksTableView.getItems().isEmpty()) {
            playTrack(tracksTableView.getItems().get(0));
        }
    }

    @FXML
    private void handleSkipBack() {
        if (mediaPlayer == null)
            return;
        mediaPlayer.seek(mediaPlayer.getCurrentTime().subtract(Duration.seconds(10)));
    }

    @FXML
    private void handleSkipFwd() {
        if (mediaPlayer == null)
            return;
        mediaPlayer.seek(mediaPlayer.getCurrentTime().add(Duration.seconds(10)));
    }

    @FXML
    private void handleMute() {
        if (isMuted) {
            // Unmute
            isMuted = false;
            volumeSlider.setValue(previousVolume);
            setVolumeIcon("/icons/volume-up-interface-symbol.png");
            if (mediaPlayer != null) {
                mediaPlayer.setMute(false);
            }
        } else {
            // Mute
            previousVolume = volumeSlider.getValue();
            isMuted = true;
            volumeSlider.setValue(0);
            setVolumeIcon("/icons/volume-mute.png");
            if (mediaPlayer != null) {
                mediaPlayer.setMute(true);
            }
        }
    }

    private void setVolumeIcon(String path) {
        if (volumeLabel != null) {
            try {
                ImageView iv = new ImageView(new Image(getClass().getResourceAsStream(path)));
                iv.setFitWidth(24);
                iv.setFitHeight(24);
                volumeLabel.setGraphic(iv);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // --- Action Handlers (Create, Import, Export, Restart) ---

    @FXML
    private void handleCreatePlaylist() {
        TextInputDialog dialog = new TextInputDialog("My Awesome Playlist");
        dialog.setTitle("New Playlist");
        dialog.setHeaderText("Create a new playlist");
        dialog.setContentText("Please enter playlist name:");

        dialog.showAndWait().ifPresent(name -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Audio Files");
            List<File> files = fileChooser.showOpenMultipleDialog(playlistListView.getScene().getWindow());

            if (files != null && !files.isEmpty()) {
                try {
                    playlistService.createPlaylist(name, "User created playlist", files);
                    refreshPlaylistList();
                    showAlert("Success", "Playlist created!");
                } catch (Exception e) {
                    logger.error("Failed create playlist", e);
                    showAlert("Error", e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleImportTorrent() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Import Torrent");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Torrents", "*.torrent"));
        File f = fc.showOpenDialog(playlistListView.getScene().getWindow());
        if (f != null) {
            try {
                playlistService.importPlaylist(f);
                refreshPlaylistList();
                showSeedingStatus(); // Switch to status view to show download
            } catch (Exception e) {
                showAlert("Error", e.getMessage());
            }
        }
    }

    private void handleExportTorrent(String playlistName) {
        findPlaylist(playlistName).ifPresent(p -> {
            FileChooser fc = new FileChooser();
            fc.setInitialFileName(playlistName + ".torrent");
            File dest = fc.showSaveDialog(playlistListView.getScene().getWindow());
            if (dest != null) {
                try {
                    playlistService.exportTorrent(p.getId(), dest);
                    showAlert("Success", "Exported!");
                } catch (Exception e) {
                    showAlert("Error", e.getMessage());
                }
            }
        });
    }

    private void handleRestartSeeding(String playlistName) {
        findPlaylist(playlistName).ifPresent(p -> {
            playlistService.restartSeeding(p.getId());
            showAlert("Success", "Restart signal sent.");
        });
    }

    private void handleTogglePermanentSeeding(String playlistName) {
        findPlaylist(playlistName).ifPresent(p -> {
            boolean newState = !p.isPermanentlySeeded();

            playlistService.setPermanentSeeding(p.getId(), newState);

            String status = newState ? "enabled" : "disabled";
            showAlert("Success", "Permanent seeding " + status + " for " + playlistName);

            // Re-enforce policy
            playlistService.enforceSeedingPolicy();

            // Refresh UI
            refreshPlaylistList();
            if (currentPlaylist != null && currentPlaylist.getId().equals(p.getId())) {
                showPlaylist(p.getName());
            }
        });
    }

    private Optional<Playlist> findPlaylist(String name) {
        return playlistService.getAllPlaylists().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst();
    }

    private void refreshPlaylistList() {
        playlistListView.getItems().clear();
        playlistService.getAllPlaylists().forEach(p -> playlistListView.getItems().add(p.getName()));
    }

    private void startStatusPoller() {
        javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), event -> updateStatusTable()));
        timeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        timeline.play();
    }

    private void updateStatusTable() {
        ObservableList<StatusModel> data = FXCollections.observableArrayList();
        for (Playlist p : playlistService.getAllPlaylists()) {
            TorrentService.ClientStatus status = playlistService.getTransferStatus(p.getId());
            data.add(new StatusModel(
                    p.getName(),
                    status.getState(),
                    String.format("%.1f%%", status.getProgress() * 100),
                    String.valueOf(status.getPeers()),
                    String.format("%.2f KB/s", status.getDownloadSpeed() / 1024.0)));
        }
        statusTable.setItems(data);
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static class StatusModel {
        private final SimpleStringProperty name;
        private final SimpleStringProperty status;
        private final SimpleStringProperty progress;
        private final SimpleStringProperty peers;
        private final SimpleStringProperty speed;

        public StatusModel(String name, String status, String progress, String peers, String speed) {
            this.name = new SimpleStringProperty(name);
            this.status = new SimpleStringProperty(status);
            this.progress = new SimpleStringProperty(progress);
            this.peers = new SimpleStringProperty(peers);
            this.speed = new SimpleStringProperty(speed);
        }

        public StringProperty nameProperty() {
            return name;
        }

        public StringProperty statusProperty() {
            return status;
        }

        public StringProperty progressProperty() {
            return progress;
        }

        public StringProperty peersProperty() {
            return peers;
        }

        public StringProperty speedProperty() {
            return speed;
        }
    }
}
