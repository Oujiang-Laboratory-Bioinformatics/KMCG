module com.apply.kmcg {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires java.prefs;

    opens com.apply.kmcg to javafx.fxml;
    exports com.apply.kmcg;
}