package com.apply.kmcg;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;

import javax.imageio.ImageIO;
import java.io.*;
import java.util.*;

import static com.apply.kmcg.KMCG_Processing.*;

public class MainController {

    @FXML
    private TabPane tabPane;  // TabPane 控件
    @FXML
    private Tab tabKMCG;  // KMCG 标签页
    @FXML
    private Button magnificationButton;  // 放大按钮
    // 添加一个布尔变量来跟踪放大功能的启用状态
    private boolean isMagnificationEnabled = false;

    @FXML
    private Button switchButton;

    // 单双倍体转换状态变量
    public static boolean isPolyploidView = false;

    @FXML
    private Button freshButton;

    @FXML
    private Button coordinateButton;

    @FXML
    private Button searchButton;


    // 处理放大按钮的点击事件
//    @FXML
//    public void handleMagnificationButton() {
//        isMagnificationEnabled = !isMagnificationEnabled;  // 切换放大功能的启用状态
//        if (isMagnificationEnabled) {
//            magnificationButton.setText("\uD83D\uDEAB");
//            magnificationButton.setStyle("");  // 清空样式，恢复默认状态
//            magnificationButton.setStyle("-fx-font-size: 17px;");
//        } else {
//            magnificationButton.setText("\uD83D\uDD0D");
//            magnificationButton.setStyle("-fx-font-size: 17px;");
//        }
//        if (kmcgdata != null && !kmcgdata.isEmpty()) {
//            KMCG_Processing.setupMouseTracking(KMCGCanvas,
//                    isPolyploidView ? kmcgdata_polyploid : kmcgdata,
//                    isMagnificationEnabled,
//                    isPolyploidView);
//        }
//    }

//
//    @FXML
//    public void handleSwitchButton() {
//        if (!kmcgdata.isEmpty()) {
//            // 切换视图前清除所有四边形数据
//            KMCG_Processing.clearQuadrilateralData();
//            clearCanvas(KmerCanvas);
//            if (!isPolyploidView) {
//                // 切换到双倍体视图
//                kmcgdata_polyploid = KMCG_Processing.convertToPolyploid(kmcgdata);
//                KMCG_Processing.drawKMCGOnCanvas(KMCGCanvas, kmcgdata_polyploid);
//                KMCG_Processing.setupMouseTracking(KMCGCanvas, kmcgdata_polyploid, isMagnificationEnabled, true);
//                switchButton.setText("👤");
//            } else {
//                // 切换回单倍体视图
//                KMCG_Processing.drawKMCGOnCanvas(KMCGCanvas, kmcgdata);
//                KMCG_Processing.setupMouseTracking(KMCGCanvas, kmcgdata, isMagnificationEnabled, false);
//                switchButton.setText("👥");
//            }
//            isPolyploidView = !isPolyploidView;
//        }
//    }


    @FXML
    public void handleSwitchButton() {
        if (!kmcgdata.isEmpty()) {
            // 切换视图前清除所有四边形数据
            KMCG_Processing.clearQuadrilateralData();
            clearCanvas(KmerCanvas);

            if (!isPolyploidView) {
                // 切换到双倍体视图
                kmcgdata_polyploid = KMCG_Processing.convertToPolyploid(kmcgdata);
                KMCG_Processing.drawKMCGOnCanvas(KMCGCanvas, kmcgdata_polyploid);
                KMCG_Processing.setupMouseTracking(KMCGCanvas, kmcgdata_polyploid, isMagnificationEnabled, true);
            } else {
                // 切换回单倍体视图
                KMCG_Processing.drawKMCGOnCanvas(KMCGCanvas, kmcgdata);
                KMCG_Processing.setupMouseTracking(KMCGCanvas, kmcgdata, isMagnificationEnabled, false);
            }

            // 切换图标
            String imagePath = isPolyploidView
                    ? "/com/apply/kmcg/image/switch.png"
                    : "/com/apply/kmcg/image/single.png";
            InputStream imageStream = getClass().getResourceAsStream(imagePath);
            if (imageStream != null) {
                ImageView icon = new ImageView(new Image(imageStream));
                icon.setFitWidth(20);
                icon.setFitHeight(20);
                icon.setPreserveRatio(true);
                switchButton.setGraphic(icon);
            }

            isPolyploidView = !isPolyploidView;
        }
    }


