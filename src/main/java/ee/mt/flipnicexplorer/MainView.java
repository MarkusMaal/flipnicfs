package ee.mt.flipnicexplorer;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
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
            fileBrowser.setDisable(true);
            actionFlow.setDisable(true);
            locationLabel.setText(this.mainApp.waitText);
            LoadFileTask lft = getLoadFileTask(binfile);
            ExecutorService executorService = Executors.newFixedThreadPool(1);
            executorService.execute(lft);
        }
    }

    @FXML
    private void SaveBin() throws IOException {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save BIN file");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Flipnic BIN files", "*.bin", "*.BIN")
        );
        File binfile = fileChooser.showSaveDialog(this.mainApp.primaryStage);
        mainApp.ffs.ExportBin(binfile);
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("FlipnicFS");
        alert.setHeaderText("Success");
        alert.setContentText("The file has been exported successfully!");
        alert.showAndWait();
    }
    @FXML
    private void ReplaceFile() throws IOException {
        String sel = this.fileBrowser.getSelectionModel().getSelectedItem();
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText("Error");
        alert.setTitle("FlipnicFS");
        if (sel.endsWith("\\")) {
            alert.setContentText("This program can only replace individual files");
            alert.showAndWait();
        }
        String ext = sel.split("\\.")[1];
        if (ext.equals("SCC")) {
            alert.setContentText("This type of file cannot be replaced");
            alert.showAndWait();
        }
        String desc = this.mainApp.ffs.GetNiceFileType(sel);
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose replacement file");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(desc, "*." + ext),
                new FileChooser.ExtensionFilter("Any file (risky)", "*.*")
        );
        fileChooser.setInitialFileName(sel);
        File repfile = fileChooser.showOpenDialog(this.mainApp.primaryStage);
        if (repfile != null) {
            if (workingDir.equals("\\")) {
                if (!mainApp.ffs.OverwriteFile(sel, repfile)) {
                    alert.setContentText("Original file is smaller than replacement file!");
                    alert.showAndWait();
                } else {
                    alert.setAlertType(Alert.AlertType.INFORMATION);
                    alert.setHeaderText("Success");
                    alert.setContentText("The file has been replaced successfully!");
                    alert.showAndWait();
                }
            } else {
                if (!mainApp.ffs.OverwriteFolderFile(sel, workingDir.substring(1), repfile)) {
                    alert.setContentText("Original file is smaller than replacement file!");
                    alert.showAndWait();
                } else {
                    alert.setAlertType(Alert.AlertType.INFORMATION);
                    alert.setHeaderText("Success");
                    alert.setContentText("The file has been replaced successfully!");
                    alert.showAndWait();
                }
            }
        }
    }

    private LoadFileTask getLoadFileTask(File binfile) throws IOException {
        LoadFileTask lft = new LoadFileTask(binfile.getAbsolutePath(), this.mainApp);
        lft.setOnSucceeded(event -> {
            mainApp.ffs = lft.getValue();
            extractFileButton.setDisable(false);
            extractAllButton.setDisable(false);
            renameButton.setDisable(false);
            rootButton.setDisable(false);
            fileBrowser.setDisable(false);
            actionFlow.setDisable(false);
            replaceButton.setDisable(false);
            saveBinButton.setDisable(false);
            this.Reload();
            locationLabel.setText("Path: \\");
        });
        return lft;
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
    private void ExtractAll() throws IOException {
        actionFlow.setDisable(true);
        fileBrowser.setDisable(true);
        locationLabel.setText(this.mainApp.waitText);
        DirectoryChooser ds = new DirectoryChooser();
        ds.setTitle("Choose output directory");
        File outFolder = ds.showDialog(this.mainApp.primaryStage);
        if (outFolder == null) {return;}
        ExtractAllTask eat = getExtractAllTask(outFolder);
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        executorService.execute(eat);
    }

    private ExtractAllTask getExtractAllTask(File outFolder) {
        ExtractAllTask eat = new ExtractAllTask(this.mainApp.ffs, outFolder.getAbsolutePath(), this.mainApp);
        Timer timer = new Timer();
        eat.setOnRunning(event -> {
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> locationLabel.setText(mainApp.waitText + "\n" + mainApp.progress));
                }
            }, 50, 50);
        });
        eat.setOnSucceeded(event -> {
            timer.cancel();
            actionFlow.setDisable(false);
            fileBrowser.setDisable(false);
            this.ReloadRoot();
        });
        return eat;
    }

    @FXML
    private void SeparateStreams() {
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

    public static class SeparateStreamsTask extends Task<List<List<Byte>>> {
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

    public static class LoadFileTask extends Task<FlipnicFilesystem> {
        String fileName;
        public LoadFileTask(String fileName, MainApp mainApp) throws IOException {
            this.fileName = fileName;
        }

        @Override
        protected FlipnicFilesystem call() throws Exception {
            return new FlipnicFilesystem(fileName);
        }
    }

    public static class ExtractAllTask extends Task<Boolean> {
        final FlipnicFilesystem ffs;
        final String outputFolder;

        final String dirSeparator;

        private String currentFile;

        MainApp mainApp;

        public ExtractAllTask(FlipnicFilesystem ffs, String outputFolder, MainApp mainApp) {
            this.ffs = ffs;
            this.outputFolder = outputFolder;
            this.currentFile = "";
            this.mainApp = mainApp;
            if (System.getProperty("os.name").startsWith("Windows")) {
                this.dirSeparator = "\\";
            } else {
                this.dirSeparator = "/";
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Boolean call() throws Exception {
            for (String fileName: this.ffs.GetRootTOC()) {
                this.currentFile = fileName + " (" + this.ffs.GetNiceSize(this.ffs.GetSize(fileName)) + ")";
                this.mainApp.progress = this.currentFile;
                if (!fileName.endsWith("\\")) {
                    if (fileName.endsWith(".SCC")) {
                        System.out.println("Warning: Skipping " + fileName);
                        continue;
                    }
                    this.ffs.SaveFile(fileName, this.outputFolder + this.dirSeparator + fileName.replace("\\", this.dirSeparator));
                } else {
                    Files.createDirectory(Paths.get(this.outputFolder + this.dirSeparator + fileName.replace("\\", "")));
                    HashMap<String, Long> toc = this.ffs.GetFolderTOCbyData(this.ffs.GetFile(fileName));
                    for (String folderFiles : toc.keySet()) {
                        if (folderFiles.endsWith(".SCC")) {
                            System.out.println("Warning: Skipping " + fileName + folderFiles);
                            continue;
                        }
                        this.currentFile = fileName + folderFiles + " (" + this.ffs.GetNiceSize(toc.get(folderFiles)) + ")";
                        this.mainApp.progress = this.currentFile;
                        this.ffs.SaveFileOnFolder(fileName, folderFiles, this.outputFolder + this.dirSeparator + fileName.replace("\\", this.dirSeparator) + folderFiles, toc);
                    }
                }
            }
            return false;
        }

    }
}
