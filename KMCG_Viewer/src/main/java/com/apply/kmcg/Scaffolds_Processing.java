package com.apply.kmcg;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;

import static com.apply.kmcg.MainController.*;

public class Scaffolds_Processing {

    private Tooltip currentTooltip;
    private int displayMode = 0; // 0: 原始值, 1: 比例模式, 2: 修改后比例模式
    private double max_value;
    private double max_ratio;
    private double max_original_value; // 原始数据的最大值
    private boolean alignWithKmcgLayout = false;
    private boolean hideMarginalBandsForLayout = false;
    /** # tab mode 0: log2 + UD heatmap instead of raw UD2. */
    private boolean mode0UsesLogTransform = false;

    private double lastColorBarX;
    private double lastColorBarY;
    private double lastColorBarWidth;
    private double lastColorBarHeight;
    private Double colorBarIndicatorRatio = null;
    private WritableImage canvasSnapshot;
    private Canvas trackedCanvas;
    private MainController.TabData trackedTabData;
    private List<List<Integer>> trackedKmcgdata;
    private Long trackedTotalKmcgSum;

    // 添加颜色区间设置
    int colorRangeMin = 20; // 默认最小值
    int colorRangeMax = 60; // 默认最大值
    private boolean enableHoverTooltips = true;

    // 添加设置颜色区间的方法
    public void setColorRange(int min, int max) {
        this.colorRangeMin = min;
        this.colorRangeMax = max;
    }

    public void setEnableHoverTooltips(boolean enableHoverTooltips) {
        this.enableHoverTooltips = enableHoverTooltips;
    }

    public void setKmcgLayoutOptions(boolean alignWithKmcgLayout, boolean hideMarginalBands) {
        this.alignWithKmcgLayout = alignWithKmcgLayout;
        this.hideMarginalBandsForLayout = hideMarginalBands;
    }

    public void setMode0UsesLogTransform(boolean mode0UsesLogTransform) {
        this.mode0UsesLogTransform = mode0UsesLogTransform;
    }

    public void drawscaffoldOnCanvas(Canvas dataCanvas, MainController.TabData tabData, List<List<Integer>> kmcgdata, Long totalKmcgSum) {
        drawscaffoldOnCanvas(dataCanvas, tabData, kmcgdata, totalKmcgSum, alignWithKmcgLayout, hideMarginalBandsForLayout);
    }

    public void drawscaffoldOnCanvas(Canvas dataCanvas, MainController.TabData tabData, List<List<Integer>> kmcgdata,
                                     Long totalKmcgSum, boolean alignWithKmcgLayout, boolean hideMarginalBands) {
        trackedCanvas = dataCanvas;
        trackedTabData = tabData;
        trackedKmcgdata = kmcgdata;
        trackedTotalKmcgSum = totalKmcgSum;

        List<List<Integer>> scaffolddata = tabData.getScaffolddata();
        int maxValidvalue = tabData.getMaxValidvalue();
        List<List<Integer>> saturationdata = tabData.getSaturationdata();
        int totalScaffoldSum = tabData.getTotalScaffoldSum();
        this.max_ratio = (double) maxValidvalue / totalScaffoldSum;
        this.max_original_value = findMaxValue(scaffolddata);

        double scaleX = 1;
        double scaleY = 2;
        double colorBarHeight = 40;
        double colorBarWidth = 900;
        double colorBarY = alignWithKmcgLayout ? KMCG_Processing.EXTENSION_COLOR_BAR_Y : 720;
        double colorBarX = 95;
        double scaleBarX = colorBarX;
        double scaleBarY = colorBarY + colorBarHeight + 15;
        int scaleCount = 5;

        GraphicsContext gc = dataCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, dataCanvas.getWidth(), dataCanvas.getHeight());
        int rowCount = scaffolddata.size();
        int colCount = scaffolddata.get(0).size();

        List<List<Double>> transformed_data = KMCG_Processing.processData(scaffolddata);