    @FXML
    public void handleMagnificationButton() {
        isMagnificationEnabled = !isMagnificationEnabled;

        // 切换图标路径
        String imagePath = isMagnificationEnabled ? "/com/apply/kmcg/image/disable.png" : "/com/apply/kmcg/image/magnifier.png";
        InputStream imageStream = getClass().getResourceAsStream(imagePath);

        if (imageStream != null) {
            ImageView icon = new ImageView(new Image(imageStream));
            icon.setFitWidth(20);
            icon.setFitHeight(20);
            icon.setPreserveRatio(true);
            magnificationButton.setGraphic(icon);
        }

        magnificationButton.setStyle("-fx-font-size: 17px;");

        // 原功能逻辑保持不变
        if (kmcgdata != null && !kmcgdata.isEmpty()) {
            KMCG_Processing.setupMouseTracking(KMCGCanvas,
                    isPolyploidView ? kmcgdata_polyploid : kmcgdata,
                    isMagnificationEnabled,
                    isPolyploidView);
        }
    }








    @FXML
    public void handleFreshButton() {
        KMCG_Processing.clickedPoints.clear();
        KMCG_Processing.quadrilateralPoints.clear();
        manualPoints.clear();
        // 清空画布并重绘原始数据
        clearCanvas(KMCGCanvas);
        clearCanvas(KmerCanvas);
        KMCG_Processing.drawKMCGOnCanvas(KMCGCanvas, kmcgdata);

    }
    // 显示手动输入的坐标点
    @FXML
    public void handleCoordinateButton() {
        if (quadrilateralPoints.size() == 4) {
            // 调用显示四边形内部点的方法
            KMCG_Processing.handleQuadrilateralSelection();
        } else if (!manualPoints.isEmpty()) {

            showManualPoints();
        } else {
            // 显示提示信息
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Tip");
            alert.setHeaderText(null);
            alert.setContentText("Please select the area in KMCG first or input coordinates manually.");
            alert.showAndWait();
        }
    }

