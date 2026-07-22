package com.apply.kmcg;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.apply.kmcg.MainController.*;

public class Quality_Processing
{
    private static final List<AssemblySegmentHit> assemblySegmentHits = new ArrayList<>();
    private static Tooltip assemblyBarTooltip;
    private static boolean assemblyBarHoverInstalled;
    private static final double SCRIPT_SCALE = 0.78;
    private static final double SUPER_SCRIPT_RAISE = 0.36;
    private static final double SUB_SCRIPT_LOWER = 0.14;
    private static final Pattern QUALITY_METADATA_TOKEN_PATTERN =
            Pattern.compile("Ki|Km|K\\+|K-|M50");

    private static double textWidth(String text, Font font) {
        Text node = new Text(text);
        node.setFont(font);
        return node.getLayoutBounds().getWidth();
    }

    private static int scriptFontSize(int baseFontSize) {
        return Math.max(11, (int) Math.round(baseFontSize * SCRIPT_SCALE));
    }

    private static double measureKWithSuperscript(char sign, int baseFontSize, String suffix) {
        Font baseFont = Font.font("Verdana", baseFontSize);
        Font scriptFont = Font.font("Verdana", scriptFontSize(baseFontSize));
        double width = textWidth("K", baseFont) + textWidth(String.valueOf(sign), scriptFont) - 2;
        if (suffix != null && !suffix.isEmpty()) {
            width += textWidth(suffix, baseFont);
        }
        return width;
    }

    private static double drawKWithSuperscript(GraphicsContext gc, char sign, double x, double baselineY,
                                               int baseFontSize, String suffix, Color color) {
        Font baseFont = Font.font("Verdana", baseFontSize);
        Font scriptFont = Font.font("Verdana", scriptFontSize(baseFontSize));
        gc.setFill(color);
        gc.setFont(baseFont);
        gc.fillText("K", x, baselineY);
        double kWidth = textWidth("K", baseFont);
        gc.setFont(scriptFont);
        gc.fillText(String.valueOf(sign), x + kWidth - 1, baselineY - baseFontSize * SUPER_SCRIPT_RAISE);
        double signWidth = textWidth(String.valueOf(sign), scriptFont);
        double cursor = x + kWidth + signWidth - 2;
        if (suffix != null && !suffix.isEmpty()) {
            gc.setFont(baseFont);
            gc.fillText(suffix, cursor, baselineY);
            cursor += textWidth(suffix, baseFont);
        }
        return cursor - x;
    }

    private static double measureKWithSubscript(char letter, int baseFontSize, String suffix) {
        Font baseFont = Font.font("Verdana", baseFontSize);
        Font scriptFont = Font.font("Verdana", scriptFontSize(baseFontSize));
        double width = textWidth("K", baseFont) + textWidth(String.valueOf(letter), scriptFont) - 1;
        if (suffix != null && !suffix.isEmpty()) {
            width += textWidth(suffix, baseFont);
        }
        return width;
    }

    private static double drawKWithSubscript(GraphicsContext gc, char letter, double x, double baselineY,
                                             int baseFontSize, String suffix, Color color) {
        Font baseFont = Font.font("Verdana", baseFontSize);
        Font scriptFont = Font.font("Verdana", scriptFontSize(baseFontSize));
        gc.setFill(color);
        gc.setFont(baseFont);
        gc.fillText("K", x, baselineY);
        double kWidth = textWidth("K", baseFont);
        gc.setFont(scriptFont);
        gc.fillText(String.valueOf(letter), x + kWidth - 1, baselineY + baseFontSize * SUB_SCRIPT_LOWER);
        double letterWidth = textWidth(String.valueOf(letter), scriptFont);
        double cursor = x + kWidth + letterWidth - 1;
        if (suffix != null && !suffix.isEmpty()) {
            gc.setFont(baseFont);
            gc.fillText(suffix, cursor, baselineY);
            cursor += textWidth(suffix, baseFont);
        }
        return cursor - x;
    }

    private static double measureMWithSubscript50(int baseFontSize, String suffix) {
        Font baseFont = Font.font("Verdana", baseFontSize);
        Font scriptFont = Font.font("Verdana", scriptFontSize(baseFontSize));
        double width = textWidth("M", baseFont) + textWidth("50", scriptFont) - 1;
        if (suffix != null && !suffix.isEmpty()) {
            width += textWidth(suffix, baseFont);
        }
        return width;
    }

