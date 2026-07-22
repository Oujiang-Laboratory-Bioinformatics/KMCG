package com.apply.kmcg;

import javafx.scene.canvas.GraphicsContext;


public class InfScaffolds_Processing extends Scaffolds_Processing {

    @Override
    protected void drawBeforeCanvasSnapshot(GraphicsContext gc, int rowCount, int colCount) {
        KMCG_Processing.drawInfMuReferenceLine(gc, rowCount, colCount);
        KMCG_Processing.drawInfMuDebugLabel(gc);
    }
}
