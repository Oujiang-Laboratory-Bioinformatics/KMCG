package com.apply.kmcg;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

import static com.apply.kmcg.MainController.*;

public class Scaffolds_Processing {

    private Tooltip currentTooltip;
    private int displayMode = 0; // 0: 原始值, 1: 比例模式, 2: 修改后比例模式
    private double max_value;
    private double max_ratio;
    private double max_original_value; // 存储原始数据的最大值


    // 添加颜色区间设置
    int colorRangeMin = 20; // 默认最小值
    int colorRangeMax = 60; // 默认最大值

    // 添加设置颜色区间的方法
    public void setColorRange(int min, int max) {
        this.colorRangeMin = min;
        this.colorRangeMax = max;
    }


    public void drawscaffoldOnCanvas(Canvas dataCanvas, MainController.TabData tabData,
                                     List<List<Integer>> kmcgdata, Long totalKmcgSum) {
        List<List<Integer>> scaffolddata = tabData.getScaffolddata();
        int maxValidvalue = tabData.getMaxValidvalue();
        List<List<Integer>> saturationdata = tabData.getSaturationdata();
        int totalScaffoldSum = tabData.getTotalScaffoldSum();
        this.max_ratio = (double) maxValidvalue / totalScaffoldSum;
        // 计算原始数据的最大值
        this.max_original_value = findMaxValue(scaffolddata);

        double scaleX = 1;
        double scaleY = 2;
        double colorBarHeight = 40;
        double colorBarWidth = 900;
        double colorBarY = 720;
        double colorBarX = 95;
        double scaleBarX = colorBarX;
        double scaleBarY = colorBarY + colorBarHeight + 15;
        int scaleCount = 5;

        GraphicsContext gc = dataCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, dataCanvas.getWidth(), dataCanvas.getHeight());

        int rowCount = scaffolddata.size();
        int colCount = scaffolddata.get(0).size();

        List<List<Double>> transformed_data = KMCG_Processing.processData(scaffolddata);

        max_value = 0; // 重置最大值
        for (List<Double> row : transformed_data) {
            for (Double value : row) {
                if (value != null) {
                    max_value = Math.max(max_value, value);
                }
            }
        }

        double totalWidth = colCount * scaleX;
        double totalHeight = rowCount * scaleY;
        double offsetX = (dataCanvas.getWidth() - totalWidth) / 2;
        double offsetY = (dataCanvas.getHeight() - totalHeight) / 2;

        for (int y = 0; y < rowCount; y++) {
            for (int x = 0; x < colCount; x++) {
                int dataValue = scaffolddata.get(y).get(x);
                int denominator = kmcgdata.get(y).get(x);
                int saturationValue = saturationdata.get(y).get(x);

                Color color;

                if (displayMode == 1) {
                    double ratio = denominator != 0 ? (double) dataValue / denominator : 0;
                    ratio = Math.max(0, Math.min(1, ratio));

                    int r = (int) (ratio * 255);
                    int g = (int) (ratio * 255);
                    int b = (int) (ratio * 255);

                    color = Color.rgb(r, g, b);
                } else if (displayMode == 2) {
                    double ratio = denominator != 0 ? (double) saturationValue / totalScaffoldSum : 0;
                    ratio = Math.max(0, Math.min(max_ratio, ratio));
                    double normalizedRatio = ratio / max_ratio;
                    double gamma = 0.5;
                    double adjustedRatio = Math.pow(normalizedRatio, gamma);
                    int grayValue = (int) (adjustedRatio * 255);
                    color = Color.rgb(grayValue, grayValue, grayValue);
                } else {
                    color = ColorUtils.getUD2ColorWithCustomRange(dataValue, max_original_value,
                            colorRangeMin, colorRangeMax);

                }

                double xPos = x * scaleX + offsetX;
                double yPos = (rowCount - y - 1) * scaleY + offsetY;
                gc.setFill(color);
                gc.fillRect(xPos, yPos, scaleX, scaleY);
            }
        }