    private static double drawMWithSubscript50(GraphicsContext gc, double x, double baselineY,
                                               int baseFontSize, String suffix, Color color) {
        Font baseFont = Font.font("Verdana", baseFontSize);
        Font scriptFont = Font.font("Verdana", scriptFontSize(baseFontSize));
        gc.setFill(color);
        gc.setFont(baseFont);
        gc.fillText("M", x, baselineY);
        double mWidth = textWidth("M", baseFont);
        gc.setFont(scriptFont);
        gc.fillText("50", x + mWidth - 1, baselineY + baseFontSize * SUB_SCRIPT_LOWER);
        double subWidth = textWidth("50", scriptFont);
        double cursor = x + mWidth + subWidth - 1;
        if (suffix != null && !suffix.isEmpty()) {
            gc.setFont(baseFont);
            gc.fillText(suffix, cursor, baselineY);
            cursor += textWidth(suffix, baseFont);
        }
        return cursor - x;
    }

    private static double measureQualityMetadataToken(String token, int baseFontSize) {
        return switch (token) {
            case "Ki" -> measureKWithSubscript('i', baseFontSize, "");
            case "Km" -> measureKWithSubscript('m', baseFontSize, "");
            case "K+" -> measureKWithSuperscript('+', baseFontSize, "");
            case "K-" -> measureKWithSuperscript('-', baseFontSize, "");
            case "M50" -> measureMWithSubscript50(baseFontSize, "");
            default -> 0;
        };
    }

    private static double drawQualityMetadataToken(GraphicsContext gc, String token, double x, double y,
                                                   int baseFontSize, Color color) {
        return switch (token) {
            case "Ki" -> drawKWithSubscript(gc, 'i', x, y, baseFontSize, "", color);
            case "Km" -> drawKWithSubscript(gc, 'm', x, y, baseFontSize, "", color);
            case "K+" -> drawKWithSuperscript(gc, '+', x, y, baseFontSize, "", color);
            case "K-" -> drawKWithSuperscript(gc, '-', x, y, baseFontSize, "", color);
            case "M50" -> drawMWithSubscript50(gc, x, y, baseFontSize, "", color);
            default -> 0;
        };
    }

    private static double measureQualityMetadataLine(String line, int baseFontSize) {
        if (line == null || line.isEmpty()) {
            return 0;
        }
        Matcher matcher = QUALITY_METADATA_TOKEN_PATTERN.matcher(line);
        int last = 0;
        double width = 0;
        Font baseFont = Font.font("Verdana", baseFontSize);
        while (matcher.find()) {
            width += textWidth(line.substring(last, matcher.start()), baseFont);
            width += measureQualityMetadataToken(matcher.group(), baseFontSize);
            last = matcher.end();
        }
        width += textWidth(line.substring(last), baseFont);
        return width;
    }

    private static void drawQualityMetadataLine(GraphicsContext gc, String line, double x, double y,
                                                int baseFontSize) {
        if (line == null || line.isEmpty()) {
            return;
        }
        Color color = Color.BLACK;
        Matcher matcher = QUALITY_METADATA_TOKEN_PATTERN.matcher(line);
        int last = 0;
        double cursor = x;
        Font baseFont = Font.font("Verdana", baseFontSize);
        while (matcher.find()) {
            String before = line.substring(last, matcher.start());
            if (!before.isEmpty()) {
                gc.setFill(color);
                gc.setFont(baseFont);
                gc.fillText(before, cursor, y);
                cursor += textWidth(before, baseFont);
            }
            cursor += drawQualityMetadataToken(gc, matcher.group(), cursor, y, baseFontSize, color);
            last = matcher.end();
        }
        String rest = line.substring(last);
        if (!rest.isEmpty()) {
            gc.setFill(color);
            gc.setFont(baseFont);
            gc.fillText(rest, cursor, y);
        }
    }

    private static final class AssemblySegmentHit {
        final double x;
        final double y;
        final double width;
        final double height;
        final String tooltipText;

        AssemblySegmentHit(double x, double y, double width, double height, String tooltipText) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.tooltipText = tooltipText;
        }

        boolean contains(double px, double py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }

    /** Hover tooltips for the K+/middle/K- assembly bar canvas. */
    public static void installAssemblyBarHover(Canvas canvas) {
        if (assemblyBarHoverInstalled || canvas == null) {
            return;
        }
        assemblyBarHoverInstalled = true;
        assemblyBarTooltip = new Tooltip();
        assemblyBarTooltip.setShowDelay(Duration.millis(80));
        assemblyBarTooltip.setHideDelay(Duration.millis(0));

        canvas.addEventHandler(MouseEvent.MOUSE_MOVED, e -> {
            if (KMCG_Processing.areHoverTooltipsSuspended()) {
                if (assemblyBarTooltip != null) {
                    assemblyBarTooltip.hide();
                }
                return;
            }
            String text = findAssemblyTooltipText(e.getX(), e.getY());
            if (text != null && canvas.getScene() != null && canvas.getScene().getWindow() != null) {
                assemblyBarTooltip.setText(text);
                assemblyBarTooltip.show(canvas, e.getScreenX() + 14, e.getScreenY() + 14);
            } else {
                assemblyBarTooltip.hide();
            }
        });
        canvas.addEventHandler(MouseEvent.MOUSE_EXITED, e -> assemblyBarTooltip.hide());
    }

