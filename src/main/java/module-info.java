module com.apply.kmcg {
    requires javafx.controls;
    requires javafx.fxml;
    opens com.apply.kmcg to javafx.fxml;
    exports com.apply.kmcg;
}