        max_value = 0;
        for (int y = 0; y < rowCount; y++) {
            boolean marginalRow = y == 0 || y == rowCount - 1;
            for (int x = 0; x < colCount; x++) {
                boolean marginalCol = x == 0 || x == colCount - 1;
                if (hideMarginalBands && (marginalRow || marginalCol)) {
                    continue;
                }
                Double value = transformed_data.get(y).get(x);
                if (value != null) {
                    max_value = Math.max(max_value, value);
                }
            }
        }

        double totalWidth = colCount * scaleX;
        double totalHeight = rowCount * scaleY;
        double offsetX = (dataCanvas.getWidth() - totalWidth) / 2;
        double offsetY = (dataCanvas.getHeight() - totalHeight) / 4;

        for (int y = 0; y < rowCount; y++) {
            boolean marginalRow = y == 0 || y == rowCount - 1;
            for (int x = 0; x < colCount; x++) {
                boolean marginalCol = x == 0 || x == colCount - 1;
                if (hideMarginalBands && (marginalRow || marginalCol)) {
                    continue;
                }

                int dataValue = scaffolddata.get(y).get(x);
                int denominator = kmcgdata.get(y).get(x);
                int saturationValue = saturationdata.get(y).get(x);
                Color color = scaffoldCellColor(dataValue, denominator, saturationValue, totalScaffoldSum);

                if (alignWithKmcgLayout) {
                    KMCG_Processing.KmcgCellRect rect = KMCG_Processing.computeKmcgCellRect(y, x, rowCount, colCount);
                    gc.setFill(color);
                    gc.fillRect(rect.x, rect.y, rect.width, rect.height);
                } else {
                    double xPos = x * scaleX + offsetX;
                    double yPos = (rowCount - y - 1) * scaleY + offsetY;
                    gc.setFill(color);
                    gc.fillRect(xPos, yPos, scaleX, scaleY);
                }
            }
        }

        if (alignWithKmcgLayout) {
            double barX = colorBarX + KMCG_Processing.Marginx_KMCG;
            double barY = colorBarY + KMCG_Processing.Marginy_KMCG;
            double scaleXPos = scaleBarX + KMCG_Processing.Marginx_KMCG;
            double scaleYPos = scaleBarY + KMCG_Processing.Marginy_KMCG;
            lastColorBarX = barX;
            lastColorBarY = barY;
            lastColorBarWidth = colorBarWidth;
            lastColorBarHeight = colorBarHeight;
            drawColorScale(gc, barX, barY, colorBarWidth, colorBarHeight,
                    scaleXPos, scaleYPos, scaleCount, dataCanvas.getWidth(), true);
        } else {
            double effectiveBarX = (dataCanvas.getWidth() - colorBarWidth) / 2;
            lastColorBarX = effectiveBarX;
            lastColorBarY = colorBarY;
            lastColorBarWidth = colorBarWidth;
            lastColorBarHeight = colorBarHeight;
            drawColorScale(gc, colorBarX, colorBarY, colorBarWidth, colorBarHeight,
                    scaleBarX, scaleBarY, scaleCount, dataCanvas.getWidth(), false);
        }

        String displayLabel = tabData.getDisplayLabel();
        if (!displayLabel.isEmpty()) {
            if (alignWithKmcgLayout) {
                KMCG_Processing.drawExtensionBlockLabel(gc, displayLabel, colorBarY);
            } else {
                gc.setFont(Font.font("Verdana", 14));
                gc.setFill(Color.BLACK);
                gc.fillText(displayLabel, offsetX, offsetY - 8);
            }
        }

        captureCanvasSnapshot(dataCanvas);

        if (colorBarIndicatorRatio != null) {
            KMCG_Processing.drawColorBarIndicatorLine(gc, lastColorBarX, lastColorBarY,
                    lastColorBarWidth, lastColorBarHeight, colorBarIndicatorRatio);
        }