    private static String findAssemblyTooltipText(double x, double y) {
        for (AssemblySegmentHit hit : assemblySegmentHits) {
            if (hit.contains(x, y)) {
                return hit.tooltipText;
            }
        }
        return null;
    }

    /** Prefer the quality line that includes μ/σ or Row mean/sigma; fall back to the last parsed line. */
    private static String selectQualityIndicationForDisplay() {
        if (quality_indication.isEmpty()) {
            return "";
        }
        for (String indication : quality_indication) {
            if (indication.contains("μ") || indication.contains("µ")
                    || indication.contains("σ")
                    || indication.toLowerCase().contains("row1_mean")) {
                return indication;
            }
        }
        return quality_indication.get(quality_indication.size() - 1);
    }

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
        gc.setLineWidth(2);
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

        gc.setFont(Font.font("Verdana", 11));

        //x=0刻度值
        for (int x = 0; x <= colCount; x += 100) {

            double xPos = offsetX + x * CELL_WIDTH;
            gc.strokeLine(xPos, offsetY + totalHeight, xPos, offsetY + totalHeight + 5);
            // 自动居中文本
            String label = Integer.toString(x);

            Text textNode = new Text(label);
            textNode.setFont(gc.getFont());
            double textWidth = textNode.getLayoutBounds().getWidth();

            // 居中 → xPos - textWidth/2
            gc.fillText(label, xPos - textWidth / 2, offsetY + totalHeight + 20);
        }
        if (colCount % 100 != 0) {
            double xPos = offsetX + colCount * CELL_WIDTH;
            gc.strokeLine(xPos, offsetY + totalHeight, xPos, offsetY + totalHeight + 5);
            gc.fillText(Integer.toString(colCount), xPos-10, offsetY + totalHeight + 20);
        }

