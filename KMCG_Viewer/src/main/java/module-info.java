module com.apply.kmcg {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires javafx.swing;
    requires org.controlsfx.controls; // 第三方库按需添加
    requires org.kordamp.ikonli.javafx;

    opens com.apply.kmcg to javafx.fxml; // 如果使用 FXML
    exports com.apply.kmcg;
}