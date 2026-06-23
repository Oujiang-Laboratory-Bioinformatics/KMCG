package com.apply.kmcg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KMCG_ProcessingTailParseTest {

    private static final int SCAFFOLD_BLOCK_DATA_ROWS = 302;

    @TempDir
    Path tempDir;

    @Test
    void parseExtensionTail_parsesSingleScaffoldBlock() throws IOException {
        KMCG_Processing.clearLoadedData();
        List<String> lines = new ArrayList<>();
        lines.add("#scaffold1");
        lines.addAll(Collections.nCopies(SCAFFOLD_BLOCK_DATA_ROWS, "1\t2"));
        lines.add("");

        parseTailLines(lines);

        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("scaffold1"));
        assertEquals(SCAFFOLD_BLOCK_DATA_ROWS, KMCG_Processing.scaffolddataMap.get("scaffold1").size());
    }

    @Test
    void parseExtensionTail_skipsBlankLineBeforeFirstBlock() throws IOException {
        KMCG_Processing.clearLoadedData();
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("#scaffold1");
        lines.addAll(Collections.nCopies(SCAFFOLD_BLOCK_DATA_ROWS, "1\t2"));
        lines.add("");

        parseTailLines(lines);

        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("scaffold1"));
    }

    @Test
    void parseExtensionTail_parsesScaffoldThenGcBlock() throws IOException {
        KMCG_Processing.clearLoadedData();
        List<String> lines = new ArrayList<>();
        lines.add("#heatmap");
        lines.addAll(Collections.nCopies(SCAFFOLD_BLOCK_DATA_ROWS, "10\t20"));
        lines.add("");
        lines.add("*lineplot");
        lines.add("1\t2\t3");
        lines.add("");

        parseTailLines(lines);

        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("heatmap"));
        assertEquals(1, KMCG_Processing.gcDataMap.size());
        assertTrue(KMCG_Processing.gcDataMap.containsKey("lineplot"));
        assertEquals(List.of(1, 2, 3), KMCG_Processing.gcDataMap.get("lineplot"));
    }

    @Test
    void parseExtensionTail_parsesTwoScaffoldBlocks() throws IOException {
        KMCG_Processing.clearLoadedData();
        List<String> lines = new ArrayList<>();
        lines.addAll(buildScaffoldBlockLines("first", "1\t1"));
        lines.addAll(buildScaffoldBlockLines("second", "2\t2"));

        parseTailLines(lines);

        assertEquals(2, KMCG_Processing.scaffolddataMap.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("first"));
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("second"));
    }

    @Test
    void parseExtensionTail_stopsOnPlainText() throws IOException {
        KMCG_Processing.clearLoadedData();
        List<String> lines = new ArrayList<>();
        lines.addAll(buildScaffoldBlockLines("heatmap", "10\t20"));
        lines.add("not-an-extension-marker");

        parseTailLines(lines);

        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
    }

    @Test
    void readFile_parsesExtensionAfterBlankLineAt1134() throws IOException {
        Path file = buildMinimalKmcgFile(buildScaffoldBlockLines("GCpercentage 0.401072", "1\t2").toArray(new String[0]));
        appendGcBlock(file, "111", "10\t20\t30");

        List<List<Integer>> loaded = KMCG_Processing.readFile(file.toString());

        assertFalse(loaded.isEmpty());
        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("GCpercentage 0.401072"));
        assertEquals(1, KMCG_Processing.gcDataMap.size());
        assertTrue(KMCG_Processing.gcDataMap.containsKey("111"));
    }

    @Test
    void readFile_parsesMultipleScaffoldBlocks() throws IOException {
        List<String> tail = new ArrayList<>();
        tail.add("");
        tail.addAll(buildScaffoldBlockLines("first", "1\t1"));
        tail.addAll(buildScaffoldBlockLines("second", "2\t2"));

        Path file = buildMinimalKmcgFile(tail.toArray(new String[0]));
        KMCG_Processing.readFile(file.toString());

        assertEquals(2, KMCG_Processing.scaffolddataMap.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("first"));
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("second"));
    }

    @Test
    void readFile_findsExtensionBlockAfterExtraLinesAfter1133() throws IOException {
        List<String> tail = new ArrayList<>();
        tail.add("Km: 1.0 Ki: 2.0");
        tail.add("unexpected-metadata");
        tail.addAll(buildScaffoldBlockLines("GCpercentage 0.401072", "1\t2"));

        Path file = buildMinimalKmcgFile(tail.toArray(new String[0]));
        KMCG_Processing.readFile(file.toString());

        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("GCpercentage 0.401072"));
    }

    @Test
    void readFile_findsExtensionWhenKmLineIsAfterBlankAt1133() throws IOException {
        List<String> tail = new ArrayList<>();
        tail.add("");
        tail.add("Km: 47.467804 Ki: 390.158615");
        tail.addAll(buildScaffoldBlockLines("GCpercentage 0.401072", "1\t2"));

        Path file = buildMinimalKmcgFile(tail.toArray(new String[0]));
        KMCG_Processing.readFile(file.toString());

        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
        assertFalse(MainController.quality_indication.isEmpty());
    }

    @Test
    void readFile_parsesSpaceSeparatedScaffoldData() throws IOException {
        List<String> tail = new ArrayList<>();
        tail.add("#GCPercentage 0.401072");
        tail.addAll(Collections.nCopies(SCAFFOLD_BLOCK_DATA_ROWS, "432 454 457 000"));
        tail.add("");

        Path file = buildMinimalKmcgFile(tail.toArray(new String[0]));
        KMCG_Processing.readFile(file.toString());

        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("GCPercentage 0.401072"));
        assertEquals(SCAFFOLD_BLOCK_DATA_ROWS, KMCG_Processing.scaffolddataMap.get("GCPercentage 0.401072").size());
        assertEquals(List.of(432, 454, 457, 0), KMCG_Processing.scaffolddataMap.get("GCPercentage 0.401072").get(0));
    }

    @Test
    void readFile_parsesKmEqualsQualityLineBeforeExtension() throws IOException {
        List<String> tail = new ArrayList<>();
        tail.add("Km= 47.467804  Ki= 390.158615");
        tail.addAll(buildScaffoldBlockLines("GCPercentage 0.401072", "432 454"));

        Path file = buildMinimalKmcgFile(tail.toArray(new String[0]));
        KMCG_Processing.readFile(file.toString());

        assertFalse(MainController.quality_indication.isEmpty());
        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
    }

    @Test
    void readFile_parsesRowMeanSigmaFromQualityLine() throws IOException {
        List<String> tail = new ArrayList<>();
        tail.add("Km= 72.489893\tRow1_Mean=37.3310\tRow1_Sigma=7.0812\tKi= 473.531136");
        tail.addAll(buildScaffoldBlockLines("GCPercentage 0.401072", "432 454"));

        Path file = buildMinimalKmcgFileWithoutKmLine(tail.toArray(new String[0]));
        KMCG_Processing.readFile(file.toString());

        assertFalse(MainController.quality_indication.isEmpty());
        assertTrue(MainController.qualityGaussianByRow.containsKey(0));
        assertEquals(37.3310, MainController.qualityGaussianByRow.get(0)[0], 1e-4);
        assertEquals(7.0812, MainController.qualityGaussianByRow.get(0)[1], 1e-4);
        String indication = MainController.quality_indication.get(0);
        assertTrue(indication.contains("Row1_Mean=37.3310"), () -> "indication=" + indication);
        assertTrue(indication.contains("Row1_Sigma=7.0812"), () -> "indication=" + indication);
    }

    @Test
    void readFile_parsesScaffoldHeaderWithoutHashPrefix() throws IOException {
        List<String> tail = new ArrayList<>();
        tail.add("Km= 47.467804\tKi= 390.158615");
        tail.add("GCpercentage\t0.401072");
        tail.addAll(Collections.nCopies(SCAFFOLD_BLOCK_DATA_ROWS, "000\t432\t454"));
        tail.add("");

        Path file = buildMinimalKmcgFile(tail.toArray(new String[0]));
        KMCG_Processing.readFile(file.toString());

        assertFalse(MainController.quality_indication.isEmpty());
        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("GCpercentage 0.401072"));
        assertEquals(SCAFFOLD_BLOCK_DATA_ROWS, KMCG_Processing.scaffolddataMap.get("GCpercentage 0.401072").size());
        assertEquals(List.of(0, 432, 454), KMCG_Processing.scaffolddataMap.get("GCpercentage 0.401072").get(0));
    }

    @Test
    void readFile_doesNotCreateGcTabFromBrokenlineBeforeFirstBlock() throws IOException {
        List<String> tail = new ArrayList<>();
        tail.add("98.46\t91.41\t88.78\t86.76");
        tail.add("Km= 47.467804\tKi= 390.158615");
        tail.add("#GCpercentage 0.401072");
        tail.addAll(Collections.nCopies(SCAFFOLD_BLOCK_DATA_ROWS, "432 454"));
        tail.add("");
        tail.add("*111");
        tail.add("0 30 50 70");
        tail.add("");
        tail.add("*12");
        tail.add("0 30 50 70");
        tail.add("");

        Path file = buildMinimalKmcgFileWithoutKmLine(tail.toArray(new String[0]));
        KMCG_Processing.readFile(file.toString());

        assertFalse(MainController.brokenlineData.isEmpty());
        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("GCpercentage 0.401072"));
        assertEquals(2, KMCG_Processing.gcDataMap.size());
        assertTrue(KMCG_Processing.gcDataMap.containsKey("111"));
        assertTrue(KMCG_Processing.gcDataMap.containsKey("12"));
        assertFalse(KMCG_Processing.gcDataMap.containsKey("98.46"));
        assertFalse(KMCG_Processing.gcDataMap.containsKey("0"));
    }

    @Test
    void isScaffoldHeaderLine_rejectsMultiColumnDataLine() {
        assertFalse(KMCG_Processing.isScaffoldHeaderLine("lineplot\t10\t20\t30"));
        assertFalse(KMCG_Processing.isScaffoldHeaderLine("000\t432\t454"));
    }

    @Test
    void isScaffoldHeaderLine_detectsTabSeparatedTagLine() {
        assertTrue(KMCG_Processing.isScaffoldHeaderLine("GCpercentage\t0.401072"));
        assertFalse(KMCG_Processing.isScaffoldHeaderLine("000\t432\t454"));
        assertFalse(KMCG_Processing.isScaffoldHeaderLine("#GCpercentage 0.401072"));
        assertFalse(KMCG_Processing.isScaffoldHeaderLine("Km= 47.467804\tKi= 390.158615"));
    }

    @Test
    void readFile_parsesExtensionWhenHashBlockStartsAt1131() throws IOException {
        List<String> tail = new ArrayList<>();
        tail.add("#GCPercentage 0.401072");
        tail.addAll(Collections.nCopies(SCAFFOLD_BLOCK_DATA_ROWS, "432 454 457 000"));
        tail.add("");

        Path file = buildMinimalKmcgFileWithoutKmLine(tail.toArray(new String[0]));
        KMCG_Processing.readFile(file.toString());

        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("GCPercentage 0.401072"));
    }

    @Test
    void readFile_returnsEmptyForMissingKmcgData() throws IOException {
        Path file = tempDir.resolve("empty.txt");
        Files.writeString(file, "not-a-kmcg-file\n");

        List<List<Integer>> loaded = KMCG_Processing.readFile(file.toString());

        assertTrue(loaded.isEmpty());
        assertTrue(KMCG_Processing.scaffolddataMap.isEmpty());
        assertTrue(KMCG_Processing.gcDataMap.isEmpty());
    }

    private void parseTailLines(List<String> lines) throws IOException {
        Path file = tempDir.resolve("tail.txt");
        Files.write(file, lines);
        try (BufferedReader br = Files.newBufferedReader(file)) {
            KMCG_Processing.parseExtensionTail(br, "");
        }
    }

    private List<String> buildScaffoldBlockLines(String tag, String row) {
        List<String> lines = new ArrayList<>();
        lines.add("#" + tag);
        lines.addAll(Collections.nCopies(SCAFFOLD_BLOCK_DATA_ROWS, row));
        lines.add("");
        return lines;
    }

    private void appendGcBlock(Path file, String tag, String yValues) throws IOException {
        List<String> lines = Files.readAllLines(file);
        lines.add("*" + tag);
        lines.add(yValues);
        lines.add("");
        Files.write(file, lines);
    }

    private Path buildMinimalKmcgFileWithoutKmLine(String... tailLines) throws IOException {
        Path file = tempDir.resolve("minimal-no-km.kmcg");
        List<String> lines = buildMinimalHeaderLines();
        Collections.addAll(lines, tailLines);
        Files.write(file, lines);
        return file;
    }

    private List<String> buildMinimalHeaderLines() {
        List<String> lines = new ArrayList<>();
        lines.add("KMCG1\t302\t302\t31\t1000000");
        lines.add("name1\tname2");
        lines.add("100\t200");
        for (int i = 0; i < 302; i++) {
            lines.add("1\t1");
        }
        lines.add("");
        for (int i = 0; i < 302; i++) {
            lines.add("a\tb");
        }
        lines.add("");
        for (int i = 0; i < 521; i++) {
            lines.add("1\t1");
        }
        lines.add("");
        lines.add("0.5\t0.6");
        return lines;
    }

    private Path buildMinimalKmcgFile(String... tailLines) throws IOException {
        Path file = tempDir.resolve("minimal.kmcg");
        List<String> lines = buildMinimalHeaderLines();
        lines.add("Km= 47.467804  Ki= 390.158615");
        Collections.addAll(lines, tailLines);
        Files.write(file, lines);
        return file;
    }
}
