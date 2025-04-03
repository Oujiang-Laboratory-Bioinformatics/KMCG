package com.apply.kmcg;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import javafx.scene.control.ScrollPane;

import javafx.scene.text.Font;


import static com.apply.kmcg.MainController.*;

public class KMCG_Processing {
    private static Canvas kmerCanvas;

    static double Marginx_KMCG = 100.0;
    static double Marginy_KMCG = 15.0;
    //覆盖块的位置
    public static Rectangle region1 = new Rectangle(0 + Marginx_KMCG, 674 + Marginy_KMCG, 31, 31);  // x, y, width, height
    public static Rectangle region2 = new Rectangle(50 + Marginx_KMCG, 674 + Marginy_KMCG, 1001, 31);
    public static Rectangle region3 = new Rectangle(1072 + Marginx_KMCG, 674 + Marginy_KMCG, 31, 31);
    public static Rectangle region4 = new Rectangle(0 + Marginx_KMCG, 51 + Marginy_KMCG, 31, 601);  // 第一列所有中间行的和

    public static Rectangle region5 = new Rectangle(50 + Marginx_KMCG, 51 + Marginy_KMCG, 1001, 601); //中间画图部分

    public static Rectangle region6 = new Rectangle(1072 + Marginx_KMCG, 51 + Marginy_KMCG, 31, 601);  // 最后一列所有中间行的和
    public static Rectangle region7 = new Rectangle(0 + Marginx_KMCG, 0 + Marginy_KMCG, 31, 31);  // x, y, width, height
    public static Rectangle region8 = new Rectangle(50 + Marginx_KMCG, 0 + Marginy_KMCG, 1001, 31);
    public static Rectangle region9 = new Rectangle(1072 + Marginx_KMCG, 0 + Marginy_KMCG, 31, 31);

    public static final double CANVAS_WIDTH = 1000.0;
    public static final double CANVAS_HEIGHT = 300.0;
    // 用于保存已点击的点
    public static List<double[]> clickedPoints = new ArrayList<>();
    public static List<double[]> quadrilateralPoints = new ArrayList<>();
    public static List<String> resultData = new ArrayList<>();
    public static boolean hasDrawnData = false;

    public static Map<String, List<List<Integer>>> scaffolddataMap = new LinkedHashMap<>();
//    public static Map<String, List<List<Integer>>> scaffolddataMap = new HashMap<>();
    // 构造函数接收 Canvas 实例
    public KMCG_Processing(Canvas kmerCanvas) {
        this.kmerCanvas = kmerCanvas;
    }

    public static List<List<Integer>> readFile(String filepath) {
        boolean hasFourthDataInFirstLine = false; // 新增标志位，用于判断第一行是否有第四个数据
        names.clear();
        lengths.clear();
        kmcgdata.clear();
        coordinateDict.clear();
        pointsInside.clear();
        targetData.clear();
        brokenlineData.clear();
        quality_indication.clear();
        quadrilateralPoints.clear();
        scaffolddataMap.clear();
        clearCanvas(kmerCanvas);

        try (BufferedReader br = new BufferedReader(new FileReader(filepath))) {
            String line;
            int lineNumber = 0;
            String currentName = null;
            List<List<Integer>> currentData = null;

            // 处理第二行
            while ((line = br.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1) {  // 第一行，跳过的实际上是第一行
                    String[] tokens = line.strip().split("\t");
                    if (tokens.length == 3) {
                        Filename = tokens[0];  // 获取第一个数据为文件名（字符串）
                        try {
                            KMCG_canvas_height = Integer.parseInt(tokens[1]);  // 获取第二个数据为高度（整数）
                            KMCG_canvas_width = Integer.parseInt(tokens[2]);  // 获取第三个数据为宽度（整数）
                        } catch (NumberFormatException e) {
                            System.err.println("Error: Invalid number format in line 1. Height and width should be integers.");
                        }

                        // 打印出三个数据
//                        System.out.println("Filename: " + Filename);
//                        System.out.println("KMCG_canvas_height: " + KMCG_canvas_height);
//                        System.out.println("KMCG_canvas_width: " + KMCG_canvas_width);
                    } else if (tokens.length >= 4) {
                        // 如果有第四个数据，设置标志位为true
                        hasFourthDataInFirstLine = true;
                        Filename = tokens[0];
                        try {
                            KMCG_canvas_height = Integer.parseInt(tokens[1]);
                            KMCG_canvas_width = Integer.parseInt(tokens[2]);
                        } catch (NumberFormatException e) {
                            System.err.println("Error: Invalid number format in line 1. Height and width should be integers.");
                        }

                    } else {
                        System.err.println("Warning: Expected at least 3 values in line 1, but got " + tokens.length);
                    }
                }
                else if (lineNumber == 2) {  // 第二行
                    String[] tokens = line.strip().split("\t");
                    for (String token : tokens) {
                        names.add(token);  // 将第二行的值存储到 names
                    }
                } else if (lineNumber == 3) {  // 第三行
                    String[] tokens = line.strip().split("\t");
                    for (String token : tokens) {
                        try {
                            lengths.add(Integer.parseInt(token));  // 转换为整数并存储
                        } catch (NumberFormatException e) {
                            System.err.println("Warning: Invalid number format in line: " + line);
                            lengths.add(null);  // 处理错误格式
                        }
                    }
                } else if (lineNumber >= 4 && lineNumber < 306) {
                    String[] tokens = line.strip().split("\t");
                    List<Integer> intLine = new ArrayList<>();
                    for (String token : tokens) {
                        try {
                            intLine.add(Integer.parseInt(token));  // 转换为整数并存储
                        } catch (NumberFormatException e) {
                            System.err.println("Warning: Invalid number format in line: " + line);
                            intLine.add(null);  // 处理错误格式
                        }
                    }
                    kmcgdata.add(intLine);
                } else if (lineNumber >= 307 && lineNumber < 609) {  // 从第 509 行开始，执行 Python 部分的转换
                    String[] tokens = line.strip().split("\t");
                    // 遍历每个数据点
                    for (int y = 0; y < tokens.length; y++) {
                        String data = tokens[y].strip();
                        if (!data.isEmpty()) {
                            // 使用 (x, y) 作为键，将数据存储到字典中
                            coordinateDict.put("(" + (lineNumber - 307) + ", " + y + ")", data);
                        }
                    }
                }

                //Quality Canvas
                else if (lineNumber >= 609 && lineNumber < 1130) {  // 从第 509 行开始，执行 Python 部分的转换
                    String[] tokens = line.strip().split("\t");
                    List<Integer> intLine = new ArrayList<>();
                    for (String token : tokens) {
                        try {
                            intLine.add(Integer.parseInt(token));
                        } catch (NumberFormatException e) {
                            System.err.println("[WARN] Invalid integer in line " + lineNumber + ": " + token);
                            intLine.add(null); // 保留 null 占位
                        }
                    }
                    targetData.add(intLine);
                }
                else if (lineNumber == 1130) {
                    String[] tokens = line.strip().split("\t");
                    // 读取第521行数据并存入 brokenlineData
                    List<Double> doubleLine = new ArrayList<>();
                    for (String token : tokens) {
                        try {
                            doubleLine.add(Double.parseDouble(token));
                        } catch (NumberFormatException e) {
                            System.err.println("[WARN] Invalid double in line " + lineNumber + ": " + token);
                            doubleLine.add(null); // 保留 null 占位
                        }
                    }
                    brokenlineData.add(doubleLine); // 将第521行数据添加到 brokenlineData
                }
                else if (lineNumber == 1131) {
                    String[] tokens = line.strip().split("\t");
                    // 读取第522行数据并存入 quality_indication
                    for (String token : tokens) {
                        if (!token.isEmpty()) {  // 避免存入空字符串（如果 split 产生空值）
                            quality_indication.add(token);
                        }
                    }
                }

                else if (lineNumber >= 1132 && hasFourthDataInFirstLine) {
                    int relativeLine = (lineNumber - 1132) % 304;  // 计算当前行在 303 行块中的相对位置

                    if (relativeLine == 0) {  // 每 304 行的第一行是名称
                        if (currentName != null && currentData != null && !currentData.isEmpty()) {
                            scaffolddataMap.put(currentName, currentData);  // 存储上一个数据块
                        }
                        currentName = line.strip();  // 读取新的名称
                        currentData = new ArrayList<>();
                    } else if (relativeLine >= 1 && relativeLine <= 303) {  // 存储 812-1114 行等数据
                        if (line.strip().isEmpty()) {
                            continue; // 忽略空行
                        }

                        String[] tokens = line.strip().split("\t");
                        List<Integer> rowData = new ArrayList<>();

                        for (String token : tokens) {
                            if (token.matches("-?\\d+")) {  // 确保是整数格式
                                rowData.add(Integer.parseInt(token));
                            } else {
                                System.err.println("Warning: Skipping non-numeric value in data: " + token);
                            }
                        }

                        if (!rowData.isEmpty()) {  // 只有有效数据才添加
                            currentData.add(rowData);
                        }
                    }
                }
            }

            // 存储最后一个数据块
            if (currentName != null && currentData != null && hasFourthDataInFirstLine) {
                scaffolddataMap.put(currentName, currentData);
            }

    }
         catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
        if (hasFourthDataInFirstLine) {
            for (Map.Entry<String, List<List<Integer>>> entry : scaffolddataMap.entrySet()) {
                System.out.println("Scaffold Name: " + entry.getKey());
            }
        }
        return kmcgdata;
    }

