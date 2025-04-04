package com.apply.kmcg;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

public class Scaffolds_Processing {

    private Tooltip currentTooltip;
    private int displayMode = 0; // 0: 原始值, 1: 比例模式, 2: 修改后比例模式
    private double max_value;
    private double max_ratio;

    public void drawscaffoldOnCanvas(Canvas dataCanvas, MainController.TabData tabData,
            List<List<Integer>> kmcgdata, int totalKmcgSum) {
        List<List<Integer>> scaffolddata = tabData.getScaffolddata();
        int maxValidvalue = tabData.getMaxValidvalue();
        List<List<Integer>> saturationdata = tabData.getSaturationdata();
        int totalScaffoldSum = tabData.getTotalScaffoldSum();
        this.max_ratio = (double) maxValidvalue / totalScaffoldSum;

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
                    Double value = transformed_data.get(y).get(x);
                    color = ColorUtils.getColorForValue(value, max_value, "UD");
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
                double value = ratio * max_value;
                color = ColorUtils.getColorForValue(value, max_value, "UD");
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
                value = (double) i / scaleCount * max_value;
            }

            String text;
            if (displayMode == 1) {
                text = String.format("%.2f%%", value * 100);
            } else if (displayMode == 2) {
                text = String.format("%.2f%%", value * 100);
            } else {
                text = String.format("%.1f", value);
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
                    displayValue = denominator != 0 ? String.format("%.1f%%", (double) dataValue / denominator * 100)
                            : "0%";
                    break;
                case 2:
                    displayValue = totalScaffoldSum != 0
                            ? String.format("%.1f%%", (double) saturationValue / totalScaffoldSum * 100)
                            : "0%";
                    break;
                default:
                    displayValue = String.valueOf(dataValue);
                    break;
            }

            showCoordinates(dataCanvas, mouseX, mouseY, xCoord, yCoord, displayValue);
        });

        dataCanvas.setOnMouseExited(_ -> {
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

        double tooltipX = Math.min(mouseX + 220, dataCanvas.getWidth() - 50);
        double tooltipY = Math.min(mouseY + 50, dataCanvas.getHeight() - 30);
        currentTooltip.show(dataCanvas, tooltipX, tooltipY);
    }

    public int toggleDisplayMode(Canvas dataCanvas, MainController.TabData tabData,
            List<List<Integer>> kmcgdata, int totalKmcgSum) {
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
