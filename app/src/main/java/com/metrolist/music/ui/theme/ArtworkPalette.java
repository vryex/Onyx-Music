package com.metrolist.music.ui.theme;

/** Select a substantial chromatic area, not a tiny bright detail or neutral border. */
public final class ArtworkPalette {
    private ArtworkPalette() {}

    public static int select(int[] colors, int[] populations) {
        if (colors.length == 0 || colors.length != populations.length) return 0xff252529;
        int dominant = 0;
        for (int i = 1; i < colors.length; i++) if (populations[i] > populations[dominant]) dominant = i;
        int best = dominant;
        double bestScore = -1;
        for (int i = 0; i < colors.length; i++) {
            double r = ((colors[i] >> 16) & 255) / 255.0;
            double g = ((colors[i] >> 8) & 255) / 255.0;
            double b = (colors[i] & 255) / 255.0;
            double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            double saturation = max == 0 ? 0 : (max - min) / max;
            if (populations[i] < populations[dominant] * .12 || saturation < .22 || max < .14) continue;
            double score = Math.pow(populations[i], .8) * (.6 + saturation * .4);
            if (score > bestScore) { bestScore = score; best = i; }
        }
        return colors[best] | 0xff000000;
    }

    /** Secondary palette hue, unlike the primary whenever the cover permits it. */
    public static int accent(int[] colors, int[] populations, int primary) {
        if (colors.length == 0 || colors.length != populations.length) return primary;
        int maxPopulation = 1;
        for (int p : populations) maxPopulation = Math.max(maxPopulation, p);
        double mainHue = hue(primary), bestScore = -1;
        int best = primary;
        for (int i = 0; i < colors.length; i++) {
            if (populations[i] < maxPopulation * .025) continue;
            int color = colors[i];
            double r = ((color >> 16) & 255) / 255.0;
            double g = ((color >> 8) & 255) / 255.0;
            double b = (color & 255) / 255.0;
            double max = Math.max(r, Math.max(g, b));
            double min = Math.min(r, Math.min(g, b));
            double saturation = max == 0 ? 0 : (max - min) / max;
            double difference = Math.abs(hue(color) - mainHue);
            difference = Math.min(difference, 360 - difference);
            if (saturation < .25 || max < .20 || difference < 33) continue;
            double score = Math.min(difference / 145, 1) * .54
                + saturation * .29 + Math.sqrt(populations[i] / (double) maxPopulation) * .17;
            if (score > bestScore) { bestScore = score; best = color; }
        }
        return best | 0xff000000;
    }

    private static double hue(int color) {
        double red = (color >> 16) & 255, green = (color >> 8) & 255, blue = color & 255;
        double degrees = Math.toDegrees(Math.atan2(Math.sqrt(3) * (green - blue), 2 * red - green - blue));
        return degrees < 0 ? degrees + 360 : degrees;
    }

    /** Keep the hue/chroma while guaranteeing contrast for white player labels. */
    public static int surface(int argb) {
        double low = 0, high = 1;
        for (int i = 0; i < 18; i++) {
            double factor = (low + high) / 2;
            if (luminance(scale(argb, factor)) > .14) high = factor; else low = factor;
        }
        return scale(argb, low);
    }

    public static double luminance(int argb) {
        return .2126 * linear((argb >> 16) & 255) + .7152 * linear((argb >> 8) & 255) + .0722 * linear(argb & 255);
    }

    private static double linear(int component) {
        double c = component / 255.0;
        return c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4);
    }

    private static int scale(int argb, double factor) {
        return 0xff000000 | ((int)Math.round(((argb >> 16) & 255) * factor) << 16)
            | ((int)Math.round(((argb >> 8) & 255) * factor) << 8) | (int)Math.round((argb & 255) * factor);
    }
}
