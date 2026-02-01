package com.ztype.zemmision.ui;

import com.ztype.zemmision.models.Playlist;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;

public class EditPlaylistDialog extends Dialog<Playlist> {

    private final Playlist playlist;
    private File selectedCoverFile;

    public EditPlaylistDialog(Playlist playlist) {
        this.playlist = playlist;
        setTitle("Edit Playlist");
        setHeaderText("Edit details for: " + playlist.getName());

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        nameField.setText(playlist.getName());
        nameField.setPromptText("Playlist Name");

        TextArea descArea = new TextArea();
        descArea.setText(playlist.getDescription());
        descArea.setPromptText("Description");
        descArea.setPrefRowCount(3);

        TextField authorField = new TextField();
        authorField.setText(playlist.getAuthor());
        authorField.setPromptText("Author / Creator");

        // Cover Art Chooser
        ImageView coverPreview = new ImageView();
        coverPreview.setFitHeight(100);
        coverPreview.setFitWidth(100);
        coverPreview.setPreserveRatio(true);
        if (playlist.getCoverImagePath() != null) {
            try {
                coverPreview.setImage(new Image("file:" + playlist.getCoverImagePath()));
            } catch (Exception ignored) {
            }
        } else {
            // Placeholder or empty
        }

        Button chooseCoverBtn = new Button("Choose Cover Image");
        chooseCoverBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
            Window window = getDialogPane().getScene().getWindow();
            File file = fc.showOpenDialog(window);
            if (file != null) {
                selectedCoverFile = file;
                coverPreview.setImage(new Image(file.toURI().toString()));
            }
        });

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Description:"), 0, 1);
        grid.add(descArea, 1, 1);
        grid.add(new Label("Author:"), 0, 2);
        grid.add(authorField, 1, 2);
        grid.add(new Label("Cover Art:"), 0, 3);

        VBox coverBox = new VBox(5, coverPreview, chooseCoverBtn);
        grid.add(coverBox, 1, 3);

        getDialogPane().setContent(grid);

        // Result Converter
        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                playlist.setName(nameField.getText());
                playlist.setDescription(descArea.getText());
                playlist.setAuthor(authorField.getText());
                if (selectedCoverFile != null) {
                    playlist.setCoverImagePath(selectedCoverFile.getAbsolutePath());
                }
                return playlist;
            }
            return null;
        });
    }
}
