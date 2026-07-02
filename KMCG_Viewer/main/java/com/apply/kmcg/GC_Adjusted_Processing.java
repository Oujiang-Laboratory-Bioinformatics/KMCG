package com.apply.kmcg;

import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.List;

public class GC_Adjusted_Processing {



    public static StackPane drawGCAdjusted(List<Integer> gcData) {

        Canvas canvas = new Canvas();
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Tooltip
        Label tooltip = new Label();
        tooltip.setStyle(
                "-fx-background-color: rgba(20,20,20,0.85);" +
                        "-fx-text-fill: white;" +
                        "-fx-padding: 6;" +
                        "-fx-background-radius: 6;"
        );
        tooltip.setVisible(false);

        Pane inner = new Pane(canvas, tooltip);
        StackPane wrapper = new StackPane(inner);
        wrapper.setAlignment(Pos.CENTER);

        // Canvas 尺寸绑定到 StackPane
        canvas.widthProperty().bind(wrapper.widthProperty());
        canvas.heightProperty().bind(wrapper.heightProperty());

        // Canvas 尺寸变化时重新绘制
        canvas.widthProperty().addListener((obs, oldVal, newVal) -> draw(gc, canvas, gcData));
        canvas.heightProperty().addListener((obs, oldVal, newVal) -> draw(gc, canvas, gcData));

        // Tooltip 鼠标移动
        canvas.setOnMouseMoved(event -> {
            double mx = event.getX();
            double my = event.getY();

            double canvasWidth = canvas.getWidth();
            double canvasHeight = canvas.getHeight();

            double marginLeft = canvasWidth * 0.075;
            double marginRight = canvasWidth * 0.05;
            double marginTop = canvasHeight * 0.1;
            double marginBottom = canvasHeight * 0.13;

            double plotWidth = canvasWidth - marginLeft - marginRight;
            double plotHeight = canvasHeight - marginTop - marginBottom;

            int maxVal = gcData.stream().max(Integer::compare).orElse(1);
            int minVal = gcData.stream().min(Integer::compare).orElse(0);
            if (maxVal == minVal) maxVal += 1;

            int n = gcData.size();
            int xMin = 0;
            int xMax = n - 1;

            double xScale = plotWidth / (xMax - xMin);
            double yScale = plotHeight / (double) (maxVal - minVal);

            double xAxisY = canvasHeight - marginBottom;
            double yAxisX = marginLeft;

            // 找最近点
            double closestDist = Double.MAX_VALUE;
            int closestIndex = -1;
            for (int i = 0; i < gcData.size(); i++) {
                double px = yAxisX + i * xScale;
                double py = xAxisY - (gcData.get(i) - minVal) * yScale;
                double dist = Math.hypot(px - mx, py - my);
                if (dist < closestDist) {
                    closestDist = dist;
                    closestIndex = i;
                }
            }

            if (closestDist < 12) {
                int value = gcData.get(closestIndex);
                tooltip.setText("(" + closestIndex + ", " + value + ")");

                double px = yAxisX + closestIndex * xScale;
                double py = xAxisY - (value - minVal) * yScale;

                // Tooltip 居中在点上方
                double tooltipWidth = tooltip.getWidth();
                tooltip.setLayoutX(px - tooltipWidth / 2);
                tooltip.setLayoutY(py - 40); // 40 px 上方，可根据需要调节
                tooltip.setVisible(true);
            } else {
                tooltip.setVisible(false);
            }
        });

        return wrapper;
    }

    // 绘制方法
    private static void draw(GraphicsContext gc, Canvas canvas, List<Integer> gcData) {
        double canvasWidth = canvas.getWidth();
        double canvasHeight = canvas.getHeight();

        double marginLeft = canvasWidth * 0.075;
        double marginRight = canvasWidth * 0.05;
        double marginTop = canvasHeight * 0.1;
        double marginBottom = canvasHeight * 0.13;

        double plotWidth = canvasWidth - marginLeft - marginRight;
        double plotHeight = canvasHeight - marginTop - marginBottom;

        int maxVal = gcData.stream().max(Integer::compare).orElse(1);
        int minVal = gcData.stream().min(Integer::compare).orElse(0);
        if (maxVal == minVal) maxVal += 1;

        int n = gcData.size();
        int xMin = 0;
        int xMax = n - 1;

        double xScale = plotWidth / (xMax - xMin);
        double yScale = plotHeight / (double) (maxVal - minVal);

        double xAxisY = canvasHeight - marginBottom;
        double yAxisX = marginLeft;

        // 背景
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        // 坐标轴
        gc.setStroke(Color.web("#444"));
        gc.setLineWidth(2);
        gc.strokeLine(yAxisX, xAxisY, canvasWidth - marginRight, xAxisY);
        gc.strokeLine(yAxisX, marginTop, yAxisX, xAxisY);

        // 网格
        gc.setStroke(Color.web("#e6e6e6"));
        gc.setLineWidth(1);
        int yTicks = 5;
        for (int i = 0; i <= yTicks; i++) {
            double y = xAxisY - i * plotHeight / yTicks;
            gc.strokeLine(yAxisX, y, canvasWidth - marginRight, y);
        }
        int xTicks = 10;
        for (int i = 0; i <= xTicks; i++) {
            double x = yAxisX + i * (xMax - xMin) / xTicks * xScale;
            gc.strokeLine(x, marginTop, x, xAxisY);
        }

        // 刻度文字
        gc.setFill(Color.BLACK);
        gc.setFont(Font.font("Arial", 16));
        for (int i = 0; i <= yTicks; i++) {
            double v = minVal + (maxVal - minVal) * i / yTicks;
            double y = xAxisY - i * plotHeight / yTicks;
            String text = String.format("%.0f", v);
            double w = new Text(text).getLayoutBounds().getWidth();
            gc.fillText(text, yAxisX - 10 - w, y + 5); // 可调整 -10 和 +5
        }
        for (int i = 0; i <= xTicks; i++) {
            int pos = xMin + (xMax - xMin) * i / xTicks;
            double x = yAxisX + (pos - xMin) * xScale;
            String text = "" + pos;
            double w = new Text(text).getLayoutBounds().getWidth();
            gc.fillText(text, x - w / 2, xAxisY + 30); // 可调整 +30
        }

        // 折线
        gc.setStroke(Color.web("#0077CC"));
        gc.setLineWidth(2.5);
        for (int i = 0; i < n - 1; i++) {
            double x1 = yAxisX + i * xScale;
            double y1 = xAxisY - (gcData.get(i) - minVal) * yScale;
            double x2 = yAxisX + (i + 1) * xScale;
            double y2 = xAxisY - (gcData.get(i + 1) - minVal) * yScale;
            gc.strokeLine(x1, y1, x2, y2);
        }

        // 小红点
        gc.setFill(Color.RED);
        for (int i = 0; i < n; i++) {
            double px = yAxisX + i * xScale;
            double py = xAxisY - (gcData.get(i) - minVal) * yScale;
            gc.fillOval(px - 4, py - 4, 8, 8);
        }

        // 标题
        gc.setFont(Font.font("Arial", 26));
        String title = "GC-Adjusted Plot";
        double titleWidth = new Text(title).getLayoutBounds().getWidth();
        gc.setFill(Color.web("#222"));
        gc.fillText(title, (canvasWidth - titleWidth) / 2, marginTop - 20);
    }
}
