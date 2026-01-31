package com.ztype.zemmision.ui;

import com.ztype.zemmision.models.Playlist;
import com.ztype.zemmision.models.Track;
import com.ztype.zemmision.services.PlaylistService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import java.io.File;

public class PlayerController {

    @FXML
    private ListView<Playlist> playlistsListView;
    @FXML
    private ListView<Track> tracksListView;
    @FXML
    private Label currentTrackLabel;
    @FXML
    private Button playPauseButton;

    private final PlaylistService playlistService;
    private MediaPlayer mediaPlayer;
    private Playlist currentPlaylist;

    public PlayerController() {
        this.playlistService = new PlaylistService();
    }

    @FXML
    public void initialize() {
        playlistsListView.setCellFactory(param -> new ListCell<Playlist>() {
            @Override
            protected void updateItem(Playlist item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getName());
                }
            }
        });

        tracksListView.setCellFactory(param -> new ListCell<Track>() {
            @Override
            protected void updateItem(Track item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getTitle());
                }
            }
        });

        playlistsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadPlaylist(newVal);
            }
        });

        tracksListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Track track = tracksListView.getSelectionModel().getSelectedItem();
                if (track != null) {
                    playTrack(track);
                }
            }
        });

        handleRefresh();

        // Init icon
        setPngIcon(playPauseButton, "/icons/play-button.png", 16);
    }

    private void setPngIcon(Button button, String resourcePath, double size) {
        try {
            ImageView iv = new ImageView(new Image(getClass().getResourceAsStream(resourcePath)));
            iv.setFitWidth(size);
            iv.setFitHeight(size);
            button.setGraphic(iv);
            button.setText("");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleRefresh() {
        playlistsListView.getItems().setAll(playlistService.getAllPlaylists());
    }

    private void loadPlaylist(Playlist playlist) {
        this.currentPlaylist = playlist;
        tracksListView.getItems().setAll(playlist.getTracks());
        // In a real app, ensure torrent is downloading/seeding
    }

    private void playTrack(Track track) {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }

        // For local simulation, we assume file exists at filePath.
        // In real streaming, this needs to point to the partially downloaded file in
        // staging.
        // For "My Playlist", filePath is absolute original path.
        // For imported playlists, we'd need to resolve from staging.
        try {
            File file = new File(track.getFilePath());
            if (file.exists()) {
                Media media = new Media(file.toURI().toString());
                mediaPlayer = new MediaPlayer(media);
                mediaPlayer.play();
                currentTrackLabel.setText("Playing: " + track.getTitle());
                setPngIcon(playPauseButton, "/icons/pause.png", 16);

                mediaPlayer.setOnEndOfMedia(() -> playNext());
            } else {
                currentTrackLabel.setText("File not found: " + track.getTitle());
            }
        } catch (Exception e) {
            currentTrackLabel.setText("Error playing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void playNext() {
        int index = tracksListView.getSelectionModel().getSelectedIndex();
        if (index >= 0 && index < tracksListView.getItems().size() - 1) {
            tracksListView.getSelectionModel().select(index + 1);
            playTrack(tracksListView.getItems().get(index + 1));
        }
    }

    @FXML
    private void handlePlayPause() {
        if (mediaPlayer != null) {
            if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                mediaPlayer.pause();
                setPngIcon(playPauseButton, "/icons/play-button.png", 16);
            } else {
                mediaPlayer.play();
                setPngIcon(playPauseButton, "/icons/pause.png", 16);
            }
        }
    }

    @FXML
    private void handleStop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            setPngIcon(playPauseButton, "/icons/play-button.png", 16);
        }
    }
}