        drawColorScale(gc, colorBarX, colorBarY, colorBarWidth, colorBarHeight,
                scaleBarX, scaleBarY, scaleCount, dataCanvas.getWidth());
        setupMouseTracking(dataCanvas, scaleX, scaleY, rowCount, colCount, offsetX, offsetY,
                scaffolddata, kmcgdata, saturationdata, totalScaffoldSum);
    }


    private void drawColorScale(GraphicsContext gc, double colorBarX, double colorBarY,
                                double colorBarWidth, double colorBarHeight,
                                double scaleBarX, double scaleBarY, int scaleCount,
                                double canvasWidth) {
        double centeredColorBarX = (canvasWidth - colorBarWidth) / 2;
        double centeredScaleBarX = centeredColorBarX;

        for (int i = 0; i < colorBarWidth; i++) {
            double ratio = (double) i / colorBarWidth;
            Color color;

            if (displayMode == 1) {
                int r = (int) (ratio * 255);
                int g = (int) (ratio * 255);
                int b = (int) (ratio * 255);
                color = Color.rgb(r, g, b);
            } else if (displayMode == 2) {
                double gamma = 0.5;
                double adjustedRatio = Math.pow(ratio, gamma);
                int grayValue = (int) (adjustedRatio * 255);
                color = Color.rgb(grayValue, grayValue, grayValue);
            } else {
                double value = ratio * max_original_value;
                color = ColorUtils.getUD2ColorWithCustomRange(value, max_original_value,
                        colorRangeMin, colorRangeMax);
            }

            gc.setFill(color);
            gc.fillRect(centeredColorBarX + i, colorBarY, 1, colorBarHeight);
        }

        gc.setFill(Color.BLACK);
        for (int i = 0; i <= scaleCount; i++) {
            double x = centeredColorBarX + (i * colorBarWidth / scaleCount);
            double value;

            if (displayMode == 1) {
                value = (double) i / scaleCount;
            } else if (displayMode == 2) {
                value = (double) i / scaleCount * max_ratio;
            } else {
//                value = (double) i / scaleCount * max_original_value; //①常数值

                value = (double) i / scaleCount; // ②比例
            }

            String text;
            if (displayMode == 1) {
                text = String.format("%.2f%%", value * 100);
            } else if (displayMode == 2) {
                text = String.format("%.5f%%", value * 100);
            } else {
//                text = String.format("%.0f", value);          //  ①常数值
                text = String.format("%.0f%%", value * 100);// ②比例
            }

            double textWidth = gc.getFont().getSize() * text.length() * 0.6;
            double textX = x - textWidth / 2;
            gc.fillText(text, textX, scaleBarY);
        }
    }

    private void setupMouseTracking(Canvas dataCanvas, double scaleX, double scaleY,
                                    int rowCount, int colCount, double offsetX, double offsetY,
                                    List<List<Integer>> scaffolddata, List<List<Integer>> kmcgdata,
                                    List<List<Integer>> saturationdata, int totalScaffoldSum) {
        dataCanvas.setOnMouseMoved(event -> {
            double mouseX = event.getX();
            double mouseY = event.getY();

            int xCoord = (int) ((mouseX - offsetX) / scaleX);
            int yCoord = rowCount - 1 - (int) ((mouseY - offsetY) / scaleY);

            if (xCoord < 0 || xCoord >= colCount || yCoord < 0 || yCoord >= rowCount) {
                if (currentTooltip != null) {
                    currentTooltip.hide();
                }
                return;
            }

            int dataValue = scaffolddata.get(yCoord).get(xCoord);
            int denominator = kmcgdata.get(yCoord).get(xCoord);
            int saturationValue = saturationdata.get(yCoord).get(xCoord);

            String displayValue;

            switch (displayMode) {
                case 1:
                    displayValue = denominator != 0 ? String.format("%.1f%%", (double) dataValue / denominator * 100) : "0%";
                    break;
                case 2:
                    displayValue = totalScaffoldSum != 0 ? String.format("%.5f%%", (double) saturationValue / totalScaffoldSum * 100) : "0%";
                    break;
                default:
                    displayValue = String.valueOf(dataValue);
                    break;
            }

            showCoordinates(dataCanvas, mouseX, mouseY, xCoord, yCoord, displayValue);
        });

        dataCanvas.setOnMouseExited(event -> {
            if (currentTooltip != null) {
                currentTooltip.hide();
            }
        });
    }

    private void showCoordinates(Canvas dataCanvas, double mouseX, double mouseY,
                                 int xCoord, int yCoord, String displayValue) {
        if (currentTooltip == null) {
            currentTooltip = new Tooltip();
            Tooltip.install(dataCanvas, currentTooltip);
        }

        currentTooltip.setText(String.format("(%d, %d) | %s", xCoord, yCoord, displayValue));

        double tooltipX = Math.min(mouseX + 320, dataCanvas.getWidth() - 50);
        double tooltipY = Math.min(mouseY + 50, dataCanvas.getHeight() - 30);
        currentTooltip.show(dataCanvas, tooltipX, tooltipY);
    }



    public int toggleDisplayMode(Canvas dataCanvas, MainController.TabData tabData,
                                 List<List<Integer>> kmcgdata, Long totalKmcgSum) {
        displayMode = (displayMode + 1) % 3;
        drawscaffoldOnCanvas(dataCanvas, tabData, kmcgdata, totalKmcgSum);
        return displayMode;
    }

    public static int findMaxValue(List<List<Integer>> scaffolddata) {
        int maxValue = Integer.MIN_VALUE;

        for (int i = 2; i < scaffolddata.size(); i++) {
            List<Integer> row = scaffolddata.get(i);
            for (Integer value : row) {
                if (value != null) {
                    maxValue = Math.max(maxValue, value);
                }
            }
        }

        return maxValue == Integer.MIN_VALUE ? 0 : maxValue;
    }

    public static List<List<Integer>> applySaturation(List<List<Integer>> scaffolddata, int maxValue) {
        List<List<Integer>> saturationdata = new ArrayList<>();

        for (List<Integer> row : scaffolddata) {
            List<Integer> newRow = new ArrayList<>();
            for (Integer value : row) {
                if (value != null && value > maxValue) {
                    newRow.add(maxValue);
                } else {
                    newRow.add(value);
                }
            }
            saturationdata.add(newRow);
        }

        return saturationdata;
    }


}
