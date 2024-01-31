package ee.mt.flipnicexplorer;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

import java.io.File;

public class InputBox {
    private MainApp mainApp;
    private Stage dialogStage;


    public void setMainApp(MainApp m) {this.mainApp = m;}
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    @FXML
    private TextField inputText;

    @FXML
    private Label topText;

    @FXML
    private AnchorPane anchorPane;

    private String value = null;

    @FXML
    private void endClicked() {
        this.value = inputText.getText();
        dialogStage.close();
    }

    public String getValue() {
        return this.value;
    }

    public void setHeader(String text) {
        topText.setText(text);
    }

    @FXML
    public void initialize() {

    }
}