        gc.setFont(Font.font("Verdana", 11));
        for (int y = 0; y < rowCount; y++) {
            double yPos;
            if (y <= NORMAL_SECTION_END) {
                int normalIndex = NORMAL_SECTION_END - y;
                yPos = offsetY + totalHeight - normalHeight + normalIndex * CELL_HEIGHT;

                // 将 y==512 显示 2^9
                if (y == NORMAL_SECTION_END) {
                    gc.strokeLine(offsetX - 5, yPos, offsetX, yPos);
                    gc.fillText("2", offsetX - 27, yPos + 4);
                    gc.setFont(Font.font("Verdana", 7));
                    gc.fillText("9", offsetX - 20, yPos - 5);
                    gc.setFont(Font.font("Verdana", 11));
                } else if (y % 100 == 0) {
                    gc.strokeLine(offsetX - 5, yPos, offsetX, yPos);
//                    gc.fillText(Integer.toString(y), offsetX - 30, yPos + 4);
                    String label = Integer.toString(y);
                    Text textNode = new Text(label);
                    textNode.setFont(Font.font("Verdana", 11));
                    double textWidth = textNode.getLayoutBounds().getWidth();
                    gc.fillText(label, offsetX - 10 - textWidth, yPos + 4);

                }
            } else {
                int stretchIndex = y - NORMAL_SECTION_END - 1;
                yPos = offsetY + totalHeight - normalHeight - (stretchIndex + 1) * CELL_HEIGHT * MAGNIFICATION;
                gc.strokeLine(offsetX - 5, yPos, offsetX, yPos);
                int exponent = 9 + (y - NORMAL_SECTION_END);
                gc.fillText("2", offsetX - 27, yPos + 4);
                gc.setFont(Font.font("Verdana", 7));
                gc.fillText(String.valueOf(exponent), offsetX - 20, yPos - 5);
                gc.setFont(Font.font("Verdana", 11));
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

        // 起点和步长（紧凑布局）
        double offsetX = 0;
        double xStart = 50 + offsetX;
        double plotTop = 28;
        double plotBottom = canvasHeight - 34;
        double yStart = plotBottom;

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
        gc.strokeLine(xStart, yStart, xStart, plotTop);

        // 绘制Y轴刻度线和标注，设置字体并绘制 Y 轴刻度（右对齐）
        Font font = new Font("Verdana", 11);
        gc.setFont(font);
        //y轴刻度值颜色
        gc.setFill(Color.BLACK);
        double plotHeight = yStart - plotTop;
        for (int i = 0; i <= 10; i++) {
            double yPos = yStart - (i * plotHeight / 10);
            gc.strokeLine(xStart - 5, yPos, xStart, yPos);

            String label = String.valueOf(i * 10);

            // 使用 Text 来测量宽度
            Text textNode = new Text(label);
            textNode.setFont(font);  // 用同样字体设置
            double textWidth = textNode.getLayoutBounds().getWidth();

            gc.fillText(label, xStart - 10 - textWidth, yPos + 4);
        }

        gc.fillText("(%)", xStart - 10, plotTop - 4);
        // 绘制折线
        gc.setStroke(Color.CORNFLOWERBLUE);  // 设置折线的颜色为蓝色
        gc.setLineWidth(2);  // 设置更粗的线条

        // 获取 brokenlineData  Y 轴数据
        List<Double> dataPoints = brokenlineData.get(0);
        boolean firstIntersectionFound = false;
        // 画折线
        for (int i = 1; i < dataPoints.size(); i++) {  // 从 i = 1 开始
            double x1 = xStart + (i - 1) * xStep;  // 上一个点的 X 坐标
            double y1 = yStart - (dataPoints.get(i - 1) * plotHeight / yMax);  // 上一个点的 Y 坐标

            double x2 = xStart + i * xStep;  // 当前点的 X 坐标
            double y2 = yStart - (dataPoints.get(i) * plotHeight / yMax);  // 当前点的 Y 坐标

            // 画折线
            gc.strokeLine(x1, y1, x2, y2);
            // 检查是否跨过 y = 50
            double yTarget = yStart - (50 * plotHeight / yMax); // 计算 y=50 对应的 y 坐标
            if (!firstIntersectionFound && ((y1 < yTarget && y2 > yTarget) || (y1 > yTarget && y2 < yTarget))) {
                double intersectX = x1 + (x2 - x1) * (yTarget - y1) / (y2 - y1);
                double intersectY = yTarget;
                intersectionPoints.add(new double[]{intersectX, intersectY, i});  // 记录交点及索引
                firstIntersectionFound = true;  // 只记录第一个交点
            }
        }
        gc.setFont(new Font("Verdana", 12));
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
            gc.fillText(String.valueOf(index), intersectX - 10, yStart + 11);  // 在x轴下方标注x值
        }
        // 在X轴上标注每1个数据点和每50个数据点的X值
        gc.setFont(new Font("Verdana", 11));
        gc.setFill(Color.BLACK);
        for (int i = 0; i < dataPoints.size(); i++) {  // 从 i = 0 开始
            if (i == 0 || i % 50 == 0) {  // 每个数据点和每50个数据点显示一次
                gc.setStroke(Color.BLACK);
                double x = xStart + i * xStep;
                String label = String.valueOf(i);

                Text textNode = new Text(label);
                textNode.setFont(gc.getFont());
                double textWidth = textNode.getLayoutBounds().getWidth();

                gc.fillText(label, x - textWidth / 2, yStart + 20);
                gc.strokeLine(x, yStart, x, yStart + 5);

            }
        }

        if (!quality_indication.isEmpty() && !intersectionPoints.isEmpty()) {
            String qualityIndication = selectQualityIndicationForDisplay();
            int index = (int) intersectionPoints.get(0)[2];
            String m50Text = "M50=" + index;

            int metadataFontSize = 16;
            gc.setFill(Color.BLACK);

            // 将 qualityIndication 按照 "\t" 分割为多行
            String[] lines = qualityIndication.split("\t");

            double maxTextWidth = measureQualityMetadataLine(m50Text, metadataFontSize);
            for (String line : lines) {
                maxTextWidth = Math.max(maxTextWidth, measureQualityMetadataLine(line, metadataFontSize));
            }
            double paddingRight = 10;
            double x_text = canvasWidth - maxTextWidth - paddingRight;
            double y_text = 45;  //信息高度

            // 分行绘制 qualityIndication
            for (int i = 0; i < lines.length; i++) {
                drawQualityMetadataLine(gc, lines[i], x_text - 250, y_text + 25 * i, metadataFontSize);
            }

            // 绘制 M50 信息在最后一行之后
            drawQualityMetadataLine(gc, m50Text, x_text - 250, y_text + 25 * lines.length, metadataFontSize);
        }

        // 画虚线 y = 50 对应的位置
        double yPositionFor50 = yStart - (50 * plotHeight / yMax);
        gc.setStroke(Color.GRAY);
        gc.setLineDashes(5, 5);  // 设置虚线样式
        gc.strokeLine(xStart, yPositionFor50, canvasWidth - 50 + 500, yPositionFor50);  // 画虚线

        gc.setLineDashes(0);  // 空数组表示实线
    }


    public static void drawQualityFittingCanvas(Canvas canvas, List<List<Integer>> targetData, int rowIndex) {
        if (targetData == null || targetData.isEmpty()) return;
        if (rowIndex < 0 || rowIndex >= targetData.size()) return;

        List<Integer> rowData = targetData.get(rowIndex);
        drawQualityBarChart(canvas, rowData, rowIndex);
    }

