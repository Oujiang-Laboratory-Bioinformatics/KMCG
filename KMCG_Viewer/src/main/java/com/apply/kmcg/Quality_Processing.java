package com.apply.kmcg;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.apply.kmcg.MainController.*;

public class Quality_Processing
{
    //质量图
    public static void drawQualityOnCanvas(Canvas dataCanvas, List<List<Integer>> data) {

        final double CELL_WIDTH = 3.0;
        final double CELL_HEIGHT = 1.0;
        final double MAGNIFICATION = 25.0;
        final int NORMAL_SECTION_END = 512;
        final double TOP_MARGIN = 20.0;

        GraphicsContext gc = dataCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, dataCanvas.getWidth(), dataCanvas.getHeight());

        List<List<Double>> transformedData = KMCG_Processing.processData(data);
        int rowCount = data.size();
        int colCount = data.get(0).size();

        double maxValue = 0.0;
        for (List<Double> row : transformedData) {
            for (Double value : row) {
                if (value != null) {
                    maxValue = Math.max(maxValue, value);
                }
            }
        }

        double totalWidth = colCount * CELL_WIDTH;
        double offsetX = (dataCanvas.getWidth() - totalWidth) / 10;

        double normalHeight = Math.min(rowCount, NORMAL_SECTION_END + 1) * CELL_HEIGHT;
        double stretchedRows = Math.max(0, rowCount - NORMAL_SECTION_END - 1);
        double stretchedHeight = stretchedRows * CELL_HEIGHT * MAGNIFICATION;
        double totalHeight = normalHeight + stretchedHeight;
        double offsetY = TOP_MARGIN;

        gc.setStroke(Color.BLACK);
        gc.setLineWidth(1);
        gc.strokeLine(offsetX, offsetY + totalHeight, offsetX + totalWidth, offsetY + totalHeight);
        gc.strokeLine(offsetX, offsetY, offsetX, offsetY + totalHeight);

        for (int y = 0; y < rowCount; y++) {
            double yPos, rowHeight;
            if (y <= NORMAL_SECTION_END) {
                int normalIndex = NORMAL_SECTION_END - y;
                yPos = offsetY + totalHeight - normalHeight + normalIndex * CELL_HEIGHT;
                rowHeight = CELL_HEIGHT;
            } else {
                int stretchIndex = y - NORMAL_SECTION_END - 1;
                yPos = offsetY + totalHeight - normalHeight - (stretchIndex + 1) * CELL_HEIGHT * MAGNIFICATION;
                rowHeight = CELL_HEIGHT * MAGNIFICATION;
            }

            for (int x = 0; x < colCount; x++) {
                Double value = transformedData.get(y).get(x);
                if (value != null) {
                    Color color = ColorUtils.getColorForValue(value, maxValue, "UD");
                    double xPos = offsetX + x * CELL_WIDTH;
                    gc.setFill(color);
                    gc.fillRect(xPos, yPos, CELL_WIDTH, rowHeight);
                }
            }
        }

        gc.setFont(Font.font("Verdana", 10));
        for (int x = 0; x <= colCount; x += 100) {
            double xPos = offsetX + x * CELL_WIDTH;
            gc.strokeLine(xPos, offsetY + totalHeight, xPos, offsetY + totalHeight + 10);
            gc.fillText(Integer.toString(x), xPos, offsetY + totalHeight + 20);
        }
        if (colCount % 100 != 0) {
            double xPos = offsetX + colCount * CELL_WIDTH;
            gc.strokeLine(xPos, offsetY + totalHeight, xPos, offsetY + totalHeight + 10);
            gc.fillText(Integer.toString(colCount), xPos, offsetY + totalHeight + 20);
        }

