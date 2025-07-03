package com.apply.kmcg;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.List;

import static com.apply.kmcg.KMCG_Processing.clearCanvas;
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
                gc.fillText(Integer.toString(y), offsetX - 30, yPos + 4);
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

        // 设置画布的宽度和高度
        double canvasWidth = dataCanvas.getWidth() * 0.6;
        double canvasHeight = dataCanvas.getHeight() * 0.8;

        // X轴和Y轴的起始点和步长
        double offsetX = 500;
        double xStart = 50 + offsetX;  // X轴起始点向右移动500

        double yStart = canvasHeight - 50;  // Y轴起始点 (底部)
        int dataSize = brokenlineData.get(0).size();  // 数据点数量
        double xStep = (canvasWidth - 100) / (dataSize - 1);  // X轴步长，根据数据点数量来计算

        // 设置Y轴最大值
        int yMax = 100;  // Y轴最大值，假设是 100（你可以根据实际数据修改）

        // 设置颜色和字体
        gc.setStroke(Color.BLACK);  // 默认线条颜色为黑色
        gc.setLineWidth(2);  // 设置折线宽度
        gc.setFill(Color.DARKSLATEGRAY);  // 设置文字颜色

        // 设置字体
        gc.setFont(new Font("Arial", 12));

        // 画X轴
        gc.strokeLine(xStart, yStart, canvasWidth - 50 + 500, yStart);  // X轴的起始点也增加500

        // 画Y轴
        gc.strokeLine(xStart, yStart, xStart, 50);  // Y轴的起始点也增加500

        // 绘制Y轴刻度线和标注
        for (int i = 0; i <= 10; i++) {
            double yPos = yStart - (i * (canvasHeight - 100) / 10);  // Y轴位置
            gc.strokeLine(xStart - 5, yPos, xStart + 5, yPos);  // 刻度线
            gc.fillText(String.valueOf(i * 10), xStart - 30, yPos);  // 标注刻度值 (从 0 到 100)
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
        gc.setFont(new Font("Arial", 16));
        // **在折线绘制完成后再画红色空心圆圈**
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
        gc.setFont(new Font("Arial", 12));
        gc.setFill(Color.BLACK);
        for (int i = 0; i < dataPoints.size(); i++) {  // 从 i = 0 开始
            if (i == 0 || i % 50 == 0) {  // 每个数据点和每50个数据点显示一次
                gc.setStroke(Color.BLACK);
                double x = xStart + i * xStep;
                gc.fillText(String.valueOf(i), x, yStart + 20);  // 标注X值（从0开始）
                gc.strokeLine(x, yStart, x, yStart - 10);
            }
        }

        // 在图的最下方贴上 quality_indication 的值
        if (!quality_indication.isEmpty()) {
            String qualityIndication = quality_indication.get(0); // 获取格式化后的字符串
            gc.setFont(new Font("Arial", 36));  // 更大的字体显示质量值
            gc.setFill(Color.BLACK);  // 设置颜色为黑色
            gc.fillText(qualityIndication,
                    (canvasWidth* 0.7)  ,
                    canvasHeight+40);
        }

        if (!intersectionPoints.isEmpty()) {
            int index = (int) intersectionPoints.get(0)[2];
            gc.setFont(new Font("Arial", 36));  // 比 qualityIndication 稍小的字体
            gc.fillText("M50=" + index,
                    (canvasWidth* 0.7) ,
                    canvasHeight + 100);  // 在 qualityIndication 下方 40 像素处
        }

        // 画虚线 y = 50 对应的位置
        double yPositionFor50 = yStart - (50 * (canvasHeight - 100) / yMax);  // 计算 y=50 对应的 y 坐标
        gc.setStroke(Color.GRAY);
        gc.setLineDashes(5, 5);  // 设置虚线样式
        gc.strokeLine(xStart, yPositionFor50, canvasWidth - 50 + 500, yPositionFor50);  // 画虚线
        gc.setLineDashes(0);  // 空数组表示实线
    }


    // 检查一个数字是否是2的幂次方
    private static boolean isPowerOfTwo(int n) {
        return (n > 0) && ((n & (n - 1)) == 0);
    }






}