    // 将数据绘制到 Canvas 上
    public static void drawKMCGOnCanvas(Canvas dataCanvas, List<List<Integer>> data) {
        hasDrawnData = true;
        // 计算缩放比例
        double scaleX = 1.0;  // 每个单元格宽度
        double scaleY = 2.0;
        // 计算经过 log2 变换后的最大值
        double max_value = 0.0;  // 初始化最大值

        double yPosOffset = 0;  // 用来追踪当前的 yPos 偏移量
        double xPosOffset = 0;
        // 在画布底部绘制颜色条
        double colorBarHeight = 40;  // 颜色条的高度
        double colorBarWidth = 900;  // 颜色条的宽度与画布宽度相同
        double colorBarY = 720;
        double colorBarX = 95;  //横向间距
        // 绘制比例尺的刻度值,设置为5个
        double scaleBarX = colorBarX;
        double scaleBarY = colorBarY + colorBarHeight + 15; // 刻度值的位置，放在比例尺的下方
        int scaleCount = 5;
        GraphicsContext gc = dataCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, dataCanvas.getWidth(), dataCanvas.getHeight());  // 清空画布

        int rowCount = data.size();
        int colCount = data.get(0).size();
        if (rowCount == 0 || colCount == 0) {
            // 如果没有数据，则显示默认图片
            showDefaultImage(dataCanvas);
            return;
        }

        List<List<Double>> transformed_data = processData(data);  // 存储 log2 变换后的数据

        for (List<Double> row : transformed_data) {
            for (Double value : row) {
                if (value != null) {
                    max_value = Math.max(max_value, value);

                }
            }
        }

        for (int y = 0; y < rowCount; y++) {
            double rowScaleY = scaleY;  // 默认的 vertical scale factor
            double colScaleX = scaleX;  // 默认的 horizontal scale factor

            // 对第一行和最后一行高度拉伸并保持间距
            if (y == 0) {
                rowScaleY = scaleY * 15;  // 15倍
                yPosOffset = 72;
            } else if (y == rowCount - 1) {
                rowScaleY = scaleY * 15;  // 15倍
                yPosOffset = 0;
            } else {
                yPosOffset = 50;
            }

            // 绘制当前行的矩形
            for (int x = 0; x < colCount; x++) {
                Double value = transformed_data.get(y).get(x);

                // 对第一列和最后一列进行拉长处理
                if (x == 0 ){
                    colScaleX = scaleX * 30;  // 横向拉长15倍
                    xPosOffset = 0;
                } else if(x == colCount - 1) {
                    colScaleX = scaleX * 30;  // 横向拉长15倍
                    xPosOffset =72;
                }
                else {
                    colScaleX = scaleX;  // 其他列保持默认宽度
                    xPosOffset = 50;
                }

                if (value != null) {
                    Color color = ColorUtils.getColorForValue(value, max_value, "UD");  // 获取颜色
                    double xPos = x * scaleX + xPosOffset;  // 横坐标
                    double yPos = (rowCount - y - 1) * scaleY + yPosOffset;  // 纵坐标
                    gc.setFill(color);
                    gc.fillRect(xPos+Marginx_KMCG, yPos+Marginy_KMCG, colScaleX, rowScaleY);  // 绘制矩形，使用动态的 colScaleX 和 rowScaleY
                }
            }


        }

        double interval = max_value / (scaleCount - 1);  // 计算间隔值

        // 绘制颜色条
        for (int i = 0; i < colorBarWidth; i++) {
            // 计算当前值对应的颜色
            double value = (double) i / colorBarWidth * max_value;
            Color color = ColorUtils.getColorForValue(value, max_value, "UD");

            // 绘制每个小矩形
            gc.setFill(color);
            gc.fillRect(i + colorBarX +Marginx_KMCG, colorBarY +Marginy_KMCG, 1, colorBarHeight);  // 每个矩形的宽度为1，高度为colorBarHeight
        }

