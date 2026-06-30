package com.apply.kmcg;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import javafx.scene.text.Font;
import javafx.scene.text.Text;


import javax.swing.*;

import static com.apply.kmcg.MainController.*;

public class KMCG_Processing {
    private static Canvas kmerCanvas;

    private static final class KmerCanvasLayout {
        double leftPadding;
        double colorBarX;
        double gap;
        double extra_spacing;
        double colorBarY;
        double colorBarHeight;
        double colorBarWidth;
        double rectWidth;
        double rectHeight;
        double lineSpacing;
        int maxBlocksPerLine;
        double nameFontSize;
        double nameAreaWidth;
        double rightMargin;
        double requiredCanvasWidth;
    }

    private static final KmerCanvasLayout kmerLayout = new KmerCanvasLayout();

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
    /** Extension # / % tabs: color bar Y before Marginy (top ≈705, aligns with setting button). */
    public static final double EXTENSION_COLOR_BAR_Y = 690;
    public static final double EXTENSION_BLOCK_LABEL_FONT_SIZE = 16;
    /** Tick labels below extension # / % color bars (font/size shared by both tabs). */
    public static final double EXTENSION_COLOR_BAR_TICK_FONT_SIZE = 12;
    static final double TOOLTIP_FONT_SIZE = 12;
    /** Cross-platform UI sans-serif, slightly bold — Windows/Linux friendly. */
    private static final String TOOLTIP_FONT_STYLE =
            "-fx-font-family: 'Segoe UI', 'Ubuntu', 'Noto Sans', 'Liberation Sans', "
                    + "'Helvetica Neue', Arial, sans-serif; "
                    + "-fx-font-size: " + (int) TOOLTIP_FONT_SIZE + "px; "
                    + "-fx-font-weight: 600;";

    private static final double KMCG_SIDE_WIDTH = 50;
    private static final double KMCG_STRIP_HEIGHT = 31;
    private static final double KMCG_MAIN_X = Marginx_KMCG + 50;
    private static final double KMCG_MAIN_Y = Marginy_KMCG + 51;
    private static final double KMCG_MAIN_W = 1001;
    private static final double KMCG_MAIN_H = 601;

    private static WritableImage kmcgBaseSnapshot;

    static void invalidateKmcgSnapshot() {
        kmcgBaseSnapshot = null;
    }

    static void captureKmcgSnapshot(Canvas canvas) {
        if (canvas == null || canvas.getWidth() <= 0 || canvas.getHeight() <= 0) {
            kmcgBaseSnapshot = null;
            return;
        }
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        kmcgBaseSnapshot = canvas.snapshot(params, null);
    }

    static void restoreKmcgSnapshot(Canvas canvas) {
        if (canvas == null || kmcgBaseSnapshot == null) {
            return;
        }
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.drawImage(kmcgBaseSnapshot, 0, 0, canvas.getWidth(), canvas.getHeight());
    }

    static void refreshKmcgHoverOverlays(Canvas dataCanvas, List<List<Integer>> data,
                                           boolean isPolyploidView, boolean enableQuadrilateralSelection,
                                           boolean isMagnificationEnabled, double mouseX, double mouseY,
                                           double magnificationFactor) {
        if (kmcgBaseSnapshot == null) {
            if (isPolyploidView) {
                drawKMCGOnCanvas(dataCanvas, kmcgdata_polyploid);
            } else {
                drawKMCGOnCanvas(dataCanvas, data);
            }
        } else {
            restoreKmcgSnapshot(dataCanvas);
        }
        drawColorBarIndicatorOverlay(dataCanvas);
        if (enableQuadrilateralSelection) {
            if (quadrilateralPoints.size() == 4) {
                drawQuadrilateral(dataCanvas, quadrilateralPoints);
            }
            drawClickedPoints(dataCanvas);
        }
        if (isMagnificationEnabled && region5.contains(mouseX, mouseY)) {
            showRegion5MagnifiedView(dataCanvas, mouseX, mouseY,
                    isPolyploidView ? kmcgdata_polyploid : data, magnificationFactor);
        }
    }

    static void refreshKmcgBaseView(Canvas dataCanvas, List<List<Integer>> data, boolean isPolyploidView,
                                    boolean enableQuadrilateralSelection) {
        if (kmcgBaseSnapshot == null) {
            if (isPolyploidView) {
                drawKMCGOnCanvas(dataCanvas, kmcgdata_polyploid);
            } else {
                drawKMCGOnCanvas(dataCanvas, data);
            }
        } else {
            restoreKmcgSnapshot(dataCanvas);
        }
        if (enableQuadrilateralSelection) {
            if (quadrilateralPoints.size() == 4) {
                drawQuadrilateral(dataCanvas, quadrilateralPoints);
            }
            drawClickedPoints(dataCanvas);
        }
    }

    /** Marginal / corner hit zones — match drawn region1–region9 rectangles exactly. */
    static String resolveKmcgMarginalHitRegion(double px, double py) {
        if (region7.contains(px, py)) {
            return "region7";
        }
        if (region9.contains(px, py)) {
            return "region9";
        }
        if (region1.contains(px, py)) {
            return "region1";
        }
        if (region3.contains(px, py)) {
            return "region3";
        }
        if (region8.contains(px, py)) {
            return "region8";
        }
        if (region2.contains(px, py)) {
            return "region2";
        }
        if (region4.contains(px, py)) {
            return "region4";
        }
        if (region6.contains(px, py)) {
            return "region6";
        }
        return null;
    }

    static void configureDataTooltip(Tooltip tooltip) {
        tooltip.setStyle(TOOLTIP_FONT_STYLE);
    }

    /** Manual tooltip show — avoids hide/show flicker; skips anchor moves under 2px when content unchanged. */
    static void positionManualTooltip(Tooltip tooltip, Canvas dataCanvas, double mouseX, double mouseY,
                                      String contentKey, double tooltipWidth, double tooltipHeight,
                                      double gapBelowTooltip) {
        Object previousKey = tooltip.getUserData();
        boolean contentChanged = previousKey == null || !contentKey.equals(previousKey);
        if (contentChanged) {
            tooltip.setUserData(contentKey);
        }

        javafx.stage.Window window = dataCanvas.getScene() != null ? dataCanvas.getScene().getWindow() : null;
        double windowScaleX = window != null ? window.getOutputScaleX() : 1.0;
        double windowScaleY = window != null ? window.getOutputScaleY() : 1.0;
        double scale = Math.max(windowScaleX, windowScaleY);

        double scaledWidth = tooltipWidth * scale;
        double scaledHeight = tooltipHeight * scale;
        double scaledOffsetX = -scaledWidth / 2;
        double scaledOffsetY = -scaledHeight - gapBelowTooltip;

        javafx.geometry.Point2D sceneCoords = new javafx.geometry.Point2D(mouseX + scaledOffsetX, mouseY + scaledOffsetY);
        javafx.geometry.Point2D screenCoords = dataCanvas.localToScreen(sceneCoords);

        if (screenCoords != null && window != null) {
            double anchorX = screenCoords.getX();
            double anchorY = screenCoords.getY();
            if (!contentChanged && tooltip.isShowing()
                    && Math.hypot(anchorX - tooltip.getAnchorX(), anchorY - tooltip.getAnchorY()) < 2.0) {
                return;
            }
            if (tooltip.isShowing()) {
                tooltip.setAnchorX(anchorX);
                tooltip.setAnchorY(anchorY);
            } else {
                tooltip.show(window, anchorX, anchorY);
            }
        } else {
            double localX = mouseX + scaledOffsetX;
            double localY = mouseY + scaledOffsetY;
            if (!contentChanged && tooltip.isShowing()
                    && Math.hypot(localX - tooltip.getAnchorX(), localY - tooltip.getAnchorY()) < 2.0) {
                return;
            }
            if (!tooltip.isShowing()) {
                tooltip.show(dataCanvas, localX, localY);
            } else {
                tooltip.setAnchorX(localX);
                tooltip.setAnchorY(localY);
            }
        }
    }

    static void ensureDataTooltip(Canvas dataCanvas) {
        if (currentTooltip == null) {
            currentTooltip = new Tooltip();
            configureDataTooltip(currentTooltip);
        }
    }

    static String formatAlignedTooltip(String[] headers, String[] values) {
        if (headers.length != values.length) {
            throw new IllegalArgumentException("headers/values length mismatch");
        }
        StringBuilder headerLine = new StringBuilder();
        StringBuilder valueLine = new StringBuilder();
        for (int i = 0; i < headers.length; i++) {
            if (i > 0) {
                headerLine.append(" | ");
                valueLine.append(" | ");
            }
            headerLine.append(headers[i]);
            valueLine.append(values[i]);
        }
        return headerLine + "\n" + valueLine;
    }

    static String alignedTooltipContentKey(String[] headers, String[] values) {
        return String.join("\0", headers) + "\n" + String.join("\0", values);
    }

    static void setAlignedTooltipGraphic(Tooltip tooltip, String[] headers, String[] values) {
        if (headers.length != values.length) {
            throw new IllegalArgumentException("headers/values length mismatch");
        }
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setPadding(new Insets(2, 8, 2, 8));
        for (int column = 0; column < headers.length; column++) {
            Label headerLabel = new Label(headers[column]);
            Label valueLabel = new Label(values[column]);
            headerLabel.setStyle(TOOLTIP_FONT_STYLE);
            valueLabel.setStyle(TOOLTIP_FONT_STYLE);
            headerLabel.setMinWidth(Region.USE_PREF_SIZE);
            valueLabel.setMinWidth(Region.USE_PREF_SIZE);
            grid.add(headerLabel, column, 0);
            grid.add(valueLabel, column, 1);
        }
        tooltip.setGraphic(grid);
        tooltip.setText("");
    }

    static void showSimpleTextTooltip(Canvas dataCanvas, double mouseX, double mouseY, String tooltipText) {
        ensureDataTooltip(dataCanvas);
        if (!tooltipText.equals(currentTooltip.getUserData())) {
            currentTooltip.setGraphic(null);
            currentTooltip.setText(tooltipText);
            currentTooltip.setUserData(tooltipText);
        }
        positionManualTooltip(currentTooltip, dataCanvas, mouseX, mouseY, tooltipText, 120, 24, 20);
    }

    static void showAlignedHeatmapTooltip(Canvas dataCanvas, double mouseX, double mouseY,
                                          String[] headers, String[] values) {
        showAlignedHeatmapTooltip(dataCanvas, mouseX, mouseY, headers, values, 220);
    }

    static void showAlignedHeatmapTooltip(Canvas dataCanvas, double mouseX, double mouseY,
                                          String[] headers, String[] values, double tooltipWidth) {
        ensureDataTooltip(dataCanvas);
        String contentKey = alignedTooltipContentKey(headers, values);
        if (!contentKey.equals(currentTooltip.getUserData())) {
            setAlignedTooltipGraphic(currentTooltip, headers, values);
            currentTooltip.setUserData(contentKey);
        }
        positionManualTooltip(currentTooltip, dataCanvas, mouseX, mouseY, contentKey, tooltipWidth, 52, 20);
    }

    static String formatColorBarTooltip(String label, String value) {
        return label + " | " + value;
    }

    static String formatKmcgCellColorBarScaleLabel(int value) {
        double transformed = value >= 0 ? Math.log(value + 1) / Math.log(2) : 0;
        return String.format("%.1f", transformed);
    }

    static String formatKmcgHeatmapTooltip(int xCoord, int yCoord, int value, double percentage) {
        return alignedTooltipContentKey(
                new String[]{"Coord", "Count", "Scale"},
                new String[]{
                        String.format("(%d, %d)", xCoord, yCoord),
                        String.format("%d (%.4f%%)", value, percentage),
                        formatKmcgCellColorBarScaleLabel(value)
                });
    }