        gc.setFont(Font.font("Verdana", 10));
        for (int y = 0; y < rowCount; y++) {
            double yPos;
            if (y <= NORMAL_SECTION_END) {
                int normalIndex = NORMAL_SECTION_END - y;
                yPos = offsetY + totalHeight - normalHeight + normalIndex * CELL_HEIGHT;

                // ✅ 修改点：将 y==512 也改为显示 2^9
                if (y == NORMAL_SECTION_END) {
                    gc.strokeLine(offsetX - 10, yPos, offsetX, yPos);
                    gc.fillText("2", offsetX - 27, yPos + 4);
                    gc.setFont(Font.font("Verdana", 7));
                    gc.fillText("9", offsetX - 20, yPos - 5);
                    gc.setFont(Font.font("Verdana", 10));
                } else if (y % 100 == 0) {
                    gc.strokeLine(offsetX - 10, yPos, offsetX, yPos);
//                    gc.fillText(Integer.toString(y), offsetX - 30, yPos + 4);
                    String label = Integer.toString(y);
                    Text textNode = new Text(label);
                    textNode.setFont(Font.font("Verdana", 10));
                    double textWidth = textNode.getLayoutBounds().getWidth();
                    gc.fillText(label, offsetX - 10 - textWidth, yPos + 4);

                }
            } else {
                int stretchIndex = y - NORMAL_SECTION_END - 1;
                yPos = offsetY + totalHeight - normalHeight - (stretchIndex + 1) * CELL_HEIGHT * MAGNIFICATION;
                gc.strokeLine(offsetX - 10, yPos, offsetX, yPos);
                int exponent = 9 + (y - NORMAL_SECTION_END);
                gc.fillText("2", offsetX - 27, yPos + 4);
                gc.setFont(Font.font("Verdana", 7));
                gc.fillText(String.valueOf(exponent), offsetX - 20, yPos - 5);
                gc.setFont(Font.font("Verdana", 10));
            }
        }

        if (rowCount > NORMAL_SECTION_END) {
            double y512Pos = offsetY + totalHeight - normalHeight;
            gc.setStroke(Color.RED);
            gc.setLineWidth(2);
            double extraLength = totalWidth * 0.02;
            gc.strokeLine(offsetX - extraLength, y512Pos, offsetX + totalWidth + extraLength, y512Pos);
        }