        // 绘制刻度值
        for (int i = 0; i < scaleCount; i++) {
            double value = i * interval;
            double xPos = scaleBarX + (i * colorBarWidth / (scaleCount - 1));  // 刻度值的横坐标位置
            gc.setFill(Color.BLACK);  // 刻度文本颜色
            gc.fillText(String.format("%.1f", value), xPos - 10 + Marginx_KMCG, scaleBarY +Marginy_KMCG);  // 绘制文本并调整位置以对齐
        }
        gc.setFill(Color.TRANSPARENT); // 透明
        gc.fillRect(region1.getX(), region1.getY(), region1.getWidth(), region1.getHeight());
        gc.fillRect(region2.getX(), region2.getY(), region2.getWidth(), region2.getHeight());
        gc.fillRect(region3.getX(), region3.getY(), region3.getWidth(), region3.getHeight());
        gc.fillRect(region4.getX(), region4.getY(), region4.getWidth(), region4.getHeight());
        gc.fillRect(region5.getX(), region5.getY(), region5.getWidth(), region5.getHeight());
        gc.fillRect(region6.getX(), region6.getY(), region6.getWidth(), region6.getHeight());
        gc.fillRect(region7.getX(), region7.getY(), region7.getWidth(), region7.getHeight());
        gc.fillRect(region8.getX(), region8.getY(), region8.getWidth(), region8.getHeight());
        gc.fillRect(region9.getX(), region9.getY(), region9.getWidth(), region9.getHeight());
    }

    // 处理数据并返回变换后的数据及最大值
    public static List<List<Double>> processData(List<List<Integer>> data) {
        double max_value = 0.0;  // 初始化最大值
        List<List<Double>> transformed_data = new ArrayList<>();  // 存储 log2 变换后的数据

        // 遍历数据并计算 transformed_data 和 max_value
        for (List<Integer> row : data) {
            List<Double> transformedRow = new ArrayList<>();
            for (Integer value : row) {
                if (value != null && value >= 0) {  // 确保值有效
                    double transformedValue = Math.log(value + 1) / Math.log(2);  // 计算 log2(x+1)
                    transformedRow.add(transformedValue);  // 存储变换后的值
                    max_value = Math.max(max_value, transformedValue);  // 更新最大值
                } else {
                    transformedRow.add(null);  // 对无效值处理
                }
            }
            transformed_data.add(transformedRow);  // 添加一行数据
        }

        return transformed_data;
    }

    // 计算叉积，用来判断两条线段是否相交
    public static boolean isIntersecting(double[] p1, double[] p2, double[] q1, double[] q2) {

        double cross1 = (p2[0] - p1[0]) * (q1[1] - p1[1]) - (p2[1] - p1[1]) * (q1[0] - p1[0]);
        double cross2 = (p2[0] - p1[0]) * (q2[1] - p1[1]) - (p2[1] - p1[1]) * (q2[0] - p1[0]);
        double cross3 = (q2[0] - q1[0]) * (p1[1] - q1[1]) - (q2[1] - q1[1]) * (p1[0] - q1[0]);
        double cross4 = (q2[0] - q1[0]) * (p2[1] - q1[1]) - (q2[1] - q1[1]) * (p2[0] - q1[0]);

        // 判断两条线段是否相交
        return (cross1 * cross2 < 0 && cross3 * cross4 < 0);
    }

    // 判断是否可以形成四边形
    public static boolean canFormQuadrilateral(List<double[]> points) {
        if (points.size() != 4) return false;

        // 通过计算点之间的斜率来判断是否共线（两两点不共线）
        double[] p1 = points.get(0);
        double[] p2 = points.get(1);
        double[] p3 = points.get(2);
        double[] p4 = points.get(3);

        // 判断点是否重合
        if (p1[0] == p2[0] && p1[1] == p2[1] || p1[0] == p3[0] && p1[1] == p3[1] || p1[0] == p4[0] && p1[1] == p4[1] ||
                p2[0] == p3[0] && p2[1] == p3[1] || p2[0] == p4[0] && p2[1] == p4[1] || p3[0] == p4[0] && p3[1] == p4[1]) {
            return false;  // 点不能重合
        }
        // 计算斜率的函数：如果斜率相同则说明点共线
        double slope1 = (p2[1] - p1[1]) / (p2[0] - p1[0]);
        double slope2 = (p3[1] - p2[1]) / (p3[0] - p2[0]);
        double slope3 = (p4[1] - p3[1]) / (p4[0] - p3[0]);
        double slope4 = (p1[1] - p4[1]) / (p1[0] - p4[0]);

        // 如果任意两点之间的斜率相同，说明点共线，不构成四边形
        return !(slope1 == slope2 || slope2 == slope3 || slope3 == slope4 || slope4 == slope1);
    }

    public static boolean isPointInQuadrilateral(double[] point, List<double[]> quadrilateral) {
        double[] p1 = quadrilateral.get(0);
        double[] p2 = quadrilateral.get(1);
        double[] p3 = quadrilateral.get(2);
        double[] p4 = quadrilateral.get(3);

        // 计算向量叉积
        double cross1 = (p2[0] - p1[0]) * (point[1] - p1[1]) - (p2[1] - p1[1]) * (point[0] - p1[0]);
        double cross2 = (p3[0] - p2[0]) * (point[1] - p2[1]) - (p3[1] - p2[1]) * (point[0] - p2[0]);
        double cross3 = (p4[0] - p3[0]) * (point[1] - p3[1]) - (p4[1] - p3[1]) * (point[0] - p3[0]);
        double cross4 = (p1[0] - p4[0]) * (point[1] - p4[1]) - (p1[1] - p4[1]) * (point[0] - p4[0]);

        // 点在四边形内时，所有叉积都应该是正数或负数
        return (cross1 >= 0 && cross2 >= 0 && cross3 >= 0 && cross4 >= 0) ||
                (cross1 <= 0 && cross2 <= 0 && cross3 <= 0 && cross4 <= 0);
    }

    //坐标点弹窗显示
    public static void handleQuadrilateralSelection() {
        if (quadrilateralPoints.size() == 4) {
            // 获取四边形内部的所有点
            pointsInside = getPointsInsideQuadrilateral(quadrilateralPoints);

            // 创建一个字符串来存储所有点的信息
            StringBuilder pointsString = new StringBuilder("Points inside the quadrilateral:\n");
            int count = 0;

            // 遍历所有点，每五个点换行
            for (double[] point : pointsInside) {
                pointsString.append(String.format("(%d, %d) ", (int) point[0], (int) point[1]));
                count++;

                if (count % 7 != 0 && count < pointsInside.size()) {
                    pointsString.append(", ");
                }

                // 每5个点后换行
                if (count % 7 == 0) {
                    pointsString.append("\n");
                }
            }

            // 如果最后一行的点少于5个，不加多余的换行符
            if (count % 7 != 0) {
                pointsString.append("\n");
            }

            // 创建一个 JavaFX Alert 弹窗
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Quadrilateral Points");
            alert.setHeaderText(null);  // 不显示标题栏

            // 将点信息显示在一个 TextArea 中，避免显示过多内容时出现溢出
            TextArea textArea = new TextArea(pointsString.toString());
            textArea.setEditable(false);  // 设置为只读
            textArea.setWrapText(true);   // 自动换行
            textArea.setMaxHeight(Region.USE_PREF_SIZE); // 控制文本区域的最大高度

            // 将 TextArea 放入弹窗
            alert.getDialogPane().setContent(textArea);

            // 显示弹窗
            alert.showAndWait();
        }
    }


    public static void CoordinateTip() {
        if (quadrilateralPoints.size() == 4) {
            // 创建一个 JavaFX Alert 弹窗
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Coordinate generation prompt");
            alert.setHeaderText(null);  // 不显示标题栏
            alert.setContentText("Generated, please check in Kmer Tab");

            // 显示弹窗
            alert.showAndWait();
        }
    }


    // 统计四边形围住的的所有坐标并交换xy
    public static List<double[]> getPointsInsideQuadrilateral(List<double[]> quadrilateral) {
        List<double[]> pointsInside = new ArrayList<>();

        // 遍历 region5 区域的所有坐标点，假设 region5 为 1000x300 的区域
        for (int x = 1; x <= (int)CANVAS_WIDTH; x++) {
            for (int y = 1; y <= (int) CANVAS_HEIGHT; y++) {
                double[] point = new double[]{x, y};
                if (isPointInQuadrilateral(point, quadrilateral)) {
                    pointsInside.add(new double[]{y, x});
                }
            }
        }

        return pointsInside;
    }


    // 四点是否能构成四边形的提示框
    public static void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

