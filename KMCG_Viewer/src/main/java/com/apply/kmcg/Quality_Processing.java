package com.apply.kmcg;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.List;

import static com.apply.kmcg.KMCG_Processing.clearCanvas;
import static com.apply.kmcg.MainController.*;

public class Quality_Processing {
    // 质量图
    public static void drawQualityOnCanvas(Canvas dataCanvas, List<List<Integer>> data) {
        clearCanvas(dataCanvas);
        // 计算缩放比例
        double scaleX = 3.0; // 每个单元格宽度
        double scaleY = 1;
        double magnification = 25.0; // 拉伸倍数

        // 初始化
        double max_value = 0.0; // 存储最大值
        GraphicsContext gc = dataCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, dataCanvas.getWidth() - totalKmcgSum, dataCanvas.getHeight()); // 清空画布

        int rowCount = data.size();
        int colCount = data.get(0).size();
        List<List<Double>> transformed_data = KMCG_Processing.processData(data); // 存储 log2 变换后的数据

        // 计算最大值
        for (List<Double> row : transformed_data) {
            for (Double value : row) {
                if (value != null) {
                    max_value = Math.max(max_value, value);
                }
            }
        }

        // 计算矩形的总宽度和总高度
        double totalWidth = colCount * scaleX;
        double totalHeight = rowCount * scaleY;

        // 计算居中偏移量
        double offsetX = (dataCanvas.getWidth() - totalWidth) / 10;
        // double offsetY = (dataCanvas.getHeight() - totalHeight) / 2;
        double normalHeight = (513 * scaleY); // 未放大部分
        double stretchedHeight = ((rowCount - 513) * scaleY * magnification); // 拉长部分
        double totalNewHeight = normalHeight + stretchedHeight; // 计算新的总高度
        // double offsetY = (dataCanvas.getHeight() - totalNewHeight)/0.8; // 重新计算居中偏移量
        double offsetY = (dataCanvas.getHeight() - totalNewHeight) / 0.4; // 重新计算居中偏移量
        // 绘制坐标轴
        gc.setStroke(Color.BLACK); // 坐标轴颜色
        gc.setLineWidth(1);

        // 绘制 X 轴和 Y 轴
        gc.strokeLine(offsetX, offsetY + totalHeight, offsetX + totalWidth, offsetY + totalHeight); // X 轴
        gc.strokeLine(offsetX, offsetY, offsetX, offsetY + totalHeight); // Y 轴

        // 绘制矩形并应用偏移量
        for (int y = 0; y < rowCount; y++) {
            double rowScaleY = scaleY;
            double colScaleX = scaleX;
            double yPos;

            // 计算 yPos，并调整 yPos 以确保矩形向上延展
            if (y < 513) {
                // y < 512 时，按照正常比例计算
                yPos = (rowCount - y - 1) * scaleY + offsetY;
            }
            // 对 y >= 513 时，修改 yPos 以确保向上拉伸
            else {
                double baseY = offsetY + (rowCount - 513) * scaleY; // 513 对应的起始位置
                double newY = (y - 513) * magnification; // 计算向上偏移的拉伸量
                yPos = baseY - (newY * scaleY) - (scaleY * magnification);
                rowScaleY *= magnification; // 纵向拉伸

                if (y == rowCount - 1) {
                    gc.setStroke(Color.RED);
                    gc.setFill(Color.RED);
                    gc.setLineWidth(2); // 线宽
                    gc.setFont(Font.font("Verdana", 10)); // 设置大字体

                    gc.strokeLine(offsetX - 10, yPos, offsetX, yPos); // 画刻度线
                }
            }

            // 绘制当前行的矩形
            for (int x = 0; x < colCount; x++) {
                Double value = transformed_data.get(y).get(x);

                if (value != null) {
                    Color color = ColorUtils.getColorForValue(value, max_value, "UD"); // 获取颜色
                    double xPos = x * scaleX + offsetX; // 横坐标，加上偏移量
                    gc.setFill(color);
                    gc.fillRect(xPos, yPos, colScaleX, rowScaleY); // 绘制矩形
                    if (y >= 513) {
                        // 绘制透明灰色矩形（覆盖在原矩形上）
                        // gc.setFill(Color.rgb(169, 169, 169, 0.4)); // 设置透明灰色，50%的透明度
                        gc.setFill(Color.TRANSPARENT); // 设置透明灰色，50%的透明度
                        gc.fillRect(xPos, yPos, colScaleX, rowScaleY); // 绘制透明矩形
                    }

                }
            }

            // 绘制 X 轴刻度（每100个单位显示一次）
            if (y == rowCount - 1) {
                double extraLength = totalWidth * 0.02; // 增加4%的长度
                gc.setStroke(Color.RED);
                gc.setFill(Color.RED);
                gc.setFont(Font.font("Verdana", 10)); // 设置大字体
                gc.strokeLine(offsetX - extraLength, yPos, offsetX + totalWidth + extraLength, yPos); // 画刻度线

            }
        }

