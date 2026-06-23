package com.apply.kmcg;

import javafx.application.Platform;

import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;

import javax.imageio.ImageIO;
import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;


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
    private Button listButton;
    //隐藏按钮状态
    private boolean buttonsHidden = false;

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


    @FXML
    private void handleListButton(ActionEvent event) {
        buttonsHidden = !buttonsHidden;

        // 切换其他按钮的可见状态
        switchButton.setVisible(!buttonsHidden);
        magnificationButton.setVisible(!buttonsHidden);
        coordinateButton.setVisible(!buttonsHidden);
        freshButton.setVisible(!buttonsHidden);
        searchButton.setVisible(!buttonsHidden);

        // listButton 始终保持可见
        listButton.setVisible(true);
    }

    @FXML
    public void handleSwitchButton() {
        if (!kmcgdata.isEmpty()) {
            // 切换视图前清除所有四边形数据
            KMCG_Processing.clearQuadrilateralData();
            clearCanvas(KmerCanvas);
            KmerCanvas.setOnMouseMoved(null);  // 移除监听，防止重新绘制
            KmerCanvas.setOnMouseClicked(null); // 移除监听，防止重新绘制
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
        // 移除鼠标移动事件监听
        KmerCanvas.setOnMouseMoved(null);  // 移除监听，防止重新绘制
        KmerCanvas.setOnMouseClicked(null); // 移除监听，防止重新绘制
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
            ButtonType okButton = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
            alert.getButtonTypes().setAll(okButton);
            alert.showAndWait();
        }
    }

    public static void showCoordinateDataDialog(String title, List<double[]> points, List<String> dataList) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);

        VBox mainContainer = new VBox(10);
        mainContainer.setPadding(new Insets(10));

        CheckBox filterCheckBox = new CheckBox("Only show coordinates with data");
        filterCheckBox.setStyle("-fx-font-weight: bold;");

        FlowPane coordsFlow = new FlowPane();
        coordsFlow.setHgap(8);
        coordsFlow.setVgap(8);
        coordsFlow.setPrefWrapLength(620);
        coordsFlow.setPadding(new Insets(5));
        coordsFlow.setStyle("-fx-background-color: #f9f9f9;");

        ScrollPane coordsScroll = new ScrollPane(coordsFlow);
        coordsScroll.setFitToWidth(true);
        coordsScroll.setPrefHeight(250);
        coordsScroll.setStyle("-fx-background-color: transparent;");

        TextArea dataArea = new TextArea("Hover over the coordinates to see the corresponding data.");
        dataArea.setEditable(false);
        dataArea.setWrapText(true);
        dataArea.setPrefHeight(150);
        dataArea.setStyle("-fx-control-inner-background: #f4faff;");

        Label[] selectedLabel = new Label[1];
        int[] selectedIndex = {-1};

        Function<Integer, String> getLabelStyle = (idx) -> {
            if (idx < dataList.size() && dataList.get(idx) != null && !dataList.get(idx).isEmpty()) {
                return "-fx-border-color: #4CAF50; -fx-padding: 4 6; -fx-background-color: #00BFFF;";
            } else {
                return "-fx-border-color: #aaa; -fx-padding: 4 6; -fx-background-color: #ffffff;";
            }
        };

        Runnable updateCoordinates = () -> {
            coordsFlow.getChildren().clear();

            for (int i = 0; i < points.size(); i++) {
                double[] pt = points.get(i);
                String coordStr = String.format("(%d, %d)", (int) pt[0], (int) pt[1]);

                if (filterCheckBox.isSelected() && (i >= dataList.size() || dataList.get(i) == null || dataList.get(i).isEmpty())) {
                    continue;
                }

                Label coordLabel = new Label(coordStr);
                coordLabel.setPrefWidth(80);
                coordLabel.setPrefHeight(30);

                int index = i;

                coordLabel.setOnMouseEntered(e -> {
                    if (selectedIndex[0] == -1) {
                        if (index < dataList.size() && dataList.get(index) != null && !dataList.get(index).isEmpty()) {
                            dataArea.setText(dataList.get(index)); // 不显示坐标
                        } else {
                            dataArea.setText("No data found");
                        }
                    }
                });

                coordLabel.setOnMouseClicked(e -> {
                    if (selectedIndex[0] == index) {
                        coordLabel.setStyle(getLabelStyle.apply(index));
                        selectedIndex[0] = -1;
                        selectedLabel[0] = null;
                        dataArea.setText("Hover over the coordinates to see the corresponding data.");
                    } else {
                        if (selectedIndex[0] != -1 && selectedLabel[0] != null) {
                            selectedLabel[0].setStyle(getLabelStyle.apply(selectedIndex[0]));
                        }

                        coordLabel.setStyle(getLabelStyle.apply(index) + " -fx-border-color: blue; -fx-border-width: 2;");
                        selectedIndex[0] = index;
                        selectedLabel[0] = coordLabel;

                        if (index < dataList.size() && dataList.get(index) != null && !dataList.get(index).isEmpty()) {
                            dataArea.setText(dataList.get(index)); // 不显示坐标
                        } else {
                            dataArea.setText("No data found");
                        }
                    }
                });

                coordLabel.setStyle(getLabelStyle.apply(index));
                coordsFlow.getChildren().add(coordLabel);
            }
        };

        updateCoordinates.run();

        filterCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            selectedIndex[0] = -1;
            selectedLabel[0] = null;
            dataArea.setText("Hover over the coordinates to see the corresponding data.");
            updateCoordinates.run();
        });

        // Save Button
        Button saveButton = new Button("Download valid datas to");
        saveButton.setStyle("-fx-font-weight: bold; -fx-background-color: #4CAF50; -fx-text-fill: white;");
        saveButton.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Download valid datas");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
            File file = fileChooser.showSaveDialog(null);

            if (file != null) {
                try (FileWriter writer = new FileWriter(file)) {
                    for (String data : dataList) {
                        if (data != null && !data.trim().isEmpty()) {
                            // 拆分并写入每项
                            String[] items = data.split(" ");
                            for (String item : items) {
                                if (!item.trim().isEmpty()) {
                                    writer.write(item.trim() + "\n");
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });


        Label coordsLabel = new Label("Coordinates (hover to view data):");
        Label dataLabel = new Label("Corresponding Data:");

        mainContainer.getChildren().addAll(
                filterCheckBox,
                new Separator(),
                saveButton,
                new Separator(),
                coordsLabel,
                coordsScroll,
                new Separator(),
                dataLabel,
                dataArea
        );

        alert.getDialogPane().setContent(mainContainer);
        alert.getDialogPane().setPrefSize(750, 500);
        alert.getButtonTypes().setAll(new ButtonType("OK", ButtonBar.ButtonData.OK_DONE));
        alert.showAndWait();
    }

    // 手动选择弹窗
    private void showManualPoints() {
        showCoordinateDataDialog("Manual Coordinates and Data View", manualPoints, KMCG_Processing.resultData);
    }


    @FXML
    public void handleSearchButton() {
        // 创建输入对话框
        Dialog<List<double[]>> dialog = new Dialog<>();
        dialog.setTitle("Manually Input Coordinates");
        dialog.setHeaderText("Enter coordinates data. Tip: After clicking the confirm button, the previous coordinate data will be cleared.");

        // 设置按钮类型
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(600); // 设置较小宽度

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

        // 添加输入模式切换
        ToggleGroup inputModeGroup = new ToggleGroup();
        RadioButton rowMode = new RadioButton("Row Input (fixed Y, range of X)");
        RadioButton columnMode = new RadioButton("Column Input (fixed X, range of Y)");
        rowMode.setToggleGroup(inputModeGroup);
        columnMode.setToggleGroup(inputModeGroup);
        rowMode.setSelected(true);

        HBox modeSelection = new HBox(10, rowMode, columnMode);
        modeSelection.setPadding(new Insets(0, 0, 10, 0));

        // 创建滚动面板
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(200);

        // 创建内容容器
        VBox contentBox = new VBox();
        contentBox.setSpacing(10);

        // 添加第一组输入控件
        addInputRow(contentBox, true);

        // 添加"添加更多"按钮
        Button addMoreButton = new Button("+");
        addMoreButton.setOnAction(e -> {
            boolean isRowMode = rowMode.isSelected();
            addInputRow(contentBox, isRowMode);
        });

        scrollPane.setContent(contentBox);
        mainContainer.getChildren().addAll(modeSelection, scrollPane, addMoreButton);
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

                            // 判断是行模式还是列模式创建的输入行
                            boolean isRowInput = row.getChildren().get(0) instanceof Label &&
                                    ((Label)row.getChildren().get(0)).getText().equals("Y:");

                            // 通过节点索引获取控件
                            TextField fixedField = (TextField) row.getChildren().get(1);
                            TextField startField = (TextField) row.getChildren().get(3);
                            TextField endField = (TextField) row.getChildren().get(5);

                            // 检查是否有输入
                            if (!fixedField.getText().isEmpty() &&
                                    !startField.getText().isEmpty() &&
                                    !endField.getText().isEmpty()) {

                                // 自动修正输入值
                                int fixedValue = autoCorrectValue(fixedField.getText(), 0, isRowInput ? 301 : 1001,
                                        isRowInput ? "y" : "x");
                                int start = autoCorrectValue(startField.getText(), 0, isRowInput ? 1001 : 301,
                                        isRowInput ? "x_start" : "y_start");
                                int end = autoCorrectValue(endField.getText(), 0, isRowInput ? 1001 : 301,
                                        isRowInput ? "x_end" : "y_end");

                                // 确保end >= start
                                if (end < start) {
                                    end = start;
                                }

                                // 生成坐标点
                                for (int i = start; i <= end; i++) {
                                    if (isRowInput) {
                                        allPoints.add(new double[]{i, fixedValue}); // x, y
                                    } else {
                                        allPoints.add(new double[]{fixedValue, i}); // x, y
                                    }
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

    // 验证，在失去焦点时验证
    private void addInputRow(VBox contentBox, boolean isRowMode) {
        HBox row = new HBox();
        row.setSpacing(5);
        row.setAlignment(Pos.CENTER_LEFT);

        Label fixedLabel = new Label(isRowMode ? "Y:" : "X:");
        TextField fixedField = new TextField();
        fixedField.setPrefWidth(100);

        // 只在失去焦点时验证
        fixedField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) { // 失去焦点时
                String text = fixedField.getText();
                if (!text.isEmpty()) {
                    int max = isRowMode ? 301 : 1001;
                    int corrected = autoCorrectValue(text, 0, max, isRowMode ? "y" : "x");
                    if (!String.valueOf(corrected).equals(text)) {
                        fixedField.setText(String.valueOf(corrected));
                    }
                }
            }
        });

        Label rangeLabel = new Label(isRowMode ? "X from:" : "Y from:");
        TextField startField = new TextField();
        startField.setPrefWidth(100);

        // 只在失去焦点时验证
        startField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) { // 失去焦点时
                String text = startField.getText();
                if (!text.isEmpty()) {
                    int max = isRowMode ? 1001 : 301;
                    int corrected = autoCorrectValue(text, 0, max, isRowMode ? "x_start" : "y_start");
                    if (!String.valueOf(corrected).equals(text)) {
                        startField.setText(String.valueOf(corrected));
                    }
                }
            }
        });

        Label toLabel = new Label("to");
        TextField endField = new TextField();
        endField.setPrefWidth(100);

        // 只在失去焦点时验证
        endField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) { // 失去焦点时
                String text = endField.getText();
                if (!text.isEmpty()) {
                    int max = isRowMode ? 1001 : 301;
                    int corrected = autoCorrectValue(text, 0, max, isRowMode ? "x_end" : "y_end");
                    if (!String.valueOf(corrected).equals(text)) {
                        endField.setText(String.valueOf(corrected));
                    }

                    // 确保end >= start
                    try {
                        int start = Integer.parseInt(startField.getText());
                        if (corrected < start) {
                            endField.setText(String.valueOf(start));
                        }
                    } catch (NumberFormatException e) {
                        // 忽略无效输入
                    }
                }
            }
        });

        Button removeButton = new Button("-");
        removeButton.setOnAction(e -> contentBox.getChildren().remove(row));

        row.getChildren().addAll(fixedLabel, fixedField, rangeLabel, startField, toLabel, endField, removeButton);
        contentBox.getChildren().add(row);
    }

    // 自动修正输入值的方法
    private int autoCorrectValue(String input, int min, int max, String fieldName) {
        try {
            int value = Integer.parseInt(input);
            if (value < min) {
                return min;
            } else if (value > max) {
                return max;
            }
            return value;
        } catch (NumberFormatException e) {
            return min;
        }
    }



    // 显示警告对话框
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getButtonTypes().setAll(new ButtonType("OK", ButtonBar.ButtonData.OK_DONE));
        alert.showAndWait();
    }


    // 手动添加的点的查询
    private void processPoints() {
        resultData.clear();
        // 处理手动添加的点
        if (!manualPoints.isEmpty()) {
            for (double[] point : manualPoints) {
                String coordinateKey = String.format("(%d, %d)", (int) point[0], (int) point[1]);
                if (coordinateDict.containsKey(coordinateKey)) {
                    resultData.add(coordinateDict.get(coordinateKey));
                }
            }

        }

        String coordData = String.join(" ", resultData);
        List<Map.Entry<Character, Integer>> parsedData = Kmer_Processing.parseData(coordData);
        Map<String, List<Integer>> storage = Kmer_Processing.initializeStorage(Kmer_Processing.NamesData(), Kmer_Processing.LengthsData());
        Map<String, List<Integer>> updatedStorage = Kmer_Processing.updateStorage(Kmer_Processing.NamesData(), storage, parsedData);
        int MaxValue = Kmer_Processing.getMaxValue(Kmer_Processing.NamesData(), storage, parsedData) + 1;
        int getTotalValue = Kmer_Processing.getTotalValue(Kmer_Processing.NamesData(), storage, parsedData);
        drawKmerCanvas(updatedStorage, MaxValue, getTotalValue);

    }

    @FXML
    private Tab tabKMer;  // K-mer 标签页

    public static boolean isTabKMCGClosed = false;
    public static boolean isTabKMerClosed = false;

    //数据第一行
    public static String Filename;
    public static String Fileversion;
    public static int KMCG_canvas_height;
    public static int KMCG_canvas_width;
    public static int Unitsize;
    // 用于存储数据的字典，键是 (x, y)，值是数据
    public static Map<String, String> coordinateDict = new HashMap<>();
    public static List<List<Integer>> kmcgdata = new ArrayList<>();
    public static List<List<Integer>> kmcgdata_polyploid = new ArrayList<>();

    public static List<List<Integer>> scaffolddata = new ArrayList<>();

    public static List<String> names = new ArrayList<>();
    public static List<Integer> lengths = new ArrayList<>();

    public static List<double[]> pointsInside = new ArrayList<>();
    public static List<double[]> manualPoints = new ArrayList<>(); // 存储手动输入的坐标点

    public static long totalKmcgSum = 0L;

    //质量图
    public static List<List<Integer>> targetData = new ArrayList<>();
    public static List<List<Double>> brokenlineData = new ArrayList<>();
    public static List<String> quality_indication = new ArrayList<>();
    /** 0-based row index -> [mu, sigma] from file Row(N+1)_Mean / Row(N+1)_Sigma */
    public static Map<Integer, double[]> qualityGaussianByRow = new HashMap<>();


    // 显示数据
    public static Tooltip currentTooltip = null;

    public static int totalScaffoldSum = 0;

    @FXML
    private Canvas KMCGCanvas;  // 画KMCGCanvas

    @FXML
    private Canvas KmerCanvas;  // 画KmerCanvas
    @FXML
    private StackPane sliderPane;
    @FXML
    private Canvas qualityCanvas;  // 画curveCanvas
    @FXML
    private Canvas qualityLineCanvas;         // 右上折线图
    @FXML
    private Canvas qualityFittingCanvas;      // 右下柱状图

    @FXML
    private Slider qualitySlider;

    @FXML
    private Label rowLabel;


    private final Preferences prefs = Preferences.userNodeForPackage(getClass());
    private static final String LAST_DIR_KEY = "last_open_dir";


    // 在初始化时显示默认图片
    @FXML
    public void initialize() {
        sliderPane.setVisible(false);

        // 关闭标签页监听
        tabKMCG.setOnClosed(event -> isTabKMCGClosed = true);
        tabKMer.setOnClosed(event -> isTabKMerClosed = true);
        // 显示默认图像
        KMCG_Processing.showDefaultImage(KMCGCanvas);
        KMCG_Processing kmerProcessing = new KMCG_Processing(KmerCanvas);

        // 隐藏按钮并设置提示
        listButton.setVisible(false);
        listButton.setTooltip(new Tooltip("Hide/Show other buttons."));
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
        //滑块
        sliderPane.setVisible(false);

//  质量图初始化显示
        if (targetData != null && !targetData.isEmpty()) {
            int initialRow = 0;
            int maxRows = targetData.size();
            qualitySlider.setMin(0);
            qualitySlider.setMax(Math.max(0, maxRows - 1));
            qualitySlider.setValue(0);
            qualitySlider.setDisable(false);

            // 固定标签宽度
            rowLabel.setMinWidth(80);
            rowLabel.setPrefWidth(80);
            rowLabel.setText("Row: " + initialRow);

        } else {
            qualitySlider.setDisable(true);
            rowLabel.setText("Row: 0");
            qualityFittingCanvas.getGraphicsContext2D().clearRect(0, 0,
                    qualityFittingCanvas.getWidth(),
                    qualityFittingCanvas.getHeight());
        }
        // 滑块监听器
        qualitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int sliderValue = newVal.intValue();
            if (targetData != null && sliderValue < targetData.size()) {
                Quality_Processing.drawQualityFittingCanvas(
                        qualityFittingCanvas,
                        targetData,
                        sliderValue
                );
                // 固定格式，保持宽度稳定
                rowLabel.setText(String.format("Row: %-5d", sliderValue));
            }
        });




        rowLabel.setText("Row: 0");
        rowLabel.setFont(new Font(11));   // 设置为 18px
    }



    // 打开文件并读取数据的处理方法
    private Stage primaryStage;

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    private void resetViewerState() {
        tabPane.getTabs().removeIf(tab ->
                Boolean.TRUE.equals(tab.getProperties().get("dynamicTab")) ||
                        Boolean.TRUE.equals(tab.getProperties().get("dynamicGCTab"))
        );

        totalKmcgSum = 0L;
        totalScaffoldSum = 0;
        isPolyploidView = false;
        resultData.clear();

        Filename = null;
        Fileversion = null;
        KMCG_canvas_height = 0;
        KMCG_canvas_width = 0;
        Unitsize = 0;
        kmerlength = 0;

        KMCG_Processing.showDefaultImage(KMCGCanvas);
        clearCanvas(KmerCanvas);
        clearCanvas(qualityCanvas);
        clearCanvas(qualityLineCanvas);
        clearCanvas(qualityFittingCanvas);

        KMCGCanvas.setOnMouseMoved(null);
        KMCGCanvas.setOnMouseClicked(null);
        KmerCanvas.setOnMouseMoved(null);
        KmerCanvas.setOnMouseClicked(null);

        qualitySlider.setDisable(true);
        qualitySlider.setValue(0);
        sliderPane.setVisible(false);
        rowLabel.setText("Row: 0");

        listButton.setVisible(false);
        magnificationButton.setVisible(false);
        switchButton.setVisible(false);
        switchButton.setDisable(true);
        freshButton.setVisible(false);
        coordinateButton.setVisible(false);
        searchButton.setVisible(false);

        if (primaryStage != null) {
            primaryStage.setTitle("KMCG Viewer");
        }
    }

    @FXML
    public void handleOpenFile() {

        // 创建文件选择器
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt", "*.csv", "*.json", "*.gz", "*.*")
        );

        // 读取上一次的目录
        String lastDir = prefs.get(LAST_DIR_KEY, null);
        if (lastDir != null) {
            File dir = new File(lastDir);
            if (dir.exists()) {
                fileChooser.setInitialDirectory(dir);
            }
        }

        // 打开文件选择对话框
        File file = fileChooser.showOpenDialog(new Stage());

        if (file != null) {
            // 保存这次的路径
            prefs.put(LAST_DIR_KEY, file.getParent());

            String filepath = file.getAbsolutePath();

            List<List<Integer>> loadedData = KMCG_Processing.readFile(filepath);

            if (loadedData == null || loadedData.isEmpty()) {
                resetViewerState();
                return;
            }
            tabPane.getTabs().removeIf(tab ->
                    Boolean.TRUE.equals(tab.getProperties().get("dynamicTab")) ||
                            Boolean.TRUE.equals(tab.getProperties().get("dynamicGCTab"))
            );
            // 文件有效：更新窗口标题为文件名
            if (primaryStage != null) {
                primaryStage.setTitle(file.getName());
                Filename = file.getName();
            }


            switchButton.setDisable(false);

            totalKmcgSum = 0L;
            totalScaffoldSum = 0;

            // 计算总KMCG sum
            for (List<Integer> row : loadedData) {
                for (Integer value : row) {
                    if (value != null) {
                        totalKmcgSum += value;
                    }
                }
            }

            for (List<Integer> row : scaffolddata) {
                for (Integer value : row) {
                    if (value != null) {
                        totalScaffoldSum += value;
                    }
                }
            }

            createScaffoldDynamicTabs(scaffolddataMap, loadedData, totalKmcgSum);
            createGCAdjustedTabs();

            if (!loadedData.isEmpty()) {
                KMCG_Processing.drawKMCGOnCanvas(KMCGCanvas, loadedData);
                KMCG_Processing.setupMouseTracking(KMCGCanvas, loadedData, isMagnificationEnabled, isPolyploidView);

                int initialRow = 0;

                if (!targetData.isEmpty()) {
                    qualitySlider.setDisable(false);
                    qualitySlider.setMax(targetData.size() - 1);
                    qualitySlider.setValue(0);
                    rowLabel.setText("Row: 0");
                    sliderPane.setVisible(true);

                    Quality_Processing.drawQualityOnCanvas(qualityCanvas, targetData);
                    if (!brokenlineData.isEmpty()) {
                        Quality_Processing.drawQualityLineCanvas(qualityLineCanvas);
                    }
                    Quality_Processing.drawQualityFittingCanvas(qualityFittingCanvas, targetData, initialRow);
                } else {
                    clearCanvas(qualityCanvas);
                    clearCanvas(qualityLineCanvas);
                    clearCanvas(qualityFittingCanvas);
                    qualitySlider.setDisable(true);
                    sliderPane.setVisible(false);
                }
            }

            listButton.setVisible(true);
            magnificationButton.setVisible(true);
            switchButton.setVisible(true);
            freshButton.setVisible(true);
            coordinateButton.setVisible(true);
            searchButton.setVisible(true);

        }
    }


    @FXML
    private void handleDetailClick(ActionEvent event) {
        if (Fileversion == null || KMCG_canvas_height == 0 || KMCG_canvas_width == 0) {

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

        // ===== 创建字体测量器 =====
        Text txt = new Text(Filename);
        txt.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        double textWidth = txt.getBoundsInLocal().getWidth();

        double windowWidth = Math.max(500, textWidth + 80);  // ⭐ 自动根据文件名长度扩展窗口
        //    最小 500，最长不限

        // ===== 根布局 =====
        VBox root = new VBox(20);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #F4F6F9;");
        root.setAlignment(Pos.CENTER);

        // ===== 卡片布局 =====
        VBox card = new VBox(16);
        card.setPadding(new Insets(20));
        card.setStyle(
                "-fx-background-color: white;"
                        + "-fx-background-radius: 12;"
                        + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 12, 0, 0, 4);"
        );
        card.setPrefWidth(windowWidth - 40);   // ⭐ 卡片宽度跟随窗口
        card.setMaxWidth(windowWidth - 40);

        // ===== 文件名（永不换行） =====
        Label filenameLabel = new Label(Filename);
        filenameLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        filenameLabel.setMaxWidth(Double.MAX_VALUE);
        filenameLabel.setAlignment(Pos.CENTER);
        filenameLabel.setWrapText(false);  // 禁止换行

        Separator separator = new Separator();

        // ===== 信息列表 =====
        VBox infoBox = new VBox(8);
        infoBox.setAlignment(Pos.CENTER_LEFT);

        Label versionLabel   = new Label("• Version: " + Fileversion);
        Label heightLabel    = new Label("• Row: " + KMCG_canvas_height);
        Label widthLabel     = new Label("• Column: " + KMCG_canvas_width);
        Label kmerLength     = new Label("• Kmer Length: " + kmerlength);
        Label kmerUnit       = new Label("• Sliding Window Size: " + Unitsize);

        for (Label lb : new Label[]{versionLabel, heightLabel, widthLabel, kmerLength, kmerUnit}) {
            lb.setStyle("-fx-font-size: 14px; -fx-text-fill: #34495E;");
        }

        infoBox.getChildren().addAll(versionLabel, heightLabel, widthLabel, kmerLength, kmerUnit);

        card.getChildren().addAll(filenameLabel, separator, infoBox);
        root.getChildren().add(card);

        Scene scene = new Scene(root, windowWidth, 300);
        stage.setScene(scene);
        stage.setTitle("Detail Description");
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.show();
    }


    public void handleAbout(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText("KMCG Application");
        alert.setContentText("Version: 3.3\n\n" +
                "Last Updated: 2026-06-18\n\n" +
                "Developed by: Jintlich, Carrot Head Oatmeal, WillChou0, ALBG\n\n" +
                "Github: https://github.com/Oujiang-Laboratory-Bioinformatics");

        // 添加这些设置以确保内容完全显示
        alert.setResizable(true);
        alert.getDialogPane().setMinWidth(400);
        alert.getDialogPane().setMinHeight(250);

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

    private void createScaffoldDynamicTabs(Map<String, List<List<Integer>>> scaffolddataMap, List<List<Integer>> kmcgdata, Long totalKmcgSum){
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
// 添加颜色区间设置按钮
            Button colorRangeButton = new Button();
            colorRangeButton.setPrefWidth(40.0);
            colorRangeButton.setPrefHeight(30.0);

            // 加载设置图标
            InputStream settingsImageStream = getClass().getResourceAsStream("/com/apply/kmcg/image/setting.png");
            if (settingsImageStream != null) {
                ImageView settingsIcon = new ImageView(new Image(settingsImageStream));
                settingsIcon.setFitWidth(30);
                settingsIcon.setFitHeight(30);
                settingsIcon.setPreserveRatio(true);
                colorRangeButton.setGraphic(settingsIcon);

                // 移除按钮背景和边框
                colorRangeButton.setStyle(
                        "-fx-background-color: transparent; " +
                                "-fx-border-color: transparent; " +
                                "-fx-padding: 0;"
                );
            }

            AnchorPane.setLeftAnchor(colorRangeButton, 1110.0);
            AnchorPane.setTopAnchor(colorRangeButton, 705.0);
            // 初始 mode = 0 → 显示
            colorRangeButton.setVisible(true);
            colorRangeButton.setManaged(true);
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
                            colorRangeButton.setVisible(true);
                            colorRangeButton.setManaged(true);
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
                            colorRangeButton.setVisible(false);
                            colorRangeButton.setManaged(false);
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
                            colorRangeButton.setVisible(false);
                            colorRangeButton.setManaged(false);
                            break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            switchFormatButton.setStyle("-fx-border-width: 0; -fx-background-color: transparent; -fx-padding: 0;");


            // 设置颜色区间按钮点击事件
            colorRangeButton.setOnAction(event -> {
                // 创建设置对话框
                Dialog<Pair<Integer, Integer>> dialog = new Dialog<>();
                dialog.setTitle("Color Range");
                dialog.setHeaderText("Enter integer values (Max must be > Min by at least 10)");

                ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
                ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                dialog.getDialogPane().getButtonTypes().addAll(okButtonType, cancelButtonType);

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
                    if (dialogButton == okButtonType) {
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





    // 动态生成 GC Tabs，只有 gcDataMap 非空才生成
    public void createGCAdjustedTabs() {
        // 先移除之前生成的 GC Tabs
        tabPane.getTabs().removeIf(tab -> Boolean.TRUE.equals(tab.getProperties().get("dynamicGCTab")));
        if (gcDataMap == null || gcDataMap.isEmpty()) return; // 没有 * 数据就不生成


        for (Map.Entry<String, List<Integer>> entry : gcDataMap.entrySet()) {
            String tagGCAdjustedName = entry.getKey();
            List<Integer> gcData = entry.getValue();
            if (gcData == null || gcData.isEmpty()) continue;

            Tab gcTab = new Tab(tagGCAdjustedName);
            gcTab.setClosable(false);
            gcTab.getProperties().put("dynamicGCTab", true);  // 标记为动态 GC Tab

            StackPane pane = GC_Adjusted_Processing.drawGCAdjusted(gcData);
            ScrollPane scrollPane = new ScrollPane(pane);
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);
            scrollPane.setPannable(true);
            gcTab.setContent(scrollPane);

            tabPane.getTabs().add(gcTab);
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
        Dialog<List<Tab>> dialog = new Dialog<>();
        dialog.setTitle("Save Pictures");
        dialog.setHeaderText("Select tab pages to export as PNG");

        ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, cancelButtonType);

        Label instructionLabel = new Label("Please select the tabs you want to save:");

        VBox checkBoxContainer = new VBox(8);
        checkBoxContainer.setPadding(new Insets(10));

        List<CheckBox> checkBoxes = new ArrayList<>();
        for (Tab tab : tabPane.getTabs()) {
            if (tab.getContent() != null) {
                CheckBox cb = new CheckBox(tab.getText());
                cb.setUserData(tab);
                checkBoxes.add(cb);
                checkBoxContainer.getChildren().add(cb);
            }
        }

        // 默认选中第一个
        if (!checkBoxes.isEmpty()) checkBoxes.get(0).setSelected(true);

        ScrollPane scrollPane = new ScrollPane(checkBoxContainer);
        scrollPane.setPrefViewportHeight(300); // 最大高度，超过显示滚动条
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Hyperlink selectAllLink = new Hyperlink("Select All");
        Hyperlink deselectAllLink = new Hyperlink("Deselect All");

        selectAllLink.setOnAction(e -> checkBoxes.forEach(cb -> cb.setSelected(true)));
        deselectAllLink.setOnAction(e -> checkBoxes.forEach(cb -> cb.setSelected(false)));

        HBox linksBox = new HBox(10, selectAllLink, deselectAllLink);
        linksBox.setPadding(new Insets(5, 0, 10, 0));

        VBox contentBox = new VBox(5, instructionLabel, scrollPane, linksBox);
        contentBox.setPrefWidth(380);  // 适当宽度
        dialog.getDialogPane().setContent(contentBox);
        dialog.getDialogPane().setPrefSize(420, 400);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == okButtonType) {
                return checkBoxes.stream()
                        .filter(CheckBox::isSelected)
                        .map(cb -> (Tab) cb.getUserData())
                        .collect(Collectors.toList());
            }
            return null;
        });

        Optional<List<Tab>> result = dialog.showAndWait();
        result.ifPresent(selectedTabs -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Save Directory");
            File dir = chooser.showDialog(tabPane.getScene().getWindow());
            if (dir != null) {
                for (Tab tab : selectedTabs) {
                    Node tabContent = tab.getContent();
                    if (tabContent != null) {
                        WritableImage image = snapshotFullNode(tabContent);
                        String fileName = tab.getText().replaceAll("[^a-zA-Z0-9._-]", "_") + ".png";
                        saveWritableImageToFile(image, new File(dir, fileName));
                    }
                }
                showAlert("Save Complete", "All selected tab pages have been saved successfully.");
            }
        });
    }


    private WritableImage snapshotFullNode(Node node) {
        Bounds bounds = node.getLayoutBounds();
        WritableImage image = new WritableImage((int) bounds.getWidth(), (int) bounds.getHeight());
        return node.snapshot(new SnapshotParameters(), image);
    }

    private void saveWritableImageToFile(WritableImage image, File file) {
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
        } catch (IOException e) {
            showAlert("Save Error", "Failed to save image: " + e.getMessage());
        }
    }


}