//    画四边形
    public static void drawQuadrilateral(Canvas dataCanvas,List<double[]> points) {
        GraphicsContext gc = dataCanvas.getGraphicsContext2D();
        gc.setFill(Color.color(0.0, 0.5, 1.0, 0.7));  // 设置填充颜色为淡蓝色
        gc.setStroke(Color.BLUE);  // 使用蓝色绘制线条
        gc.setLineWidth(2);

        // 先填充四边形的内部
        double[] p1 = points.get(0);
        double[] p2 = points.get(1);
        double[] p3 = points.get(2);
        double[] p4 = points.get(3);

        double scaleX = region5.getWidth() / CANVAS_WIDTH;
        double scaleY = region5.getHeight() / CANVAS_HEIGHT;

        double x1 = region5.getX() + (p1[0] - 1) * scaleX;
        double y1 = region5.getY() + ((int)CANVAS_HEIGHT - p1[1]) * scaleY;

        double x2 = region5.getX() + (p2[0] - 1) * scaleX;
        double y2 = region5.getY() + ((int)CANVAS_HEIGHT - p2[1]) * scaleY;

        double x3 = region5.getX() + (p3[0] - 1) * scaleX;
        double y3 = region5.getY() + ((int)CANVAS_HEIGHT - p3[1]) * scaleY;

        double x4 = region5.getX() + (p4[0] - 1) * scaleX;
        double y4 = region5.getY() + ((int)CANVAS_HEIGHT - p4[1]) * scaleY;

        // 创建一个路径，并填充区域
        gc.beginPath();
        gc.moveTo(x1, y1);
        gc.lineTo(x2, y2);
        gc.lineTo(x3, y3);
        gc.lineTo(x4, y4);
        gc.closePath();
        gc.fill();  // 填充四边形的内部为淡蓝色

        // 连接四个点，形成四边形的边
        gc.strokeLine(x1, y1, x2, y2);
        gc.strokeLine(x2, y2, x3, y3);
        gc.strokeLine(x3, y3, x4, y4);
        gc.strokeLine(x4, y4, x1, y1);
    }

    // 绘制已点击的点
    public static void drawClickedPoints(Canvas dataCanvas) {
        GraphicsContext gc = dataCanvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE); // 使用红色绘制点击点

        for (double[] point : clickedPoints) {
            int x = (int) point[0];
            int y = (int) point[1];

            // 将坐标转换到 region5 区域的坐标系
            double scaleX = region5.getWidth() / CANVAS_WIDTH;
            double scaleY = region5.getHeight() / CANVAS_HEIGHT;

            double xPos = region5.getX() + (x - 1) * scaleX;
            double yPos = region5.getY() + ((int)CANVAS_HEIGHT - y) * scaleY;  // 反向y轴

            gc.fillOval(xPos, yPos, 4, 4);  // 绘制红色圆点，半径为 4
        }
    }

    // 显示 region5 区域的坐标
    public static void showRegion5Coordinates(Canvas dataCanvas, double mouseX, double mouseY) {
        // 将鼠标坐标映射到 region5 坐标系内
        double relativeX = mouseX - region5.getX();  // 鼠标位置相对于 region5
        double relativeY = mouseY - region5.getY();
        // 计算在 1000x300 坐标系中的对应坐标
        double scaleX = CANVAS_WIDTH / region5.getWidth();  // 区域宽
        double scaleY = CANVAS_HEIGHT / region5.getHeight();  // 区域高
        int xCoord = (int) (relativeX * scaleX) + 1;  // 计算横坐标，+1确保坐标从1开始
        // 计算纵坐标，基于左下角原点
        int yCoord = (int) ((region5.getHeight() - relativeY) * scaleY) + 1;  // 改为使用反向的y计算

        // 确保坐标在1到1000范围内
        xCoord = Math.min(Math.max(xCoord, 1), (int)CANVAS_WIDTH);
        yCoord = Math.min(Math.max(yCoord, 1), (int)CANVAS_HEIGHT);

        // 显示坐标信息
        if (currentTooltip == null) {
            currentTooltip = new Tooltip();
            Tooltip.install(dataCanvas, currentTooltip);
        }
        String tooltipText = String.format("(%d, %d)", xCoord, yCoord);
        currentTooltip.setText(tooltipText);

        // 显示 Tooltip，设置其位置为鼠标当前位置
        currentTooltip.show(dataCanvas, mouseX + 230, mouseY + 50);  // 添加偏移量避免与鼠标重叠
    }

    public static void showDataTooltip(Canvas dataCanvas, List<List<Integer>> data, Object regionIdentifier, double mouseX, double mouseY) {
        if (data.size() > 0) {
            Integer value = null;
            String tooltipText = "";
            // 根据不同的区域计算对应的数值
            if (regionIdentifier.equals("region1")) {
                value = data.get(0).get(0);  // 第一行的第一列
                double percentage = (totalKmcgSum != 0) ? (value * 100.0 / totalKmcgSum) : 0.0;

          
                tooltipText = String.format("Count: %d (%.4f%%)", value, percentage);
            } else if (regionIdentifier.equals("region2")) {
                List<Integer> firstRow = data.get(0);
                int sum = 0;
                for (int i = 1; i < firstRow.size() - 1; i++) {
                    sum += firstRow.get(i);
                }
                value = sum;
                double percentage = (totalKmcgSum != 0) ? (value * 100.0 / totalKmcgSum) : 0.0;

                tooltipText = String.format("Count: %d (%.4f%%)", value, percentage);
            } else if (regionIdentifier.equals("region3")) {
                List<Integer> firstRow = data.get(0);
                value = firstRow.get(firstRow.size() - 1);
                double percentage = (totalKmcgSum != 0) ? (value * 100.0 / totalKmcgSum) : 0.0;

                tooltipText = String.format("Count: %d (%.4f%%)", value, percentage);
            } else if (regionIdentifier.equals("region4")) {
                int sum = 0;
                for (int i = 1; i < data.size() - 1; i++) {
                    sum += data.get(i).get(0);
                }
                value = sum;
                double percentage = (totalKmcgSum != 0) ? (value * 100.0 / totalKmcgSum) : 0.0;

                tooltipText = String.format("Count: %d (%.4f%%)", value, percentage);
            } else if (regionIdentifier.equals("region6")) {
                int sum = 0;
                for (int i = 1; i < data.size() - 1; i++) {
                    sum += data.get(i).get(data.get(i).size() - 1);
                }
                value = sum;

                double percentage = (totalKmcgSum != 0) ? (value * 100.0 / totalKmcgSum) : 0.0;

                tooltipText = String.format("Count: %d (%.4f%%)", value, percentage);
            } else if (regionIdentifier.equals("region7")) {
                value = data.get(data.size() - 1).get(0);  // 最后一行的第一列
//                tooltipText = String.format("Count: %d", value);
                double percentage = (totalKmcgSum != 0) ? (value * 100.0 / totalKmcgSum) : 0.0;

                tooltipText = String.format("Count: %d (%.4f%%)", value, percentage);
            } else if (regionIdentifier.equals("region8")) {
                List<Integer> lastRow = data.get(data.size() - 1);
                int sum = 0;
                for (int i = 1; i < lastRow.size() - 1; i++) {
                    sum += lastRow.get(i);
                }
                value = sum;
                double percentage = (totalKmcgSum != 0) ? (value * 100.0 / totalKmcgSum) : 0.0;

                tooltipText = String.format("Count: %d (%.4f%%)", value, percentage);

            } else if (regionIdentifier.equals("region9")) {
                List<Integer> lastRow = data.get(data.size() - 1);
                value = lastRow.get(lastRow.size() - 1);

                double percentage = (totalKmcgSum != 0) ? (value * 100.0 / totalKmcgSum) : 0.0;

                tooltipText = String.format("Count: %d (%.4f%%)", value, percentage);
            }

            // 创建 Tooltip
            if (currentTooltip == null) {
                currentTooltip = new Tooltip();
                Tooltip.install(dataCanvas, currentTooltip);
            }
            currentTooltip.setText(tooltipText);

            // 显示 Tooltip，设置其位置为鼠标当前位置
            currentTooltip.show(dataCanvas, mouseX + 250 , mouseY + 70);  // 加上偏移量，让 Tooltip 不会与鼠标重叠
        }
    }

    // 在设置鼠标移动事件时，确保 Tooltips 的显示和更新
    public static void setupMouseTracking(Canvas dataCanvas, List<List<Integer>> data, boolean isMagnificationEnabled) {

        dataCanvas.setOnMouseMoved(event -> {
            double mouseX = event.getX();
            double mouseY = event.getY();

            // 放大倍数，根据实际需求设置
            double magnificationFactor = 3; // 你可以根据需要调整放大倍数

            // 判断鼠标是否在预定义区域内
            if (region1.contains(mouseX, mouseY)) {
                showDataTooltip(dataCanvas, data, "region1",  mouseX, mouseY);  // 显示第一行的第一列
            } else if (region2.contains(mouseX, mouseY)) {
                showDataTooltip(dataCanvas, data, "region2",  mouseX, mouseY);  // 显示第一行的中间列之和
            } else if (region3.contains(mouseX, mouseY)) {
                showDataTooltip(dataCanvas, data, "region3",  mouseX, mouseY);  // 显示第一行的最后一列
            } else if (region4.contains(mouseX, mouseY)) {
                showDataTooltip(dataCanvas, data, "region4",  mouseX, mouseY);  // 显示第一列所有中间行的和
            } else if (region6.contains(mouseX, mouseY)) {
                showDataTooltip(dataCanvas, data, "region6",  mouseX, mouseY);  // 显示最后一列所有中间行的和
            } else if (region7.contains(mouseX, mouseY)) {
                showDataTooltip(dataCanvas, data, "region7",  mouseX, mouseY);  // 显示最后一行第一列
            } else if (region8.contains(mouseX, mouseY)) {
                showDataTooltip(dataCanvas, data, "region8",  mouseX, mouseY);  // 显示最后一行中间列的和
            } else if (region9.contains(mouseX, mouseY)) {
                showDataTooltip(dataCanvas, data, "region9",  mouseX, mouseY);  // 显示最后一行最后一列
            } else if (region5.contains(mouseX, mouseY)) {
                //后续放大需要↓
                drawKMCGOnCanvas(dataCanvas, data);
                drawClickedPoints(dataCanvas);
                showRegion5Coordinates(dataCanvas, mouseX, mouseY);  // 新增：显示 region5 坐标
                // 调用 showRegion5MagnifiedView 方法，传入放大倍数
                if (isMagnificationEnabled) {
                    showRegion5MagnifiedView(dataCanvas, mouseX, mouseY, data, magnificationFactor);
                }
            } else {

                // 如果鼠标不在任何区域内，隐藏 Tooltip
                if (currentTooltip != null) {
                    currentTooltip.hide();
                    drawKMCGOnCanvas(dataCanvas, data);
                    drawClickedPoints(dataCanvas);
                }
            }
        });


    // 设置鼠标点击事件，记录点击的坐标
        dataCanvas.setOnMouseClicked(event -> {
            double mouseX = event.getX();
            double mouseY = event.getY();

            // 判断是否在 region5 区域内
            if (region5.contains(mouseX, mouseY)) {
                // 点击位置映射到 region5 坐标系内
                double relativeX = mouseX - region5.getX();
                double relativeY = mouseY - region5.getY();
                double scaleX = CANVAS_WIDTH / region5.getWidth();
                double scaleY = CANVAS_HEIGHT / region5.getHeight();
                int xCoord = (int) (relativeX * scaleX) + 1;
                int yCoord = (int) ((region5.getHeight() - relativeY) * scaleY) + 1;

                // 确保坐标在有效范围内
                xCoord = Math.min(Math.max(xCoord, 1), (int)CANVAS_WIDTH);
                yCoord = Math.min(Math.max(yCoord, 1), (int)CANVAS_HEIGHT);

                // 保存点击的点
                clickedPoints.add(new double[]{xCoord, yCoord});
                // 如果已点击超过四个点，则清空之前的点，重新开始
                if (clickedPoints.size() > 4) {
                    clickedPoints.clear();
                    clickedPoints.add(new double[]{xCoord, yCoord});
                }
                clearCanvas(dataCanvas);
                drawKMCGOnCanvas(dataCanvas,data);
                drawClickedPoints(dataCanvas);
                // 检查相交并绘制线段
                for (int i = 0; i < clickedPoints.size() - 1; i++) {
                    double[] p1 = clickedPoints.get(i);
                    double[] p2 = clickedPoints.get(i + 1);
                    for (int j = 0; j < clickedPoints.size() - 1; j++) {
                        if (i != j) {
                            double[] q1 = clickedPoints.get(j);
                            double[] q2 = clickedPoints.get(j + 1);
                            if (isIntersecting(p1, p2, q1, q2)) {
                                // 如果相交，则重绘所有内容
                                showAlert("Warning", "Line segments intersect, please redraw");
                                clearCanvas(dataCanvas);
                                drawKMCGOnCanvas(dataCanvas, data);
                                clickedPoints.clear();
                                break;
                            }
                        }
                    }
                }
                if (clickedPoints.size() == 4) {
                    quadrilateralPoints.clear();
                    quadrilateralPoints.addAll(clickedPoints);
                    // 检查是否能形成有效的四边形
                    if (canFormQuadrilateral(quadrilateralPoints)) {
                        drawQuadrilateral(dataCanvas, quadrilateralPoints);
//                        handleQuadrilateralSelection(); //选取后用于显示坐标的弹窗
                        CoordinateTip();
                        getCoordinatePoints();

                        // 将查询结果拼接成一个字符串，方便后续使用
                        String coordData = String.join(" ", resultData);
                        // 调用数据解析方法
                        List<Map.Entry<Character, Integer>> parsedData = Kmer_Processing.parseData(coordData);

                        Map<String, List<Integer>> storage = Kmer_Processing.initializeStorage(Kmer_Processing.NamesData(), Kmer_Processing.LengthsData());
                        Map<String, List<Integer>> updatedStorage = Kmer_Processing.updateStorage(Kmer_Processing.NamesData(), storage, parsedData);

                        int MaxValue = Kmer_Processing.getMaxValue(Kmer_Processing.NamesData(), storage, parsedData) + 1;
                        int getTotalValue = Kmer_Processing.getTotalValue(Kmer_Processing.NamesData(), storage, parsedData);
                        drawToKmerCanvas(updatedStorage, MaxValue, getTotalValue);

                    } else {
                        // 弹出提示框提示用户无法构成四边形
                        showAlert("Cannot form a quadrilateral", "The selected points cannot form a valid quadrilateral, please reselect the points。");
                        clickedPoints.clear();  // 清空点击的点
                        clearCanvas(dataCanvas);  // 清空画布
                        drawKMCGOnCanvas(dataCanvas, data);
                    }
                }
            }
        });
    }

    public static void showRegion5MagnifiedView(Canvas dataCanvas, double mouseX, double mouseY, List<List<Integer>> data, double magnificationFactor) {
        double max_value = 0.0;
        List<List<Double>> transformedData = processData(data);

        for (List<Double> row : transformedData) {
            for (Double value : row) {
                if (value != null) {
                    max_value = Math.max(max_value, value);  // 更新最大值
                }
            }
        }
        GraphicsContext gc = dataCanvas.getGraphicsContext2D();

        // region5 的位置和大小
        double region5X = region5.getX();
        double region5Y = region5.getY();
        double region5Width = region5.getWidth();
        double region5Height = region5.getHeight();

        // 计算鼠标在 region5 中的相对坐标
        double relativeX = mouseX - region5X;
        double relativeY = mouseY - region5Y;

        // 防止相对坐标超出边界
        if (relativeX < 0 || relativeX > region5Width || relativeY < 0 || relativeY > region5Height) {
            return; // 鼠标不在 region5 区域内时，不做任何操作
        }

        // 计算数据在 region5 中的比例
        double scaleX = data.get(0).size() / region5Width;  // 横向比例
        double scaleY = data.size() / region5Height;  // 纵向比例

        // 获取相应的数据索引
        int dataX = (int) (relativeX * scaleX);
        int dataY = (int) (relativeY * scaleY);

        // 确保索引在有效范围内
        if (dataX < 0 || dataX >= data.get(0).size() || dataY < 0 || dataY >= data.size()) {
            return; // 如果索引超出数据范围，退出
        }

        // 设置放大区域的大小
        int regionSize = 50; // 放大区域的边长（比如：10x10）
        int halfRegionSize = regionSize / 2;

        // 计算放大区域的边界，确保不超出数据范围
        int startX = Math.max(dataX - halfRegionSize, 0);
        int startY = Math.max(dataY - halfRegionSize, 0);
        int endX = Math.min(dataX + halfRegionSize, data.get(0).size() - 1);
        int endY = Math.min(dataY + halfRegionSize, data.size() - 1);

        // 放大区域的实际显示大小
        double magnifiedWidth = (endX - startX + 1) * magnificationFactor;
        double magnifiedHeight = (endY - startY + 1) * magnificationFactor;

        // 根据鼠标位置调整放大区域的显示位置，显示在鼠标右下角
        double offsetX = mouseX + 20; // 右移 20 像素
        double offsetY = mouseY + 20; // 下移 20 像素

        // 确保放大区域不会超出画布的边界
        if (offsetX + magnifiedWidth > dataCanvas.getWidth()) {
            offsetX = mouseX - magnifiedWidth - 20; // 如果超出右边界，显示在左侧
        }
        if (offsetY + magnifiedHeight > dataCanvas.getHeight()) {
            offsetY = mouseY - magnifiedHeight - 20; // 如果超出下边界，显示在上侧
        }

        // 绘制放大区域的数据
        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {

                Double value = transformedData.get(data.size() - 1 - y).get(x);
                if (value != null) {
                    // 获取颜色
                    Color color = ColorUtils.getColorForValue(value, max_value, "UD");

                    // 放大后的位置
                    double xPos = (x - startX) * magnificationFactor + offsetX;
                    double yPos = (y - startY) * magnificationFactor + offsetY;

                    // 在放大区域内绘制图像
                    gc.setFill(color);
                    gc.fillRect(xPos, yPos, magnificationFactor, magnificationFactor);
                }
            }
        }

        // 可选：绘制放大区域的边框，表示放大区域的范围
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2);
        gc.strokeRect(offsetX, offsetY, magnifiedWidth, magnifiedHeight);

        // 计算矩形的中心位置
        double centerX = offsetX + magnifiedWidth / 2;
        double centerY = offsetY + magnifiedHeight / 2;

        // 设置虚线样式
        gc.setLineDashes(5, 5); // 设置虚线的长度和间隔
        gc.setLineWidth(1);
        gc.setStroke(Color.WHITE);
        // 绘制竖直的虚线（田字格中的垂直线）
        gc.strokeLine(centerX, offsetY, centerX, offsetY + magnifiedHeight); // 中间的竖直虚线

        // 绘制水平的虚线（田字格中的水平线）
        gc.strokeLine(offsetX, centerY, offsetX + magnifiedWidth, centerY); // 中间的水平虚线
    }


    //清除画布
    public static void clearCanvas(Canvas dataCanvas) {
        GraphicsContext gc = dataCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, dataCanvas.getWidth(), dataCanvas.getHeight());  // 清空整个画布
    }

    // 显示默认图片
    public static void showDefaultImage(Canvas dataCanvas) {
        // 加载默认图片
        Image defaultImage = new Image(Objects.requireNonNull(KMCG_Processing.class.getResourceAsStream("/image/default_image.png")));
        // 在 Canvas 上绘制默认图片
        GraphicsContext gc = dataCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, dataCanvas.getWidth(), dataCanvas.getHeight());  // 清空画布
        gc.drawImage(defaultImage, 0, 0, dataCanvas.getWidth(), dataCanvas.getHeight());
    }

    public static void getCoordinatePoints(){
        resultData.clear();
        pointsInside = getPointsInsideQuadrilateral(quadrilateralPoints);

        //        打印坐标
        for (double[] point : pointsInside) {
            String coordinateKey = String.format("(%d, %d)", (int) point[0], (int) point[1]);
            // 查询字典中是否存在对应的键
            if (coordinateDict.containsKey(coordinateKey)) {
                // 将对应值加入结果列表
                resultData.add(coordinateDict.get(coordinateKey));

            }
        }
    }