    /** Draw K+/middle/K- assembly bar in the dedicated bottom canvas. */
    public static void drawQualityAssemblyCanvas(Canvas canvas) {
        if (canvas == null || canvas.getWidth() <= 0 || canvas.getHeight() <= 0) {
            return;
        }
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        assemblySegmentHits.clear();

        if (!hasAssemblyBarData()) {
            return;
        }

        double canvasWidth = canvas.getWidth();
        double marginLeft = 50;
        double marginRight = 20;
        double barLeft = marginLeft;
        double barRight = canvasWidth - marginRight - 2;
        drawAssemblySegmentBar(gc, barLeft, barRight, canvas.getHeight());
    }


    public static void drawQualityBarChart(Canvas canvas, List<Integer> rowData, int rowIndex) {
        if (rowData == null || rowData.isEmpty() || canvas.getWidth() <= 0 || canvas.getHeight() <= 0) {
            return;
        }

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        double canvasWidth = canvas.getWidth();
        double canvasHeight = canvas.getHeight();

        int barCount = rowData.size();
        double maxValue = rowData.stream()
                .filter(Objects::nonNull)
                .mapToInt(v -> v)
                .max()
                .orElse(1);

        // ========================
        // Layout
        // ========================
        double marginLeft = 50;
        double sliderReserve = 54;
        double marginBottom = 24 + sliderReserve;
        double marginTop = 14;
        double marginRight = 20;

        double chartWidth = canvasWidth - marginLeft - marginRight;
        double chartHeight = canvasHeight - marginTop - marginBottom;
        double yCompressFactor = 0.78;
        double effectiveChartHeight = chartHeight * yCompressFactor;
        double barWidth = chartWidth / barCount;

        gc.setFont(Font.font("Verdana", 11));

        // Axes
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(2.0);

        gc.strokeLine(marginLeft, marginTop, marginLeft, canvasHeight - marginBottom);
        gc.strokeLine(marginLeft, canvasHeight - marginBottom,
                canvasWidth - marginRight, canvasHeight - marginBottom);

        // Y ticks
        gc.setFill(Color.BLACK);
        int yTickCount = 5;
        for (int i = 0; i <= yTickCount; i++) {
            double value = maxValue * i / yTickCount;
            double y = canvasHeight - marginBottom - (value / maxValue) * effectiveChartHeight;

            gc.strokeLine(marginLeft - 5, y, marginLeft, y);

            String label = formatYTickLabel(value);
            Text textNode = new Text(label);
            textNode.setFont(gc.getFont());

            double textWidth = textNode.getLayoutBounds().getWidth();
            double textHeight = textNode.getLayoutBounds().getHeight();

            gc.fillText(label,
                    marginLeft - 10 - textWidth,
                    y + textHeight / 4);
        }

        // Bars
        gc.setFill(Color.LIGHTBLUE);
        for (int i = 0; i < barCount; i++) {
            Integer value = rowData.get(i);
            if (value == null) continue;

            double scaledHeight = (value / maxValue) * effectiveChartHeight;
            double x = marginLeft + i * barWidth;
            double y = canvasHeight - marginBottom - scaledHeight;

            gc.fillRect(x, y, barWidth * 0.8, scaledHeight);
        }

        // X ticks
        int xTickInterval = Math.max(1, barCount / 10);
        gc.setFill(Color.BLACK);
        for (int i = 0; i < barCount; i += xTickInterval) {
            double x = marginLeft + i * barWidth + barWidth * 0.4;
            gc.strokeLine(x, canvasHeight - marginBottom,
                    x, canvasHeight - marginBottom + 5);

            String label = String.valueOf(i);
            Text textNode = new Text(label);
            textNode.setFont(gc.getFont());
            double textWidth = textNode.getLayoutBounds().getWidth();

            gc.fillText(label,
                    x - textWidth / 2,
                    canvasHeight - marginBottom + 15 + textNode.getLayoutBounds().getHeight() / 2);
        }

        // μ from Row1_Mean; σ = Row1_Sigma / sqrt(row) where row is the slider index
        double mu;
        double row1Sigma = 1.0;
        double[] row1Params = qualityGaussianByRow.get(0);
        if (row1Params != null && row1Params.length >= 1) {
            mu = row1Params[0];
            if (row1Params.length >= 2) {
                row1Sigma = row1Params[1];
            }
        } else {
            double[] estimated = estimateGaussianParams(rowData);
            mu = estimated[0];
            row1Sigma = estimated[1];
        }
        double sigma = rowIndex > 0 ? row1Sigma / Math.sqrt(rowIndex) : 0;

        double sum = rowData.stream()
                .filter(Objects::nonNull)
                .mapToDouble(v -> v)
                .sum();

        if (rowIndex != 0 && sum > 0 && sigma > 0) {
            gc.setStroke(Color.RED);
            gc.setLineWidth(2.0);
            gc.setLineDashes(8, 6);

            double prevX = -1, prevY = -1;

            for (int i = 0; i < barCount; i++) {
                double pdf = normalPDF(i, mu, sigma);
                double modelValue = pdf * sum;

                double x = marginLeft + i * barWidth + barWidth * 0.4;
                double y = canvasHeight - marginBottom
                        - (modelValue / maxValue) * effectiveChartHeight;

                if (prevX >= 0) {
                    gc.strokeLine(prevX, prevY, x, y);
                }
                prevX = x;
                prevY = y;
            }
            gc.setLineDashes(null);
        }

        // Curve tails sit on the baseline; redraw x-axis in black so it is not tinted red
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(2.0);
        gc.setLineDashes(null);
        gc.strokeLine(marginLeft, canvasHeight - marginBottom,
                canvasWidth - marginRight, canvasHeight - marginBottom);

        double textRightX = canvasWidth - marginRight - 345;
        double statsBaseY = marginTop + effectiveChartHeight * 0.20;

        if (rowIndex != 0) {
            gc.setFill(Color.BLACK);
            gc.setFont(Font.font("Verdana", 16));

            boolean hasOverlapData = !brokenlineData.isEmpty()
                    && rowIndex >= 0
                    && rowIndex < brokenlineData.get(0).size();

            if (hasOverlapData) {
                String muText = String.format("μ = %.2f", mu);
                String sigmaText = String.format("σ = %.2f", sigma);

                Text muNode = new Text(muText);
                muNode.setFont(gc.getFont());
                double muHeight = muNode.getLayoutBounds().getHeight();

                Text sigmaNode = new Text(sigmaText);
                sigmaNode.setFont(gc.getFont());
                double sigmaHeight = sigmaNode.getLayoutBounds().getHeight();

                gc.fillText(muText, textRightX, statsBaseY);
                gc.fillText(sigmaText, textRightX, statsBaseY + muHeight + 6);
                gc.fillText(
                        String.format("Overlap = %.2f%%", brokenlineData.get(0).get(rowIndex)),
                        textRightX,
                        statsBaseY + muHeight + sigmaHeight + 12);
            } else {
                gc.fillText("No data", textRightX, statsBaseY);
            }
        }
    }

