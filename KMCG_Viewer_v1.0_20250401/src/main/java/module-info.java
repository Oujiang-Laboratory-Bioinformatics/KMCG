module com.apply.kmcg {
    requires javafx.fxml;
    requires org.controlsfx.controls; // 第三方库按需添加
    requires org.kordamp.ikonli.javafx;
    requires transitive javafx.graphics;
    requires transitive javafx.controls;

    opens com.apply.kmcg to javafx.fxml; // 如果使用 FXML

    exports com.apply.kmcg;
}
