package ee.mt.flipnicexplorer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

public class MainApp extends Application {
    public Stage primaryStage;
    public FlipnicFilesystem ffs;
    public String progress = "";
    public final String waitText = "Please wait...";

    public MainApp() throws IOException {
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("mainView.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 640, 480);
        stage.setTitle("FlipnicFS");
        stage.setScene(scene);
        MainView mv = fxmlLoader.getController();
        mv.setMainApp(this);
        primaryStage = stage;
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }


    public String showInputBox(String topText) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("InputBox.fxml"));
            AnchorPane page = (AnchorPane) loader.load();

            Stage dialogStage = new Stage();
            dialogStage.centerOnScreen();
            dialogStage.setTitle("FlipnicFS");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(primaryStage);
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);

            InputBox controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setHeader(topText);
            dialogStage.showAndWait();

            return controller.getValue();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}