    private static String formatAssemblyPercentValue(double value) {
        String formatted = String.format(Locale.US, "%.6f", value);
        if (formatted.contains(".")) {
            formatted = formatted.replaceAll("0+$", "");
            if (formatted.endsWith(".")) {
                formatted = formatted.substring(0, formatted.length() - 1);
            }
        }
        return formatted;
    }

    private static Double resolveKPlusPercent() {
        Double kPlus = qualityKPlus;
        Pattern kPlusPattern = Pattern.compile("K\\+\\s*[:=]\\s*([\\d.]+)", Pattern.CASE_INSENSITIVE);
        if (kPlus == null) {
            for (String line : qualityMetadataRawLines) {
                Matcher m = kPlusPattern.matcher(line);
                if (m.find()) {
                    kPlus = Double.parseDouble(m.group(1));
                    break;
                }
            }
        }
        return kPlus;
    }

    private static Double resolveKMinusPercent() {
        Double kMinus = qualityKMinus;
        Pattern kMinusPattern = Pattern.compile("K[-−–]\\s*[:=]\\s*([\\d.]+)", Pattern.CASE_INSENSITIVE);
        if (kMinus == null) {
            for (String line : qualityMetadataRawLines) {
                Matcher m = kMinusPattern.matcher(line);
                if (m.find()) {
                    kMinus = Double.parseDouble(m.group(1));
                    break;
                }
            }
        }
        return kMinus;
    }

    private static double[] resolveAssemblyFractions() {
        Double kPlusPercent = resolveKPlusPercent();
        Double kMinusPercent = resolveKMinusPercent();
        if (kPlusPercent == null || kMinusPercent == null) {
            return null;
        }
        if (kPlusPercent < 0 || kMinusPercent < 0 || kPlusPercent + kMinusPercent > 100.0 + 1e-9) {
            return null;
        }
        return new double[]{kPlusPercent / 100.0, kMinusPercent / 100.0};
    }

    private static boolean hasAssemblyBarData() {
        return resolveAssemblyFractions() != null;
    }

    public static boolean hasQualityAssemblyBarData() {
        return hasAssemblyBarData();
    }

