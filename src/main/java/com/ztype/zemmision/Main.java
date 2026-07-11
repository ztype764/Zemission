package com.ztype.zemmision;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main extends Application {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    @Override
    public void start(Stage primaryStage) throws Exception {
        logger.info("Starting Zemmision Audio Streamer...");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/ztype/zemmision/ui/main.fxml"));
            Parent root = loader.load();

            primaryStage.setTitle("Zemmision Audio Streamer");
            Scene scene = new Scene(root, 800, 600);
            primaryStage.setScene(scene);
            primaryStage.show();

            startCssHotReload(root, scene);

            logger.info("Application started successfully.");
        } catch (Exception e) {
            logger.error("Failed to start application", e);
            throw e;
        }
    }

    private void startCssHotReload(Parent root, Scene scene) {
        java.io.File sourceCss = new java.io.File("src/main/resources/com/ztype/zemmision/ui/style.css");
        java.io.File targetCss = new java.io.File("target/classes/com/ztype/zemmision/ui/style.css");
        
        java.io.File fileToWatch = null;
        if (sourceCss.exists()) {
            fileToWatch = sourceCss;
        } else if (targetCss.exists()) {
            fileToWatch = targetCss;
        }
        
        if (fileToWatch == null) {
            logger.info("style.css not found on disk. Hot reload disabled.");
            return;
        }
        
        final java.io.File watchFile = fileToWatch;
        logger.info("Starting CSS hot reload watcher on: " + watchFile.getAbsolutePath());
        
        // Define a reload task
        Runnable reloadCss = () -> {
            try {
                root.getStylesheets().clear();
                String cssUrl = watchFile.toURI().toURL().toExternalForm() + "?t=" + System.currentTimeMillis();
                root.getStylesheets().add(cssUrl);
                logger.info("CSS hot reloaded from: " + cssUrl);
            } catch (Exception ex) {
                logger.error("Failed to reload CSS", ex);
            }
        };
        
        // Setup file watcher thread
        Thread watchThread = new Thread(() -> {
            long lastModified = watchFile.lastModified();
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
                
                long currentModified = watchFile.lastModified();
                if (currentModified > lastModified) {
                    lastModified = currentModified;
                    logger.info("CSS file change detected. Triggering reload...");
                    javafx.application.Platform.runLater(reloadCss);
                }
            }
        });
        watchThread.setDaemon(true);
        watchThread.start();
        
        // Setup hotkey (F5 or Ctrl+R) to trigger manual reload
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.F5 || 
                (event.isControlDown() && event.getCode() == javafx.scene.input.KeyCode.R)) {
                logger.info("Hot reload shortcut pressed. Triggering reload...");
                javafx.application.Platform.runLater(reloadCss);
            }
        });
    }

    public static void main(String[] args) {
        logger.debug("Launching application...");
        launch(args);
    }
}
