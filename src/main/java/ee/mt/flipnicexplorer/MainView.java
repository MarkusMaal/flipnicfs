package ee.mt.flipnicexplorer;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.ArrayUtils;

public class MainView {
    @FXML
    ListView<String> fileBrowser;

    @FXML
    Label locationLabel;

    @FXML
    Button sepStreamButton;
    @FXML
    Button saveBinButton;
    @FXML
    Button renameButton;
    @FXML
    Button extractFileButton;
    @FXML
    Button extractAllButton;
    @FXML
    Button replaceButton;
    @FXML
    Button rootButton;
    @FXML
    FlowPane actionFlow;

    public final ObservableList<String> files = FXCollections.observableArrayList();

    MainApp mainApp;

    String workingDir = "\\";

    public void setMainApp(MainApp mainApp) {
        this.mainApp = mainApp;
    }

    private void Reload() {
        this.files.clear();
        this.files.addAll(mainApp.ffs.GetRootTOC());
        this.fileBrowser.setItems(this.files);
    }

    @FXML
    public void initialize() {

    }

    @FXML
    private void ClickItem(MouseEvent click) {
        if (click.getClickCount() == 2) {
            DoubleClickItem();
        } else {
            CycleItem();
        }
    }

    @FXML
    private void SelectItem(KeyEvent key) {
        if (key.getCode() == KeyCode.ENTER) {
            DoubleClickItem();
        } else {
            CycleItem();
        }
    }

    private void DoubleClickItem() {
        String sel = this.fileBrowser.getSelectionModel().getSelectedItem();
        if (!this.fileBrowser.getSelectionModel().getSelectedIndices().isEmpty() && sel.endsWith("\\")) {
            this.files.clear();
            this.files.addAll(mainApp.ffs.GetFolderTOC(sel));
            this.fileBrowser.setItems(this.files);
            locationLabel.setText("Path: \\" + sel);
            workingDir = "\\" + sel;
        }
    }

    @SuppressWarnings("unchecked")
    private void CycleItem() {
        try {
            String sel = this.fileBrowser.getSelectionModel().getSelectedItem();
            String labelText = "Path: " + workingDir + sel + "\n" +
                    String.format("Size: %s", mainApp.ffs.GetNiceSize(workingDir.equals("\\") ? mainApp.ffs.GetSize(sel) : mainApp.ffs.GetFolderFileSize(sel, workingDir.substring(1))));
            labelText += "\nOffset: " + (workingDir.equals("\\") ? mainApp.ffs.GetRootOffset(sel) : mainApp.ffs.GetFolderOffset(sel, workingDir.substring(1)));
            labelText += "\nType: " + mainApp.ffs.GetNiceFileType(sel);
            locationLabel.setText(labelText);
            sepStreamButton.setDisable(!sel.endsWith(".PSS"));
        } catch (NullPointerException ignored) {

        }
    }

    @FXML
    private void ReloadRoot() {
        this.files.clear();
        this.files.addAll(mainApp.ffs.GetRootTOC());
        this.fileBrowser.setItems(this.files);
        locationLabel.setText("Path: \\");
        workingDir = "\\";
    }

    private void ReloadFolder() {
        this.files.clear();
        this.files.addAll(mainApp.ffs.GetFolderTOC(workingDir.substring(1)));
        this.fileBrowser.setItems(this.files);
    }

    @FXML
    private void SaveFile() throws IOException {
        String sel = this.fileBrowser.getSelectionModel().getSelectedItem();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Extract file as...");
        fileChooser.setInitialFileName(sel);
        File outputName = fileChooser.showSaveDialog(this.mainApp.primaryStage);
        if (outputName == null) { return; }
        if (workingDir.equals("\\")) {
            mainApp.ffs.SaveFile(sel, outputName.getAbsolutePath());
        } else {
            mainApp.ffs.SaveFileOnFolder(workingDir, sel, outputName.getAbsolutePath());
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("FlipnicFS");
        alert.setHeaderText("File saved successfully");
        alert.setContentText("Saved as: " + outputName.getAbsolutePath());
        alert.showAndWait();
    }

    @FXML
    private void OpenBin() throws IOException {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open BIN file");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Flipnic BIN files", "*.bin", "*.BIN")
        );
        File binfile = fileChooser.showOpenDialog(this.mainApp.primaryStage);
        if (binfile != null) {
            mainApp.ffs = new FlipnicFilesystem(binfile.getAbsolutePath());
            this.Reload();
            extractFileButton.setDisable(false);
            renameButton.setDisable(false);
            rootButton.setDisable(false);
            fileBrowser.setDisable(false);
            locationLabel.setText("Path: \\");
        }
    }

    @FXML
    private void RenameFile() {
        String sel = this.fileBrowser.getSelectionModel().getSelectedItem();
        String newName = mainApp.showInputBox("Current name: " + sel);
        if (this.workingDir.equals("\\")) {
            mainApp.ffs.RenameRootFile(sel, newName);
            ReloadRoot();
        } else {
            mainApp.ffs.RenameFolderFile(sel, newName, workingDir.substring(1));
            ReloadFolder();
        }
    }

    @FXML
    private void SeparateStreams() throws IOException {
        String sel = this.fileBrowser.getSelectionModel().getSelectedItem();
        actionFlow.setDisable(true);
        fileBrowser.setDisable(true);
        locationLabel.setText("Please wait...");
        SeparateStreamsTask strmTsk = new SeparateStreamsTask(this.mainApp.ffs, sel);

        strmTsk.setOnSucceeded(event -> {
            List<List<Byte>> streams = strmTsk.getValue();
            for (int i = 0; i < streams.size() - 1; i++) {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Extract file as...");
                fileChooser.setInitialFileName(sel + "." + (i+1) + ".INT");
                File output = fileChooser.showSaveDialog(this.mainApp.primaryStage);
                if (output == null) {continue;}
                try (FileOutputStream outputStream = new FileOutputStream(output)) {
                    Byte[] data = streams.get(i).toArray(new Byte[streams.get(i).size()]);
                    outputStream.write(ArrayUtils.toPrimitive(data));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Extract file as...");
            fileChooser.setInitialFileName(sel + ".IPU");
            File output = fileChooser.showSaveDialog(this.mainApp.primaryStage);
            if (output == null) {return; }
            try (FileOutputStream outputStream = new FileOutputStream(output)) {
                Byte[] data = streams.get(streams.size() - 1).toArray(new Byte[streams.get(streams.size() - 1).size()]);
                outputStream.write(ArrayUtils.toPrimitive(data));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("FlipnicFS");
            alert.setHeaderText("Streams extracted successfully");
            alert.setContentText("Found " + (streams.size() - 1) + " audio stream" + ((streams.size() - 1 != 1)?"s":""));
            alert.showAndWait();
            actionFlow.setDisable(false);
            fileBrowser.setDisable(false);
            this.ReloadRoot();
            try {
                this.mainApp.ffs = new FlipnicFilesystem(this.mainApp.ffs.blobPath);
            } catch (Exception ignored) {}
        });

        ExecutorService executorService = Executors.newFixedThreadPool(1);
        executorService.execute(strmTsk);
    }

    public class SeparateStreamsTask extends Task<List<List<Byte>>> {
        final FlipnicFilesystem ffs;
        final String fileName;
        public SeparateStreamsTask(FlipnicFilesystem ffs, String fileName) {
            this.ffs = ffs;
            this.fileName = fileName;
        }

        @Override
        protected List<List<Byte>> call() throws Exception {
            return this.ffs.GetStreams(this.fileName);
        }
    }
}