    static void showKmcgHeatmapTooltip(Canvas dataCanvas, double mouseX, double mouseY,
                                       int xCoord, int yCoord, int value, double percentage) {
        showAlignedHeatmapTooltip(dataCanvas, mouseX, mouseY,
                new String[]{"Coord", "Count", "Scale"},
                new String[]{
                        String.format("(%d, %d)", xCoord, yCoord),
                        String.format("%d (%.4f%%)", value, percentage),
                        formatKmcgCellColorBarScaleLabel(value)
                });
    }

    static void showKmcgMarginalHeatmapTooltip(Canvas dataCanvas, double mouseX, double mouseY,
                                               int xCoord, int yCoord, int value, double percentage,
                                               int blockSum, double blockPercentage) {
        showAlignedHeatmapTooltip(dataCanvas, mouseX, mouseY,
                new String[]{"Coord", "Count", "Scale", "Block Count"},
                new String[]{
                        String.format("(%d, %d)", xCoord, yCoord),
                        String.format("%d (%.4f%%)", value, percentage),
                        formatKmcgCellColorBarScaleLabel(value),
                        String.format("%d (%.4f%%)", blockSum, blockPercentage)
                },
                300);
    }

    /** Sum of all cells in a marginal strip or corner block (region1–region9). */
    static int computeMarginalBlockSum(List<List<Integer>> data, String regionId) {
        if (data == null || data.isEmpty() || regionId == null) {
            return 0;
        }
        int rowCount = data.size();
        int colCount = data.get(0).size();
        switch (regionId) {
            case "region1":
                return data.get(0).get(0);
            case "region2":
                return sumMatrixRowRange(data, 0, 1, colCount - 2);
            case "region3":
                return data.get(0).get(colCount - 1);
            case "region4":
                return sumMatrixColRange(data, 0, 1, rowCount - 2);
            case "region6":
                return sumMatrixColRange(data, colCount - 1, 1, rowCount - 2);
            case "region7":
                return data.get(rowCount - 1).get(0);
            case "region8":
                return sumMatrixRowRange(data, rowCount - 1, 1, colCount - 2);
            case "region9":
                return data.get(rowCount - 1).get(colCount - 1);
            default:
                return 0;
        }
    }

    private static int sumMatrixRowRange(List<List<Integer>> data, int row, int colStart, int colEnd) {
        if (colStart > colEnd) {
            return 0;
        }
        int sum = 0;
        List<Integer> line = data.get(row);
        for (int col = colStart; col <= colEnd; col++) {
            sum += line.get(col);
        }
        return sum;
    }

    private static int sumMatrixColRange(List<List<Integer>> data, int col, int rowStart, int rowEnd) {
        if (rowStart > rowEnd) {
            return 0;
        }
        int sum = 0;
        for (int row = rowStart; row <= rowEnd; row++) {
            sum += data.get(row).get(col);
        }
        return sum;
    }

    static void showKmcgCountHeatmapTooltip(Canvas dataCanvas, double mouseX, double mouseY,
                                            int value, double percentage) {
        showAlignedHeatmapTooltip(dataCanvas, mouseX, mouseY,
                new String[]{"Count", "Scale"},
                new String[]{
                        String.format("%d (%.4f%%)", value, percentage),
                        formatKmcgCellColorBarScaleLabel(value)
                });
    }

    private static Double colorBarIndicatorRatio = null;
    private static double kmcgColorBarX;
    private static double kmcgColorBarY;
    private static double kmcgColorBarWidth;
    private static double kmcgColorBarHeight;
    private static double kmcgColorBarMaxValue = 1.0;
    private static boolean kmcgColorBarUsesLogTransform = true;

    static void clearColorBarIndicator() {
        colorBarIndicatorRatio = null;
    }

    static void setColorBarIndicatorFromKmcgValue(int value) {
        if (kmcgColorBarMaxValue <= 0) {
            setColorBarIndicatorRatio(0.0);
            return;
        }
        double transformed = value >= 0 ? Math.log(value + 1) / Math.log(2) : 0;
        setColorBarIndicatorRatio(transformed / kmcgColorBarMaxValue);
    }

