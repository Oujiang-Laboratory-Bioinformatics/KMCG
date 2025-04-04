package com.apply.kmcg;

import java.util.*;

import static com.apply.kmcg.KMCG_Processing.resultData;
import static com.apply.kmcg.MainController.coordinateDict;

public class Kmer_Processing {
    // 声明全局变量 maxValue
    public static int maxValue = 0; // 初始化为0
    // 名称

    public static List<String> NamesData() {
        // 返回 names 数据
        return MainController.names;
    }

    // 长度
    public static List<Integer> LengthsData() {
        // 返回 lengths 数据
        return MainController.lengths;
    }

    public static void PointsData() {
        KMCG_Processing.getCoordinatePoints();
    }

    // 初始化存储空间，接收 names 和 lengths 列表
    public static Map<String, List<Integer>> initializeStorage(List<String> names, List<Integer> lengths) {

        Map<String, List<Integer>> storage = new LinkedHashMap<>();// 按顺序
        // 遍历 names 和 lengths 列表，初始化存储空间
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            int length = lengths.get(i);
            List<Integer> zerosList = new ArrayList<>(Collections.nCopies(length, 0)); // 用零初始化长度为 length 的列表
            storage.put(name, zerosList);
        }
        return storage;
    }

    public static List<Map.Entry<Character, Integer>> parseData(String data) {
        List<Map.Entry<Character, Integer>> parsedData = new ArrayList<>();
        // 按空格分隔数据项
        String[] dataItems = data.split(" ");
        for (String item : dataItems) {
            // 忽略前31个字符，从第32个字符开始处理
            if (item.length() <= 31)
                continue; // 确保数据足够长
            String trimmedItem = item.substring(31);
            // 每四个字符处理一次
            for (int i = 0; i < trimmedItem.length(); i += 4) {
                if (i + 4 <= trimmedItem.length()) { // 确保后续有足够的字符可读取
                    char symbol = trimmedItem.charAt(i);
                    int index = Integer.parseInt(trimmedItem.substring(i + 1, i + 4));
                    // 直接添加到结果中，不做去重
                    parsedData.add(new AbstractMap.SimpleEntry<>(symbol, index));
                }
            }
        }
        return parsedData;
    }

    // 统计每种数据的出现次数，并更新存储空间
    public static Map<String, List<Integer>> updateStorage(List<String> names, Map<String, List<Integer>> storage,
            List<Map.Entry<Character, Integer>> parsedData) {
        Map<Integer, Map<Integer, Integer>> counts = new HashMap<>();
        int maxValue = 0;

        // 统计每种数据的出现次数
        for (Map.Entry<Character, Integer> entry : parsedData) {
            char symbol = entry.getKey();
            int index = entry.getValue();
            int storageIndex = (int) symbol - 33; // ASCII 值 - 33
            counts.putIfAbsent(storageIndex, new HashMap<>());
            counts.get(storageIndex).putIfAbsent(index, 0);
            counts.get(storageIndex).put(index, counts.get(storageIndex).get(index) + 1);
        }
        // 更新 storage 中的数据
        for (Map.Entry<Integer, Map<Integer, Integer>> storageEntry : counts.entrySet()) {
            int storageIndex = storageEntry.getKey();
            if (storageIndex < names.size()) {
                String name = names.get(storageIndex);

                for (Map.Entry<Integer, Integer> indexCountEntry : storageEntry.getValue().entrySet()) {
                    int index = indexCountEntry.getKey();
                    int count = indexCountEntry.getValue();

                    if (index < storage.get(name).size()) {
                        storage.get(name).set(index, count); // 更新存储空间
                        maxValue = Math.max(maxValue, count); // 更新最大值
                    }
                }
            }
        }
        // 返回更新后的 storage 和最大值
        return storage;
    }

    public static int getMaxValue(List<String> names, Map<String, List<Integer>> storage,
            List<Map.Entry<Character, Integer>> parsedData) {
        // 获取更新后的存储空间
        Map<String, List<Integer>> updatedStorage = updateStorage(names, storage, parsedData);

        int maxValue = 0;
        // 从存储空间中计算最大值
        for (List<Integer> counts : updatedStorage.values()) {
            for (int count : counts) {
                maxValue = Math.max(maxValue, count);
            }
        }
        return maxValue;
    }

    public static int getTotalValue(List<String> names, Map<String, List<Integer>> storage,
            List<Map.Entry<Character, Integer>> parsedData) {
        // 获取更新后的存储空间
        Map<String, List<Integer>> updatedStorage = updateStorage(names, storage, parsedData);

        int totalValue = 0;
        // 从存储空间中计算总和
        for (List<Integer> counts : updatedStorage.values()) {
            for (int count : counts) {
                totalValue += count;
            }
        }
        // System.out.println(totalValue);
        return totalValue;
    }

    public static String processBlockIndex(int rowIndex, int columnIndex) {
        if (columnIndex < 0) {
            throw new IllegalArgumentException("Column index cannot be negative: " + columnIndex);
        }
        char rowChar = (char) (rowIndex + 33); // 确保 row 转换为合法字符
        String colStr = String.format(Locale.US, "%03d", columnIndex); // 确保 locale 兼容
        return rowChar + colStr;
    }

    // 测试打印字典内容 把类似于(177, 108): AGT...!007!007! (2, 416): TCT...!007!007!都放在一起
    public static void printDataDict() {
        for (Map.Entry<String, String> entry : coordinateDict.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
    }

    public static Map<String, Integer> parseAndCount(int rowIndex, int columnIndex) {
        Map<String, Integer> nameCount = new HashMap<>();
        String targetIndex = processBlockIndex(rowIndex, columnIndex);

        for (String value : resultData) {
            String[] dataItems = value.split(" ");

            for (String item : dataItems) {
                if (item.length() < 31)
                    continue; // 确保数据项足够长

                String name = item.substring(0, 31); // 前31个字符作为名字
                String data = item.substring(31); // 剩下的部分作为数据内容

                // **获取已有的计数**
                int totalCount = nameCount.getOrDefault(name, 0);
                int count = 0; // **局部计数变量**

                for (int i = 0; i + 4 <= data.length(); i += 4) {
                    String block = data.substring(i, i + 4);
                    if (block.equals(targetIndex)) {
                        count++;
                    }
                }

                // **更新总计数**
                if (count > 0) {
                    nameCount.put(name, totalCount + count);
                }
            }
        }

        return sortByValueDesc(nameCount);
    }

    private static Map<String, Integer> sortByValueDesc(Map<String, Integer> map) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(map.entrySet());
        list.sort((a, b) -> b.getValue().compareTo(a.getValue())); // 降序排序

        Map<String, Integer> sortedMap = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : list) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }
        return sortedMap;
    }

}