    private void showManualPoints() {
        StringBuilder pointsString = new StringBuilder("Manually added points:\n");
        int count = 0;

        for (double[] point : manualPoints) {
            pointsString.append(String.format("(%d, %d) ", (int) point[0], (int) point[1]));
            count++;

            if (count % 7 != 0 && count < manualPoints.size()) {
                pointsString.append(", ");
            }

            if (count % 7 == 0) {
                pointsString.append("\n");
            }
        }

        if (count % 7 != 0) {
            pointsString.append("\n");
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Manual Points");
        alert.setHeaderText(null);

        TextArea textArea = new TextArea(pointsString.toString());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxHeight(Region.USE_PREF_SIZE);

        alert.getDialogPane().setContent(textArea);
        alert.showAndWait();
    }


    @FXML
    public void handleSearchButton() {
        // 创建输入对话框
        Dialog<List<double[]>> dialog = new Dialog<>();
        dialog.setTitle("Manually Input Coordinates");
        dialog.setHeaderText("Enter y value and x range to add coordinate points.\nTip: After clicking the confirm button, the previous coordinate data will be cleared.");

        // 设置按钮类型
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // 设置按钮文本
        Platform.runLater(() -> {
            Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
            okButton.setText("Ok");
            Button cancelButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
            cancelButton.setText("Cancel");
        });

        // 创建主容器
        VBox mainContainer = new VBox();
        mainContainer.setSpacing(10);
        mainContainer.setPadding(new Insets(20, 20, 10, 10));

        // 创建滚动面板
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(200);


        // 创建内容容器
        VBox contentBox = new VBox();
        contentBox.setSpacing(10);

        // 添加第一组输入控件
        addInputRow(contentBox);

        // 添加"添加更多"按钮
        Button addMoreButton = new Button("+");
        addMoreButton.setOnAction(e -> addInputRow(contentBox));

        scrollPane.setContent(contentBox);
        mainContainer.getChildren().addAll(scrollPane, addMoreButton);
        dialog.getDialogPane().setContent(mainContainer);

        // 设置结果转换器
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                try {
                    List<double[]> allPoints = new ArrayList<>();
                    boolean hasValidInput = false;

                    // 遍历所有输入行
                    for (Node node : contentBox.getChildren()) {
                        if (node instanceof HBox) {
                            HBox row = (HBox) node;

                            // 通过节点索引获取控件
                            TextField yField = (TextField) row.getChildren().get(1);
                            TextField xStartField = (TextField) row.getChildren().get(3);
                            TextField xEndField = (TextField) row.getChildren().get(5);

                            // 检查是否有输入
                            if (!yField.getText().isEmpty() &&
                                    !xStartField.getText().isEmpty() &&
                                    !xEndField.getText().isEmpty()) {

                                // 自动修正输入值
                                int y = autoCorrectValue(yField.getText(), 0, 301, "y");
                                int xStart = autoCorrectValue(xStartField.getText(), 0, 1001, "x_start");
                                int xEnd = autoCorrectValue(xEndField.getText(), 0, 1001, "x_end");

                                // 确保xEnd >= xStart
                                if (xEnd < xStart) {
                                    xEnd = xStart;
                                    xEndField.setText(String.valueOf(xEnd));
                                }

                                // 生成坐标点
                                for (int x = xStart; x <= xEnd; x++) {
                                    allPoints.add(new double[]{x, y});
                                }
                                hasValidInput = true;
                            }
                        }
                    }

                    if (!hasValidInput) {
                        showAlert("Input Error", "Please enter at least one valid coordinate set");
                        return null;
                    }
                    return allPoints;

                } catch (NumberFormatException e) {
                    showAlert("Input Error", "Please enter valid integers");
                    return null;
                }
            }
            return null;
        });

        // 处理对话框结果
        Optional<List<double[]>> result = dialog.showAndWait();

        result.ifPresent(points -> {
            if (points != null && !points.isEmpty()) {
                manualPoints.clear();  // 清空手动添加的点
                pointsInside.clear();  // 清空四边形选取的点
                quadrilateralPoints.clear();
                clickedPoints.clear();
                manualPoints.addAll(points);

                processPoints();
            }
        });
    }

    // 自动修正输入值的方法
    private int autoCorrectValue(String input, int min, int max, String fieldName) {
        try {
            int value = Integer.parseInt(input);
            if (value < min) {
                System.out.println(fieldName + " value " + value + " corrected to " + min);
                return min;
            } else if (value > max) {
                System.out.println(fieldName + " value " + value + " corrected to " + max);
                return max;
            }
            return value;
        } catch (NumberFormatException e) {
            System.out.println(fieldName + " invalid input corrected to " + min);
            return min;
        }
    }

    // 添加输入行的方法（增加自动修正监听器）
    private void addInputRow(VBox container) {
        HBox row = new HBox();
        row.setSpacing(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5));

        // 创建标签和输入框
        Label yLabel = new Label("y:");
        TextField yField = new TextField();
        yField.setPromptText("0-301");
        yField.setPrefWidth(80);

        Label xStartLabel = new Label("x_start:");
        TextField xStartField = new TextField();
        xStartField.setPromptText("0-1001");
        xStartField.setPrefWidth(80);

        Label xEndLabel = new Label("x_end:");
        TextField xEndField = new TextField();
        xEndField.setPromptText("0-1001");
        xEndField.setPrefWidth(80);

        // 添加自动修正监听器
        yField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) { // 失去焦点时触发修正
                String text = yField.getText();
                if (!text.isEmpty()) {
                    int corrected = autoCorrectValue(text, 0, 301, "y");
                    if (corrected != Integer.parseInt(text)) {
                        yField.setText(String.valueOf(corrected));
                    }
                }
            }
        });

        xStartField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                String text = xStartField.getText();
                if (!text.isEmpty()) {
                    int corrected = autoCorrectValue(text, 0, 1001, "x_start");
                    if (corrected != Integer.parseInt(text)) {
                        xStartField.setText(String.valueOf(corrected));
                    }
                }
            }
        });

        xEndField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                String text = xEndField.getText();
                if (!text.isEmpty()) {
                    int corrected = autoCorrectValue(text, 0, 1001, "x_end");
                    // 额外检查xEnd >= xStart
                    if (!xStartField.getText().isEmpty()) {
                        int xStart = Integer.parseInt(xStartField.getText());
                        if (corrected < xStart) {
                            corrected = xStart;
                        }
                    }
                    if (corrected != Integer.parseInt(text)) {
                        xEndField.setText(String.valueOf(corrected));
                    }
                }
            }
        });

        // 添加数字输入验证
        addNumberValidation(yField);
        addNumberValidation(xStartField);
        addNumberValidation(xEndField);

        // 添加删除按钮
        Button removeButton = new Button("-");
        removeButton.setOnAction(e -> {
            container.getChildren().remove(row);
            if (container.getChildren().isEmpty()) {
                addInputRow(container);
            }
        });

        row.getChildren().addAll(
                yLabel, yField,
                xStartLabel, xStartField,
                xEndLabel, xEndField,
                removeButton
        );

        container.getChildren().add(row);
    }


    // 添加数字输入验证
    private void addNumberValidation(TextField textField) {
        textField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) {
                textField.setText(oldVal);
            }
        });
    }

    // 显示警告对话框
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }


    // 手动添加的点的查询
    private void processPoints() {
        resultData.clear();
        // 如果有四边形选取的点，先处理这些点
//        if (!pointsInside.isEmpty()) {
//            for (double[] point : pointsInside) {
//                String coordinateKey = String.format("(%d, %d)", (int) point[0], (int) point[1]);
//                if (coordinateDict.containsKey(coordinateKey)) {
//                    resultData.add(coordinateDict.get(coordinateKey));
//                }
//            }
//        }

        // 处理手动添加的点
        if (!manualPoints.isEmpty()) {
            for (double[] point : manualPoints) {
                String coordinateKey = String.format("(%d, %d)", (int) point[0], (int) point[1]);
                if (coordinateDict.containsKey(coordinateKey)) {
                    resultData.add(coordinateDict.get(coordinateKey));
                }
            }
            //手动添加的点对应的数据类似[GAGCCCCATCAGCCCGGGACGGGGTCCCTCA!001 CCAAGTTCGGTCAAACATGCAAGATTGCCAC"119
//            System.out.println(resultData);
        }


        String coordData = String.join(" ", resultData);
        List<Map.Entry<Character, Integer>> parsedData = Kmer_Processing.parseData(coordData);
        Map<String, List<Integer>> storage = Kmer_Processing.initializeStorage(Kmer_Processing.NamesData(), Kmer_Processing.LengthsData());
        Map<String, List<Integer>> updatedStorage = Kmer_Processing.updateStorage(Kmer_Processing.NamesData(), storage, parsedData);
        int MaxValue = Kmer_Processing.getMaxValue(Kmer_Processing.NamesData(), storage, parsedData) + 1;
        int getTotalValue = Kmer_Processing.getTotalValue(Kmer_Processing.NamesData(), storage, parsedData);
        drawToKmerCanvas(updatedStorage, MaxValue, getTotalValue);

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
    public static List<List<Integer>> kmcgdata_polyploid = new ArrayList<>();

    public static List<List<Integer>> scaffolddata = new ArrayList<>();

    public static List<String> names = new ArrayList<>();
    public static List<Integer> lengths = new ArrayList<>();



    public static List<double[]> pointsInside = new ArrayList<>();
    public static List<double[]> manualPoints = new ArrayList<>(); // 新增：存储手动输入的坐标点

    public static long totalKmcgSum = 0L;

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
        magnificationButton.setTooltip(new Tooltip("Enlarge the area range."));
        switchButton.setVisible(false);
        switchButton.setTooltip(new Tooltip("Conversion of haploid or polyploid."));
        freshButton.setVisible(false);
        freshButton.setTooltip(new Tooltip("Refresh Canvas."));
        coordinateButton.setVisible(false);
        coordinateButton.setTooltip(new Tooltip("Display the selected coordinate range."));
        searchButton.setVisible(false);
        searchButton.setTooltip(new Tooltip("Manually search coordinate range."));

    }

    // 打开文件并读取数据的处理方法
    @FXML
    public void handleOpenFile() {
        // 创建文件选择器
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt", "*.csv", "*.json", "*.gz","*.*"));
        // 打开文件选择对话框
        File file = fileChooser.showOpenDialog(new Stage());

        if (file != null) {

            // 获取文件路径并读取文件
            String filepath = file.getAbsolutePath();

            List<List<Integer>> kmcgdata = KMCG_Processing.readFile(filepath);
            switchButton.setDisable(false);

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
                KMCG_Processing.drawKMCGOnCanvas(KMCGCanvas, kmcgdata);
                KMCG_Processing.setupMouseTracking(KMCGCanvas, kmcgdata, isMagnificationEnabled, isPolyploidView);
                Quality_Processing.drawQualityOnCanvas(qualityCanvas, targetData);
                Quality_Processing.drawQualityLineCanvas(qualityCanvas);

            }
                magnificationButton.setVisible(true);
                switchButton.setVisible(true);
                freshButton.setVisible(true);
                coordinateButton.setVisible(true);
                searchButton.setVisible(true);
            } else {
                // 数据无效时，显示默认图片
                KMCG_Processing.showDefaultImage(KMCGCanvas);
            }

        }

    @FXML
    private void handleDetailClick(ActionEvent event) {
        if (Filename == null || KMCG_canvas_height == 0 || KMCG_canvas_width == 0) {
            System.out.println(Filename);
            System.out.println(KMCG_canvas_height);
            System.out.println(KMCG_canvas_width);
            // 没有数据的情况
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Tip");
            alert.setHeaderText(null);
            alert.setContentText("Please import the file first.");
            alert.getButtonTypes().setAll(new ButtonType("OK", ButtonBar.ButtonData.OK_DONE));
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
        Label heightLabel = new Label("KMCG Canvas Rows: " + KMCG_canvas_height);
        Label widthLabel = new Label("KMCG Canvas Columns: " + KMCG_canvas_width);

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
        alert.setContentText("Version: 2.0\n" +
                "Last Updated: 2025-4-30\n" +
                "Developed by: Jintlich, Carrot Head Oatmeal, WillChou0, ALBG\n" +
                "Contact: https://github.com/Oujiang-Laboratory-Bioinformatics");
        alert.getButtonTypes().setAll(new ButtonType("OK", ButtonBar.ButtonData.OK_DONE));
        alert.showAndWait();
    }

    @FXML
    private void handleCoordinatePointsClick(ActionEvent event) {
        if (quadrilateralPoints.size() == 4|| !manualPoints.isEmpty()) {
            // 调用显示四边形内部点的方法
            KMCG_Processing.handleQuadrilateralSelection();
        } else {
            // 显示提示信息
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Tip");
            alert.setHeaderText(null);
            alert.setContentText("Please select the area in KMCG first.");
            alert.getButtonTypes().setAll(new ButtonType("OK", ButtonBar.ButtonData.OK_DONE));
            alert.showAndWait();
        }
    }

    private void createDynamicTabs(Map<String, List<List<Integer>>> scaffolddataMap, List<List<Integer>> kmcgdata, Long totalKmcgSum){
        tabPane.getTabs().removeIf(tab -> tab.getProperties().containsKey("dynamicTab"));

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
            newTab.getProperties().put("dynamicTab", true);  // 添加动态Tab标识
            // 创建 Tab 内容
            HBox hbox = new HBox();
            StackPane stackPane = new StackPane();
            AnchorPane anchorPane = new AnchorPane();
            Canvas canvas = new Canvas(1300.0, 800.0);

            // 创建按钮
            Button switchFormatButton = new Button();
            switchFormatButton.setPrefWidth(50.0);
            switchFormatButton.setPrefHeight(40.0);
            // 加载图片
            InputStream percentImageStream = getClass().getResourceAsStream("/com/apply/kmcg/image/numerator.png");
            if (percentImageStream != null) {
                ImageView icon = new ImageView(new Image(percentImageStream));
                icon.setFitWidth(50);
                icon.setFitHeight(40);
                icon.setPreserveRatio(true);
                switchFormatButton.setGraphic(icon);
            }

            AnchorPane.setLeftAnchor(switchFormatButton, 1180.0);
            AnchorPane.setTopAnchor(switchFormatButton, 370.0);

            // 切换按钮点击事件
            switchFormatButton.setOnAction(event -> {
                int mode = processor.toggleDisplayMode(canvas, tabData, kmcgdata, totalKmcgSum);
                try {
                    switch (mode) {
                        case 0:
                            InputStream percentStream = getClass().getResourceAsStream("/com/apply/kmcg/image/numerator.png");
                            if (percentStream != null) {
                                ImageView percentIcon = new ImageView(new Image(percentStream));
                                percentIcon.setFitWidth(50);
                                percentIcon.setFitHeight(40);
                                percentIcon.setPreserveRatio(true);
                                switchFormatButton.setGraphic(percentIcon);
                            }
                            break;
                        case 1:
                            InputStream permilStream = getClass().getResourceAsStream("/com/apply/kmcg/image/greycontrast.png");
                            if (permilStream != null) {
                                ImageView permilIcon = new ImageView(new Image(permilStream));
                                permilIcon.setFitWidth(50);
                                permilIcon.setFitHeight(40);
                                permilIcon.setPreserveRatio(true);
                                switchFormatButton.setGraphic(permilIcon);
                            }
                            break;
                        case 2:
                            InputStream numberStream = getClass().getResourceAsStream("/com/apply/kmcg/image/gc.png");
                            if (numberStream != null) {
                                ImageView numberIcon = new ImageView(new Image(numberStream));
                                numberIcon.setFitWidth(50);
                                numberIcon.setFitHeight(40);
                                numberIcon.setPreserveRatio(true);
                                switchFormatButton.setGraphic(numberIcon);
                            }
                            break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });


            // 添加颜色区间设置按钮
            Button colorRangeButton = new Button();
            colorRangeButton.setPrefWidth(40.0);
            colorRangeButton.setPrefHeight(30.0);

            // 加载设置图标
            InputStream settingsImageStream = getClass().getResourceAsStream("/com/apply/kmcg/image/setting.png");
            if (settingsImageStream != null) {
                ImageView settingsIcon = new ImageView(new Image(settingsImageStream));
                settingsIcon.setFitWidth(20);
                settingsIcon.setFitHeight(20);
                settingsIcon.setPreserveRatio(true);
                colorRangeButton.setGraphic(settingsIcon);
            }

            AnchorPane.setLeftAnchor(colorRangeButton, 1110.0);
            AnchorPane.setTopAnchor(colorRangeButton, 725.0);

            // 设置颜色区间按钮点击事件
            colorRangeButton.setOnAction(event -> {
                // 创建设置对话框
                Dialog<Pair<Integer, Integer>> dialog = new Dialog<>();
                dialog.setTitle("Color Range");
                dialog.setHeaderText("Enter integer values (Max must be > Min by at least 10)");

                // 设置按钮类型
                dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

                // 创建输入字段并设置整数过滤器
                TextField minField = new TextField(String.valueOf(processor.colorRangeMin));
                minField.setTextFormatter(new TextFormatter<>(change -> {
                    if (change.getControlNewText().matches("\\d*")) {
                        return change;
                    }
                    return null;
                }));

                TextField maxField = new TextField(String.valueOf(processor.colorRangeMax));
                maxField.setTextFormatter(new TextFormatter<>(change -> {
                    if (change.getControlNewText().matches("\\d*")) {
                        return change;
                    }
                    return null;
                }));

                // 创建布局
                GridPane grid = new GridPane();
                grid.setHgap(10);
                grid.setVgap(10);
                grid.setPadding(new Insets(20, 150, 10, 10));

                grid.add(new Label("Minimum (0-90%):"), 0, 0);
                grid.add(minField, 1, 0);
                grid.add(new Label("Maximum (10-100%):"), 0, 1);
                grid.add(maxField, 1, 1);

                dialog.getDialogPane().setContent(grid);

                // 转换结果为Pair<Integer, Integer>
                dialog.setResultConverter(dialogButton -> {
                    if (dialogButton == ButtonType.OK) {
                        try {
                            int min = Integer.parseInt(minField.getText());
                            int max = Integer.parseInt(maxField.getText());

                            // 验证输入范围
                            if (min < 0 || max > 100) {
                                showAlert("Invalid Range", "Values must be between 0 and 100");
                                return null;
                            }

                            // 验证最大值至少比最小值大10
                            if (max - min < 10) {
                                showAlert("Invalid Range", "Maximum must be at least 10 greater than minimum");
                                return null;
                            }

                            return new Pair<>(min, max);
                        } catch (NumberFormatException e) {
                            showAlert("Invalid Input", "Please enter integer numbers");
                            return null;
                        }
                    }
                    return null;
                });

                // 处理结果
                Optional<Pair<Integer, Integer>> result = dialog.showAndWait();
                result.ifPresent(range -> {
                    processor.setColorRange(range.getKey(), range.getValue());
                    processor.drawscaffoldOnCanvas(canvas, tabData, kmcgdata, totalKmcgSum);
                });
            });

            // 添加组件
            anchorPane.getChildren().addAll(canvas, switchFormatButton, colorRangeButton);
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


    // 处理保存图片的菜单项点击事件
    @FXML
    public void handleSavePicture(ActionEvent event) {
        // 创建选择Tab的对话框
        Dialog<List<Tab>> dialog = new Dialog<>();
        dialog.setTitle("Save Pictures");
        dialog.setHeaderText("Select tabs to save as png format");

        // 设置按钮类型
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // 创建复选框列表，只包含有内容的Tab
        ListView<CheckBox> tabListView = new ListView<>();
        ObservableList<CheckBox> checkBoxes = FXCollections.observableArrayList();

        // 检查每个Tab是否有Canvas内容
        for (Tab tab : tabPane.getTabs()) {
            Canvas canvas = findCanvasInTab(tab);
            if (canvas != null) {
                CheckBox checkBox = new CheckBox(tab.getText());
                checkBox.setSelected(false); // 默认不选中
                checkBoxes.add(checkBox);
            }
        }

        tabListView.setItems(checkBoxes);
        tabListView.setPrefSize(300, 200);

        // 布局
        VBox content = new VBox(10, new Label("Select tabs to save:"), tabListView);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        // 转换结果为选中的Tab列表
        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                List<Tab> selectedTabs = new ArrayList<>();
                for (int i = 0; i < tabListView.getItems().size(); i++) {
                    CheckBox checkBox = tabListView.getItems().get(i);
                    if (checkBox.isSelected()) {
                        // 根据CheckBox的文本找到对应的Tab
                        String tabText = checkBox.getText();
                        for (Tab tab : tabPane.getTabs()) {
                            if (tab.getText().equals(tabText)) {
                                selectedTabs.add(tab);
                                break;
                            }
                        }
                    }
                }
                return selectedTabs;
            }
            return null;
        });

        // 显示对话框并处理结果
        Optional<List<Tab>> result = dialog.showAndWait();
        result.ifPresent(selectedTabs -> {
            // 选择保存目录
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Select Save Directory");
            File selectedDirectory = directoryChooser.showDialog(tabPane.getScene().getWindow());

            if (selectedDirectory != null) {
                // 保存所有选中的Tab为PNG
                for (Tab tab : selectedTabs) {
                    saveCanvasAsPNG(tab, selectedDirectory);
                }
                showAlert("Save Complete", "All selected images have been saved as PNG successfully!");
            }
        });
    }

    // 从Tab中查找Canvas
    private Canvas findCanvasInTab(Tab tab) {
        if (tab.getContent() instanceof HBox) {
            HBox hbox = (HBox) tab.getContent();
            for (Node node : hbox.getChildren()) {
                if (node instanceof StackPane) {
                    StackPane stackPane = (StackPane) node;
                    for (Node stackNode : stackPane.getChildren()) {
                        if (stackNode instanceof AnchorPane) {
                            AnchorPane anchorPane = (AnchorPane) stackNode;
                            for (Node anchorNode : anchorPane.getChildren()) {
                                if (anchorNode instanceof Canvas) {
                                    return (Canvas) anchorNode;
                                }
                            }
                        }
                    }
                }
            }
        } else if (tab.getContent() instanceof ScrollPane) {
            ScrollPane scrollPane = (ScrollPane) tab.getContent();
            if (scrollPane.getContent() instanceof Canvas) {
                return (Canvas) scrollPane.getContent();
            }
        }
        return null;
    }

    // 保存Tab中的Canvas为图片
    private void saveCanvasAsPNG(Tab tab, File directory) {
        Canvas canvas = findCanvasInTab(tab);
        if (canvas == null) return;

        // 创建快照
        WritableImage image = new WritableImage((int) canvas.getWidth(), (int) canvas.getHeight());
        canvas.snapshot(null, image);

        // 生成文件名
        String fileName = tab.getText().replaceAll("[^a-zA-Z0-9.-]", "_") + ".png";
        File file = new File(directory, fileName);

        try {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
        } catch (IOException e) {
            showAlert("Save Error", "Failed to save image: " + e.getMessage());
        }
    }
}






