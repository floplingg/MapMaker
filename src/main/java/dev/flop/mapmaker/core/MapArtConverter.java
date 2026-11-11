package dev.flop.mapmaker.core;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** Turns any image into map-art blocks. Pure Java, no Minecraft classes. */
public final class MapArtConverter {

    public enum Fit { CROP, STRETCH }

    public record Settings(int mapsWide, int mapsTall, boolean staircase, boolean dither, Fit fit, boolean carpets) {
        public int width() { return mapsWide * 128; }
        public int height() { return mapsTall * 128; }
    }

    /** Result: colour entry index + shade per pixel, the preview colours, and block heights. */
    public static final class Result {
        public final int width, height;
        public final boolean carpets;
        public final int[] entry;   // index into MapColors.ENTRIES, [z * width + x]
        public final int[] shade;   // 0/1/2
        public final int[] preview; // RGB the map will actually show
        public final int[] y;       // block height per pixel
        public final int[] nooblineY; // height of the support row north of each column
        public int maxY;

        Result(int w, int h, boolean carpets) {
            width = w; height = h; this.carpets = carpets;
            entry = new int[w * h]; shade = new int[w * h]; preview = new int[w * h];
            y = new int[w * h]; nooblineY = new int[w];
        }
    }

    // ---------- candidate colours (precomputed in Lab) ----------
    private record Cand(int entry, int shade, int rgb, double L, double A, double B) {}

    private static Cand[] candidates(boolean staircase, java.util.List<MapColors.Entry> pal) {
        int[] shades = staircase ? new int[]{0, 1, 2} : new int[]{1};
        Cand[] out = new Cand[pal.size() * shades.length];
        int i = 0;
        for (int ei = 0; ei < pal.size(); ei++) {
            for (int s : shades) {
                int rgb = MapColors.shadedRgb(pal.get(ei), s);
                double[] lab = lab((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
                out[i++] = new Cand(ei, s, rgb, lab[0], lab[1], lab[2]);
            }
        }
        return out;
    }

    public static Result convert(BufferedImage src, Settings s) {
        int w = s.width(), h = s.height();
        int[] px = resize(src, w, h, s.fit());
        Cand[] cands = candidates(s.staircase(), MapColors.palette(s.carpets()));
        Map<Integer, Cand> cache = new HashMap<>();
        Result res = new Result(w, h, s.carpets());

        float[] r = new float[w * h], g = new float[w * h], b = new float[w * h];
        for (int i = 0; i < px.length; i++) {
            r[i] = (px[i] >> 16) & 255; g[i] = (px[i] >> 8) & 255; b[i] = px[i] & 255;
        }

        for (int z = 0; z < h; z++) {
            boolean ltr = (z & 1) == 0; // serpentine scan for cleaner dithering
            for (int k = 0; k < w; k++) {
                int x = ltr ? k : w - 1 - k;
                int i = z * w + x;
                int cr = clamp(r[i]), cg = clamp(g[i]), cb = clamp(b[i]);
                int key = (cr << 16) | (cg << 8) | cb;
                Cand c = cache.computeIfAbsent(key, kk -> nearest(cands, cr, cg, cb));
                res.entry[i] = c.entry; res.shade[i] = c.shade; res.preview[i] = c.rgb;

                if (s.dither()) {
                    // error from the clamped value, damped, so it can't snowball into halos/smears
                    final float damp = 0.8f;
                    float er = (cr - ((c.rgb >> 16) & 255)) * damp;
                    float eg = (cg - ((c.rgb >> 8) & 255)) * damp;
                    float eb = (cb - (c.rgb & 255)) * damp;
                    int dx = ltr ? 1 : -1;
                    spread(r, g, b, w, h, x + dx, z, er, eg, eb, 7 / 16f);
                    spread(r, g, b, w, h, x - dx, z + 1, er, eg, eb, 3 / 16f);
                    spread(r, g, b, w, h, x, z + 1, er, eg, eb, 5 / 16f);
                    spread(r, g, b, w, h, x + dx, z + 1, er, eg, eb, 1 / 16f);
                }
            }
        }
        computeHeights(res);
        return res;
    }

    /**
     * Minimal-height staircase. Each column is a chain: noobline, row 0, row 1...
     * shade 2 => strictly higher than the block north of it, 0 => strictly lower, 1 => same.
     * Equal runs are merged, then heights = max(rising run ending here, falling run starting here),
     * which is the lowest valid layout (much flatter than a classic staircase).
     */
    private static void computeHeights(Result res) {
        int w = res.width, h = res.height;
        int n = h + 1; // node 0 = noobline
        int[] rel = new int[n]; // rel[i] = relation of node i to node i-1: +1 up, -1 down, 0 equal
        int[] L = new int[n], R = new int[n], hh = new int[n];
        int maxY = 0;
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) rel[z + 1] = res.shade[z * w + x] - 1;
            L[0] = 0;
            for (int i = 1; i < n; i++) L[i] = rel[i] > 0 ? L[i - 1] + 1 : rel[i] == 0 ? L[i - 1] : 0;
            R[n - 1] = 0;
            for (int i = n - 2; i >= 0; i--) R[i] = rel[i + 1] < 0 ? R[i + 1] + 1 : rel[i + 1] == 0 ? R[i + 1] : 0;
            // equal-run nodes must share one height: take the run's max
            int i = 0;
            while (i < n) {
                int j = i, m = 0;
                while (j + 1 < n && rel[j + 1] == 0) j++;
                for (int t = i; t <= j; t++) m = Math.max(m, Math.max(L[t], R[t]));
                for (int t = i; t <= j; t++) hh[t] = m;
                i = j + 1;
            }
            res.nooblineY[x] = hh[0];
            maxY = Math.max(maxY, hh[0]);
            for (int z = 0; z < h; z++) {
                res.y[z * w + x] = hh[z + 1];
                maxY = Math.max(maxY, hh[z + 1]);
            }
        }
        res.maxY = maxY;
    }

