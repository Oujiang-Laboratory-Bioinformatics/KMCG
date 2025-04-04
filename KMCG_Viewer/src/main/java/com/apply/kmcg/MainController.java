package com.apply.kmcg;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.io.*;
import java.util.*;

import static com.apply.kmcg.KMCG_Processing.*;

public class MainController {

    @FXML
    private TabPane tabPane; // TabPane 控件
    @FXML
    private Tab tabKMCG; // KMCG 标签页
    @FXML
    private Button magnificationButton; // 放大按钮
    // 添加一个布尔变量来跟踪放大功能的启用状态
    private boolean isMagnificationEnabled = false;

    // 处理放大按钮的点击事件
    @FXML
    public void handleMagnificationButton() {
        isMagnificationEnabled = !isMagnificationEnabled; // 切换放大功能的启用状态
        if (isMagnificationEnabled) {
            magnificationButton.setText("\uD83D\uDEAB");
            magnificationButton.setStyle(""); // 清空样式，恢复默认状态
            magnificationButton.setStyle("-fx-font-size: 15px;"); // 清空样式，恢复默认状态
        } else {
            magnificationButton.setText("🔍");
            magnificationButton.setStyle("-fx-font-size: 15px;");
        }
        if (kmcgdata != null && !kmcgdata.isEmpty()) {
            KMCG_Processing.setupMouseTracking(KMCGCanvas, kmcgdata, isMagnificationEnabled);
        }
    }

    @FXML
    private Tab tabKMer; // K-mer 标签页

    public static boolean isTabKMCGClosed = false;
    public static boolean isTabKMerClosed = false;

    // 数据第一行
    public static String Filename;
    public static int KMCG_canvas_height;
    public static int KMCG_canvas_width;
    // 用于存储数据的字典，键是 (x, y)，值是数据
    public static Map<String, String> coordinateDict = new HashMap<>();
    public static List<List<Integer>> kmcgdata = new ArrayList<>();

    public static List<List<Integer>> scaffolddata = new ArrayList<>();

    public static List<String> names = new ArrayList<>();
    public static List<Integer> lengths = new ArrayList<>();
    public static List<double[]> pointsInside = new ArrayList<>();
    public static int totalKmcgSum = 0;
    // 质量图
    public static List<List<Integer>> targetData = new ArrayList<>();
    public static List<List<Double>> brokenlineData = new ArrayList<>();
    public static List<String> quality_indication = new ArrayList<>();

    // 显示数据
    public static Tooltip currentTooltip = null;

    public static int totalScaffoldSum = 0;
    public static double max_ratio;

    @FXML
    public void handleKMCGTab() {
        if (isTabKMCGClosed) {
            isTabKMCGClosed = false;
        }
        tabKMCG.setDisable(false);
        tabPane.getSelectionModel().select(tabKMCG); // 选中 TabKMCG
    }

    // 处理“查看 K-mer”选项卡的点击事件
    @FXML
    public void handleKMerTab() {
        if (isTabKMerClosed) {
            isTabKMerClosed = false;
        }
        tabKMer.setDisable(false);
        tabPane.getSelectionModel().select(tabKMer); // 选中 TabKMer
    }

    @FXML
    private Canvas KMCGCanvas; // 画KMCGCanvas

    @FXML
    private Canvas KmerCanvas; // 画KmerCanvas

    @FXML
    private Canvas qualityCanvas; // 画curveCanvas

    // 在初始化时显示默认图片
    @FXML
    public void initialize() {
        // 为关闭事件添加监听器
        tabKMCG.setOnClosed(_ -> isTabKMCGClosed = true);
        tabKMer.setOnClosed(_ -> isTabKMerClosed = true);
        // 默认图片
        KMCG_Processing.showDefaultImage(KMCGCanvas);
        // 初始化时隐藏按钮
        magnificationButton.setVisible(false);
    }

    // 打开文件并读取数据的处理方法
    @FXML
    public void handleOpenFile() {
        // 创建文件选择器
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter("Text Files", "*.txt", "*.csv", "*.json", "*.*"));
        // 打开文件选择对话框
        File file = fileChooser.showOpenDialog(new Stage());

