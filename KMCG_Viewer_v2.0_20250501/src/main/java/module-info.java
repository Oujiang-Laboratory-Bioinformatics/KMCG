module com.apply.kmcg {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;

    opens com.apply.kmcg to javafx.fxml;
    exports com.apply.kmcg;
}