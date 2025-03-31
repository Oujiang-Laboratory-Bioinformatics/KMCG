package com.apply.kmcg;

import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.*;
import java.util.*;

import static com.apply.kmcg.KMCG_Processing.scaffolddataMap;

public class MainController {

    @FXML
    private TabPane tabPane;  // TabPane 控件
    @FXML
    private Tab tabKMCG;  // KMCG 标签页
    @FXML
    private Button magnificationButton;  // 放大按钮
    // 添加一个布尔变量来跟踪放大功能的启用状态
    private boolean isMagnificationEnabled = false;


    // 处理放大按钮的点击事件
    @FXML
    public void handleMagnificationButton() {
        isMagnificationEnabled = !isMagnificationEnabled;  // 切换放大功能的启用状态
        if (isMagnificationEnabled) {
            magnificationButton.setText("\uD83D\uDEAB");
            magnificationButton.setStyle("");  // 清空样式，恢复默认状态
            magnificationButton.setStyle("-fx-font-size: 15px;");  // 清空样式，恢复默认状态
        } else {
            magnificationButton.setText("🔍");
            magnificationButton.setStyle("-fx-font-size: 15px;");
        }
        if (kmcgdata != null && !kmcgdata.isEmpty()) {
            KMCG_Processing.setupMouseTracking(KMCGCanvas, kmcgdata, isMagnificationEnabled);
        }
    }


    @FXML
    private Tab tabKMer;  // K-mer 标签页

    public static boolean isTabKMCGClosed = false;
    public static boolean isTabKMerClosed = false;

    //数据第一行
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

    //质量图
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
        tabPane.getSelectionModel().select(tabKMCG);  // 选中 TabKMCG
    }

    // 处理“查看 K-mer”选项卡的点击事件
    @FXML
    public void handleKMerTab() {
        if (isTabKMerClosed) {
            isTabKMerClosed = false;
        }
        tabKMer.setDisable(false);
        tabPane.getSelectionModel().select(tabKMer);  // 选中 TabKMer
    }

    @FXML
    private Canvas KMCGCanvas;  // 画KMCGCanvas

    @FXML
    private Canvas KmerCanvas;  // 画KmerCanvas

    @FXML
    private Canvas qualityCanvas;  // 画curveCanvas



    // 在初始化时显示默认图片
    @FXML
    public void initialize() {
        // 为关闭事件添加监听器
        tabKMCG.setOnClosed(event -> isTabKMCGClosed = true);
        tabKMer.setOnClosed(event -> isTabKMerClosed = true);
        // 默认图片
        KMCG_Processing.showDefaultImage(KMCGCanvas);
        KMCG_Processing kmerProcessing = new KMCG_Processing(KmerCanvas);
        // 初始化时隐藏按钮
        magnificationButton.setVisible(false);
    }
    // 打开文件并读取数据的处理方法
    @FXML
    public void handleOpenFile() {
        // 创建文件选择器
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt", "*.csv", "*.json", "*.*"));
        // 打开文件选择对话框
        File file = fileChooser.showOpenDialog(new Stage());

        if (file != null) {
            // 获取文件路径并读取文件
            String filepath = file.getAbsolutePath();

            List<List<Integer>> kmcgdata = KMCG_Processing.readFile(filepath);
// 创建一个Map来存储每个tab的数据
            Map<String, TabData> tabDataMap = new HashMap<>();

// 计算总KMCG sum
            for (List<Integer> row : kmcgdata) {
                for (Integer value : row) {
                    if (value != null) {  // 避免 null 值
                        totalKmcgSum += value;
                    }
                }
            }

            for (List<Integer> row : scaffolddata) {
                for (Integer value : row) {
                    if (value != null) {  // 避免 null 值
                        totalScaffoldSum += value;
                    }
                }
            }
            createDynamicTabs(scaffolddataMap, kmcgdata, totalKmcgSum);
            // 将读取的数据传给函数
            if (!kmcgdata.isEmpty()) {
                KMCG_Processing.drawKMCGOnCanvas(KMCGCanvas,kmcgdata);
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


    private void createDynamicTabs(Map<String, List<List<Integer>>> scaffolddataMap, List<List<Integer>> kmcgdata, int totalKmcgSum){
        for (Map.Entry<String, List<List<Integer>>> entry : scaffolddataMap.entrySet()) {
            String tabName = entry.getKey();
            List<List<Integer>> tabScaffoldData = entry.getValue();

            // 为每个tab计算独立的值
            int tabMaxValidValue = Scaffolds_Processing.findMaxValue(tabScaffoldData);
            List<List<Integer>> tabSaturationData = Scaffolds_Processing.applySaturation(tabScaffoldData, tabMaxValidValue);
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
            switchFormatButton.setOnAction(event -> {
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