        if (file != null) {

            // 获取文件路径并读取文件
            String filepath = file.getAbsolutePath();

            List<List<Integer>> kmcgdata = KMCG_Processing.readFile(filepath);

            // 计算总KMCG sum
            for (List<Integer> row : kmcgdata) {
                for (Integer value : row) {
                    if (value != null) { // 避免 null 值
                        totalKmcgSum += value;
                    }
                }
            }

            for (List<Integer> row : scaffolddata) {
                for (Integer value : row) {
                    if (value != null) { // 避免 null 值
                        totalScaffoldSum += value;
                    }
                }
            }
            createDynamicTabs(scaffolddataMap, kmcgdata, totalKmcgSum);
            // 将读取的数据传给函数
            if (!kmcgdata.isEmpty()) {
                KMCG_Processing.drawKMCGOnCanvas(KMCGCanvas, kmcgdata);
                KMCG_Processing.setupMouseTracking(KMCGCanvas, kmcgdata, isMagnificationEnabled);
                Quality_Processing.drawQualityOnCanvas(qualityCanvas, targetData);
                Quality_Processing.drawQualityLineCanvas(qualityCanvas);

            }
            magnificationButton.setVisible(true);
        } else {
            // 数据无效时，显示默认图片
            KMCG_Processing.showDefaultImage(KMCGCanvas);
        }

    }

    @FXML
    private void handleDetailClick(ActionEvent event) {
        if (Filename == null || KMCG_canvas_height == 0 || KMCG_canvas_width == 0) {
            // 没有数据的情况
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Tip");
            alert.setHeaderText(null);
            alert.setContentText("Please import the file first.");
            alert.showAndWait();
        } else {
            // 有数据的情况，显示详细信息
            showFileDetails();
        }
    }

    private void showFileDetails() {
        Stage stage = new Stage();
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(15));
        vbox.setAlignment(Pos.CENTER);

        // 文件名样式（居中、加粗、稍大）
        Label filenameLabel = new Label("File Name: " + Filename);
        filenameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        filenameLabel.setMaxWidth(Double.MAX_VALUE);
        filenameLabel.setAlignment(Pos.CENTER);

        // 其他信息
        Label heightLabel = new Label("KMCG canvas height: " + KMCG_canvas_height);
        Label widthLabel = new Label("KMCG canvas width: " + KMCG_canvas_width);

        vbox.getChildren().addAll(filenameLabel, heightLabel, widthLabel);

        Scene scene = new Scene(vbox, 300, 150);
        stage.setScene(scene);
        stage.setTitle("Detail description");
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.show();
    }

    public void handleAbout(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText("KMCG Application");
        alert.setContentText("Version: 1.0\n" +
                "Last Updated: 2025-4-1\n" +
                "Developed by: Carrot Head Oatmeal, WillChou0, ALBG\n" +
                "Contact: ");

        alert.showAndWait();
    }

    @FXML
    private void handleCoordinatePointsClick(ActionEvent event) {
        if (quadrilateralPoints.size() == 4) {
            // 调用显示四边形内部点的方法
            KMCG_Processing.handleQuadrilateralSelection();
        } else {
            // 显示提示信息
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Tip");
            alert.setHeaderText(null);
            alert.setContentText("Please select the area in KMCG first.");
            alert.showAndWait();
        }
    }

    private void createDynamicTabs(Map<String, List<List<Integer>>> scaffolddataMap, List<List<Integer>> kmcgdata,
            int totalKmcgSum) {
        tabPane.getTabs().removeIf(tab -> tab.getProperties().containsKey("dynamicTab"));

        for (Map.Entry<String, List<List<Integer>>> entry : scaffolddataMap.entrySet()) {
            String tabName = entry.getKey();
            List<List<Integer>> tabScaffoldData = entry.getValue();

            // 为每个tab计算独立的值
            int tabMaxValidValue = Scaffolds_Processing.findMaxValue(tabScaffoldData);
            List<List<Integer>> tabSaturationData = Scaffolds_Processing.applySaturation(tabScaffoldData,
                    tabMaxValidValue);
            // 计算该tab的总scaffold sum
            int tabScaffoldSum = 0;
            for (List<Integer> row : tabScaffoldData) {
                for (Integer value : row) {
                    if (value != null) {
                        tabScaffoldSum += value;
                    }
                }
            }
            // 创建Tab数据对象
            TabData tabData = new TabData(tabScaffoldData, tabMaxValidValue, tabSaturationData, tabScaffoldSum);
            // 为每个tab创建独立的processor实例
            Scaffolds_Processing processor = new Scaffolds_Processing();

            // 创建新的 Tab
            Tab newTab = new Tab(tabName);
            newTab.setClosable(false);
            newTab.getProperties().put("dynamicTab", true); // 添加动态Tab标识
            // 创建 Tab 内容
            HBox hbox = new HBox();
            StackPane stackPane = new StackPane();
            AnchorPane anchorPane = new AnchorPane();
            Canvas canvas = new Canvas(1300.0, 800.0);

            // 创建按钮
            Button switchFormatButton = new Button("%");
            switchFormatButton.setPrefWidth(40.0);
            switchFormatButton.setPrefHeight(30.0);
            switchFormatButton.setStyle("-fx-font-size: 15px;");
            AnchorPane.setLeftAnchor(switchFormatButton, 1100.0);
            AnchorPane.setTopAnchor(switchFormatButton, 30.0);

            // 设置按钮点击事件
            switchFormatButton.setOnAction(_ -> {
                int mode = processor.toggleDisplayMode(canvas, tabData, kmcgdata, totalKmcgSum);
                switch (mode) {
                    case 0:
                        switchFormatButton.setText("%");
                        break;
                    case 1:
                        switchFormatButton.setText("‰");
                        break;
                    case 2:
                        switchFormatButton.setText("#");
                        break;
                }
            });

            // 添加组件
            anchorPane.getChildren().addAll(canvas, switchFormatButton);
            stackPane.getChildren().add(anchorPane);
            hbox.getChildren().add(stackPane);
            newTab.setContent(hbox);
            tabPane.getTabs().add(newTab);

            // 初始绘制
            processor.drawscaffoldOnCanvas(canvas, tabData, kmcgdata, totalKmcgSum);
        }
    }

    // 添加一个内部类来存储每个tab的数据
    public static class TabData {
        private final List<List<Integer>> scaffolddata;
        private final int maxValidvalue;
        private final List<List<Integer>> saturationdata;
        private final int totalScaffoldSum;

        public TabData(List<List<Integer>> scaffolddata, int maxValidvalue,
                List<List<Integer>> saturationdata, int totalScaffoldSum) {
            this.scaffolddata = scaffolddata;
            this.maxValidvalue = maxValidvalue;
            this.saturationdata = saturationdata;
            this.totalScaffoldSum = totalScaffoldSum;
        }

        public List<List<Integer>> getScaffolddata() {
            return scaffolddata;
        }

        public int getMaxValidvalue() {
            return maxValidvalue;
        }

        public List<List<Integer>> getSaturationdata() {
            return saturationdata;
        }

        public int getTotalScaffoldSum() {
            return totalScaffoldSum;
        }
    }

}
