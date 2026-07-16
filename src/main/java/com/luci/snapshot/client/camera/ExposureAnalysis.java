package com.luci.snapshot.client.camera;

import com.mojang.blaze3d.platform.NativeImage;

public record ExposureAnalysis(
    int[] redHistogram,
    int[] greenHistogram,
    int[] blueHistogram,
    float[] waveform,
    byte[] luminanceGrid,
    int gridWidth,
    int gridHeight,
    double clippedHighlights,
    double crushedShadows
) {
    private static final int HISTOGRAM_BINS = 64;
    private static final int GRID_WIDTH = 32;
    private static final int GRID_HEIGHT = 18;

    static ExposureAnalysis analyze(NativeImage image) {
        int[] red = new int[HISTOGRAM_BINS];
        int[] green = new int[HISTOGRAM_BINS];
        int[] blue = new int[HISTOGRAM_BINS];
        double[] waveformSum = new double[HISTOGRAM_BINS];
        int[] waveformCount = new int[HISTOGRAM_BINS];
        int[] gridSum = new int[GRID_WIDTH * GRID_HEIGHT];
        int[] gridCount = new int[GRID_WIDTH * GRID_HEIGHT];
        int highlights = 0;
        int shadows = 0;
        int total = image.getWidth() * image.getHeight();

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixel(x, y);
                int redValue = (pixel >> 16) & 0xFF;
                int greenValue = (pixel >> 8) & 0xFF;
                int blueValue = pixel & 0xFF;
                red[Math.min(HISTOGRAM_BINS - 1, redValue * HISTOGRAM_BINS / 256)]++;
                green[Math.min(HISTOGRAM_BINS - 1, greenValue * HISTOGRAM_BINS / 256)]++;
                blue[Math.min(HISTOGRAM_BINS - 1, blueValue * HISTOGRAM_BINS / 256)]++;
                int luminance = (int) Math.round(redValue * 0.2126 + greenValue * 0.7152 + blueValue * 0.0722);
                int column = Math.min(HISTOGRAM_BINS - 1, x * HISTOGRAM_BINS / image.getWidth());
                waveformSum[column] += luminance;
                waveformCount[column]++;
                int gridX = Math.min(GRID_WIDTH - 1, x * GRID_WIDTH / image.getWidth());
                int gridY = Math.min(GRID_HEIGHT - 1, y * GRID_HEIGHT / image.getHeight());
                int gridIndex = gridY * GRID_WIDTH + gridX;
                gridSum[gridIndex] += luminance;
                gridCount[gridIndex]++;
                if (Math.max(redValue, Math.max(greenValue, blueValue)) >= 250) {
                    highlights++;
                }
                if (luminance <= 5) {
                    shadows++;
                }
            }
        }

        float[] waveform = new float[HISTOGRAM_BINS];
        for (int column = 0; column < waveform.length; column++) {
            waveform[column] = waveformCount[column] == 0 ? 0.0F : (float) (waveformSum[column] / waveformCount[column] / 255.0);
        }
        byte[] grid = new byte[GRID_WIDTH * GRID_HEIGHT];
        for (int index = 0; index < grid.length; index++) {
            grid[index] = (byte) (gridCount[index] == 0 ? 0 : gridSum[index] / gridCount[index]);
        }
        return new ExposureAnalysis(red, green, blue, waveform, grid, GRID_WIDTH, GRID_HEIGHT,
            highlights / (double) Math.max(1, total), shadows / (double) Math.max(1, total));
    }

    public int luminanceAt(int x, int y) {
        int clampedX = Math.max(0, Math.min(gridWidth - 1, x));
        int clampedY = Math.max(0, Math.min(gridHeight - 1, y));
        return luminanceGrid[clampedY * gridWidth + clampedX] & 0xFF;
    }
}