        // 绘制 X 轴刻度（每100个单位显示一次）
        for (int x = 0; x < colCount; x++) {
            if (x % 100 == 0) { // 每100个单位显示一次刻度
                double xPos = x * scaleX + offsetX;
                gc.strokeLine(xPos, offsetY + totalHeight, xPos, offsetY + totalHeight + 10); // 画刻度线
                gc.fillText(Integer.toString(x), xPos, offsetY + totalHeight + 20); // 画刻度值
            }
        }

        // 绘制 X 轴最大值刻度
        double maxXPos = colCount * scaleX + offsetX;
        gc.strokeLine(maxXPos, offsetY + totalHeight, maxXPos, offsetY + totalHeight + 10); // 画刻度线
        gc.fillText(Integer.toString(colCount), maxXPos, offsetY + totalHeight + 20); // 画刻度值

        // 绘制 Y 轴刻度（每100个单位显示一次）
        for (int y = 0; y < rowCount; y++) {
            double yPos = (rowCount - y - 1) * scaleY + offsetY;
            double displayYPos = yPos;

            if (y >= 512) {
                double baseY = offsetY + (rowCount - 512) * scaleY; // 512 对应的起始位置
                double newY = (y - 512) * magnification; // 计算向上偏移的拉伸量
                displayYPos = baseY - (newY * scaleY);
            }

            if (y % 100 == 0) { // 每100个单位显示一次刻度
                gc.strokeLine(offsetX - 10, displayYPos, offsetX, displayYPos); // 画刻度线
                gc.fillText(Integer.toString(y), offsetX - 30, displayYPos); // 画刻度值
            }

            // 绘制 2 的幂次方刻度
            if (y >= 512) {
                int exponent = 9 + (y - 512); // 从 2^9 开始，每次 y 增加 1，指数加 1
                gc.setStroke(Color.RED); // 可更改为自定义颜色
                gc.strokeLine(offsetX - 10, displayYPos, offsetX, displayYPos); // 画 2 的幂次方刻度线

                // 显示指数值
                gc.setFont(Font.font("Verdana", 10)); // 设置字体
                gc.fillText("2", offsetX - 27, displayYPos); // 绘制 "2"
                gc.setFont(Font.font("Verdana", 7)); // 设置小字体
                gc.fillText(String.valueOf(exponent), offsetX - 20, displayYPos - 5); // 绘制指数
                gc.setStroke(Color.BLACK); // 恢复默认颜色
            }

            // 绘制 y == 512 时的横线
            if (y == 512) {
                gc.setStroke(Color.RED); // 横线颜色
                gc.setLineWidth(2); // 线宽
                double extraLength = totalWidth * 0.02; // 增加额外长度
                gc.strokeLine(offsetX - extraLength, displayYPos, offsetX + totalWidth + extraLength, displayYPos); // 绘制横线
            }
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
        double xStart = 50 + offsetX; // X轴起始点向右移动500

        double yStart = canvasHeight - 50; // Y轴起始点 (底部)
        int dataSize = brokenlineData.get(0).size(); // 数据点数量
        double xStep = (canvasWidth - 100) / (dataSize - 1); // X轴步长，根据数据点数量来计算

        // 设置Y轴最大值
        int yMax = 100; // Y轴最大值，假设是 100（你可以根据实际数据修改）

        // 设置颜色和字体
        gc.setStroke(Color.BLACK); // 默认线条颜色为黑色
        gc.setLineWidth(2); // 设置折线宽度
        gc.setFill(Color.DARKSLATEGRAY); // 设置文字颜色

        // 设置字体
        gc.setFont(new Font("Arial", 12));

        // 画X轴
        gc.strokeLine(xStart, yStart, canvasWidth - 50 + 500, yStart); // X轴的起始点也增加500

        // 画Y轴
        gc.strokeLine(xStart, yStart, xStart, 50); // Y轴的起始点也增加500

        // 绘制Y轴刻度线和标注
        for (int i = 0; i <= 10; i++) {
            double yPos = yStart - (i * (canvasHeight - 100) / 10); // Y轴位置
            gc.strokeLine(xStart - 5, yPos, xStart + 5, yPos); // 刻度线
            gc.fillText(String.valueOf(i * 10), xStart - 30, yPos); // 标注刻度值 (从 0 到 100)
        }
        gc.fillText("(%)", xStart - 10, 30); // 40 是 Y 轴顶部的偏移量，可以根据需要调整
        // 绘制折线
        gc.setStroke(Color.CORNFLOWERBLUE); // 设置折线的颜色为蓝色
        gc.setLineWidth(2); // 设置更粗的线条

        // 获取 brokenlineData 中的第一个子列表（表示 Y 轴数据）
        List<Double> dataPoints = brokenlineData.get(0);
        boolean firstIntersectionFound = false;
        // 画折线
        for (int i = 1; i < dataPoints.size(); i++) { // 从 i = 1 开始
            double x1 = xStart + (i - 1) * xStep; // 上一个点的 X 坐标
            double y1 = yStart - (dataPoints.get(i - 1) * (canvasHeight - 100) / yMax); // 上一个点的 Y 坐标

            double x2 = xStart + i * xStep; // 当前点的 X 坐标
            double y2 = yStart - (dataPoints.get(i) * (canvasHeight - 100) / yMax); // 当前点的 Y 坐标

            // 画折线
            gc.strokeLine(x1, y1, x2, y2);
            // 检查是否跨过 y = 50
            double yTarget = yStart - (50 * (canvasHeight - 100) / yMax); // 计算 y=50 对应的 y 坐标
            if (!firstIntersectionFound && ((y1 < yTarget && y2 > yTarget) || (y1 > yTarget && y2 < yTarget))) {
                double intersectX = x1 + (x2 - x1) * (yTarget - y1) / (y2 - y1);
                double intersectY = yTarget;
                intersectionPoints.add(new double[] { intersectX, intersectY, i }); // 记录交点及索引
                firstIntersectionFound = true; // 只记录第一个交点
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

            // 标注 "x=xx" 在交点下方
            gc.setFill(Color.RED);
            gc.fillText("x=" + index, intersectX + 10, intersectY - 15);
        }
        // 在X轴上标注每1个数据点和每50个数据点的X值
        gc.setFont(new Font("Arial", 12));
        gc.setFill(Color.BLACK);
        for (int i = 0; i < dataPoints.size(); i++) { // 从 i = 0 开始
            if (i == 0 || i % 50 == 0) { // 每个数据点和每50个数据点显示一次
                gc.setStroke(Color.BLACK);
                double x = xStart + i * xStep;
                gc.fillText(String.valueOf(i), x, yStart + 20); // 标注X值（从0开始）
                gc.strokeLine(x, yStart, x, yStart - 10);

            }
        }

        // 在图的最下方贴上 quality_indication 的值
        String qualityIndication = quality_indication.toString();
        gc.setFont(new Font("Arial", 36)); // 更大的字体显示质量值
        gc.setFill(Color.BLACK); // 设置颜色为绿色
        gc.fillText(qualityIndication, (canvasWidth - gc.getFont().getSize() * qualityIndication.length()) / 2 + 700,
                canvasHeight + 40); // 质量值也向右移动500

        // 画虚线 y = 50 对应的位置
        double yPositionFor50 = yStart - (50 * (canvasHeight - 100) / yMax); // 计算 y=50 对应的 y 坐标
        gc.setStroke(Color.GRAY);
        gc.setLineDashes(5, 5); // 设置虚线样式
        gc.strokeLine(xStart, yPositionFor50, canvasWidth - 50 + 500, yPositionFor50); // 画虚线
        gc.setLineDashes(0); // 空数组表示实线
    }
}