        if (rowCount > 0) {
            double lastYPos;
            if (rowCount - 1 <= NORMAL_SECTION_END) {
                int normalIndex = NORMAL_SECTION_END - (rowCount - 1);
                lastYPos = offsetY + totalHeight - normalHeight + normalIndex * CELL_HEIGHT;
            } else {
                int stretchIndex = rowCount - NORMAL_SECTION_END - 2;
                lastYPos = offsetY + totalHeight - normalHeight - (stretchIndex + 1) * CELL_HEIGHT * MAGNIFICATION;
            }

            gc.setStroke(Color.RED);
            gc.setLineWidth(2);
            double extraLength = totalWidth * 0.02;
            gc.strokeLine(offsetX - extraLength, lastYPos, offsetX + totalWidth + extraLength, lastYPos);
        }
    }



    // 绘制折线图
    public static void drawQualityLineCanvas(Canvas dataCanvas) {
        List<double[]> intersectionPoints = new ArrayList<>();
        // 获取 Canvas 的绘图工具
        GraphicsContext gc = dataCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, dataCanvas.getWidth(), dataCanvas.getHeight()); // 清空画布

        // 使用实际画布大小
        double canvasWidth = dataCanvas.getWidth();
        double canvasHeight = dataCanvas.getHeight();

        // 起点和步长
        double offsetX = 0;
        double xStart = 50 + offsetX;
        double yStart = canvasHeight - 50;

        int dataSize = brokenlineData.get(0).size();
        double xStep = (canvasWidth - 100) / (dataSize - 1);

        // 设置Y轴最大值
        int yMax = 100;  // Y轴最大值，假设是 100（你可以根据实际数据修改）

        // 设置颜色和字体
        gc.setStroke(Color.BLACK);  // 默认线条颜色为黑色
        gc.setLineWidth(2);  // 设置折线宽度
        gc.setFill(Color.DARKSLATEGRAY);  // 设置文字颜色

        // 设置字体
        gc.setFont(new Font("Verdana", 12));

        // 画X轴
        gc.strokeLine(xStart, yStart, canvasWidth - 50 + 500, yStart);  // X轴的起始点也增加500

        // 画Y轴
        gc.strokeLine(xStart, yStart, xStart, 50);  // Y轴的起始点也增加500

        // 绘制Y轴刻度线和标注，设置字体并绘制 Y 轴刻度（右对齐）
        Font font = new Font("Verdana", 12);  // <-- 添加这一句
        gc.setFont(font);

        for (int i = 0; i <= 10; i++) {
            double yPos = yStart - (i * (canvasHeight - 100) / 10);
            gc.strokeLine(xStart - 5, yPos, xStart + 5, yPos);

            String label = String.valueOf(i * 10);

            // 使用 Text 来测量宽度
            Text textNode = new Text(label);
            textNode.setFont(font);  // 用同样字体设置
            double textWidth = textNode.getLayoutBounds().getWidth();

            gc.fillText(label, xStart - 10 - textWidth, yPos + 4);
        }

        gc.fillText("(%)", xStart - 10, 30);  // 40 是 Y 轴顶部的偏移量，可以根据需要调整
        // 绘制折线
        gc.setStroke(Color.CORNFLOWERBLUE);  // 设置折线的颜色为蓝色
        gc.setLineWidth(2);  // 设置更粗的线条

        // 获取 brokenlineData 中的第一个子列表（表示 Y 轴数据）
        List<Double> dataPoints = brokenlineData.get(0);
        boolean firstIntersectionFound = false;
        // 画折线
        for (int i = 1; i < dataPoints.size(); i++) {  // 从 i = 1 开始
            double x1 = xStart + (i - 1) * xStep;  // 上一个点的 X 坐标
            double y1 = yStart - (dataPoints.get(i - 1) * (canvasHeight - 100) / yMax);  // 上一个点的 Y 坐标

            double x2 = xStart + i * xStep;  // 当前点的 X 坐标
            double y2 = yStart - (dataPoints.get(i) * (canvasHeight - 100) / yMax);  // 当前点的 Y 坐标

            // 画折线
            gc.strokeLine(x1, y1, x2, y2);
            // 检查是否跨过 y = 50
            double yTarget = yStart - (50 * (canvasHeight - 100) / yMax); // 计算 y=50 对应的 y 坐标
            if (!firstIntersectionFound && ((y1 < yTarget && y2 > yTarget) || (y1 > yTarget && y2 < yTarget))) {
                double intersectX = x1 + (x2 - x1) * (yTarget - y1) / (y2 - y1);
                double intersectY = yTarget;
                intersectionPoints.add(new double[]{intersectX, intersectY, i});  // 记录交点及索引
                firstIntersectionFound = true;  // 只记录第一个交点
            }
        }
        gc.setFont(new Font("Verdana", 16));
        // 画红色空心圆
        if (!intersectionPoints.isEmpty()) {
            double[] firstPoint = intersectionPoints.get(0);
            double intersectX = firstPoint[0];
            double intersectY = firstPoint[1];
            int index = (int) firstPoint[2];

            gc.setStroke(Color.RED);
            gc.setLineWidth(2);
            gc.strokeOval(intersectX - 4, intersectY - 4, 8, 8);

            // 添加垂直虚线到x轴
            gc.setStroke(Color.RED);
            gc.setLineDashes(5, 5);  // 设置虚线样式
            gc.strokeLine(intersectX, intersectY, intersectX, yStart);  // 画垂直虚线
            gc.setLineDashes(0);  // 恢复实线
            // 在x轴上标注x值
            gc.setFill(Color.RED);
            gc.fillText(String.valueOf(index), intersectX - 10, yStart + 35);  // 在x轴下方标注x值
        }
        // 在X轴上标注每1个数据点和每50个数据点的X值
        gc.setFont(new Font("Verdana", 12));
        gc.setFill(Color.BLACK);
        for (int i = 0; i < dataPoints.size(); i++) {  // 从 i = 0 开始
            if (i == 0 || i % 50 == 0) {  // 每个数据点和每50个数据点显示一次
                gc.setStroke(Color.BLACK);
                double x = xStart + i * xStep;
                gc.fillText(String.valueOf(i), x, yStart + 20);  // 标注X值（从0开始）
                gc.strokeLine(x, yStart, x, yStart - 10);
            }
        }

        if (!quality_indication.isEmpty() && !intersectionPoints.isEmpty()) {
            String qualityIndication = quality_indication.get(0);
            int index = (int) intersectionPoints.get(0)[2];
            String m50Text = "M50=" + index;

            gc.setFont(new Font("Verdana", 16));
            gc.setFill(Color.BLACK);

            double charWidth = 7.5;

            // 将 qualityIndication 按照 "\t" 分割为多行
            String[] lines = qualityIndication.split("\t");

            // 计算最长一行文字的宽度
            double maxLineLength = m50Text.length();  // 初始化为 M50 的长度
            for (String line : lines) {
                if (line.length() > maxLineLength) {
                    maxLineLength = line.length();
                }
            }
            double maxTextWidth = maxLineLength * charWidth;
            double paddingRight = 10;
            double x_text = canvasWidth - maxTextWidth - paddingRight;
            double y_text = 30;

            // 分行绘制 qualityIndication
            for (int i = 0; i < lines.length; i++) {
                gc.fillText(lines[i], x_text - 250, y_text + 25 * i);
            }

            // 绘制 M50 信息在最后一行之后
            gc.fillText(m50Text, x_text - 250, y_text + 25 * lines.length);  // 继续向下 25 像素
        }

        // 画虚线 y = 50 对应的位置
        double yPositionFor50 = yStart - (50 * (canvasHeight - 100) / yMax);  // 计算 y=50 对应的 y 坐标
        gc.setStroke(Color.GRAY);
        gc.setLineDashes(5, 5);  // 设置虚线样式
        gc.strokeLine(xStart, yPositionFor50, canvasWidth - 50 + 500, yPositionFor50);  // 画虚线
        gc.setLineDashes(0);  // 空数组表示实线
    }


    public static void drawQualityFittingCanvas(Canvas canvas, List<List<Integer>> targetData, int rowIndex) {
        if (targetData == null || targetData.isEmpty()) return;
        if (rowIndex < 0 || rowIndex >= targetData.size()) return;

        List<Integer> rowData = targetData.get(rowIndex);
        drawQualityBarChart(canvas, rowData);
    }


    public static void drawQualityBarChart(Canvas canvas, List<Integer> rowData) {
        if (rowData == null || rowData.isEmpty() || canvas.getWidth() <= 0 || canvas.getHeight() <= 0) {
            return;
        }

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        double canvasWidth = canvas.getWidth();
        double canvasHeight = canvas.getHeight();

        int barCount = rowData.size();
        double maxValue = rowData.stream().filter(Objects::nonNull).mapToInt(v -> v).max().orElse(1);

        // 预留空间
        double marginLeft = 50;
        double marginBottom = 30;
        double marginTop = 20;
        double marginRight = 20;

        double chartWidth = canvasWidth - marginLeft - marginRight;
        double chartHeight = canvasHeight - marginTop - marginBottom;

        double barWidth = chartWidth / barCount;

        // 画坐标轴
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(1.0);

        // Y轴
        gc.strokeLine(marginLeft, marginTop, marginLeft, canvasHeight - marginBottom);
        // X轴
        gc.strokeLine(marginLeft, canvasHeight - marginBottom, canvasWidth - marginRight, canvasHeight - marginBottom);

        // Y轴刻度（5个刻度）
        gc.setFill(Color.BLACK);
        int yTickCount = 5;
        for (int i = 0; i <= yTickCount; i++) {
            double value = maxValue * i / yTickCount;
            double y = canvasHeight - marginBottom - (value / maxValue) * chartHeight;

            gc.strokeLine(marginLeft - 5, y, marginLeft, y); // 刻度线

//            gc.fillText(formatYTickLabel(value), marginLeft - 50, y - 5);
            String label = formatYTickLabel(value);
            Text textNode = new Text(label);
            textNode.setFont(gc.getFont()); // 保持一致字体
            double textWidth = textNode.getLayoutBounds().getWidth();

            double textX = marginLeft - 10 - textWidth; // -10 是刻度线左侧空隙
            gc.fillText(label, textX, y - 5);

        }

        // 绘制柱状图
        gc.setFill(Color.LIGHTBLUE);
        for (int i = 0; i < barCount; i++) {
            Integer value = rowData.get(i);
            if (value == null) continue;

            double scaledHeight = (value / maxValue) * chartHeight;
            double x = marginLeft + i * barWidth;
            double y = canvasHeight - marginBottom - scaledHeight;

            gc.fillRect(x, y, barWidth * 0.8, scaledHeight); // 柱子带空隙
        }

        // X轴刻度（每隔n个显示）
        int xTickInterval = Math.max(1, barCount / 10); // 最多显示10个刻度
        gc.setFill(Color.BLACK);
        for (int i = 0; i < barCount; i += xTickInterval) {
            double x = marginLeft + i * barWidth + barWidth * 0.4; // 刻度对准柱子中心
            gc.strokeLine(x, canvasHeight - marginBottom, x, canvasHeight - marginBottom + 5);
            gc.fillText(String.valueOf(i), x - 5, canvasHeight - marginBottom + 15);


        }
    }

    // 格式化右下图的Y轴刻度标签
    private static String formatYTickLabel(double value) {
        if (value < 10000) {
            return String.format("%.0f", value);
        } else if (value < 10000000) {
            // 5-7位数：转换为K单位，保持最多4位数字
            double kValue = value / 1000;
            if (kValue < 10) {
                return String.format("%.2fK", kValue);
            } else if (kValue < 100) {
                return String.format("%.2fK", kValue);
            } else {
                return String.format("%.0fK", kValue);
            }
        } else {
            // 8-10位数：转换为M单位，保持最多4位数字
            double mValue = value / 1000000;
            if (mValue < 10) {
                return String.format("%.2fM", mValue);
            } else if (mValue < 100) {
                return String.format("%.2fM", mValue);
            } else {

                return String.format("%.0fM", mValue);
            }
        }
    }


    // 检查一个数字是否是2的幂次方
    private static boolean isPowerOfTwo(int n) {
        return (n > 0) && ((n & (n - 1)) == 0);
    }



}
