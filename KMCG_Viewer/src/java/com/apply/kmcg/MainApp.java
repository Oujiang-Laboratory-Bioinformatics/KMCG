package com.apply.kmcg;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/apply/kmcg/main.fxml"));
            Scene scene = new Scene(loader.load());

            // 更改标题名称
            MainController controller = loader.getController();
            controller.setPrimaryStage(primaryStage);
            primaryStage.setTitle("KMCG Viewer");
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (IOException e) {
            System.err.println("Error loading FXML file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