    private static void drawAssemblySegmentBar(GraphicsContext gc, double barLeft, double barRight,
                                               double canvasHeight) {
        double[] fractions = resolveAssemblyFractions();
        if (fractions == null) {
            return;
        }
        double kPlusFraction = fractions[0];
        double kMinusFraction = fractions[1];
        double kPlusPercent = resolveKPlusPercent();
        double kMinusPercent = resolveKMinusPercent();
        double middle = 1.0 - kPlusFraction - kMinusFraction;

        double barWidth = barRight - barLeft;
        double barHeight = 28;
        double bottomLabelZone = 16;
        double bottomBracketZone = 12;
        double barY = canvasHeight - bottomLabelZone - bottomBracketZone - barHeight;
        double wPlus = barWidth * kPlusFraction;
        double wMid = barWidth * middle;
        double wMinus = barWidth * kMinusFraction;
        double percentLabelY = 26;
        double hitTop = 4;
        double hitHeight = barY + barHeight + bottomBracketZone + bottomLabelZone - hitTop;

        Color cPlus = Color.web("#F0988C");
        Color cMid = Color.web("#72C47A");
        Color cMinus = Color.web("#7EB8E8");

        gc.setFill(cPlus);
        gc.fillRoundRect(barLeft, barY, wPlus, barHeight, 5, 5);
        gc.setFill(cMid);
        gc.fillRect(barLeft + wPlus, barY, wMid, barHeight);
        gc.setFill(cMinus);
        gc.fillRoundRect(barLeft + wPlus + wMid, barY, wMinus, barHeight, 5, 5);

        gc.setStroke(Color.web("#3D3D3D"));
        gc.setLineWidth(1.3);
        gc.strokeRoundRect(barLeft, barY, barWidth, barHeight, 5, 5);
        if (wPlus > 0.5) {
            gc.strokeLine(barLeft + wPlus, barY + 2, barLeft + wPlus, barY + barHeight - 2);
        }
        if (wMinus > 0.5) {
            gc.strokeLine(barLeft + wPlus + wMid, barY + 2, barLeft + wPlus + wMid, barY + barHeight - 2);
        }

        gc.setFill(Color.web("#1F1F1F"));
        drawCenteredKWithSuperscript(gc, '+', barLeft, wPlus, barY, barHeight);
        drawInsideSegmentLabel(gc, "Correctly assembled Kmers", barLeft + wPlus, wMid, barY, barHeight);
        drawCenteredKWithSuperscript(gc, '-', barLeft + wPlus + wMid, wMinus, barY, barHeight);

        drawHorizontalBracket(gc, barLeft, barLeft + wPlus + wMid, barY + barHeight + 3, false,
                "Current assembly");
        drawHorizontalBracket(gc, barLeft + wPlus, barRight, barY - 3, true,
                "Expected perfect assembly");

        drawKPercentLabel(gc, '+', kPlusPercent, percentLabelY, barLeft, true);
        drawKPercentLabel(gc, '-', kMinusPercent, percentLabelY, barRight, false);

        registerAssemblySegmentHit(barLeft, hitTop, wPlus, hitHeight,
                String.format("K+: %s%%", formatAssemblyPercentValue(kPlusPercent)));
        registerAssemblySegmentHit(barLeft + wPlus, hitTop, wMid, hitHeight,
                String.format("Correctly assembled Kmers: %.2f%%", middle * 100));
        registerAssemblySegmentHit(barLeft + wPlus + wMid, hitTop, wMinus, hitHeight,
                String.format("K-: %s%%", formatAssemblyPercentValue(kMinusPercent)));
    }

    private static void drawCenteredKWithSuperscript(GraphicsContext gc, char sign, double segX, double segWidth,
                                                     double barY, double barHeight) {
        if (segWidth < 4) {
            return;
        }
        Color color = Color.web("#1F1F1F");
        for (int fontSize = 12; fontSize >= 8; fontSize--) {
            double totalWidth = measureKWithSuperscript(sign, fontSize, "");
            if (totalWidth <= segWidth - 4) {
                Font baseFont = Font.font("Verdana", fontSize);
                Text probe = new Text("K");
                probe.setFont(baseFont);
                double textHeight = probe.getLayoutBounds().getHeight();
                double baselineY = barY + (barHeight + textHeight) / 2 - 2;
                double x = segX + (segWidth - totalWidth) / 2;
                drawKWithSuperscript(gc, sign, x, baselineY, fontSize, "", color);
                return;
            }
        }
        Font baseFont = Font.font("Verdana", 8);
        double totalWidth = measureKWithSuperscript(sign, 8, "");
        Text probe = new Text("K");
        probe.setFont(baseFont);
        double textHeight = probe.getLayoutBounds().getHeight();
        double baselineY = barY + (barHeight + textHeight) / 2 - 2;
        double x = segX + Math.max(1, (segWidth - totalWidth) / 2);
        drawKWithSuperscript(gc, sign, x, baselineY, 8, "", color);
    }

