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
            primaryStage.setScene(new Scene(root, 800, 600));
            primaryStage.show();
            logger.info("Application started successfully.");
        } catch (Exception e) {
            logger.error("Failed to start application", e);
            throw e;
        }
    }

    public static void main(String[] args) {
        logger.debug("Launching application...");
        launch(args);
    }
}
