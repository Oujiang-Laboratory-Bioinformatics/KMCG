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
    void parseExtensionTail_parsesSinglePrmBlock() throws IOException {
        KMCG_Processing.clearLoadedData();
        List<String> lines = buildExtensionBlockLines("scaffold1", "%", "", "1\t2");

        parseTailLines(lines);

        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("scaffold1"));
        assertEquals(SCAFFOLD_BLOCK_DATA_ROWS, KMCG_Processing.scaffolddataMap.get("scaffold1").size());
        assertTrue(KMCG_Processing.infDataMap.isEmpty());
    }

    @Test
    void parseExtensionTail_parsesSingleInfBlock() throws IOException {
        KMCG_Processing.clearLoadedData();
        List<String> lines = buildExtensionBlockLines("GCpercentage", "#", "0.315", "3\t4");

        parseTailLines(lines);

        assertEquals(1, KMCG_Processing.infDataMap.size());
        assertTrue(KMCG_Processing.infDataMap.containsKey("GCpercentage"));
        assertEquals(SCAFFOLD_BLOCK_DATA_ROWS, KMCG_Processing.infDataMap.get("GCpercentage").size());
        assertEquals("0.315", KMCG_Processing.infBlockLabelMap.get("GCpercentage"));
        assertTrue(KMCG_Processing.scaffolddataMap.isEmpty());
    }

    @Test
    void parseExtensionTail_skipsBlankLineBeforeFirstBlock() throws IOException {
        KMCG_Processing.clearLoadedData();
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.addAll(buildExtensionBlockLines("scaffold1", "%", "", "1\t2"));

        parseTailLines(lines);

        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("scaffold1"));
    }

    @Test
    void parseExtensionTail_parsesInfThenPrmBlock() throws IOException {
        KMCG_Processing.clearLoadedData();
        List<String> lines = new ArrayList<>();
        lines.addAll(buildExtensionBlockLines("heatmap", "#", "0.1", "10\t20"));
        lines.addAll(buildExtensionBlockLines("lineplot", "%", "0.2", "1\t2\t3"));

        parseTailLines(lines);

        assertEquals(1, KMCG_Processing.infDataMap.size());
        assertTrue(KMCG_Processing.infDataMap.containsKey("heatmap"));
        assertEquals("0.1", KMCG_Processing.infBlockLabelMap.get("heatmap"));
        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("lineplot"));
        assertEquals("0.2", KMCG_Processing.scaffoldBlockLabelMap.get("lineplot"));
        assertEquals(2, KMCG_Processing.extensionBlockOrder.size());
        assertEquals("heatmap", KMCG_Processing.extensionBlockOrder.get(0).tagName);
        assertEquals("inf", KMCG_Processing.extensionBlockOrder.get(0).mode);
        assertEquals("lineplot", KMCG_Processing.extensionBlockOrder.get(1).tagName);
        assertEquals("prm", KMCG_Processing.extensionBlockOrder.get(1).mode);
    }

    @Test
    void parseExtensionTail_preservesFileOrderForPrmThenInf() throws IOException {
        KMCG_Processing.clearLoadedData();
        List<String> lines = new ArrayList<>();
        lines.addAll(buildExtensionBlockLines("GCpercentage", "%", "", "1\t2"));
        lines.addAll(buildExtensionBlockLines("test", "#", "Km:1.0", "3\t4"));

        parseTailLines(lines);

        assertEquals(2, KMCG_Processing.extensionBlockOrder.size());
        assertEquals("GCpercentage", KMCG_Processing.extensionBlockOrder.get(0).tagName);
        assertEquals("prm", KMCG_Processing.extensionBlockOrder.get(0).mode);
        assertEquals("test", KMCG_Processing.extensionBlockOrder.get(1).tagName);
        assertEquals("inf", KMCG_Processing.extensionBlockOrder.get(1).mode);
    }

    @Test
    void parseExtensionTail_parsesTwoPrmBlocks() throws IOException {
        KMCG_Processing.clearLoadedData();
        List<String> lines = new ArrayList<>();
        lines.addAll(buildExtensionBlockLines("first", "%", "", "1\t1"));
        lines.addAll(buildExtensionBlockLines("second", "%", "", "2\t2"));

        parseTailLines(lines);

        assertEquals(2, KMCG_Processing.scaffolddataMap.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("first"));
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("second"));
    }

    @Test
    void parseExtensionTail_stopsOnPlainText() throws IOException {
        KMCG_Processing.clearLoadedData();
        List<String> lines = new ArrayList<>();
        lines.addAll(buildExtensionBlockLines("heatmap", "%", "", "10\t20"));
        lines.add("not-an-extension-marker");

        parseTailLines(lines);

        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
    }

    @Test
    void readFile_parsesInfBlockAfter1133() throws IOException {
        Path file = buildMinimalKmcgFile(
                buildExtensionBlockLines("GCpercentage", "#", "0.315", "1\t2").toArray(new String[0]));

        List<List<Integer>> loaded = KMCG_Processing.readFile(file.toString());

        assertFalse(loaded.isEmpty());
        assertEquals(1, KMCG_Processing.infDataMap.size());
        assertTrue(KMCG_Processing.infDataMap.containsKey("GCpercentage"));
        assertEquals("0.315", KMCG_Processing.infBlockLabelMap.get("GCpercentage"));
    }

    @Test
    void readFile_parsesMultipleExtensionBlocks() throws IOException {
        List<String> tail = new ArrayList<>();
        tail.add("");
        tail.addAll(buildExtensionBlockLines("first", "%", "", "1\t1"));
        tail.addAll(buildExtensionBlockLines("second", "#", "0.5", "2\t2"));

        Path file = buildMinimalKmcgFile(tail.toArray(new String[0]));
        KMCG_Processing.readFile(file.toString());

        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
        assertEquals(1, KMCG_Processing.infDataMap.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("first"));
        assertTrue(KMCG_Processing.infDataMap.containsKey("second"));
        assertEquals("0.5", KMCG_Processing.infBlockLabelMap.get("second"));
    }

    @Test
    void readFile_findsExtensionBlockAfterExtraLinesAfter1133() throws IOException {
        List<String> tail = new ArrayList<>();
        tail.add("Km: 1.0 Ki: 2.0");
        tail.add("unexpected-metadata");
        tail.addAll(buildExtensionBlockLines("GCpercentage", "%", "0.315", "1\t2"));

        Path file = buildMinimalKmcgFile(tail.toArray(new String[0]));
        KMCG_Processing.readFile(file.toString());

        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("GCpercentage"));
        assertEquals("0.315", KMCG_Processing.scaffoldBlockLabelMap.get("GCpercentage"));
    }

    @Test
    void readFile_findsExtensionWhenKmLineIsAfterBlankAt1133() throws IOException {
        List<String> tail = new ArrayList<>();
        tail.add("");
        tail.add("Km: 47.467804 Ki: 390.158615");
        tail.addAll(buildExtensionBlockLines("GCpercentage", "%", "", "1\t2"));

        Path file = buildMinimalKmcgFile(tail.toArray(new String[0]));
        KMCG_Processing.readFile(file.toString());

        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
        assertFalse(MainController.quality_indication.isEmpty());
    }

    @Test
    void readFile_parsesSpaceSeparatedPrmData() throws IOException {
        List<String> tail = new ArrayList<>();
        tail.add("GCPercentage\t%");
        tail.addAll(Collections.nCopies(SCAFFOLD_BLOCK_DATA_ROWS, "432 454 457 000"));
        tail.add("");

        Path file = buildMinimalKmcgFile(tail.toArray(new String[0]));
        KMCG_Processing.readFile(file.toString());

        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("GCPercentage"));
        assertEquals(SCAFFOLD_BLOCK_DATA_ROWS, KMCG_Processing.scaffolddataMap.get("GCPercentage").size());
        assertEquals(List.of(432, 454, 457, 0), KMCG_Processing.scaffolddataMap.get("GCPercentage").get(0));
    }

    @Test
    void readFile_parsesKmEqualsQualityLineBeforeExtension() throws IOException {
        List<String> tail = new ArrayList<>();
        tail.add("Km= 47.467804  Ki= 390.158615");
        tail.addAll(buildExtensionBlockLines("GCPercentage", "%", "", "432 454"));

        Path file = buildMinimalKmcgFile(tail.toArray(new String[0]));
        KMCG_Processing.readFile(file.toString());

        assertFalse(MainController.quality_indication.isEmpty());
        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
    }

    @Test
    void readFile_parsesRowMeanSigmaFromQualityLine() throws IOException {
        List<String> tail = new ArrayList<>();
        tail.add("Km= 72.489893\tRow1_Mean=37.3310\tRow1_Sigma=7.0812\tKi= 473.531136");
        tail.addAll(buildExtensionBlockLines("GCPercentage", "%", "", "432 454"));

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
    void readFile_parsesMuSigmaFromQualityLine() throws IOException {
        List<String> tail = new ArrayList<>();
        tail.add("μ:38.7412\tσ:7.0332\tKm= 75.153332\tKi= 561.308396");
        tail.addAll(buildExtensionBlockLines("GCPercentage", "%", "", "432 454"));

        Path file = buildMinimalKmcgFileWithoutKmLine(tail.toArray(new String[0]));
        KMCG_Processing.readFile(file.toString());

        assertFalse(MainController.quality_indication.isEmpty());
        assertTrue(MainController.qualityGaussianByRow.containsKey(0));
        assertEquals(38.7412, MainController.qualityGaussianByRow.get(0)[0], 1e-4);
        assertEquals(7.0332, MainController.qualityGaussianByRow.get(0)[1], 1e-4);
        String indication = MainController.quality_indication.get(0);
        assertTrue(indication.contains("μ: 38.7412"), () -> "indication=" + indication);
        assertTrue(indication.contains("σ: 7.0332"), () -> "indication=" + indication);
        assertTrue(indication.contains("Km: 75.15"), () -> "indication=" + indication);
        assertTrue(indication.contains("Ki: 561.31"), () -> "indication=" + indication);
    }

    @Test
    void readFile_usesMuSigmaLineWhenEarlierKmKiLineExists() throws IOException {
        List<String> tail = new ArrayList<>();
        tail.add("Km= 47.467804\tKi= 390.158615");
        tail.add("μ:38.7412\tσ:7.0332\tKm= 75.153332\tKi= 561.308396");
        tail.addAll(buildExtensionBlockLines("GCPercentage", "%", "", "432 454"));

        Path file = buildMinimalKmcgFileWithoutKmLine(tail.toArray(new String[0]));
        KMCG_Processing.readFile(file.toString());

        assertEquals(2, MainController.quality_indication.size());
        String indicationForDisplay = MainController.quality_indication.stream()
                .filter(s -> s.contains("μ: 38.7412"))
                .findFirst()
                .orElse("");
        assertTrue(indicationForDisplay.contains("σ: 7.0332"), () -> "indication=" + indicationForDisplay);
    }

    @Test
    void readFile_parsesPrmHeaderLine() throws IOException {
        List<String> tail = new ArrayList<>();
        tail.add("Km= 47.467804\tKi= 390.158615");
        tail.add("GCpercentage\t%\t0.315");
        tail.addAll(Collections.nCopies(SCAFFOLD_BLOCK_DATA_ROWS, "000\t432\t454"));
        tail.add("");

        Path file = buildMinimalKmcgFile(tail.toArray(new String[0]));
        KMCG_Processing.readFile(file.toString());

        assertFalse(MainController.quality_indication.isEmpty());
        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("GCpercentage"));
        assertEquals("0.315", KMCG_Processing.scaffoldBlockLabelMap.get("GCpercentage"));
        assertEquals(SCAFFOLD_BLOCK_DATA_ROWS, KMCG_Processing.scaffolddataMap.get("GCpercentage").size());
        assertEquals(List.of(0, 432, 454), KMCG_Processing.scaffolddataMap.get("GCpercentage").get(0));
    }

    @Test
    void readFile_doesNotCreateGcTabFromBrokenlineBeforeFirstBlock() throws IOException {
        List<String> tail = new ArrayList<>();
        tail.add("98.46\t91.41\t88.78\t86.76");
        tail.add("Km= 47.467804\tKi= 390.158615");
        tail.addAll(buildExtensionBlockLines("GCpercentage", "%", "", "432 454"));

        Path file = buildMinimalKmcgFileWithoutKmLine(tail.toArray(new String[0]));
        KMCG_Processing.readFile(file.toString());

        assertFalse(MainController.brokenlineData.isEmpty());
        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("GCpercentage"));
        assertTrue(KMCG_Processing.gcDataMap.isEmpty());
    }

    @Test
    void isExtensionBlockHeaderLine_rejectsMultiColumnDataLine() {
        assertFalse(KMCG_Processing.isExtensionBlockHeaderLine("lineplot\t10\t20\t30"));
        assertFalse(KMCG_Processing.isExtensionBlockHeaderLine("000\t432\t454"));
    }

    @Test
    void isExtensionBlockHeaderLine_detectsHashAndPercentMarkers() {
        assertTrue(KMCG_Processing.isExtensionBlockHeaderLine("GCpercentage\t#\t0.315"));
        assertTrue(KMCG_Processing.isExtensionBlockHeaderLine("GCpercentage\t%\t0.315"));
        assertTrue(KMCG_Processing.isExtensionBlockHeaderLine("GCpercentage # 0.315"));
        assertFalse(KMCG_Processing.isExtensionBlockHeaderLine("GCpercentage\t0.401072"));
        assertFalse(KMCG_Processing.isExtensionBlockHeaderLine("#GCpercentage"));
        assertFalse(KMCG_Processing.isExtensionBlockHeaderLine("Km= 47.467804\tKi= 390.158615"));
    }

    @Test
    void isExtensionBlockHeaderLine_stillAcceptsLegacyInfPrmMarkers() {
        assertTrue(KMCG_Processing.isExtensionBlockHeaderLine("GCpercentage\tinf"));
        assertTrue(KMCG_Processing.isExtensionBlockHeaderLine("GCpercentage\tprm"));
    }

    @Test
    void isExtensionBlockHeaderLine_detectsFullwidthHashAndPercent() {
        assertTrue(KMCG_Processing.isExtensionBlockHeaderLine("test\t＃\tKm:1.000192"));
        assertTrue(KMCG_Processing.isExtensionBlockHeaderLine("GCpercentage\t％\t0.315"));
    }

    @Test
    void isExtensionBlockHeaderLine_acceptsKmKiInDisplayColumn() {
        assertTrue(KMCG_Processing.isExtensionBlockHeaderLine("test\t#\tKm:75.38 Ki:561.20"));
        assertFalse(KMCG_Processing.isStandaloneQualityMetadataLine("test\t#\tKm:75.38 Ki:561.20"));
    }

    @Test
    void parseExtensionTail_parsesPrmThenInfWithKmDisplayText() throws IOException {
        KMCG_Processing.clearLoadedData();
        List<String> lines = new ArrayList<>();
        lines.addAll(buildExtensionBlockLines("GCpercentage", "%", "0.315", "000 386"));
        lines.addAll(buildExtensionBlockLines("test", "#", "Km:1.000192", "1 2"));

        parseTailLines(lines);

        assertEquals(2, KMCG_Processing.extensionBlockOrder.size());
        assertEquals("GCpercentage", KMCG_Processing.extensionBlockOrder.get(0).tagName);
        assertEquals("prm", KMCG_Processing.extensionBlockOrder.get(0).mode);
        assertEquals("test", KMCG_Processing.extensionBlockOrder.get(1).tagName);
        assertEquals("inf", KMCG_Processing.extensionBlockOrder.get(1).mode);
        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
        assertEquals(1, KMCG_Processing.infDataMap.size());
        assertTrue(KMCG_Processing.infDataMap.containsKey("test"));
        assertEquals("Km:1.000192", KMCG_Processing.infBlockLabelMap.get("test"));
    }

    @Test
    void parseExtensionTail_parsesHashHeaderAfterBrokenlineAndQuality() throws IOException {
        KMCG_Processing.clearLoadedData();
        List<String> lines = new ArrayList<>();
        lines.add("99.24 92.82 89.37");
        lines.add("μ:38.7411 σ:7.0333 Km:75.385731 Ki= 561.205206");
        lines.addAll(buildExtensionBlockLines("GCpercentage", "%", "", "000 386"));
        lines.addAll(buildExtensionBlockLines("test", "#", "Km:1.000192", "1 2"));

        parseTailLines(lines);

        assertEquals(2, KMCG_Processing.extensionBlockOrder.size());
        assertTrue(KMCG_Processing.infDataMap.containsKey("test"));
    }

    @Test
    void parseExtensionTail_parsesInfWhenHashHeaderImmediatelyAfterPrmData() throws IOException {
        KMCG_Processing.clearLoadedData();
        List<String> lines = new ArrayList<>();
        lines.add("GCpercentage\t%");
        lines.addAll(Collections.nCopies(SCAFFOLD_BLOCK_DATA_ROWS - 1, "000 386"));
        lines.add("test\t#\tKm:1.000192");
        lines.addAll(Collections.nCopies(SCAFFOLD_BLOCK_DATA_ROWS, "1 2"));
        lines.add("");

        parseTailLines(lines);

        assertEquals(2, KMCG_Processing.extensionBlockOrder.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("GCpercentage"));
        assertEquals(SCAFFOLD_BLOCK_DATA_ROWS - 1,
                KMCG_Processing.scaffolddataMap.get("GCpercentage").size());
        assertTrue(KMCG_Processing.infDataMap.containsKey("test"));
        assertEquals(SCAFFOLD_BLOCK_DATA_ROWS, KMCG_Processing.infDataMap.get("test").size());
        assertEquals("Km:1.000192", KMCG_Processing.infBlockLabelMap.get("test"));
    }

    @Test
    void parseExtensionTail_parsesInfAfterPrmWithBlankLinesBeforeHashHeader() throws IOException {
        KMCG_Processing.clearLoadedData();
        List<String> lines = new ArrayList<>();
        lines.add("GCpercentage\t%");
        lines.addAll(Collections.nCopies(SCAFFOLD_BLOCK_DATA_ROWS, "000 386"));
        lines.add("");
        lines.add("");
        lines.add("test\t#\tKm:1.000192");
        lines.addAll(Collections.nCopies(SCAFFOLD_BLOCK_DATA_ROWS, "1 2"));

        parseTailLines(lines);

        assertEquals(2, KMCG_Processing.extensionBlockOrder.size());
        assertTrue(KMCG_Processing.infDataMap.containsKey("test"));
    }

    @Test
    void parseExtensionBlockHeaderByMarkers_detectsTabHashTab() {
        assertNotNull(KMCG_Processing.parseExtensionBlockHeader("test\t#\tKm:1.000192"));
        String[] header = KMCG_Processing.parseExtensionBlockHeaderByMarkers("test\t#\tKm:1.000192");
        assertNotNull(header);
        assertEquals("test", header[0]);
        assertEquals("inf", header[1]);
    }

    @Test
    void readFile_parsesPrmThenInfWhenHashBlockStartsAt1438() throws IOException {
        List<String> tail = new ArrayList<>();
        tail.add("");
        tail.add("99.24 92.82 89.37");
        tail.add("μ:38.7411 σ:7.0333 Km:75.385731 Ki= 561.205206");
        tail.addAll(buildExtensionBlockLines("GCpercentage", "%", "0.315", "000 386"));
        tail.addAll(buildExtensionBlockLines("test", "#", "Km:1.000192", "1 2"));

        Path file = buildMinimalKmcgFile(tail.toArray(new String[0]));
        KMCG_Processing.readFile(file.toString());

        assertEquals(2, KMCG_Processing.extensionBlockOrder.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("GCpercentage"));
        assertTrue(KMCG_Processing.infDataMap.containsKey("test"));
        assertEquals("Km:1.000192", KMCG_Processing.infBlockLabelMap.get("test"));
    }

    @Test
    void readFile_parsesExtensionWhenBlockStartsAt1131() throws IOException {
        List<String> tail = new ArrayList<>();
        tail.add("GCPercentage\t%\t0.315");
        tail.addAll(Collections.nCopies(SCAFFOLD_BLOCK_DATA_ROWS, "432 454 457 000"));
        tail.add("");

        Path file = buildMinimalKmcgFileWithoutKmLine(tail.toArray(new String[0]));
        KMCG_Processing.readFile(file.toString());

        assertEquals(1, KMCG_Processing.scaffolddataMap.size());
        assertTrue(KMCG_Processing.scaffolddataMap.containsKey("GCPercentage"));
        assertEquals("0.315", KMCG_Processing.scaffoldBlockLabelMap.get("GCPercentage"));
    }

    @Test
    void readFile_returnsEmptyForMissingKmcgData() throws IOException {
        Path file = tempDir.resolve("empty.txt");
        Files.writeString(file, "not-a-kmcg-file\n");

        List<List<Integer>> loaded = KMCG_Processing.readFile(file.toString());

        assertTrue(loaded.isEmpty());
        assertTrue(KMCG_Processing.scaffolddataMap.isEmpty());
        assertTrue(KMCG_Processing.infDataMap.isEmpty());
        assertTrue(KMCG_Processing.gcDataMap.isEmpty());
    }

    private void parseTailLines(List<String> lines) throws IOException {
        Path file = tempDir.resolve("tail.txt");
        Files.write(file, lines);
        try (BufferedReader br = Files.newBufferedReader(file)) {
            KMCG_Processing.parseExtensionTail(br, "");
        }
    }

    private List<String> buildExtensionBlockLines(String tag, String mode, String displayText, String row) {
        List<String> lines = new ArrayList<>();
        String header = tag + "\t" + mode;
        if (displayText != null && !displayText.isEmpty()) {
            header += "\t" + displayText;
        }
        lines.add(header);
        lines.addAll(Collections.nCopies(SCAFFOLD_BLOCK_DATA_ROWS, row));
        lines.add("");
        return lines;
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

    @Test
    void resolveKmcgMarginalHitRegion_matchesDrawnRegionRectangles() {
        assertEquals("region8", KMCG_Processing.resolveKmcgMarginalHitRegion(200, 30));
        assertEquals("region4", KMCG_Processing.resolveKmcgMarginalHitRegion(120, 200));
        assertEquals("region1", KMCG_Processing.resolveKmcgMarginalHitRegion(120, 700));
        assertEquals("region2", KMCG_Processing.resolveKmcgMarginalHitRegion(400, 700));
        assertEquals("region7", KMCG_Processing.resolveKmcgMarginalHitRegion(120, 20));
        assertNull(KMCG_Processing.resolveKmcgMarginalHitRegion(200, 50));
        assertNull(KMCG_Processing.resolveKmcgMarginalHitRegion(200, 200));
    }

    @Test
    void formatAlignedTooltip_buildsTwoLineHeaderAndValueRows() {
        String text = KMCG_Processing.formatAlignedTooltip(
                new String[]{"Coord", "Count", "Scale"},
                new String[]{"(292, 70)", "193 (0.0000%)", "7.6"});
        String[] lines = text.split("\n", -1);
        assertEquals(2, lines.length);
        assertTrue(lines[0].contains("Coord"));
        assertTrue(lines[0].contains("Count"));
        assertTrue(lines[0].contains("Scale"));
        assertTrue(lines[1].contains("(292, 70)"));
        assertTrue(lines[1].contains("193 (0.0000%)"));
        assertTrue(lines[1].contains("7.6"));
    }

    @Test
    void computeMarginalBlockSum_aggregatesStripAndCornerCells() {
        List<List<Integer>> data = new ArrayList<>();
        data.add(List.of(1, 2, 3, 4));
        data.add(List.of(10, 100, 200, 40));
        data.add(List.of(100, 1000, 2000, 400));

        assertEquals(1, KMCG_Processing.computeMarginalBlockSum(data, "region1"));
        assertEquals(4, KMCG_Processing.computeMarginalBlockSum(data, "region3"));
        assertEquals(2 + 3, KMCG_Processing.computeMarginalBlockSum(data, "region2"));
        assertEquals(10, KMCG_Processing.computeMarginalBlockSum(data, "region4"));
        assertEquals(40, KMCG_Processing.computeMarginalBlockSum(data, "region6"));
        assertEquals(100, KMCG_Processing.computeMarginalBlockSum(data, "region7"));
        assertEquals(1000 + 2000, KMCG_Processing.computeMarginalBlockSum(data, "region8"));
        assertEquals(400, KMCG_Processing.computeMarginalBlockSum(data, "region9"));
    }

    @Test
    void alignedTooltipContentKey_isStableForSameColumns() {
        String key = KMCG_Processing.alignedTooltipContentKey(
                new String[]{"Coord", "Count"},
                new String[]{"(1, 2)", "3 (0.01%)"});
        assertEquals(key, KMCG_Processing.alignedTooltipContentKey(
                new String[]{"Coord", "Count"},
                new String[]{"(1, 2)", "3 (0.01%)"}));
    }
}
