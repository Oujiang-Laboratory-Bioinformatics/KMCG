package com.apply.kmcg;

import javafx.scene.paint.Color;

public class ColorUtils {

    // 根据 ctype 获取颜色方案
    public static int[][] getColorScheme(String ctype) {
        switch (ctype) {
            case "BW": // black-white
                return new int[][] {
                        { 0, 255, 255 }, { 0 }, { 0, 0, 0 }, { 50 }, { 128, 128, 128 }, { 100 }, { 255, 255, 255 },
                        { 101 }, { 255, 0, 255 }, { 255, 0, 255 }
                };
            case "C1": // white-red-black
                return new int[][] {
                        { 255, 255, 255 }, { 3 }, { 255, 255, 255 }, { 50 }, { 255, 0, 0 }, { 100 }, { 0, 0, 0 },
                        { 101 }, { 0, 0, 0 }, { 0, 0, 0 }
                };
            case "C2": // yellow-blue-black
                return new int[][] {
                        { 255, 255, 200 }, { 10 }, { 255, 255, 200 }, { 60 }, { 0, 255, 0 }, { 100 }, { 0, 0, 0 },
                        { 101 }, { 0, 0, 0 }, { 0, 0, 0 }
                };
            case "HM":
                return new int[][] {
                        { 0, 0, 0 }, { 0 }, { 0, 0, 0 }, { 30 }, { 200, 0, 0 }, { 70 }, { 200, 200, 100 }, { 100 },
                        { 240, 240, 255 }, { 255, 0, 255 }
                };
            case "UD":
                return new int[][] {
                        { 0, 0, 0 }, { 0 }, { 0, 0, 255 }, { 30 }, { 255, 255, 0 }, { 70 }, { 255, 165, 0 }, { 100 },
                        { 255, 0, 0 }
                };
            default:
                throw new IllegalArgumentException("Invalid color scheme type: " + ctype);
        }
    }

    // 根据数值计算颜色
    public static Color getColorForValue(double count, double maxres, String ctype) {
        int[][] Scheme = getColorScheme(ctype);
        int percentage = (int) (count * 100.0 / maxres);
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

    // 颜色插值函数，按比例插值计算 RGB 颜色值
    private static Color interpolateColor(int[] color1, int[] color2, double percentage, double startPercent,
            double endPercent) {
        // 计算插值比例
        double t = (percentage - startPercent) / (endPercent - startPercent);

        int r = (int) (color1[0] + t * (color2[0] - color1[0]));
        int g = (int) (color1[1] + t * (color2[1] - color1[1]));
        int b = (int) (color1[2] + t * (color2[2] - color1[2]));

        return Color.rgb(r, g, b);
    }
}
