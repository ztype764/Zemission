package com.ztype.zemmision.ui;

import com.ztype.zemmision.services.PlaylistService;
import com.ztype.zemmision.models.Playlist;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import java.io.File;
import java.util.List;

public class PlaylistCreatorController {

    @FXML
    private TextField nameField;
    @FXML
    private TextField descriptionField;
    @FXML
    private ListView<String> filesListView;
    @FXML
    private Label statusLabel;

    private final PlaylistService playlistService;
    private final List<File> selectedFiles;

    public PlaylistCreatorController() {
        this.playlistService = new PlaylistService();
        this.selectedFiles = new java.util.ArrayList<>();
    }

    @FXML
    public void initialize() {
        // Init
    }

    @FXML
    private void handleAddFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Audio Files");
        fileChooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.ogg", "*.flac"));
        List<File> files = fileChooser.showOpenMultipleDialog(nameField.getScene().getWindow());

        if (files != null) {
            selectedFiles.addAll(files);
            for (File file : files) {
                filesListView.getItems().add(file.getName());
            }
        }
    }

    @FXML
    private void handleCreatePlaylist() {
        String name = nameField.getText();
        String description = descriptionField.getText();

        if (name == null || name.isEmpty()) {
            statusLabel.setText("Please enter a playlist name.");
            return;
        }

        if (selectedFiles.isEmpty()) {
            statusLabel.setText("Please add at least one audio file.");
            return;
        }

        statusLabel.setText("Creating playlist and torrent... please wait.");

        new Thread(() -> {
            Playlist playlist = playlistService.createPlaylist(name, description, selectedFiles);
            javafx.application.Platform.runLater(() -> {
                if (playlist != null) {
                    statusLabel.setText("Playlist '" + playlist.getName() + "' created and seeding!");
                    // Ideally, refresh the player/playlist list
                } else {
                    statusLabel.setText("Error creating playlist.");
                }
            });
        }).start();
    }
}