    private static void drawKPercentLabel(GraphicsContext gc, char sign, double percentValue, double labelY,
                                          double anchorX, boolean leftAlign) {
        int baseSize = 16;
        String suffix = String.format(" = %s%%", formatAssemblyPercentValue(percentValue));
        Color color = Color.web("#333333");
        double width = measureKWithSuperscript(sign, baseSize, suffix);
        double x = leftAlign ? anchorX : anchorX - width;
        drawKWithSuperscript(gc, sign, x, labelY, baseSize, suffix, color);
    }

    private static void registerAssemblySegmentHit(double x, double y, double width, double height,
                                                   String tooltipText) {
        if (width <= 0.5) {
            return;
        }
        assemblySegmentHits.add(new AssemblySegmentHit(x, y, width, height, tooltipText));
    }

    private static void drawInsideSegmentLabel(GraphicsContext gc, String label, double x, double width,
                                               double barY, double barHeight) {
        if (width < 4) {
            return;
        }
        for (int fontSize = 12; fontSize >= 8; fontSize--) {
            Font font = Font.font("Verdana", fontSize);
            gc.setFont(font);
            Text textNode = new Text(label);
            textNode.setFont(font);
            double textWidth = textNode.getLayoutBounds().getWidth();
            if (textWidth <= width - 4) {
                double textHeight = textNode.getLayoutBounds().getHeight();
                gc.fillText(label, x + (width - textWidth) / 2,
                        barY + (barHeight + textHeight) / 2 - 2);
                return;
            }
        }
        Font font = Font.font("Verdana", 8);
        gc.setFont(font);
        Text textNode = new Text(label);
        textNode.setFont(font);
        double textWidth = textNode.getLayoutBounds().getWidth();
        double textHeight = textNode.getLayoutBounds().getHeight();
        gc.fillText(label, x + Math.max(1, (width - textWidth) / 2),
                barY + (barHeight + textHeight) / 2 - 2);
    }

    private static void drawHorizontalBracket(GraphicsContext gc, double x1, double x2, double y,
                                              boolean above, String label) {
        if (x2 - x1 < 12) {
            return;
        }
        double hook = 7;
        gc.setStroke(Color.web("#555555"));
        gc.setLineWidth(1.0);
        gc.setLineDashes(null);

        if (above) {
            gc.strokeLine(x1, y, x1, y - hook);
            gc.strokeLine(x1, y - hook, x2, y - hook);
            gc.strokeLine(x2, y - hook, x2, y);
        } else {
            gc.strokeLine(x1, y, x1, y + hook);
            gc.strokeLine(x1, y + hook, x2, y + hook);
            gc.strokeLine(x2, y + hook, x2, y);
        }

        gc.setFill(Color.web("#333333"));
        gc.setFont(Font.font("Verdana", 10));
        Text textNode = new Text(label);
        textNode.setFont(gc.getFont());
        double textWidth = textNode.getLayoutBounds().getWidth();
        double midX = (x1 + x2) / 2;
        if (above) {
            gc.fillText(label, midX - textWidth / 2, y - hook - 5);
        } else {
            gc.fillText(label, midX - textWidth / 2, y + hook + 12);
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

    //拟合曲线的函数
    private static double normalPDF(double x, double mean, double sigma) {
        if (sigma <= 1e-9) return 0.0;
        return (1.0 / (sigma * Math.sqrt(2.0 * Math.PI)))
                * Math.exp(-0.5 * Math.pow((x - mean) / sigma, 2));
    }
    private static double[] estimateGaussianParams(List<Integer> data) {
        int n = data.size();
        if (n == 0) return new double[]{0, 1};

        // 找主峰
        int peakIdx = 0;
        int peakVal = 0;
        for (int i = 0; i < n; i++) {
            Integer v = data.get(i);
            if (v != null && v > peakVal) {
                peakVal = v;
                peakIdx = i;
            }
        }

        if (peakVal == 0) return new double[]{peakIdx, 1};

        double halfMax = peakVal * 0.5;

        // 左 FWHM
        double left = peakIdx;
        for (int i = peakIdx; i >= 0; i--) {
            if (data.get(i) <= halfMax) {
                left = i;
                break;
            }
        }

        // 右 FWHM
        double right = peakIdx;
        for (int i = peakIdx; i < n; i++) {
            if (data.get(i) <= halfMax) {
                right = i;
                break;
            }
        }

        double fwhm = Math.max(1.0, right - left);
        double sigma = fwhm / 2.35482004503; // 常量一致

        // Poisson
        if (sigma > peakIdx * 0.8 && peakIdx > 0) {
            sigma = Math.sqrt(peakIdx);
        }

        return new double[]{peakIdx, sigma};
    }



}
