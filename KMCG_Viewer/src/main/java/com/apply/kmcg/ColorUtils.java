package com.apply.kmcg;

import javafx.scene.paint.Color;

public class ColorUtils {

    // 根据 ctype 获取颜色方案
    public static int[][] getColorScheme(String ctype) {
        switch (ctype) {
            case "BW": // black-white
                return new int[][]{
                        {0, 255, 255}, {0}, {0, 0, 0}, {50}, {128, 128, 128}, {100}, {255, 255, 255}, {101}, {255, 0, 255}, {255, 0, 255}
                };
            case "C1": // white-red-black
                return new int[][]{
                        {255, 255, 255}, {3}, {255, 255, 255}, {50}, {255, 0, 0}, {100}, {0, 0, 0}, {101}, {0, 0, 0}, {0, 0, 0}
                };
            case "C2": // yellow-blue-black
                return new int[][]{
                        {255, 255, 200}, {10}, {255, 255, 200}, {60}, {0, 255, 0}, {100}, {0, 0, 0}, {101}, {0, 0, 0}, {0, 0, 0}
                };
            case "HM":
                return new int[][]{
                        {0, 0, 0}, {0}, {0, 0, 0}, {30}, {200, 0, 0}, {70}, {200, 200, 100}, {100}, {240, 240, 255}, {255, 0, 255}
                };
            case "UD":
                return new int[][]{
                        {0, 0, 0}, {0}, {0, 0, 255}, {30}, {255, 255, 0}, {70}, {255, 165, 0}, {100}, {255, 0, 0}
                };
            case "UD2":
                return new int[][]{
                        {0, 0, 0}, {0},            // 0-1% 黑色
                        {0, 0, 255}, {15},         // 2%-15% 蓝色
                        {0, 0, 255}, {20},         // 21%-34% 蓝色 → 黄色
                        {255, 255, 0}, {35},       // 34%-47% 黄色 → 橙色
                        {255, 165, 0}, {50},
                        {255, 0, 0}, {60},       // 50%-60% 橙色 → 红色
                        {255, 0, 0}, {101}         // >60% 红色
                };

            default:
                throw new IllegalArgumentException("Invalid color scheme type: " + ctype);
        }
    }



    public static Color getColorForValue(double count, double maxres, String ctype) {
        int[][] Scheme = getColorScheme(ctype);
        int percentage = (int) (count * 100.0 / maxres);

        if (ctype.equals("UD2")) {
            if (percentage <= Scheme[1][0]) { // 0-1% 黑色
                return Color.rgb(Scheme[0][0], Scheme[0][1], Scheme[0][2]);
            } else if (percentage <= Scheme[3][0]) { // 2%-15% 蓝色
                return Color.rgb(Scheme[2][0], Scheme[2][1], Scheme[2][2]);
            } else if (percentage <= Scheme[5][0]) { // 15%-20% 蓝 → 黄
                return interpolateColor(Scheme[2], Scheme[4], percentage, Scheme[3][0], Scheme[5][0]);
            } else if (percentage <= Scheme[7][0]) { // 20%-42% 黄 → 橙
                return interpolateColor(Scheme[4], Scheme[6], percentage, Scheme[5][0], Scheme[7][0]);
            } else if (percentage <= Scheme[9][0]) { // 42%-55% 橙 → 红
                return interpolateColor(Scheme[6], Scheme[8], percentage, Scheme[7][0], Scheme[9][0]);
            } else if (percentage <= Scheme[11][0]) { // 55%-60% 橙 → 红
                return interpolateColor(Scheme[8], Scheme[10], percentage, Scheme[9][0], Scheme[11][0]);
            } else { // >60% 红色
                return Color.rgb(Scheme[12][0], Scheme[12][1], Scheme[12][2]);
            }
        }

        else {
            // 原有逻辑保持不变
            if (percentage <= Scheme[1][0]) {
                return Color.rgb(Scheme[0][0], Scheme[0][1], Scheme[0][2]);
            } else if (percentage >= Scheme[7][0]) {
                return Color.rgb(Scheme[8][0], Scheme[8][1], Scheme[8][2]);
            } else if (percentage <= Scheme[3][0]) {
                return interpolateColor(Scheme[2], Scheme[4], percentage, Scheme[1][0], Scheme[3][0]);
            } else if (percentage >= Scheme[5][0]) {
                return interpolateColor(Scheme[6], Scheme[8], percentage, Scheme[5][0], Scheme[7][0]);
            } else {
                return interpolateColor(Scheme[4], Scheme[6], percentage, Scheme[3][0], Scheme[5][0]);
            }
        }
    }


    public static Color getUD2ColorWithCustomRange(double count, double maxres,
                                                   double minPercent, double maxPercent) {
        int[][] Scheme = getColorScheme("UD2");
        double percentage = (count * 100.0 / maxres);

        // 1. 处理低于minPercent的值 - 显示蓝色
        if (percentage <= minPercent) {
            if (percentage <= Scheme[1][0]) { // 0-1% 黑色
                return Color.rgb(Scheme[0][0], Scheme[0][1], Scheme[0][2]);
            } else { // >1% 蓝色
                return Color.rgb(Scheme[2][0], Scheme[2][1], Scheme[2][2]);
            }
        }
        // 2. 处理高于maxPercent的值 - 显示红色
        else if (percentage >= maxPercent) {
            return Color.rgb(Scheme[12][0], Scheme[12][1], Scheme[12][2]);
        }
        // 3. 处理中间范围的值
        else {
            // 将[minPercent,maxPercent]线性映射到[20,60]
            double mappedPercentage = 20 + (percentage - minPercent) *
                    (60 - 20) / (maxPercent - minPercent);

            // 使用原有逻辑处理映射后的百分比
            if (mappedPercentage <= 20) {
                // 蓝色到黄色渐变（15-20%）
                return interpolateColor(Scheme[2], Scheme[4], mappedPercentage, 15, 20);
            }
            else if (mappedPercentage <= 40) {
                // 黄色到橙色渐变（20-35%）
                return interpolateColor(Scheme[4], Scheme[6], mappedPercentage, 20, 40);
            }
            else if (mappedPercentage <= 50) {
                // 橙色渐变（35-50%）
                return interpolateColor(Scheme[6], Scheme[8], mappedPercentage, 40, 50);
            }
            else {
                // 橙色到红色渐变（50-60%）
                return interpolateColor(Scheme[8], Scheme[10], mappedPercentage, 50, 60);
            }
        }
    }

    // 颜色插值函数，按比例插值计算 RGB 颜色值
    private static Color interpolateColor(int[] color1, int[] color2, double percentage, double startPercent, double endPercent) {
        // 计算插值比例
        double t = (percentage - startPercent) / (endPercent - startPercent);

        int r = (int) (color1[0] + t * (color2[0] - color1[0]));
        int g = (int) (color1[1] + t * (color2[1] - color1[1]));
        int b = (int) (color1[2] + t * (color2[2] - color1[2]));

        return Color.rgb(r, g, b);
    }
}