    private static void spread(float[] r, float[] g, float[] b, int w, int h, int x, int z,
                               float er, float eg, float eb, float f) {
        if (x < 0 || x >= w || z >= h) return;
        int i = z * w + x;
        r[i] += er * f; g[i] += eg * f; b[i] += eb * f;
    }

    private static Cand nearest(Cand[] cands, int r, int g, int b) {
        double[] lab = lab(r, g, b);
        Cand best = null; double bd = Double.MAX_VALUE;
        for (Cand c : cands) {
            double dl = lab[0] - c.L, da = lab[1] - c.A, db = lab[2] - c.B;
            double d = dl * dl + da * da + db * db;
            if (d < bd) { bd = d; best = c; }
        }
        return best;
    }

    private static int clamp(float v) { return v < 0 ? 0 : v > 255 ? 255 : Math.round(v); }

    // ---------- resizing ----------
    private static int[] resize(BufferedImage src, int w, int h, Fit fit) {
        BufferedImage img = flatten(src);
        int sx = 0, sy = 0, sw = img.getWidth(), sh = img.getHeight();
        if (fit == Fit.CROP) { // fill the frame, crop the overflow from the centre
            double target = (double) w / h, have = (double) sw / sh;
            if (have > target) { int nw = (int) Math.round(sh * target); sx = (sw - nw) / 2; sw = nw; }
            else { int nh = (int) Math.round(sw / target); sy = (sh - nh) / 2; sh = nh; }
            img = img.getSubimage(sx, sy, Math.max(1, sw), Math.max(1, sh));
        }
        // step down by halves for a clean downscale of big photos
        while (img.getWidth() / 2 >= w && img.getHeight() / 2 >= h) {
            img = scale(img, img.getWidth() / 2, img.getHeight() / 2);
        }
        img = scale(img, w, h);
        return img.getRGB(0, 0, w, h, null, 0, w);
    }

    private static BufferedImage flatten(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(java.awt.Color.WHITE); // transparent areas become white
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    // ---------- sRGB -> CIELAB ----------
    private static double[] lab(int r, int g, int b) {
        double R = lin(r), G = lin(g), B = lin(b);
        double X = (R * 0.4124 + G * 0.3576 + B * 0.1805) / 0.95047;
        double Y = (R * 0.2126 + G * 0.7152 + B * 0.0722);
        double Z = (R * 0.0193 + G * 0.1192 + B * 0.9505) / 1.08883;
        double fx = f(X), fy = f(Y), fz = f(Z);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }
    private static double lin(int c) { double v = c / 255.0; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }
    private static double f(double t) { return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116; }

    private MapArtConverter() {}
}