public static void drawToKmerCanvas(Map<String, List<Integer>> updatedStorage, double maxValue, double totalValue) {


    if (kmerCanvas != null) {
        var gc = kmerCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, kmerCanvas.getWidth(), kmerCanvas.getHeight()); // 清除画布

        // 设定比例尺的一些参数
        double colorBarHeight = 40;  // 颜色条的高度
        double colorBarWidth = 900;  // 颜色条的宽度与画布宽度相同
        double colorBarY = 10;  // 将比例尺放到最上方
        double colorBarX = 30;  // 横向间距
        double gap = 150; //比例尺距离左边距离
        double extra_spacing = 180;
        int scaleCount = 5; // 刻度数量
        double interval = maxValue / (scaleCount - 1);  // 计算间隔值

        // 绘制颜色条
        for (int i = 0; i < colorBarWidth; i++) {
            // 计算当前值对应的颜色
            double value = (double) i / colorBarWidth * maxValue;
            Color color = ColorUtils.getColorForValue(value, maxValue, "UD");  // 通过此方法获取颜色
            // 绘制每个小矩形
            gc.setFill(color);
            gc.fillRect(i + colorBarX + gap, colorBarY, 1, colorBarHeight);  // 每个矩形的宽度为1，高度为colorBarHeight
        }

        // 绘制刻度值
        for (int i = 0; i < scaleCount; i++) {
            double value = i * interval;
            double xPos = colorBarX + (i * colorBarWidth / (scaleCount - 1));  // 刻度值的横坐标位置
            gc.setFill(Color.BLACK);  // 刻度文本颜色
            gc.setFont(Font.font("Verdana", 12));
            gc.fillText(String.format("%d", (int) value), xPos + gap -10, colorBarY + colorBarHeight + 15);  // 绘制整数值文本
        }


        // 绘制名称和矩形
        double yPos = colorBarY + colorBarHeight + 40; // 绘制名称的初始Y位置
        double rectWidth = 4; // 默认矩形宽度
        double rectHeight = 25; // 矩形高度

        for (Map.Entry<String, List<Integer>> entry : updatedStorage.entrySet()) {
            String name = entry.getKey();
            List<Integer> values = entry.getValue();
            if (name.length() < 10) {
                // 名称全长显示
            } else if (name.length() > 36) {
                name = name.substring(0, 5) + "···" + name.substring(31, 36)  + "+";  // 过长的名称
            } else {
                name = name.substring(0, 5) + "···" + name.substring(name.length() - 5);  // 中等长度的名称
            }
            // 绘制名称
            gc.setFill(Color.BLACK);
            gc.setFont(Font.font("Verdana", 20));
            gc.fillText(name, colorBarX, yPos + 18);  // 绘制名称

            double xPosRect = colorBarX + extra_spacing; // 矩形的X位置

            // 为每个值创建矩形块
            for (int i = 0; i < values.size(); i++) {
                int value = values.get(i);
                double blockWidth = rectWidth;  // 每个块的宽度

                // 绘制原始矩形
                gc.setFill(ColorUtils.getColorForValue(value, maxValue, "UD"));
                gc.fillRect(xPosRect, yPos, blockWidth, rectHeight);  // 绘制每个矩形
                xPosRect += blockWidth;  // 每个矩形的X坐标递增

                // 生成透明红色矩形覆盖在原矩形上
                gc.setFill(Color.TRANSPARENT);  // 设置透明红色
                gc.fillRect(xPosRect - blockWidth, yPos, blockWidth, rectHeight);  // 绘制透明红色矩形
            }

            // 更新Y坐标以绘制下一个名称和矩形
            yPos += rectHeight + 20;  // 适当增加间距
        }

        // 添加鼠标事件监听 - 显示块区间
        kmerCanvas.setOnMouseMoved(event -> {
            double mouseX = event.getX();
            double mouseY = event.getY();
            boolean isMouseInBlock = false; // 标记鼠标是否在矩形内

            String displayText = "";
            // 清除之前的文本显示
            gc.clearRect(0, 0, kmerCanvas.getWidth(), kmerCanvas.getHeight());
            // 重新绘制画布
            drawToKmerCanvas(updatedStorage, maxValue, totalValue);

            // 遍历每个名称及其对应的矩形
            double currentYPos = colorBarY + colorBarHeight + 40;

            for (Map.Entry<String, List<Integer>> entry : updatedStorage.entrySet()) {
                String name = entry.getKey();
                List<Integer> values = entry.getValue();
                List<String> splitNames = new ArrayList<>();
                boolean useSplitNames = name.length() > 36; // 仅在名称超过36时拆分

                if (useSplitNames) {
                    splitNames = Arrays.asList(name.split(":"));
                }
                double currentXPos = colorBarX + extra_spacing;

                for (int i = 0; i < values.size(); i++) {
                    // 获取每个块的坐标
                    double blockXPos = currentXPos + i * rectWidth;  // 每个块的起始X坐标
                    double blockEndX = blockXPos + rectWidth;  // 每个块的结束X坐标

                    if (mouseX >= blockXPos && mouseX <= blockEndX && mouseY >= currentYPos && mouseY <= currentYPos + rectHeight) {
                        // 如果鼠标在块内，计算并显示块的区间
                        if (useSplitNames && i < splitNames.size()) {
                            displayText = splitNames.get(i);  // 名称超过36，显示拆分后的部分
                        } else {
                            displayText = String.format("%d ~ %d", i, i + 1);  // 仍然显示序号
                        }
                        isMouseInBlock = true; // 标记鼠标在块内
                        break;
                    }
                }

                // 更新Y坐标以进行下一个名称的处理
                currentYPos += rectHeight + 20;

                if (isMouseInBlock) break;  // 跳出循环，防止覆盖
            }

            // 如果鼠标在某个块上方，显示文本
            if (isMouseInBlock) {
                double padding = 8; // 文字内边距
                double textWidth = gc.getFont().getSize() * displayText.length() * 0.6; // 估算文字宽度
                double textHeight = gc.getFont().getSize() + 6; // 文字高度

                // 限制 tooltip 不超出画布范围
                double tooltipX = Math.min(mouseX, kmerCanvas.getWidth() - textWidth - padding * 2);
                double tooltipY = Math.max(mouseY - textHeight - 10, 0); // 避免超出顶部

                // 绘制 tooltip 背景（带圆角）
                gc.setFill(new Color(0, 0, 0, 0.7)); // 半透明黑色
                gc.fillRoundRect(tooltipX, tooltipY, textWidth + padding * 2, textHeight + padding, 10, 10);

                // 绘制文字
                gc.setFill(Color.WHITE);
                gc.fillText(displayText, tooltipX + padding, tooltipY + textHeight);
            }
        });

        // 添加鼠标点击事件 - 显示矩形块的具体值
        kmerCanvas.setOnMouseClicked(event -> {
            double mouseX = event.getX();
            double mouseY = event.getY();
            boolean isMouseInBlock = false; // 标记鼠标是否在矩形内
            int blockValue = -1; // 存储点击块的值
            int rowIndex = -1;  // 存储行索引
            int columnIndex = -1;  // 存储列索引
            String clickedName= "";
            double currentYPos = colorBarY + colorBarHeight + 40;

            int currentRowIndex = 0;  // 初始化行索引
            for (Map.Entry<String, List<Integer>> entry : updatedStorage.entrySet()) {
                String name = entry.getKey();  // 获取当前行的名称
                List<Integer> values = entry.getValue();
                List<String> splitNames = new ArrayList<>();
                boolean useSplitNames = name.length() > 36;

                if (useSplitNames) {
                    splitNames = Arrays.asList(name.split(":"));
                }

                double currentXPos = colorBarX + extra_spacing;

                for (int i = 0; i < values.size(); i++) {
                    // 获取每个块的坐标
                    double blockXPos = currentXPos + i * rectWidth;  // 每个块的起始X坐标
                    double blockEndX = blockXPos + rectWidth;  // 每个块的结束X坐标

                    if (mouseX >= blockXPos && mouseX <= blockEndX && mouseY >= currentYPos && mouseY <= currentYPos + rectHeight) {
                        // 如果鼠标在块内，获取该块的值
                        blockValue = values.get(i);
                        rowIndex  = currentRowIndex; // 获取当前行的索引
                        columnIndex = i;  // 获取当前列的索引
                        isMouseInBlock = true;
                        if (useSplitNames && i < splitNames.size()) {
                            clickedName = splitNames.get(i);
                        } else {
                            clickedName = name;
                        }

                        break;  // 找到后退出循环
                    }
                }

                // 更新Y坐标以进行下一个名称的处理
                currentYPos += rectHeight + 20;
                currentRowIndex++;
            }

            // 如果鼠标点击了某个块，弹出对话框显示该块的值
            if (isMouseInBlock && blockValue != -1) {
                // 显示弹窗
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Detail description");
                alert.setHeaderText(null);

                double percentage = (blockValue / totalValue) * 100;

                // 处理数据
                Kmer_Processing.processBlockIndex(rowIndex, columnIndex);
                Map<String, Integer> result = Kmer_Processing.parseAndCount(rowIndex, columnIndex);

                // 构建内容字符串
                StringBuilder content = new StringBuilder();
                content.append(String.format("Row: %d, Column: %d\nName: %s\nCount: %d (%s)\n\n",
                        rowIndex, columnIndex, clickedName, blockValue, String.format("%.3f%%", percentage)));

                result.forEach((name, count) -> content.append(name).append(": ").append(count).append("\n"));

                // 创建 TextArea 显示内容
                TextArea textArea = new TextArea(content.toString());
                textArea.setEditable(false);
                textArea.setWrapText(true);

                // 创建 ScrollPane 以支持滚动
                ScrollPane scrollPane = new ScrollPane(textArea);
                scrollPane.setFitToWidth(true);
                scrollPane.setFitToHeight(true);
                scrollPane.setPrefSize(400, 300); // 设置适当的大小，防止弹窗过长

                // 将滚动区域设置到弹窗
                alert.getDialogPane().setContent(scrollPane);
                alert.showAndWait();  // 显示弹窗

            }
        });
    }
}



}
