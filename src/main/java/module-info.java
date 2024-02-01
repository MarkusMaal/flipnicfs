module ee.mt.flipnicexplorer {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires org.kordamp.bootstrapfx.core;
    requires org.apache.commons.lang3;

    opens ee.mt.flipnicexplorer to javafx.fxml;
    exports ee.mt.flipnicexplorer;
}