    static void drawColorBarIndicatorLine(GraphicsContext gc, double barX, double barY,
                                          double barWidth, double barHeight, double ratio) {
        double clamped = Math.max(0, Math.min(1, ratio));
        double x = Math.floor(barX + clamped * barWidth) + 0.5;
        double overshoot = 5.0;
        gc.save();
        gc.setStroke(Color.RED);
        gc.setLineWidth(1.0);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.BUTT);
        gc.setLineDashes(2.0, 3.0);
        gc.strokeLine(x, barY - overshoot, x, barY + barHeight + overshoot);
        gc.setLineDashes(null);
        gc.restore();
    }

    private static boolean setColorBarIndicatorRatio(double ratio) {
        double clamped = Math.max(0, Math.min(1, ratio));
        if (colorBarIndicatorRatio != null && Math.abs(colorBarIndicatorRatio - clamped) < 1e-9) {
            return false;
        }
        colorBarIndicatorRatio = clamped;
        return true;
    }

    static void drawColorBarIndicatorOverlay(Canvas canvas) {
        if (colorBarIndicatorRatio == null || canvas == null) {
            return;
        }
        drawColorBarIndicatorLine(canvas.getGraphicsContext2D(),
                kmcgColorBarX, kmcgColorBarY, kmcgColorBarWidth, kmcgColorBarHeight,
                colorBarIndicatorRatio);
    }

    private static void storeKmcgColorBarLayout(double colorBarX, double colorBarY,
                                                double colorBarWidth, double colorBarHeight,
                                                double maxValue, boolean useLogTransform) {
        kmcgColorBarX = colorBarX + Marginx_KMCG;
        kmcgColorBarY = colorBarY + Marginy_KMCG;
        kmcgColorBarWidth = colorBarWidth;
        kmcgColorBarHeight = colorBarHeight;
        kmcgColorBarMaxValue = maxValue;
        kmcgColorBarUsesLogTransform = useLogTransform;
    }

    private static boolean isOverKmcgColorBar(double mouseX, double mouseY) {
        return mouseX >= kmcgColorBarX && mouseX <= kmcgColorBarX + kmcgColorBarWidth
                && mouseY >= kmcgColorBarY && mouseY <= kmcgColorBarY + kmcgColorBarHeight;
    }

    static String formatKmcgColorBarScaleLabel(double ratio) {
        ratio = Math.max(0, Math.min(1, ratio));
        double value = ratio * kmcgColorBarMaxValue;
        return kmcgColorBarUsesLogTransform ? String.format("%.1f", value) : String.format("%.0f", value);
    }

    private static boolean tryShowKmcgColorBarTooltip(Canvas dataCanvas, double mouseX, double mouseY,
                                                      List<List<Integer>> data, boolean isPolyploidView,
                                                      boolean enableQuadrilateralSelection) {
        if (!isOverKmcgColorBar(mouseX, mouseY)) {
            return false;
        }
        double ratio = (mouseX - kmcgColorBarX) / kmcgColorBarWidth;
        boolean ratioChanged = setColorBarIndicatorRatio(ratio);
        if (ratioChanged) {
            refreshKmcgHoverOverlays(dataCanvas, data, isPolyploidView, enableQuadrilateralSelection,
                    false, mouseX, mouseY, 5);
        }
        showSimpleTextTooltip(dataCanvas, mouseX, mouseY,
                formatColorBarTooltip("Scale", formatKmcgColorBarScaleLabel(ratio)));
        return true;
    }

    static void drawExtensionColorBarTickLabels(GraphicsContext gc, double colorBarX, double colorBarWidth,
                                                double scaleBarY, int scaleCount, String[] tickTexts) {
        gc.setFont(Font.font("Verdana", EXTENSION_COLOR_BAR_TICK_FONT_SIZE));
        gc.setFill(Color.BLACK);
        for (int i = 0; i <= scaleCount; i++) {
            double x = colorBarX + (i * colorBarWidth / (double) scaleCount);
            String text = tickTexts[i];
            Text measure = new Text(text);
            measure.setFont(gc.getFont());
            double textWidth = measure.getLayoutBounds().getWidth();
            gc.fillText(text, x - textWidth / 2, scaleBarY);
        }
    }

  /** Canvas rectangle for one KMCG matrix cell (includes margin offsets). */
    public static final class KmcgCellRect {
        public final double x;
        public final double y;
        public final double width;
        public final double height;

        public KmcgCellRect(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public static KmcgCellRect computeKmcgCellRect(int y, int x, int rowCount, int colCount) {
        double scaleX = 1.0;
        double scaleY = 2.0;
        double rowScaleY;
        double colScaleX;
        double yPosOffset;
        double xPosOffset;

        if (y == 0) {
            rowScaleY = scaleY * 15;
            yPosOffset = 72;
        } else if (y == rowCount - 1) {
            rowScaleY = scaleY * 15;
            yPosOffset = 0;
        } else {
            rowScaleY = scaleY;
            yPosOffset = 50;
        }

        if (x == 0) {
            colScaleX = scaleX * 30;
            xPosOffset = 0;
        } else if (x == colCount - 1) {
            colScaleX = scaleX * 30;
            xPosOffset = 72;
        } else {
            colScaleX = scaleX;
            xPosOffset = 50;
        }

        double xPos = x * scaleX + xPosOffset + Marginx_KMCG;
        double yPos = (rowCount - y - 1) * scaleY + yPosOffset + Marginy_KMCG;
        return new KmcgCellRect(xPos, yPos, colScaleX, rowScaleY);
    }

    public static int[] findKmcgDataIndexAt(double mouseX, double mouseY, int rowCount, int colCount,
                                            boolean hideMarginalBands) {
        for (int y = 0; y < rowCount; y++) {
            boolean marginalRow = y == 0 || y == rowCount - 1;
            for (int x = 0; x < colCount; x++) {
                boolean marginalCol = x == 0 || x == colCount - 1;
                if (hideMarginalBands && (marginalRow || marginalCol)) {
                    continue;
                }
                KmcgCellRect rect = computeKmcgCellRect(y, x, rowCount, colCount);
                if (mouseX >= rect.x && mouseX < rect.x + rect.width
                        && mouseY >= rect.y && mouseY < rect.y + rect.height) {
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }

    public static int kmerlength;


    // 用于保存已点击的点
    public static List<double[]> clickedPoints = new ArrayList<>();
    public static List<double[]> quadrilateralPoints = new ArrayList<>();

    public static List<String> resultData = new ArrayList<>();

    public static boolean hasDrawnData = false;
    private static Image defaultKmcgImageCache = null;
    public static boolean hasFourthDataInFirstLine = false; // 判断第一行是否有第四个数据的标志
    public static Map<String, List<Integer>> gcDataMap = new LinkedHashMap<>();

    public static boolean hasSixthColumnInFirstLine = false;
    // 新增：GC Tab 的独立存储
    public static List<Integer> GCData = new ArrayList<>();
    public static Map<String, List<List<Integer>>> infDataMap = new LinkedHashMap<>();
    public static Map<String, String> infBlockLabelMap = new LinkedHashMap<>();
    public static Map<String, List<List<Integer>>> scaffolddataMap = new LinkedHashMap<>();
    public static Map<String, String> scaffoldBlockLabelMap = new LinkedHashMap<>();
    public static final class ExtensionBlockRef {
        public final String tagName;
        public final String mode;

        public ExtensionBlockRef(String tagName, String mode) {
            this.tagName = tagName;
            this.mode = mode;
        }
    }

    public static List<ExtensionBlockRef> extensionBlockOrder = new ArrayList<>();
//    public static Map<String, List<List<Integer>>> scaffolddataMap = new HashMap<>();
    // 构造函数接收 Canvas 实例
    public KMCG_Processing(Canvas kmerCanvas) {
        KMCG_Processing.kmerCanvas = kmerCanvas;
    }

    public static void clearLoadedData() {
        names.clear();
        lengths.clear();
        kmcgdata.clear();
        kmcgdata_polyploid.clear();
        coordinateDict.clear();
        pointsInside.clear();
        targetData.clear();
        brokenlineData.clear();
        quality_indication.clear();
        qualityGaussianByRow.clear();
        invalidateKmcgSnapshot();
        quadrilateralPoints.clear();
        scaffolddataMap.clear();
        scaffoldBlockLabelMap.clear();
        infDataMap.clear();
        infBlockLabelMap.clear();
        extensionBlockOrder.clear();
        manualPoints.clear();
        clickedPoints.clear();
        gcDataMap.clear();
        resultData.clear();
        extensionBlocksStarted = false;
        extensionTailHeaderInfCount = 0;
        extensionTailHeaderPrmCount = 0;

        Filename = null;
        Fileversion = null;
        KMCG_canvas_height = 0;
        KMCG_canvas_width = 0;
        kmerlength = 0;
        Unitsize = 0;

        if (kmerCanvas != null) {
            clearCanvas(kmerCanvas);
            kmerCanvas.setOnMouseMoved(null);
            kmerCanvas.setOnMouseClicked(null);
        }
    }

    private static final int SCAFFOLD_BLOCK_DATA_ROWS = 302;
    private static final String EXTENSION_PARSER_ID = "20260626-sequential";

    static String normalizeLine(String line) {
        if (line == null) {
            return "";
        }
        String text = line.replace("\r", "").replace('\u00A0', ' ');
        text = Normalizer.normalize(text, Normalizer.Form.NFKC);
        return text.strip();
    }

    static boolean isExtensionBlockHeaderLine(String raw) {
        return parseExtensionBlockHeader(raw) != null;
    }

  /** Returns [tagName, mode, displayText] where mode is "inf" (#) or "prm" (%). */
    static String[] parseExtensionBlockHeader(String raw) {
        String[] strict = parseExtensionBlockHeaderStrict(raw);
        if (strict != null) {
            return strict;
        }
        return parseExtensionBlockHeaderLoose(raw);
    }

    static String[] parseExtensionBlockHeaderStrict(String raw) {
        if (raw.isEmpty()) {
            return null;
        }
        String normalized = raw.replace("\ufeff", "").strip();
        String[] parts = normalized.split("\t", 3);
        if (parts.length < 2) {
            parts = normalized.split("\\s+", 3);
        }
        if (parts.length < 2) {
            return null;
        }
        String tag = parts[0].strip();
        if (tag.isEmpty() || !tag.matches("[A-Za-z].*")) {
            return null;
        }
        String internalMode = resolveExtensionMode(parts[1]);
        if (internalMode == null) {
            return null;
        }
        String displayText = parts.length >= 3 ? parts[2].strip() : "";
        return new String[]{tag, internalMode, displayText};
    }

    static String[] parseExtensionBlockHeaderLoose(String raw) {
        if (raw.isEmpty()) {
            return null;
        }
        String normalized = normalizeLine(raw.replace("\ufeff", ""));
        Pattern tabPattern = Pattern.compile(
                "^([A-Za-z][^\t]*)\\t([#%＃％]|inf|prm)\\s*(?:\\t(.*))?$",
                Pattern.CASE_INSENSITIVE);
        Matcher tabMatcher = tabPattern.matcher(normalized);
        if (tabMatcher.matches()) {
            String internalMode = resolveExtensionMode(tabMatcher.group(2));
            if (internalMode != null) {
                String display = tabMatcher.group(3) != null ? tabMatcher.group(3).strip() : "";
                return new String[]{tabMatcher.group(1).strip(), internalMode, display};
            }
        }
        Pattern spacePattern = Pattern.compile(
                "^([A-Za-z][^\\s]*)\\s+([#%＃％]|inf|prm)\\s*(?:\\s+(.*))?$",
                Pattern.CASE_INSENSITIVE);
        Matcher spaceMatcher = spacePattern.matcher(normalized);
        if (spaceMatcher.matches()) {
            String internalMode = resolveExtensionMode(spaceMatcher.group(2));
            if (internalMode != null) {
                String display = spaceMatcher.group(3) != null ? spaceMatcher.group(3).strip() : "";
                return new String[]{spaceMatcher.group(1).strip(), internalMode, display};
            }
        }
        return parseExtensionBlockHeaderByMarkers(normalized);
    }

    /** Fallback when column split fails (e.g. unusual whitespace) but line contains tab-#-tab markers. */
    static String[] parseExtensionBlockHeaderByMarkers(String normalized) {
        String[] markers = {"\t#\t", "\t%\t", "\tinf\t", "\tprm\t", "\t＃\t", "\t％\t"};
        for (String marker : markers) {
            int idx = normalized.indexOf(marker);
            if (idx <= 0) {
                continue;
            }
            String tag = normalized.substring(0, idx).strip();
            if (tag.isEmpty() || !tag.matches("[A-Za-z].*")) {
                continue;
            }
            String modeToken = marker.replace("\t", "").strip();
            String internalMode = resolveExtensionMode(modeToken);
            if (internalMode == null) {
                continue;
            }
            int displayStart = idx + marker.length();
            String display = displayStart < normalized.length()
                    ? normalized.substring(displayStart).strip() : "";
            return new String[]{tag, internalMode, display};
        }
        return null;
    }

    static String normalizeExtensionModeToken(String modeToken) {
        if (modeToken == null) {
            return "";
        }
        String token = modeToken.replace("\ufeff", "").strip();
        token = Normalizer.normalize(token, Normalizer.Form.NFKC);
        if ("#".equals(token) || "＃".equals(token) || "﹟".equals(token)) {
            return "#";
        }
        if ("%".equals(token) || "％".equals(token)) {
            return "%";
        }
        return token;
    }

    static String resolveExtensionMode(String modeToken) {
        String token = normalizeExtensionModeToken(modeToken);
        if ("#".equals(token)) {
            return "inf";
        }
        if ("%".equals(token)) {
            return "prm";
        }
        String lower = token.toLowerCase(Locale.ROOT);
        if ("inf".equals(lower)) {
            return "inf";
        }
        if ("prm".equals(lower)) {
            return "prm";
        }
        return null;
    }

    static boolean isQualityLine(String raw) {
        return (raw.contains("Km:") || raw.contains("Km="))
                && (raw.contains("Ki:") || raw.contains("Ki="));
    }

    /** Quality metadata line (1133-style), not an extension block header. */
    static boolean isStandaloneQualityMetadataLine(String raw) {
        return isQualityLine(raw) && parseExtensionBlockHeader(raw) == null;
    }

    static boolean extensionBlocksStarted = false;
    static int extensionTailHeaderInfCount = 0;
    static int extensionTailHeaderPrmCount = 0;

    static boolean isBrokenlineRow(String raw) {
        if (parseExtensionBlockHeader(raw) != null) {
            return false;
        }
        if (!raw.contains(".")) {
            return false;
        }
        return parseDoubleRow(raw).size() >= 2;
    }

    static boolean isNumericDataRow(String raw) {
        if (raw.isEmpty()) {
            return false;
        }
        for (String token : raw.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (!token.matches("-?\\d+(\\.\\d+)?")) {
                return false;
            }
        }
        return true;
    }

    static void parseQualityLine(String line) {
        String normalized = normalizeLine(line);
        Pattern kmKiPattern = Pattern.compile("Km[=:]\\s*(\\d+\\.\\d+).*?Ki[=:]\\s*(\\d+\\.\\d+)");
        Matcher kmKiMatcher = kmKiPattern.matcher(normalized);

        Pattern muPattern = Pattern.compile("(?:μ|µ|mu)\\s*[:=]\\s*([\\d.]+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Pattern sigmaPattern = Pattern.compile("(?:σ|sigma)\\s*[:=]\\s*([\\d.]+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Double muFromLine = null;
        Double sigmaFromLine = null;
        Matcher muMatcher = muPattern.matcher(normalized);
        if (muMatcher.find()) {
            muFromLine = Double.parseDouble(muMatcher.group(1));
        }
        Matcher sigmaLineMatcher = sigmaPattern.matcher(normalized);
        if (sigmaLineMatcher.find()) {
            sigmaFromLine = Double.parseDouble(sigmaLineMatcher.group(1));
        }

        Pattern rowMeanPattern = Pattern.compile("Row(\\d+)_Mean=([\\d.]+)", Pattern.CASE_INSENSITIVE);
        Pattern rowSigmaPattern = Pattern.compile("Row(\\d+)_Sigma=([\\d.]+)", Pattern.CASE_INSENSITIVE);
        Map<Integer, Double> means = new HashMap<>();
        Map<Integer, Double> sigmas = new HashMap<>();

        Matcher meanMatcher = rowMeanPattern.matcher(normalized);
        while (meanMatcher.find()) {
            means.put(Integer.parseInt(meanMatcher.group(1)), Double.parseDouble(meanMatcher.group(2)));
        }
        Matcher sigmaMatcher = rowSigmaPattern.matcher(normalized);
        while (sigmaMatcher.find()) {
            sigmas.put(Integer.parseInt(sigmaMatcher.group(1)), Double.parseDouble(sigmaMatcher.group(2)));
        }
        for (Map.Entry<Integer, Double> entry : means.entrySet()) {
            Double sigma = sigmas.get(entry.getKey());
            if (sigma != null) {
                qualityGaussianByRow.put(entry.getKey() - 1, new double[]{entry.getValue(), sigma});
            }
        }
        if (means.isEmpty() && muFromLine != null && sigmaFromLine != null) {
            qualityGaussianByRow.put(0, new double[]{muFromLine, sigmaFromLine});
        }

        if (kmKiMatcher.find()) {
            try {
                double kmValue = Double.parseDouble(kmKiMatcher.group(1));
                double kiValue = Double.parseDouble(kmKiMatcher.group(2));
                StringBuilder display = new StringBuilder();
                if (muFromLine != null) {
                    display.append(String.format("μ: %.4f", muFromLine));
                }
                if (sigmaFromLine != null) {
                    if (display.length() > 0) {
                        display.append("\t");
                    }
                    display.append(String.format("σ: %.4f", sigmaFromLine));
                }
                if (display.length() > 0) {
                    display.append("\t");
                }
                display.append(String.format("Km: %.2f", kmValue));
                List<Integer> rowNums = new ArrayList<>(means.keySet());
                Collections.sort(rowNums);
                for (int rowNum : rowNums) {
                    Double mean = means.get(rowNum);
                    Double sigma = sigmas.get(rowNum);
                    if (mean != null && sigma != null) {
                        display.append(String.format("\tRow%d_Mean=%.4f", rowNum, mean));
                        display.append(String.format("\tRow%d_Sigma=%.4f", rowNum, sigma));
                    }
                }
                display.append(String.format("\tKi: %.2f", kiValue));
                quality_indication.add(display.toString());
            } catch (NumberFormatException e) {
                quality_indication.add(line);
            }
        } else {
            quality_indication.add(line);
        }
    }

    static List<Integer> parseIntegerRow(String raw) {
        List<Integer> row = new ArrayList<>();
        for (String token : raw.trim().split("\\s+")) {
            String trimmed = token.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.matches("-?\\d+")) {
                row.add(Integer.parseInt(trimmed));
            } else {
                try {
                    row.add((int) Math.round(Double.parseDouble(trimmed)));
                } catch (NumberFormatException ignored) {
                    // skip non-numeric tokens
                }
            }
        }
        return row;
    }

    static List<Double> parseDoubleRow(String raw) {
        List<Double> row = new ArrayList<>();
        for (String token : raw.trim().split("\\s+")) {
            String trimmed = token.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                row.add(Double.parseDouble(trimmed));
            } catch (NumberFormatException ignored) {
                // skip non-numeric tokens
            }
        }
        return row;
    }

    static void logExtensionDebug(String filepath, int lineNumber, String raw) {
        if (lineNumber < 1131 || lineNumber > 1145) {
            return;
        }
        System.err.println("KMCG extension debug L" + lineNumber + ": " + raw.substring(0, Math.min(80, raw.length())));
    }

    static final class ExtensionHeaderRef {
        final int lineIndex;
        final String tagName;
        final String mode;
        final String displayText;

        ExtensionHeaderRef(int lineIndex, String tagName, String mode, String displayText) {
            this.lineIndex = lineIndex;
            this.tagName = tagName;
            this.mode = mode;
            this.displayText = displayText;
        }
    }

    static final class ExtensionDataReadResult {
        final List<List<Integer>> dataBlock;
        final int nextLineIndex;

        ExtensionDataReadResult(List<List<Integer>> dataBlock, int nextLineIndex) {
            this.dataBlock = dataBlock;
            this.nextLineIndex = nextLineIndex;
        }
    }

    /** Read exactly {@link #SCAFFOLD_BLOCK_DATA_ROWS} file lines after the header (legacy KMCG layout). */
    static ExtensionDataReadResult readExtensionBlockDataRows(List<String> lines, int startIndex) {
        List<List<Integer>> dataBlock = new ArrayList<>();
        int i = startIndex;
        for (int rowsRead = 0; rowsRead < SCAFFOLD_BLOCK_DATA_ROWS && i < lines.size(); rowsRead++) {
            String raw = normalizeLine(lines.get(i));
            if (!raw.isEmpty()) {
                if (parseExtensionBlockHeader(raw) != null) {
                    return new ExtensionDataReadResult(dataBlock, i);
                }
                List<Integer> row = parseIntegerRow(raw);
                if (!row.isEmpty()) {
                    dataBlock.add(row);
                }
            }
            i++;
        }
        return new ExtensionDataReadResult(dataBlock, i);
    }

    static ExtensionDataReadResult readExtensionDataRowsFromLines(List<String> lines, int startIndex) {
        return readExtensionDataRowsFromLines(lines, startIndex, lines.size());
    }

    static ExtensionDataReadResult readExtensionDataRowsFromLines(List<String> lines, int startIndex, int endIndex) {
        List<List<Integer>> dataBlock = new ArrayList<>();
        int i = startIndex;
        int limit = Math.min(endIndex, lines.size());
        while (i < limit && dataBlock.size() < SCAFFOLD_BLOCK_DATA_ROWS) {
            String raw = normalizeLine(lines.get(i));
            if (raw.isEmpty()) {
                i++;
                continue;
            }
            if (parseExtensionBlockHeader(raw) != null) {
                break;
            }
            List<Integer> row = parseIntegerRow(raw);
            if (!row.isEmpty()) {
                dataBlock.add(row);
            }
            i++;
        }
        return new ExtensionDataReadResult(dataBlock, i);
    }

    static void storeExtensionDataBlock(String tagName, String mode, String displayText,
                                        List<List<Integer>> dataBlock) {
        if (tagName.isEmpty() || dataBlock.isEmpty()) {
            System.err.println("KMCG extension: skipped " + mode + " block '" + tagName + "' (no numeric rows)");
            return;
        }
        if ("inf".equals(mode)) {
            infDataMap.put(tagName, dataBlock);
            if (displayText != null && !displayText.isEmpty()) {
                infBlockLabelMap.put(tagName, displayText);
            }
            extensionBlockOrder.add(new ExtensionBlockRef(tagName, mode));
            System.err.println("KMCG extension: parsed # block '" + tagName + "' rows=" + dataBlock.size());
        } else if ("prm".equals(mode)) {
            scaffolddataMap.put(tagName, dataBlock);
            if (displayText != null && !displayText.isEmpty()) {
                scaffoldBlockLabelMap.put(tagName, displayText);
            }
            extensionBlockOrder.add(new ExtensionBlockRef(tagName, mode));
            System.err.println("KMCG extension: parsed % block '" + tagName + "' rows=" + dataBlock.size());
        }
    }

    static List<ExtensionHeaderRef> findAllExtensionHeadersInFile(List<String> allLines) {
        List<ExtensionHeaderRef> headers = new ArrayList<>();
        for (int i = 0; i < allLines.size(); i++) {
            String raw = normalizeLine(allLines.get(i));
            String[] header = parseExtensionBlockHeader(raw);
            if (header != null) {
                headers.add(new ExtensionHeaderRef(i, header[0], header[1], header[2]));
            }
        }
        return headers;
    }

    static void parsePreExtensionMetadata(List<String> allLines, int startIndex, int endIndex) {
        int limit = Math.min(endIndex, allLines.size());
        for (int i = startIndex; i < limit; i++) {
            String line = allLines.get(i);
            String raw = normalizeLine(line);
            if (raw.isEmpty()) {
                continue;
            }
            if (parseExtensionBlockHeader(raw) != null) {
                break;
            }
            if (isStandaloneQualityMetadataLine(raw)) {
                parseQualityLine(line);
            } else if (brokenlineData.isEmpty() && isBrokenlineRow(raw)) {
                brokenlineData.add(parseDoubleRow(raw));
            }
        }
    }

    static void parseAllExtensionBlocksFromFile(List<String> allLines) {
        List<ExtensionHeaderRef> headers = findAllExtensionHeadersInFile(allLines);
        extensionTailHeaderInfCount = 0;
        extensionTailHeaderPrmCount = 0;
        for (ExtensionHeaderRef ref : headers) {
            System.err.println("KMCG extension: full-file header L" + (ref.lineIndex + 1)
                    + " tag='" + ref.tagName + "' mode=" + ref.mode);
            if ("inf".equals(ref.mode)) {
                extensionTailHeaderInfCount++;
            } else if ("prm".equals(ref.mode)) {
                extensionTailHeaderPrmCount++;
            }
        }
        System.err.println("KMCG extension: full-file scan found " + headers.size()
                + " header(s) in " + allLines.size() + " total lines");
        if (headers.size() == 1 && allLines.size() < 1500) {
            System.err.println("KMCG extension: NOTE: file may be truncated - files with both % and # blocks"
                    + " usually have 1700+ lines; this file has " + allLines.size());
        }

        if (headers.isEmpty()) {
            return;
        }

        int metadataStart = 1129;
        if (metadataStart < headers.get(0).lineIndex) {
            parsePreExtensionMetadata(allLines, metadataStart, headers.get(0).lineIndex);
        }

        for (int h = 0; h < headers.size(); h++) {
            ExtensionHeaderRef ref = headers.get(h);
            extensionBlocksStarted = true;
            int dataStart = ref.lineIndex + 1;
            int dataEnd = h + 1 < headers.size() ? headers.get(h + 1).lineIndex : allLines.size();
            ExtensionDataReadResult result = readExtensionDataRowsFromLines(allLines, dataStart, dataEnd);
            storeExtensionDataBlock(ref.tagName, ref.mode, ref.displayText, result.dataBlock);
        }
    }

    static void logExtensionHeaderScan(List<String> lines, int startIndex, int baseLineNumber) {
        extensionTailHeaderInfCount = 0;
        extensionTailHeaderPrmCount = 0;
        int found = 0;
        for (int i = startIndex; i < lines.size(); i++) {
            String raw = normalizeLine(lines.get(i));
            String[] header = parseExtensionBlockHeader(raw);
            if (header != null) {
                found++;
                if ("inf".equals(header[1])) {
                    extensionTailHeaderInfCount++;
                } else if ("prm".equals(header[1])) {
                    extensionTailHeaderPrmCount++;
                }
                System.err.println("KMCG extension: header at L" + (baseLineNumber + (i - startIndex))
                        + " tag='" + header[0] + "' mode=" + header[1]);
            }
        }
        System.err.println("KMCG extension: " + found + " block header(s) in tail ("
                + (lines.size() - startIndex) + " lines)");
    }

    static void logExtensionParseProbe(List<String> allLines) {
        if (infDataMap.isEmpty() && allLines.size() > 1437) {
            String raw1438 = normalizeLine(allLines.get(1437));
            String[] header = parseExtensionBlockHeader(raw1438);
            System.err.println("KMCG extension: probe L1438: "
                    + raw1438.substring(0, Math.min(80, raw1438.length())));
            System.err.println("KMCG extension: probe L1438 header="
                    + (header != null ? header[0] + "/" + header[1] : "not recognized"));
        } else if (infDataMap.isEmpty() && allLines.size() <= 1437) {
            System.err.println("KMCG extension: probe file ends at L" + allLines.size()
                    + " (expected # block header around L1438)");
        }
    }

    static void logExtensionHeaderScan(List<String> lines, int baseLineNumber) {
        logExtensionHeaderScan(lines, 0, baseLineNumber);
    }

    static void parseExtensionTailFromLines(List<String> lines, int startIndex, int baseLineNumber) {
        int i = startIndex;
        while (i < lines.size()) {
            String line = lines.get(i);
            String raw = normalizeLine(line);
            if (raw.isEmpty()) {
                i++;
                continue;
            }
            String[] header = parseExtensionBlockHeader(raw);
            if (header != null) {
                extensionBlocksStarted = true;
                ExtensionDataReadResult result = readExtensionBlockDataRows(lines, i + 1);
                storeExtensionDataBlock(header[0], header[1], header[2], result.dataBlock);
                i = result.nextLineIndex;
                continue;
            }
            if (isStandaloneQualityMetadataLine(raw)) {
                parseQualityLine(line);
                i++;
                continue;
            }
            if (brokenlineData.isEmpty() && isBrokenlineRow(raw)) {
                brokenlineData.add(parseDoubleRow(raw));
                i++;
                continue;
            }
            if (isNumericDataRow(raw)) {
                i++;
                continue;
            }
            System.err.println("KMCG extension: skipping unrecognized line at L" + (i + 1)
                    + ": " + raw.substring(0, Math.min(80, raw.length())));
            i++;
        }
    }

    static String parseExtensionDataBlockFixed(BufferedReader br, String tagName, String mode,
                                             String displayText) throws IOException {
        List<String> tailLines = new ArrayList<>();
        String line;
        while ((line = br.readLine()) != null) {
            tailLines.add(line);
        }
        ExtensionDataReadResult result = readExtensionBlockDataRows(tailLines, 0);
        storeExtensionDataBlock(tagName, mode, displayText, result.dataBlock);
        if (result.nextLineIndex < tailLines.size()) {
            parseExtensionTailFromLines(tailLines, result.nextLineIndex, 0);
        }
        return null;
    }

    static String parseScaffoldBlockFixed(BufferedReader br, String tagName) throws IOException {
        return parseExtensionDataBlockFixed(br, tagName, "prm", "");
    }

    static void parseExtensionTail(BufferedReader br, String firstLine) throws IOException {
        List<String> lines = new ArrayList<>();
        if (firstLine != null) {
            lines.add(firstLine);
        }
        String line;
        while ((line = br.readLine()) != null) {
            lines.add(line);
        }
        parseExtensionTailFromLines(lines, 0, 0);
    }

    static String parseExtensionLine(BufferedReader br, String line) throws IOException {
        String raw = normalizeLine(line);
        if (raw.isEmpty()) {
            return br.readLine();
        }
        String[] header = parseExtensionBlockHeader(raw);
        if (header != null) {
            extensionBlocksStarted = true;
            return parseExtensionDataBlockFixed(br, header[0], header[1], header[2]);
        }
        if (isStandaloneQualityMetadataLine(raw)) {
            parseQualityLine(line);
            return br.readLine();
        }
        if (brokenlineData.isEmpty() && isBrokenlineRow(raw)) {
            brokenlineData.add(parseDoubleRow(raw));
            return br.readLine();
        }
        if (isNumericDataRow(raw)) {
            return br.readLine();
        }
        System.err.println("KMCG extension: skipping unrecognized line: "
                + raw.substring(0, Math.min(80, raw.length())));
        return br.readLine();
    }

    static boolean isValidKmcgVersionLine(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        String version = raw.split("\t", -1)[0].replace("\ufeff", "").strip();
        return version.startsWith("KMCG");
    }

    public static List<List<Integer>> readFile(String filepath) {
        clearLoadedData();

        boolean isGzip = filepath.toLowerCase().endsWith(".gz");

        try {
            List<String> allLines = new ArrayList<>();
            if (isGzip) {
                try (InputStream tempStream = new GZIPInputStream(new FileInputStream(filepath));
                     BufferedReader tempBr = new BufferedReader(new InputStreamReader(tempStream, StandardCharsets.UTF_8))) {
                    String firstLine = tempBr.readLine();
                    if (!isValidKmcgVersionLine(normalizeLine(firstLine))) {
                        clearLoadedData();
                        return new ArrayList<>();
                    }
                    if (firstLine != null) {
                        allLines.add(firstLine);
                    }
                    String line;
                    while ((line = tempBr.readLine()) != null) {
                        allLines.add(line);
                    }
                }
            } else {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(
                        new FileInputStream(filepath), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        allLines.add(line);
                    }
                }
            }

            if (allLines.isEmpty() || !isValidKmcgVersionLine(normalizeLine(allLines.get(0)))) {
                clearLoadedData();
                return new ArrayList<>();
            }

            System.err.println("KMCG extension: parser=" + EXTENSION_PARSER_ID + " file=" + filepath);

            for (int idx = 0; idx < allLines.size(); idx++) {
                int lineNumber = idx + 1;
                String line = allLines.get(idx);
                String raw = normalizeLine(line);

                if (lineNumber == 1) {
                    String[] tokens = normalizeLine(line).split("\t");
                    hasSixthColumnInFirstLine = (tokens.length >= 6);

                    if (tokens.length >= 3) {
                        hasFourthDataInFirstLine = (tokens.length >= 4);
                        try {
                            Fileversion = tokens[0].replace("\ufeff", "").strip();
                            KMCG_canvas_height = Integer.parseInt(tokens[1]);
                            KMCG_canvas_width = Integer.parseInt(tokens[2]);
                            kmerlength = (tokens.length >= 4) ? Integer.parseInt(tokens[3]) : 31;
                            Unitsize = (tokens.length >= 5) ? Integer.parseInt(tokens[4]) : 1_000_000;
                        } catch (NumberFormatException e) {
                            System.err.println("Error: Invalid number format in line 1.");
                        }
                    }
                } else if (lineNumber == 2) {
                    names.addAll(Arrays.asList(line.strip().split("\t")));
                } else if (lineNumber == 3) {
                    for (String token : line.strip().split("\t")) {
                        try { lengths.add(Integer.parseInt(token)); }
                        catch (NumberFormatException e) { lengths.add(null); }
                    }
                } else if (lineNumber >= 4 && lineNumber < 306) {
                    List<Integer> intLine = new ArrayList<>();
                    for (String token : line.strip().split("\t")) {
                        try { intLine.add(Integer.parseInt(token)); }
                        catch (NumberFormatException e) { intLine.add(null); }
                    }
                    kmcgdata.add(intLine);
                } else if (lineNumber >= 307 && lineNumber < 609) {
                    String[] tokens = line.split("\t", -1);
                    for (int x = 0; x < tokens.length - 1; x++) {
                        coordinateDict.put("(" + x + ", " + (lineNumber - 307) + ")", tokens[x].strip());
                    }
                } else if (lineNumber >= 610 && lineNumber < 1131) {
                    List<Integer> intLine = new ArrayList<>();
                    for (String token : line.strip().split("\t")) {
                        try { intLine.add(Integer.parseInt(token)); }
                        catch (NumberFormatException e) { intLine.add(null); }
                    }
                    targetData.add(intLine);
                }
            }

            if (allLines.size() > 1130) {
                int tailStart = 1130;
                System.err.println("KMCG extension: sequential tail from L1131, fileLines="
                        + allLines.size() + ", tailLines=" + (allLines.size() - tailStart));
                logExtensionHeaderScan(allLines, tailStart, 1131);
                parseExtensionTailFromLines(allLines, tailStart, 1131);
                logExtensionParseProbe(allLines);
            }

        } catch (OutOfMemoryError e) {
            clearLoadedData();
            System.err.println("Error: file too large for available memory. Try closing other apps or use a machine with more RAM.");
            JOptionPane.showMessageDialog(null,
                    "File is too large to load in available memory.",
                    "Out of memory",
                    JOptionPane.ERROR_MESSAGE);
            return new ArrayList<>();
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }

        if (kmcgdata.isEmpty() || Fileversion == null || !Fileversion.startsWith("KMCG")) {
            clearLoadedData();
            return new ArrayList<>();
        }

        if (scaffolddataMap.isEmpty() && infDataMap.isEmpty() && gcDataMap.isEmpty()) {
            System.err.println("KMCG extension: no inf/prm blocks parsed after line 1133");
        } else {
            System.err.println("KMCG extension: inf tabs=" + infDataMap.size()
                    + ", prm tabs=" + scaffolddataMap.size()
                    + ", gc tabs=" + gcDataMap.size());
        }
        if (extensionTailHeaderInfCount > 0 && infDataMap.isEmpty()) {
            System.err.println("KMCG extension: WARNING: found " + extensionTailHeaderInfCount
                    + " # block header(s) in file but none were parsed into infDataMap");
        }

        return kmcgdata;
    }


    // 将数据绘制到 Canvas 上
    public static void drawKMCGOnCanvas(Canvas dataCanvas, List<List<Integer>> data) {
        drawKMCGOnCanvas(dataCanvas, data, null);
    }

    public static void drawKMCGOnCanvas(Canvas dataCanvas, List<List<Integer>> data, String cornerLabel) {
        drawKMCGOnCanvas(dataCanvas, data, cornerLabel, false);
    }

    public static void drawKMCGOnCanvas(Canvas dataCanvas, List<List<Integer>> data, String cornerLabel,
                                        boolean hideMarginalBands) {
        drawKMCGOnCanvas(dataCanvas, data, cornerLabel, hideMarginalBands, true);
    }

    public static void drawKMCGOnCanvas(Canvas dataCanvas, List<List<Integer>> data, String cornerLabel,
                                        boolean hideMarginalBands, boolean useLogTransform) {
        if (data == null || data.isEmpty() || data.get(0) == null || data.get(0).isEmpty()) {
            showDefaultImage(dataCanvas);
            return;
        }
        hasDrawnData = true;
        double max_value = 0.0;
        // 在画布底部绘制颜色条
        double colorBarHeight = 40;  // 颜色条的高度
        double colorBarWidth = 900;  // 颜色条的宽度与画布宽度相同
        double colorBarY = hideMarginalBands ? EXTENSION_COLOR_BAR_Y : 720;
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

        List<List<Double>> transformed_data = useLogTransform ? processData(data) : null;

        for (int y = 0; y < rowCount; y++) {
            boolean marginalRow = y == 0 || y == rowCount - 1;
            for (int x = 0; x < colCount; x++) {
                boolean marginalCol = x == 0 || x == colCount - 1;
                if (hideMarginalBands && (marginalRow || marginalCol)) {
                    continue;
                }
                if (useLogTransform) {
                    Double value = transformed_data.get(y).get(x);
                    if (value != null) {
                        max_value = Math.max(max_value, value);
                    }
                } else {
                    Integer value = data.get(y).get(x);
                    if (value != null) {
                        max_value = Math.max(max_value, value);
                    }
                }
            }
        }

        for (int y = 0; y < rowCount; y++) {
            boolean marginalRow = y == 0 || y == rowCount - 1;
            for (int x = 0; x < colCount; x++) {
                boolean marginalCol = x == 0 || x == colCount - 1;
                if (hideMarginalBands && (marginalRow || marginalCol)) {
                    continue;
                }

                Color color;
                if (useLogTransform) {
                    Double value = transformed_data.get(y).get(x);
                    if (value == null) {
                        continue;
                    }
                    color = ColorUtils.getColorForValue(value, max_value, "UD");
                } else {
                    Integer value = data.get(y).get(x);
                    if (value == null) {
                        continue;
                    }
                    color = ColorUtils.getColorForValue(value, max_value, "UD");
                }
                KmcgCellRect rect = computeKmcgCellRect(y, x, rowCount, colCount);
                gc.setFill(color);
                gc.fillRect(rect.x, rect.y, rect.width, rect.height);
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
            gc.fillText(String.format(useLogTransform ? "%.1f" : "%.0f", value), xPos - 10 + Marginx_KMCG, scaleBarY +Marginy_KMCG);  // 绘制文本并调整位置以对齐
        }
        storeKmcgColorBarLayout(colorBarX, colorBarY, colorBarWidth, colorBarHeight, max_value, useLogTransform);
        if (hideMarginalBands) {
            drawExtensionBlockLabel(gc, cornerLabel, colorBarY);
        } else {
            drawCornerLabel(gc, cornerLabel, region1);
        }
        captureKmcgSnapshot(dataCanvas);
    }

    static void drawExtensionBlockLabel(GraphicsContext gc, String text, double colorBarY) {
        if (text == null || text.isEmpty()) {
            return;
        }
        gc.setFont(Font.font("Verdana", EXTENSION_BLOCK_LABEL_FONT_SIZE));
        gc.setFill(Color.BLACK);
        Text measure = new Text(text);
        measure.setFont(gc.getFont());
        double textWidth = measure.getLayoutBounds().getWidth();
        double labelX = region8.getX() + (region8.getWidth() - textWidth) / 2;
        double labelY = Marginy_KMCG + colorBarY - 8;
        gc.fillText(text, labelX, labelY);
    }

    static void drawCornerLabel(GraphicsContext gc, String text, Rectangle region) {
        gc.setFill(Paint.valueOf("#f4f4f4"));
        gc.fillRect(region.getX(), region.getY(), region.getWidth(), region.getHeight());
        if (text == null || text.isEmpty()) {
            return;
        }
        double fontSize = 11;
        Font font = Font.font("Verdana", fontSize);
        Text measure = new Text(text);
        measure.setFont(font);
        double textWidth = measure.getLayoutBounds().getWidth();
        while (textWidth > region.getWidth() - 4 && fontSize > 6) {
            fontSize -= 1;
            font = Font.font("Verdana", fontSize);
            measure.setFont(font);
            textWidth = measure.getLayoutBounds().getWidth();
        }
        gc.setFont(font);
        gc.setFill(Color.BLACK);
        double x = region.getX() + (region.getWidth() - textWidth) / 2;
        double y = region.getY() + (region.getHeight() + fontSize) / 2 - 2;
        gc.fillText(text, x, y);
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

    // 判断两条线段是否相交
    public static boolean isIntersecting(double[] p1, double[] p2, double[] q1, double[] q2) {
        // 快速排除检测 - 检查线段包围盒是否重叠
        if (Math.max(p1[0], p2[0]) < Math.min(q1[0], q2[0]) ||
                Math.max(q1[0], q2[0]) < Math.min(p1[0], p2[0]) ||
                Math.max(p1[1], p2[1]) < Math.min(q1[1], q2[1]) ||
                Math.max(q1[1], q2[1]) < Math.min(p1[1], p2[1])) {
            return false;
        }

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

    // 清除四边形点相关数据
    public static void clearQuadrilateralData() {
        clickedPoints.clear();
        quadrilateralPoints.clear();
        manualPoints.clear();
        pointsInside.clear();
        resultData.clear();
    }

    //选择四个点-坐标点弹窗显示
    public static void handleQuadrilateralSelection() {
        List<double[]> allPoints = new ArrayList<>();
        if (!pointsInside.isEmpty()) {
            manualPoints.clear(); // 清空
            allPoints.addAll(pointsInside);
        }
        if (!manualPoints.isEmpty()) {
            quadrilateralPoints.clear();
            allPoints.addAll(manualPoints);
        }

        showCoordinateDataDialog("Quadrilateral Coordinates and Data View", allPoints, resultData);
    }


    // 统计四边形围住的所有坐标
    public static List<double[]> getPointsInsideQuadrilateral(List<double[]> quadrilateral, boolean isPolyploidView) {
        List<double[]> pointsInside = new ArrayList<>();

        for (int x = 1; x <= (int)CANVAS_WIDTH; x++) {
            for (int displayY = 1; displayY <= 300; displayY++) {
                // 在多倍体视图下，displayY 1-150对应逻辑Y的奇数行，151-300对应偶数行
                // 在单倍体视图下，displayY直接对应逻辑Y
                int logicalY = isPolyploidView ?
                        (displayY <= 150 ? displayY * 2 - 1 : (displayY - 150) * 2) :
                        displayY;

                // 创建显示坐标点（用于判断是否在四边形内）
                double[] displayPoint = new double[]{x, displayY};

                // 创建逻辑坐标点（用于最终结果）
                double[] logicalPoint = new double[]{x, logicalY};

                // 检查显示坐标点是否在四边形内
                if (isPointInQuadrilateral(displayPoint, quadrilateral)) {
                    pointsInside.add(logicalPoint);
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
        alert.getButtonTypes().setAll(new ButtonType("OK", ButtonBar.ButtonData.OK_DONE));
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


    public static void showRegion5Coordinates(Canvas dataCanvas, List<List<Integer>> data,
                                              double mouseX, double mouseY, boolean isPolyploidView) {

        // 将鼠标坐标映射到 region5 坐标系
        double relativeX = mouseX - region5.getX();
        double relativeY = mouseY - region5.getY();

        // 缩放系数（只需计算一次）
        double scaleX = CANVAS_WIDTH / region5.getWidth();
        double scaleY = CANVAS_HEIGHT / region5.getHeight();

        // 显示坐标（1-300）
        int displayY = (int) ((region5.getHeight() - relativeY) * scaleY) + 1;
        int xCoord = (int) (relativeX * scaleX) + 1;

        // 逻辑坐标转换
        int displayLogicalY = isPolyploidView
                ? (displayY <= 150 ? displayY * 2 - 1 : (displayY - 150) * 2)
                : displayY;

        // 限制坐标范围
        xCoord = Math.max(1, Math.min(xCoord, 1000));
        displayLogicalY = Math.max(1, Math.min(displayLogicalY, 300));

        int dataX = xCoord;
        int dataY = displayY;
        dataX = Math.min(dataX, 1000);
        dataY = Math.min(dataY, 300);

        // 获取数据值（避免越界异常）
        int value = 0;
        if (dataY < data.size() && dataX < data.get(dataY).size()) {
            value = data.get(dataY).get(dataX);
        }

        double percentage = (totalKmcgSum != 0) ? (value * 100.0 / totalKmcgSum) : 0.0;
        setColorBarIndicatorFromKmcgValue(value);
        showKmcgHeatmapTooltip(dataCanvas, mouseX, mouseY, xCoord, displayLogicalY, value, percentage);
    }

    private static int[] displayCoordsForMarginalCell(int matrixX, int matrixY, int rowCount, int colCount,
                                                      double mouseX, double mouseY, boolean isPolyploidView) {
        boolean topRow = matrixY == rowCount - 1;
        boolean bottomRow = matrixY == 0;
        boolean leftCol = matrixX == 0;
        boolean rightCol = matrixX == colCount - 1;

        int displayX;
        int displayY;
        if (bottomRow || topRow) {
            double relX = Math.max(0, Math.min(mouseX - region5.getX(), region5.getWidth() - 1));
            displayX = (int) (relX / region5.getWidth() * CANVAS_WIDTH) + 1;
            displayX = Math.max(1, Math.min(displayX, (int) CANVAS_WIDTH));
            displayY = bottomRow ? 1 : (int) CANVAS_HEIGHT;
        } else {
            double relY = Math.max(0, Math.min(mouseY - region5.getY(), region5.getHeight() - 1));
            displayY = (int) ((region5.getHeight() - relY) / region5.getHeight() * CANVAS_HEIGHT) + 1;
            displayY = Math.max(1, Math.min(displayY, (int) CANVAS_HEIGHT));
            displayX = leftCol ? 1 : (int) CANVAS_WIDTH;
        }

        if (leftCol && bottomRow) {
            displayX = 1;
            displayY = 1;
        } else if (rightCol && bottomRow) {
            displayX = (int) CANVAS_WIDTH;
            displayY = 1;
        } else if (leftCol && topRow) {
            displayX = 1;
            displayY = (int) CANVAS_HEIGHT;
        } else if (rightCol && topRow) {
            displayX = (int) CANVAS_WIDTH;
            displayY = (int) CANVAS_HEIGHT;
        }

        int displayLogicalY = isPolyploidView
                ? (displayY <= 150 ? displayY * 2 - 1 : (displayY - 150) * 2)
                : displayY;
        displayLogicalY = Math.max(1, Math.min(displayLogicalY, 300));
        return new int[]{displayX, displayLogicalY};
    }

    public static void showMarginalMatrixCellTooltip(Canvas dataCanvas, List<List<Integer>> data,
                                                     double mouseX, double mouseY,
                                                     int matrixX, int matrixY,
                                                     String marginalRegion,
                                                     boolean isPolyploidView,
                                                     boolean enableQuadrilateralSelection) {
        if (data.isEmpty()) {
            return;
        }
        int rowCount = data.size();
        int colCount = data.get(0).size();
        if (matrixY < 0 || matrixY >= rowCount || matrixX < 0 || matrixX >= colCount) {
            return;
        }

        int value = data.get(matrixY).get(matrixX);
        double percentage = (totalKmcgSum != 0) ? (value * 100.0 / totalKmcgSum) : 0.0;
        int blockSum = computeMarginalBlockSum(data, marginalRegion);
        double blockPercentage = (totalKmcgSum != 0) ? (blockSum * 100.0 / totalKmcgSum) : 0.0;
        int[] displayCoords = displayCoordsForMarginalCell(matrixX, matrixY, rowCount, colCount,
                mouseX, mouseY, isPolyploidView);

        setColorBarIndicatorFromKmcgValue(value);
        refreshKmcgHoverOverlays(dataCanvas, data, isPolyploidView, enableQuadrilateralSelection,
                false, mouseX, mouseY, 5);
        showKmcgMarginalHeatmapTooltip(dataCanvas, mouseX, mouseY,
                displayCoords[0], displayCoords[1], value, percentage, blockSum, blockPercentage);
    }

    // 在设置鼠标移动事件时，确保 Tooltips 的显示和更新
    public static void clearKmcgCanvasMouseHandlers(Canvas dataCanvas) {
        if (dataCanvas == null) {
            return;
        }
        dataCanvas.setOnMouseMoved(null);
        dataCanvas.setOnMouseClicked(null);
        dataCanvas.setOnMouseExited(null);
        dataCanvas.setOnMouseEntered(null);
        if (currentTooltip != null) {
            currentTooltip.hide();
        }
    }

    /** Hover tooltip for extension # / % tabs — same format as main KMCG region5. */
    public static void setupExtensionHeatmapMouseTracking(Canvas dataCanvas, List<List<Integer>> data) {
        dataCanvas.setOnMouseMoved(event ->
                showExtensionHeatmapTooltip(dataCanvas, event.getX(), event.getY(), data));
        dataCanvas.setOnMouseExited(event -> {
            if (currentTooltip != null) {
                currentTooltip.hide();
            }
        });
    }

    public static void showExtensionHeatmapTooltip(Canvas dataCanvas, double mouseX, double mouseY,
                                                   List<List<Integer>> data) {
        if (data == null || data.isEmpty() || data.get(0) == null || data.get(0).isEmpty()) {
            if (currentTooltip != null) {
                currentTooltip.hide();
            }
            return;
        }
        int rowCount = data.size();
        int colCount = data.get(0).size();
        int[] index = findKmcgDataIndexAt(mouseX, mouseY, rowCount, colCount, true);
        if (index == null) {
            if (currentTooltip != null) {
                currentTooltip.hide();
            }
            return;
        }
        int xCoord = index[0];
        int yCoord = index[1];
        int value = data.get(yCoord).get(xCoord);
        double percentage = (totalKmcgSum != 0) ? (value * 100.0 / totalKmcgSum) : 0.0;
        showKmcgHeatmapTooltip(dataCanvas, mouseX, mouseY, xCoord, yCoord, value, percentage);
    }

    public static void setupMouseTracking(Canvas dataCanvas, List<List<Integer>> data,
                                          boolean isMagnificationEnabled, boolean isPolyploidView) {
        setupMouseTracking(dataCanvas, data, isMagnificationEnabled, isPolyploidView, true);
    }

    public static void setupMouseTracking(Canvas dataCanvas, List<List<Integer>> data,
                                          boolean isMagnificationEnabled, boolean isPolyploidView,
                                          boolean enableQuadrilateralSelection) {

        dataCanvas.setOnMouseMoved(event -> {
            double mouseX = event.getX();
            double mouseY = event.getY();
            double magnificationFactor = 5;

            if (tryShowKmcgColorBarTooltip(dataCanvas, mouseX, mouseY, data, isPolyploidView,
                    enableQuadrilateralSelection)) {
                return;
            }

            String marginalRegion = resolveKmcgMarginalHitRegion(mouseX, mouseY);
            if (marginalRegion != null) {
                int[] index = findKmcgDataIndexAt(mouseX, mouseY, data.size(), data.get(0).size(), false);
                if (index != null) {
                    showMarginalMatrixCellTooltip(dataCanvas, data, mouseX, mouseY, index[0], index[1],
                            marginalRegion, isPolyploidView, enableQuadrilateralSelection);
                }
            } else if (region5.contains(mouseX, mouseY)) {
                showRegion5Coordinates(dataCanvas, data, mouseX, mouseY, isPolyploidView);
                refreshKmcgHoverOverlays(dataCanvas, data, isPolyploidView, enableQuadrilateralSelection,
                        isMagnificationEnabled, mouseX, mouseY, magnificationFactor);
            } else {
                if (currentTooltip != null) {
                    currentTooltip.hide();
                }
                clearColorBarIndicator();
                refreshKmcgBaseView(dataCanvas, data, isPolyploidView, enableQuadrilateralSelection);
            }
        });

        dataCanvas.setOnMouseExited(event -> {
            if (currentTooltip != null) {
                currentTooltip.hide();
            }
            clearColorBarIndicator();
            refreshKmcgBaseView(dataCanvas, data, isPolyploidView, enableQuadrilateralSelection);
        });

        if (!enableQuadrilateralSelection) {
            dataCanvas.setOnMouseClicked(null);
            return;
        }

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
                    quadrilateralPoints.clear();
                    manualPoints.clear();
                }
                clearCanvas(dataCanvas);
                drawKMCGOnCanvas(dataCanvas,data);
                drawClickedPoints(dataCanvas);
                // 检查相交并绘制线段
                int pointCount = clickedPoints.size();
                if (pointCount < 2) return;
                // 添加一个标志变量来控制循环
                boolean shouldBreak = false;

                for (int i = 0; i < pointCount && !shouldBreak; i++) {
                    double[] p1 = clickedPoints.get(i);
                    double[] p2 = clickedPoints.get((i + 1) % pointCount);

                    for (int j = i + 1; j < pointCount && !shouldBreak; j++) {
                        // 跳过相邻线段（它们会在同一点相交，但不视为交叉）
                        if (j == (i + 1) % pointCount || j == (i - 1 + pointCount) % pointCount) {
                            continue;
                        }

                        double[] q1 = clickedPoints.get(j);
                        double[] q2 = clickedPoints.get((j + 1) % pointCount);

                        if (isIntersecting(p1, p2, q1, q2)) {
                            // 如果相交，则重绘所有内容
                            showAlert("Warning", "Line segments intersect, please redraw");
                            clearCanvas(dataCanvas);
                            drawKMCGOnCanvas(dataCanvas, data);
                            clickedPoints.clear();
                            shouldBreak = true;  // 设置标志以跳出所有循环
                            break;
                        }
                    }
                }
                if (clickedPoints.size() == 4) {
                    quadrilateralPoints.clear();
                    quadrilateralPoints.addAll(clickedPoints);
                    manualPoints.clear();
                    // 检查是否能形成有效的四边形
                    if (canFormQuadrilateral(quadrilateralPoints)) {
                        drawQuadrilateral(dataCanvas, quadrilateralPoints);

                        getCoordinatePoints();

                        // 将查询结果拼接成一个字符串，方便后续使用
                        String coordData = String.join(" ", resultData);
                        // 调用数据解析方法
                        List<Map.Entry<Character, Integer>> parsedData = Kmer_Processing.parseData(coordData);

                        Map<String, List<Integer>> storage = Kmer_Processing.initializeStorage(Kmer_Processing.NamesData(), Kmer_Processing.LengthsData());
                        Map<String, List<Integer>> updatedStorage = Kmer_Processing.updateStorage(Kmer_Processing.NamesData(), storage, parsedData);

                        int MaxValue = Kmer_Processing.getMaxValue(Kmer_Processing.NamesData(), storage, parsedData) + 1;
                        int getTotalValue = Kmer_Processing.getTotalValue(Kmer_Processing.NamesData(), storage, parsedData);
                        drawKmerCanvas(updatedStorage, MaxValue, getTotalValue);

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

    public static void showRegion5MagnifiedView(Canvas dataCanvas, double mouseX, double mouseY,
                                                List<List<Integer>> data, double magnificationFactor) {
        double max_value = 0.0;
        List<List<Double>> transformedData = processData(data);

        for (List<Double> row : transformedData) {
            for (Double value : row) {
                if (value != null) {
                    max_value = Math.max(max_value, value);
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

        // 初始放大区域的大小
        int baseRegionSize = 50;
        int halfRegionSize = baseRegionSize / 2;

        // 动态计算可用的放大区域范围
        int availableLeft = dataX;
        int availableRight = data.get(0).size() - 1 - dataX;
        int availableTop = dataY;
        int availableBottom = data.size() - 1 - dataY;

        // 根据可用空间调整区域大小
        halfRegionSize = Math.min(halfRegionSize,
                Math.min(Math.min(availableLeft, availableRight),
                        Math.min(availableTop, availableBottom)));

        // 如果靠近边缘，减小放大区域
        if (halfRegionSize < baseRegionSize / 2) {
            baseRegionSize = halfRegionSize * 2;
        }

        // 放大的区域边界
        int startX = Math.max(dataX - halfRegionSize, 0);
        int startY = Math.max(dataY - halfRegionSize, 0);
        int endX = Math.min(dataX + halfRegionSize, data.get(0).size() - 1);
        int endY = Math.min(dataY + halfRegionSize, data.size() - 1);

        // 放大区域的实际显示大小
        double magnifiedWidth = (endX - startX + 1) * magnificationFactor;
        double magnifiedHeight = (endY - startY + 1) * magnificationFactor;

        // 根据鼠标位置调整放大区域的显示位置
        double offsetX = mouseX + 20; // 默认显示在鼠标右下角
        double offsetY = mouseY + 20;

        // 确保放大区域不会超出画布的右边界
        if (offsetX + magnifiedWidth > dataCanvas.getWidth()) {
            offsetX = mouseX - magnifiedWidth - 20; // 显示在左侧
        }
        // 确保放大区域不会超出画布的左边界
        if (offsetX < 0) {
            offsetX = 0;
        }

        // 确保放大区域不会超出画布的下边界
        if (offsetY + magnifiedHeight > dataCanvas.getHeight()) {
            offsetY = mouseY - magnifiedHeight - 20; // 显示在上侧
        }
        // 确保放大区域不会超出画布的上边界
        if (offsetY < 0) {
            offsetY = 0;
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

        // 绘制放大区域的边框
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2);
        gc.strokeRect(offsetX, offsetY, magnifiedWidth, magnifiedHeight);

        // 计算实际中心点位置（基于鼠标指向的数据点）
        double centerX = offsetX + (dataX - startX) * magnificationFactor;
        double centerY = offsetY + (dataY - startY) * magnificationFactor;

        // 设置虚线样式
        gc.setLineDashes(5, 5);
        gc.setLineWidth(1);
        gc.setStroke(Color.WHITE);

        // 绘制竖直的虚线（通过实际中心点）
        gc.strokeLine(centerX, offsetY, centerX, offsetY + magnifiedHeight);

        // 绘制水平的虚线（通过实际中心点）
        gc.strokeLine(offsetX, centerY, offsetX + magnifiedWidth, centerY);

        // 重置虚线样式
        gc.setLineDashes(null);
    }

    //清除画布
    public static void clearCanvas(Canvas dataCanvas) {
        GraphicsContext gc = dataCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, dataCanvas.getWidth(), dataCanvas.getHeight());  // 清空整个画布
    }

    // 显示默认图片
    public static void showDefaultImage(Canvas dataCanvas) {
        if (dataCanvas == null) {
            return;
        }
        if (defaultKmcgImageCache == null) {
            var resource = KMCG_Processing.class.getResource("/com/apply/kmcg/image/default_image.png");
            if (resource == null) {
                return;
            }
            defaultKmcgImageCache = new Image(resource.toExternalForm(), false);
        }
        if (defaultKmcgImageCache.isError()) {
            return;
        }
        hasDrawnData = false;
        drawDefaultImageOnCanvas(dataCanvas, defaultKmcgImageCache);
    }

    private static void drawDefaultImageOnCanvas(Canvas dataCanvas, Image image) {
        GraphicsContext gc = dataCanvas.getGraphicsContext2D();
        double width = dataCanvas.getWidth();
        double height = dataCanvas.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        gc.clearRect(0, 0, width, height);
        gc.drawImage(image, 0, 0, width, height);
    }

    //四边形围住的坐标点的具体存入值 MainController.java的processPoints是手动输入的
    public static void getCoordinatePoints(){
        resultData.clear();
        pointsInside = getPointsInsideQuadrilateral(quadrilateralPoints,isPolyploidView);

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

    // 辅助方法：格式化数字单位（最多两位有效数字）
    public static String formatWithUnit(double value) {
        if (value >= 1_000_000) {
            double v = value / 1_000_000.0;
            return formatNumber(v) + "M";
        } else if (value >= 1_000) {
            double v = value / 1_000.0;
            return formatNumber(v) + "K";
        } else {
            return formatNumber(value);
        }
    }

    // 格式化一个区间，避免重复单位
    public static String formatRange(double start, double end) {
        String startStr = formatWithUnit(start);
        String endStr = formatWithUnit(end);

        // 取出单位部分
        String startUnit = startStr.replaceAll("[0-9.]", "");
        String endUnit = endStr.replaceAll("[0-9.]", "");

        // 单位相同 → 把单位放到最后
        if (!startUnit.isEmpty() && startUnit.equals(endUnit)) {
            String startVal = startStr.replace(startUnit, "");
            return startVal + " ~ " + endStr;  // 只保留最后的单位
        }

        // 单位不同 → 各自保留
        return startStr + " ~ " + endStr;
    }

    // 统一数字格式化逻辑（去掉多余0，最多保留两位小数）
    private static String formatNumber(double v) {
        if (v == (long) v) {
            return String.format("%d", (long) v); // 整数直接输出
        } else {
            return String.format("%.2f", v).replaceAll("\\.?0+$", "");
        }
    }

    static String formatKmerDisplayName(String name) {
        if (name.length() > 10) {
            return name.substring(0, 3) + "..." + name.substring(name.length() - 3);
        }
        return name;
    }

    static void computeKmerLayout(Map<String, List<Integer>> updatedStorage) {
        final double leftPadding = 10;
        final double nameBarGap = 12;
        final double maxFontSize = 18;
        final double minFontSize = 12;
        final double nameAreaMaxWidth = 240;
        final double nameInnerPadding = 2;
        final double rightMargin = 56;

        double maxTextWidthAtMax = 0;
        Font probeFont = Font.font("Verdana", maxFontSize);
        for (String rawName : updatedStorage.keySet()) {
            Text textProbe = new Text(formatKmerDisplayName(rawName));
            textProbe.setFont(probeFont);
            maxTextWidthAtMax = Math.max(maxTextWidthAtMax, textProbe.getLayoutBounds().getWidth());
        }
        if (updatedStorage.isEmpty()) {
            maxTextWidthAtMax = 80;
        }

        double fitWidth = Math.min(
                Math.max(maxTextWidthAtMax + nameInnerPadding, 72),
                nameAreaMaxWidth);
        double fontSize = maxFontSize;
        if (maxTextWidthAtMax > fitWidth - nameInnerPadding) {
            fontSize = Math.max(minFontSize,
                    maxFontSize * (fitWidth - nameInnerPadding) / maxTextWidthAtMax);
        }

        Font nameFont = Font.font("Verdana", fontSize);
        double maxTextWidth = 0;
        for (String rawName : updatedStorage.keySet()) {
            Text textProbe = new Text(formatKmerDisplayName(rawName));
            textProbe.setFont(nameFont);
            maxTextWidth = Math.max(maxTextWidth, textProbe.getLayoutBounds().getWidth());
        }
        if (updatedStorage.isEmpty()) {
            maxTextWidth = 80;
        }

        kmerLayout.leftPadding = leftPadding;
        kmerLayout.colorBarX = leftPadding;
        kmerLayout.nameAreaWidth = maxTextWidth + nameInnerPadding;
        kmerLayout.gap = kmerLayout.nameAreaWidth;
        kmerLayout.extra_spacing = leftPadding + maxTextWidth + nameBarGap;
        kmerLayout.nameFontSize = fontSize;
        kmerLayout.colorBarY = 10;
        kmerLayout.colorBarHeight = 40;
        kmerLayout.rectWidth = 5;
        kmerLayout.rectHeight = 25;
        kmerLayout.lineSpacing = kmerLayout.rectHeight + 2;
        kmerLayout.maxBlocksPerLine = 200;
        kmerLayout.colorBarWidth = kmerLayout.maxBlocksPerLine * kmerLayout.rectWidth;
        kmerLayout.rightMargin = rightMargin;
        kmerLayout.requiredCanvasWidth =
                kmerLayout.extra_spacing + kmerLayout.colorBarWidth + rightMargin;
    }

    private static void drawKmerBlockTooltip(GraphicsContext gc, Canvas canvas,
                                             String displayText, double mouseX, double mouseY) {
        Font tooltipFont = Font.font("Verdana", 14);
        Text measure = new Text(displayText);
        measure.setFont(tooltipFont);
        double textWidth = measure.getLayoutBounds().getWidth();
        double textHeight = measure.getLayoutBounds().getHeight();
        double padding = 8;
        double boxWidth = textWidth + padding * 2;
        double boxHeight = textHeight + padding * 2;

        double tooltipX = mouseX - boxWidth / 2;
        if (tooltipX < 4) {
            tooltipX = 4;
        }
        if (tooltipX + boxWidth > canvas.getWidth() - 4) {
            tooltipX = canvas.getWidth() - boxWidth - 4;
        }

        double tooltipY = mouseY - boxHeight - 6;
        if (tooltipY < 4) {
            tooltipY = mouseY + 8;
        }

        gc.setFill(new Color(0, 0, 0, 0.78));
        gc.fillRoundRect(tooltipX, tooltipY, boxWidth, boxHeight, 8, 8);
        gc.setFill(Color.WHITE);
        gc.setFont(tooltipFont);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText(displayText, tooltipX + (boxWidth - textWidth) / 2, tooltipY + boxHeight / 2);
        gc.setTextBaseline(VPos.BASELINE);
    }

    public static void drawKmerCanvas(Map<String, List<Integer>> updatedStorage, double maxValue, double totalValue) {

        if (kmerCanvas != null) {
            computeKmerLayout(updatedStorage);

            double canvasWidth = Math.max(1300, kmerLayout.requiredCanvasWidth);
            kmerCanvas.setWidth(canvasWidth);

            var gc = kmerCanvas.getGraphicsContext2D();
            gc.clearRect(0, 0, kmerCanvas.getWidth(), kmerCanvas.getHeight());

            double colorBarHeight = kmerLayout.colorBarHeight;
            double colorBarWidth = kmerLayout.colorBarWidth;
            double colorBarY = kmerLayout.colorBarY;
            double colorBarX = kmerLayout.colorBarX;
            double extra_spacing = kmerLayout.extra_spacing;
            int scaleCount = 5;
            double interval = maxValue / (scaleCount - 1);

            double rectWidth = kmerLayout.rectWidth;
            double rectHeight = kmerLayout.rectHeight;
            int maxBlocksPerLine = kmerLayout.maxBlocksPerLine;
            double rowSpacing = rectHeight + 20;
            double lineSpacing = kmerLayout.lineSpacing;
            double topOffset = 40 + 40 + 15;

            // 粗略计算高度（按换行数）
            double totalHeight = topOffset + updatedStorage.size() * rowSpacing * 2 + 2000;
            kmerCanvas.setHeight(totalHeight);

            // 绘制颜色条（与数据条块左对齐）
            for (int i = 0; i < colorBarWidth; i++) {
                double value = (double) i / colorBarWidth * maxValue;
                Color color = ColorUtils.getColorForValue(value, maxValue, "UD");
                gc.setFill(color);
                gc.fillRect(extra_spacing + i, colorBarY, 1, colorBarHeight);
            }

            // 绘制刻度值
            for (int i = 0; i < scaleCount; i++) {
                double value = i * interval;
                double xPos = extra_spacing + (i * colorBarWidth / (scaleCount - 1));
                gc.setFill(Color.BLACK);
                gc.setFont(Font.font("Verdana", 12));
                Text scaleText = new Text(String.format("%d", (int) value));
                scaleText.setFont(gc.getFont());
                double scaleWidth = scaleText.getLayoutBounds().getWidth();
                gc.fillText(String.format("%d", (int) value), xPos - scaleWidth / 2,
                        colorBarY + colorBarHeight + 15);
            }

            double yPos = colorBarY + colorBarHeight + 40;
            Font nameFont = Font.font("Verdana", kmerLayout.nameFontSize);

            // 绘制每条记录
            for (Map.Entry<String, List<Integer>> entry : updatedStorage.entrySet()) {
                String name = formatKmerDisplayName(entry.getKey());
                List<Integer> values = entry.getValue();

                gc.setFill(Color.BLACK);
                gc.setFont(nameFont);

                double nameX = colorBarX + 4;
                gc.fillText(name, nameX, yPos + 18);

                double xPosRect = extra_spacing;
                double currentY = yPos;

                for (int i = 0; i < values.size(); i++) {
                    int value = values.get(i);

                    gc.setFill(ColorUtils.getColorForValue(value, maxValue, "UD"));
                    gc.fillRect(xPosRect, currentY, rectWidth, rectHeight);

                    xPosRect += rectWidth;

                    if ((i + 1) % maxBlocksPerLine == 0) { // 换行
                        xPosRect = extra_spacing;
                        currentY += lineSpacing;
                    }
                }

                // 整个条目结束后跳到最后一行下面
                int extraLines = (values.size() - 1) / maxBlocksPerLine;
                yPos = yPos + (extraLines + 1) * (rectHeight + 5) + 20;
            }

            // 鼠标悬浮显示区间
            // 鼠标悬浮显示区间或分段名称
            kmerCanvas.setOnMouseMoved(event -> {
                double mouseX = event.getX();
                double mouseY = event.getY();
                boolean showTooltip = false;
                String displayText = "";

                gc.clearRect(0, 0, kmerCanvas.getWidth(), kmerCanvas.getHeight());
                drawKmerCanvas(updatedStorage, maxValue, totalValue);

                double currentYPos = kmerLayout.colorBarY + kmerLayout.colorBarHeight + 40;
                double blockRectWidth = kmerLayout.rectWidth;
                double blockRectHeight = kmerLayout.rectHeight;
                double blockLineSpacing = kmerLayout.lineSpacing;
                int blocksPerLine = kmerLayout.maxBlocksPerLine;
                double blocksStartX = kmerLayout.extra_spacing;
                Font rowNameFont = Font.font("Verdana", kmerLayout.nameFontSize);

                for (Map.Entry<String, List<Integer>> entry : updatedStorage.entrySet()) {
                    String fullName = entry.getKey();
                    List<Integer> values = entry.getValue();
                    double rowStartY = currentYPos;

                    Text nameMeasure = new Text(formatKmerDisplayName(fullName));
                    nameMeasure.setFont(rowNameFont);
                    double nameWidth = nameMeasure.getLayoutBounds().getWidth();
                    double nameLeft = kmerLayout.colorBarX;
                    double nameRight = nameLeft + nameWidth + 6;
                    double nameBottom = rowStartY + blockRectHeight + 6;
                    if (mouseX >= nameLeft && mouseX <= nameRight
                            && mouseY >= rowStartY && mouseY <= nameBottom) {
                        displayText = fullName;
                        showTooltip = true;
                        break;
                    }

                    String[] nameParts = fullName.contains(":") ? fullName.split(":") : null;

                    double currentXPos = blocksStartX;
                    double blockY = currentYPos;

                    for (int i = 0; i < values.size(); i++) {
                        double blockX = currentXPos;
                        double blockEndX = blockX + blockRectWidth;

                        if (mouseX >= blockX && mouseX <= blockEndX
                                && mouseY >= blockY && mouseY <= blockY + blockRectHeight) {
                            if (nameParts != null && i < nameParts.length) {
                                displayText = nameParts[i];
                            } else {
                                double start = i * MainController.Unitsize;
                                double end = (i + 1) * MainController.Unitsize;
                                displayText = formatRange(start, end);
                            }
                            showTooltip = true;
                            break;
                        }

                        currentXPos += blockRectWidth;
                        if ((i + 1) % blocksPerLine == 0) {
                            currentXPos = blocksStartX;
                            blockY += blockLineSpacing;
                        }
                    }

                    int extraLines = (values.size() - 1) / blocksPerLine;
                    currentYPos = currentYPos + (extraLines + 1) * (blockRectHeight + 5) + 20;

                    if (showTooltip) {
                        break;
                    }
                }

                if (showTooltip) {
                    drawKmerBlockTooltip(gc, kmerCanvas, displayText, mouseX, mouseY);
                }
            });

            // 鼠标点击显示详细值
            kmerCanvas.setOnMouseClicked(event -> {
                double mouseX = event.getX();
                double mouseY = event.getY();
                boolean isMouseInBlock = false;
                int blockValue = -1;
                int rowIndex = -1;
                int columnIndex = -1;
                String clickedName = "";

                double currentYPos = kmerLayout.colorBarY + kmerLayout.colorBarHeight + 40;
                int currentRowIndex = 0;
                double blockRectWidth = kmerLayout.rectWidth;
                double blockRectHeight = kmerLayout.rectHeight;
                double blockLineSpacing = kmerLayout.lineSpacing;
                int blocksPerLine = kmerLayout.maxBlocksPerLine;
                double blocksStartX = kmerLayout.extra_spacing;

                for (Map.Entry<String, List<Integer>> entry : updatedStorage.entrySet()) {
                    String name = entry.getKey();
                    List<Integer> values = entry.getValue();
                    double currentXPos = blocksStartX;
                    double blockY = currentYPos;

                    for (int i = 0; i < values.size(); i++) {
                        double blockX = currentXPos;
                        double blockEndX = blockX + blockRectWidth;

                        if (mouseX >= blockX && mouseX <= blockEndX &&
                                mouseY >= blockY && mouseY <= blockY + blockRectHeight) {
                            blockValue = values.get(i);
                            rowIndex = currentRowIndex;
                            columnIndex = i;
                            clickedName = name;
                            isMouseInBlock = true;
                            break;
                        }

                        currentXPos += blockRectWidth;
                        if ((i + 1) % blocksPerLine == 0) {
                            currentXPos = blocksStartX;
                            blockY += blockLineSpacing;
                        }
                    }

                    int extraLines = (values.size() - 1) / blocksPerLine;
                    currentYPos = currentYPos + (extraLines + 1) * (blockRectHeight + 5) + 20;

                    currentRowIndex++;
                    if (isMouseInBlock) break;
                }

                if (isMouseInBlock && blockValue != -1) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Detail description");
                    alert.setHeaderText(null);

                    double percentage = (blockValue / totalValue) * 100;

                    Kmer_Processing.processBlockIndex(rowIndex, columnIndex);
                    Map<String, Integer> result = Kmer_Processing.parseAndCount(rowIndex, columnIndex);

                    StringBuilder content = new StringBuilder();
                    double start = columnIndex * MainController.Unitsize;
                    double end = (columnIndex + 1) * MainController.Unitsize;

                    content.append(String.format("Row: %d, position: %s\nName: %s\nCount: %d (%s)\n\n",
                            rowIndex, formatRange(start, end),
                            clickedName, blockValue, String.format("%.3f%%", percentage)));

                    result.forEach((n, count) -> content.append(n).append(": ").append(count).append("\n"));

                    TextArea textArea = new TextArea(content.toString());
                    textArea.setEditable(false);
                    textArea.setWrapText(true);

                    ScrollPane scrollPane = new ScrollPane(textArea);
                    scrollPane.setFitToWidth(true);
                    scrollPane.setFitToHeight(true);
                    scrollPane.setPrefSize(400, 300);

                    alert.getDialogPane().setContent(scrollPane);
                    alert.getButtonTypes().setAll(new ButtonType("OK", ButtonBar.ButtonData.OK_DONE));
                    alert.showAndWait();
                }
            });
        }
    }

    public static List<List<Integer>> convertToPolyploid(List<List<Integer>> haploidData) {
        List<List<Integer>> polyploidData = new ArrayList<>();

        if (haploidData.size() != 302) {
            showAlert("Error", "Invalid data size for polyploid conversion");
            return haploidData;
        }

        // 1. 保留首行
        polyploidData.add(new ArrayList<>(haploidData.get(0)));

        // 2. 添加偶数行（第2,4,6,...300）
        for (int i = 1; i < 301; i += 2) {
            polyploidData.add(new ArrayList<>(haploidData.get(i)));
        }

        // 3. 添加奇数行（第3,5,7,...301）
        for (int i = 2; i < 301; i += 2) {
            polyploidData.add(new ArrayList<>(haploidData.get(i)));
        }

        // 4. 保留末行
        polyploidData.add(new ArrayList<>(haploidData.get(301)));

        return polyploidData;
    }

}