        if (enableHoverTooltips) {
            if (alignWithKmcgLayout) {
                setupKmcgLayoutMouseTracking(dataCanvas, rowCount, colCount, hideMarginalBands,
                        scaffolddata, kmcgdata, saturationdata, totalScaffoldSum);
            } else {
                setupMouseTracking(dataCanvas, scaleX, scaleY, rowCount, colCount, offsetX, offsetY,
                        scaffolddata, kmcgdata, saturationdata, totalScaffoldSum);
            }
        } else {
            dataCanvas.setOnMouseMoved(null);
            dataCanvas.setOnMouseExited(null);
            if (currentTooltip != null) {
                currentTooltip.hide();
            }
        }
    }

    private Color scaffoldCellColor(int dataValue, int denominator, int saturationValue, int totalScaffoldSum) {
        if (displayMode == 1) {
            double ratio = denominator != 0 ? (double) dataValue / denominator : 0;
            ratio = Math.max(0, Math.min(1, ratio));
            int r = (int) (ratio * 255);
            int g = (int) (ratio * 255);
            int b = (int) (ratio * 255);
            return Color.rgb(r, g, b);
        } else if (displayMode == 2) {
            double ratio = denominator != 0 ? (double) saturationValue / totalScaffoldSum : 0;
            ratio = Math.max(0, Math.min(max_ratio, ratio));
            double normalizedRatio = ratio / max_ratio;
            double gamma = 0.5;
            double adjustedRatio = Math.pow(normalizedRatio, gamma);
            int grayValue = (int) (adjustedRatio * 255);
            return Color.rgb(grayValue, grayValue, grayValue);
        }
        return mode0CellColor(dataValue);
    }

    private Color mode0CellColor(int dataValue) {
        if (mode0UsesLogTransform) {
            double transformed = dataValue >= 0 ? Math.log(dataValue + 1) / Math.log(2) : 0;
            return ColorUtils.getColorForValue(transformed, max_value, "UD");
        }
        return ColorUtils.getUD2ColorWithCustomRange(dataValue, max_original_value,
                colorRangeMin, colorRangeMax);
    }

    private void drawColorScale(GraphicsContext gc, double colorBarX, double colorBarY,
                                double colorBarWidth, double colorBarHeight,
                                double scaleBarX, double scaleBarY, int scaleCount,
                                double canvasWidth, boolean useKmcgColorBarPosition) {
        if (!useKmcgColorBarPosition) {
            colorBarX = (canvasWidth - colorBarWidth) / 2;
            scaleBarX = colorBarX;
        }

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
            } else if (mode0UsesLogTransform) {
                double value = ratio * max_value;
                color = ColorUtils.getColorForValue(value, max_value, "UD");
            } else {
                double value = ratio * max_original_value;
                color = ColorUtils.getUD2ColorWithCustomRange(value, max_original_value,
                        colorRangeMin, colorRangeMax);
            }

            gc.setFill(color);
            gc.fillRect(colorBarX + i, colorBarY, 1, colorBarHeight);
        }

        String[] tickTexts = new String[scaleCount + 1];
        for (int i = 0; i <= scaleCount; i++) {
            double value;

            if (displayMode == 1) {
                value = (double) i / scaleCount;
            } else if (displayMode == 2) {
                value = (double) i / scaleCount * max_ratio;
            } else if (mode0UsesLogTransform) {
                value = (double) i / scaleCount * max_value;
            } else {
                value = (double) i / scaleCount;
            }

            if (displayMode == 1) {
                tickTexts[i] = String.format("%.2f%%", value * 100);
            } else if (displayMode == 2) {
                tickTexts[i] = String.format("%.4f%%", value * 100);
            } else if (mode0UsesLogTransform) {
                tickTexts[i] = String.format("%.1f", value);
            } else {
                tickTexts[i] = String.format("%.0f%%", value * 100);
            }
        }
        KMCG_Processing.drawExtensionColorBarTickLabels(gc, colorBarX, colorBarWidth, scaleBarY, scaleCount, tickTexts);
    }

    private boolean isOverColorBar(double mouseX, double mouseY) {
        return mouseX >= lastColorBarX && mouseX <= lastColorBarX + lastColorBarWidth
                && mouseY >= lastColorBarY && mouseY <= lastColorBarY + lastColorBarHeight;
    }

    private String formatColorBarScaleLabel(double ratio) {
        ratio = Math.max(0, Math.min(1, ratio));
        if (displayMode == 1) {
            return String.format("%.2f%%", ratio * 100);
        }
        if (displayMode == 2) {
            return String.format("%.4f%%", ratio * max_ratio * 100);
        }
        if (mode0UsesLogTransform) {
            return String.format("%.1f", ratio * max_value);
        }
        return String.format("%.1f%%", ratio * 100);
    }

    private String formatCellColorBarScaleLabel(int dataValue, int denominator,
                                                int saturationValue, int totalScaffoldSum) {
        if (displayMode == 1) {
            double ratio = denominator != 0 ? (double) dataValue / denominator : 0;
            ratio = Math.max(0, Math.min(1, ratio));
            return String.format("%.2f%%", ratio * 100);
        }
        if (displayMode == 2) {
            double satRatio = totalScaffoldSum != 0 ? (double) saturationValue / totalScaffoldSum : 0;
            satRatio = Math.max(0, Math.min(max_ratio, satRatio));
            return String.format("%.4f%%", satRatio * 100);
        }
        if (mode0UsesLogTransform) {
            double transformed = dataValue >= 0 ? Math.log(dataValue + 1) / Math.log(2) : 0;
            return String.format("%.1f", transformed);
        }
        double ratio = max_original_value > 0 ? (double) dataValue / max_original_value : 0;
        ratio = Math.max(0, Math.min(1, ratio));
        return String.format("%.1f%%", ratio * 100);
    }

    private double computeCellColorBarRatio(int dataValue, int denominator,
                                            int saturationValue, int totalScaffoldSum) {
        if (displayMode == 1) {
            if (denominator == 0) {
                return 0.0;
            }
            return Math.max(0, Math.min(1, (double) dataValue / denominator));
        }
        if (displayMode == 2) {
            if (totalScaffoldSum == 0 || max_ratio <= 0) {
                return 0.0;
            }
            double satRatio = (double) saturationValue / totalScaffoldSum;
            satRatio = Math.max(0, Math.min(max_ratio, satRatio));
            return satRatio / max_ratio;
        }
        if (mode0UsesLogTransform) {
            if (max_value <= 0) {
                return 0.0;
            }
            double transformed = dataValue >= 0 ? Math.log(dataValue + 1) / Math.log(2) : 0;
            return Math.max(0, Math.min(1, transformed / max_value));
        }
        if (max_original_value <= 0) {
            return 0.0;
        }
        return Math.max(0, Math.min(1, (double) dataValue / max_original_value));
    }

    private void captureCanvasSnapshot(Canvas canvas) {
        if (canvas == null || canvas.getWidth() <= 0 || canvas.getHeight() <= 0) {
            canvasSnapshot = null;
            return;
        }
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        canvasSnapshot = canvas.snapshot(params, null);
    }

    private void restoreCanvasSnapshot(Canvas canvas) {
        if (canvas == null || canvasSnapshot == null) {
            return;
        }
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.drawImage(canvasSnapshot, 0, 0, canvas.getWidth(), canvas.getHeight());
    }

    private void redrawColorBarIndicatorOnly() {
        if (trackedCanvas == null) {
            return;
        }
        if (canvasSnapshot != null) {
            restoreCanvasSnapshot(trackedCanvas);
        } else if (trackedTabData != null) {
            drawscaffoldOnCanvas(trackedCanvas, trackedTabData, trackedKmcgdata, trackedTotalKmcgSum);
            return;
        }
        if (colorBarIndicatorRatio != null) {
            KMCG_Processing.drawColorBarIndicatorLine(trackedCanvas.getGraphicsContext2D(),
                    lastColorBarX, lastColorBarY, lastColorBarWidth, lastColorBarHeight,
                    colorBarIndicatorRatio);
        }
    }

    private void redrawTrackedCanvas() {
        if (trackedCanvas != null && trackedTabData != null) {
            drawscaffoldOnCanvas(trackedCanvas, trackedTabData, trackedKmcgdata, trackedTotalKmcgSum);
        }
    }

    private void updateColorBarIndicatorFromCell(int dataValue, int denominator,
                                                 int saturationValue, int totalScaffoldSum) {
        double newRatio = computeCellColorBarRatio(dataValue, denominator, saturationValue, totalScaffoldSum);
        if (colorBarIndicatorRatio != null && Math.abs(colorBarIndicatorRatio - newRatio) < 1e-9) {
            return;
        }
        colorBarIndicatorRatio = newRatio;
        redrawColorBarIndicatorOnly();
    }

    private void clearColorBarIndicator() {
        if (colorBarIndicatorRatio == null) {
            return;
        }
        colorBarIndicatorRatio = null;
        restoreCanvasSnapshot(trackedCanvas);
    }

    private boolean tryShowColorBarTooltip(Canvas dataCanvas, double mouseX, double mouseY) {
        if (!isOverColorBar(mouseX, mouseY)) {
            return false;
        }
        double ratio = (mouseX - lastColorBarX) / lastColorBarWidth;
        double newRatio = Math.max(0, Math.min(1, ratio));
        if (colorBarIndicatorRatio == null || Math.abs(colorBarIndicatorRatio - newRatio) >= 1e-9) {
            colorBarIndicatorRatio = newRatio;
            redrawColorBarIndicatorOnly();
        }
        showSimpleCoordinates(dataCanvas, mouseX, mouseY,
                KMCG_Processing.formatColorBarTooltip("Scale", formatColorBarScaleLabel(ratio)));
        return true;
    }

    private void setupKmcgLayoutMouseTracking(Canvas dataCanvas, int rowCount, int colCount,
                                              boolean hideMarginalBands,
                                              List<List<Integer>> scaffolddata, List<List<Integer>> kmcgdata,
                                              List<List<Integer>> saturationdata, int totalScaffoldSum) {
        dataCanvas.setOnMouseMoved(event -> {
            double mouseX = event.getX();
            double mouseY = event.getY();
            if (tryShowColorBarTooltip(dataCanvas, mouseX, mouseY)) {
                return;
            }
            int[] index = KMCG_Processing.findKmcgDataIndexAt(mouseX, mouseY, rowCount, colCount, hideMarginalBands);
            if (index == null) {
                if (currentTooltip != null) {
                    currentTooltip.hide();
                }
                clearColorBarIndicator();
                return;
            }
            int xCoord = index[0];
            int yCoord = index[1];
            int dataValue = scaffolddata.get(yCoord).get(xCoord);
            int denominator = kmcgdata.get(yCoord).get(xCoord);
            int saturationValue = saturationdata.get(yCoord).get(xCoord);
            updateColorBarIndicatorFromCell(dataValue, denominator, saturationValue, totalScaffoldSum);
            showScaffoldCellTooltip(dataCanvas, mouseX, mouseY, xCoord, yCoord, dataValue, denominator,
                    saturationValue, totalScaffoldSum);
        });

        dataCanvas.setOnMouseExited(event -> {
            if (currentTooltip != null) {
                currentTooltip.hide();
            }
            clearColorBarIndicator();
        });
    }

    private void showScaffoldCellTooltip(Canvas dataCanvas, double mouseX, double mouseY,
                                         int xCoord, int yCoord, int dataValue, int denominator,
                                         int saturationValue, int totalScaffoldSum) {
        if (currentTooltip == null) {
            currentTooltip = new Tooltip();
            KMCG_Processing.configureDataTooltip(currentTooltip);
        }
        String[] headers;
        String[] values;
        String coordVal = String.format("(%d, %d)", xCoord, yCoord);
        String scaleVal = formatCellColorBarScaleLabel(dataValue, denominator, saturationValue, totalScaffoldSum);
        switch (displayMode) {
            case 1:
                headers = new String[]{"Coord", "Ratio", "Scale"};
                values = new String[]{
                        coordVal,
                        denominator != 0
                                ? String.format("%.1f%%", (double) dataValue / denominator * 100) : "0%",
                        scaleVal
                };
                break;
            case 2:
                headers = new String[]{"Coord", "Saturation", "Scale"};
                values = new String[]{
                        coordVal,
                        totalScaffoldSum != 0
                                ? String.format("%.4f%%", (double) saturationValue / totalScaffoldSum * 100) : "0%",
                        scaleVal
                };
                break;
            default:
                headers = new String[]{"Coord", "Count", "Scale"};
                double kmcgPct = (totalKmcgSum != 0) ? (dataValue * 100.0 / totalKmcgSum) : 0.0;
                values = new String[]{
                        coordVal,
                        String.format("%d (%.4f%%)", dataValue, kmcgPct),
                        scaleVal
                };
                break;
        }
        String contentKey = KMCG_Processing.alignedTooltipContentKey(headers, values);
        if (!contentKey.equals(currentTooltip.getUserData())) {
            KMCG_Processing.setAlignedTooltipGraphic(currentTooltip, headers, values);
            currentTooltip.setUserData(contentKey);
        }
        KMCG_Processing.positionManualTooltip(currentTooltip, dataCanvas, mouseX, mouseY, contentKey, 220, 52, 20);
    }

    private void showSimpleCoordinates(Canvas dataCanvas, double mouseX, double mouseY, String tooltipText) {
        if (currentTooltip == null) {
            currentTooltip = new Tooltip();
            KMCG_Processing.configureDataTooltip(currentTooltip);
        }
        if (!tooltipText.equals(currentTooltip.getUserData())) {
            currentTooltip.setGraphic(null);
            currentTooltip.setText(tooltipText);
            currentTooltip.setUserData(tooltipText);
        }
        KMCG_Processing.positionManualTooltip(currentTooltip, dataCanvas, mouseX, mouseY, tooltipText, 120, 24, 20);
    }

    private void setupMouseTracking(Canvas dataCanvas, double scaleX, double scaleY,
                                    int rowCount, int colCount, double offsetX, double offsetY,
                                    List<List<Integer>> scaffolddata, List<List<Integer>> kmcgdata,
                                    List<List<Integer>> saturationdata, int totalScaffoldSum) {
        dataCanvas.setOnMouseMoved(event -> {
            double mouseX = event.getX();
            double mouseY = event.getY();

            if (tryShowColorBarTooltip(dataCanvas, mouseX, mouseY)) {
                return;
            }

            int xCoord = (int) ((mouseX - offsetX) / scaleX);
            int yCoord = rowCount - 1 - (int) ((mouseY - offsetY) / scaleY);

            if (xCoord < 0 || xCoord >= colCount || yCoord < 0 || yCoord >= rowCount) {
                if (currentTooltip != null) {
                    currentTooltip.hide();
                }
                clearColorBarIndicator();
                return;
            }

            int dataValue = scaffolddata.get(yCoord).get(xCoord);
            int denominator = kmcgdata.get(yCoord).get(xCoord);
            int saturationValue = saturationdata.get(yCoord).get(xCoord);
            updateColorBarIndicatorFromCell(dataValue, denominator, saturationValue, totalScaffoldSum);
            showScaffoldCellTooltip(dataCanvas, mouseX, mouseY, xCoord, yCoord, dataValue, denominator,
                    saturationValue, totalScaffoldSum);
        });

        dataCanvas.setOnMouseExited(event -> {
            if (currentTooltip != null) {
                currentTooltip.hide();
            }
            clearColorBarIndicator();
        });
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

        return maxValue;
    }

    public static List<List<Integer>> applySaturation(List<List<Integer>> scaffolddata, int maxValidvalue) {
        List<List<Integer>> saturationdata = new ArrayList<>();

        for (List<Integer> row : scaffolddata) {
            List<Integer> saturationRow = new ArrayList<>();
            for (Integer value : row) {
                if (value != null && value > maxValidvalue) {
                    saturationRow.add(maxValidvalue);
                } else {
                    saturationRow.add(value);
                }
            }
            saturationdata.add(saturationRow);
        }

        return saturationdata;
    }
}
