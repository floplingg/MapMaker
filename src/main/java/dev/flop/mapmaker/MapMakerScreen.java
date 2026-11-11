package dev.flop.mapmaker;

import dev.flop.mapmaker.core.MapArtConverter;
import dev.flop.mapmaker.core.StructureWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class MapMakerScreen extends Screen {

    // Settings persist while the game is open
    private static int mapsWide = 1, mapsTall = 1;
    private static boolean staircase = false, dither = true;
    private static MapArtConverter.Fit fit = MapArtConverter.Fit.CROP;
    private static final int MAX_MAPS = 8;

    // Last image also persists, so reopening the menu keeps your work
    private static ImageSources.Loaded image;
    private static String outName;
    private static volatile Preview preview;
    private static volatile String status = "", status2 = "";
    private static volatile int statusColor = 0xFFAAAAAA;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MapMaker worker");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicInteger GENERATION = new AtomicInteger();

    private final Screen parent;
    private Button widthBtn, heightBtn, modeBtn, ditherBtn, fitBtn;
    private boolean pasteHeld;

    /** Downsampled preview stored as horizontal runs (x0, x1, argb) per row, so drawing stays cheap. */
    private record Preview(int cols, int rows, int[][] runs) {}

    public MapMakerScreen(Screen parent) {
        super(Component.literal("MapMaker"));
        this.parent = parent;
    }

    public static Path schematicsDir() {
        return FabricLoader.getInstance().getGameDir().resolve("schematics");
    }

    // ---------------------------------------------------------------- layout
    private int box() { return Math.max(64, Math.min(192, Math.min(this.height - 70, this.width / 2 - 30))); }
    private int boxX() { return this.width / 2 - box() - 10; }
    private int boxY() { return 28; }

    @Override
    protected void init() {
        int x = this.width / 2 + 10, w = 160, y = 28, row = 24;

        addRenderableWidget(Button.builder(Component.literal(ImageSources.MAC ? "Paste (Cmd+V)" : "Paste (Ctrl+V)"), b -> paste())
                .bounds(x, y, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Upload..."), b -> upload())
                .bounds(x + 82, y, 78, 20).build());
        y += row;

        addRenderableWidget(Button.builder(Component.literal("-"), b -> { mapsWide = Math.max(1, mapsWide - 1); changed(); }).bounds(x, y, 20, 20).build());
        widthBtn = addRenderableWidget(Button.builder(Component.empty(), b -> { mapsWide = mapsWide % MAX_MAPS + 1; changed(); }).bounds(x + 22, y, w - 44, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> { mapsWide = Math.min(MAX_MAPS, mapsWide + 1); changed(); }).bounds(x + w - 20, y, 20, 20).build());
        y += row;

        addRenderableWidget(Button.builder(Component.literal("-"), b -> { mapsTall = Math.max(1, mapsTall - 1); changed(); }).bounds(x, y, 20, 20).build());
        heightBtn = addRenderableWidget(Button.builder(Component.empty(), b -> { mapsTall = mapsTall % MAX_MAPS + 1; changed(); }).bounds(x + 22, y, w - 44, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> { mapsTall = Math.min(MAX_MAPS, mapsTall + 1); changed(); }).bounds(x + w - 20, y, 20, 20).build());
        y += row;

        modeBtn = addRenderableWidget(Button.builder(Component.empty(), b -> { staircase = !staircase; changed(); }).bounds(x, y, w, 20).build());
        y += row;

        ditherBtn = addRenderableWidget(Button.builder(Component.empty(), b -> { dither = !dither; changed(); }).bounds(x, y, 78, 20).build());
        fitBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            fit = fit == MapArtConverter.Fit.CROP ? MapArtConverter.Fit.STRETCH : MapArtConverter.Fit.CROP; changed();
        }).bounds(x + 82, y, 78, 20).build());
        y += row;

        addRenderableWidget(Button.builder(Component.literal("Open schematics folder"), b -> {
            try { Files.createDirectories(schematicsDir()); } catch (Exception ignored) {}
            openFolder(schematicsDir());
        }).bounds(x, y, w, 20).build());
        y += row;

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x, y, w, 20).build());

        refreshLabels();
        if (image == null && status.isEmpty()) {
            setStatus("Paste, upload or drag an image in.", "It saves straight to your schematics folder.", 0xFFAAAAAA);
        }
    }

    private void refreshLabels() {
        widthBtn.setMessage(Component.literal("Width: " + mapsWide + (mapsWide == 1 ? " map" : " maps") + " (" + mapsWide * 128 + ")"));
        heightBtn.setMessage(Component.literal("Height: " + mapsTall + (mapsTall == 1 ? " map" : " maps") + " (" + mapsTall * 128 + ")"));
        modeBtn.setMessage(Component.literal("Mode: " + (staircase ? "Staircase (3 shades)" : "Flat")));
        ditherBtn.setMessage(Component.literal("Dither: " + (dither ? "On" : "Off")));
        fitBtn.setMessage(Component.literal("Fit: " + (fit == MapArtConverter.Fit.CROP ? "Crop" : "Stretch")));
    }

    private void changed() {
        refreshLabels();
        if (image != null) convertAndSave();
    }

    // ---------------------------------------------------------------- input
    @Override
    public void tick() {
        long win = GLFW.glfwGetCurrentContext();
        if (win == 0) return;
        boolean mod = ImageSources.MAC
                ? GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SUPER) == GLFW.GLFW_PRESS
                : GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean v = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_V) == GLFW.GLFW_PRESS;
        boolean down = mod && v;
        if (down && !pasteHeld) paste();
        pasteHeld = down;
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        for (Path p : paths) {
            try {
                load(ImageSources.fromFile(p));
                return;
            } catch (Exception e) {
                setStatus("Couldn't open that file", e.getMessage(), 0xFFFF5555);
            }
        }
    }

    private void paste() {
        setStatus("Reading clipboard...", "", 0xFFAAAAAA);
        WORKER.execute(() -> {
            try { load(ImageSources.fromClipboard()); }
            catch (Exception e) { setStatus("Nothing to paste", e.getMessage(), 0xFFFF5555); }
        });
    }

    private void upload() {
        try {
            ImageSources.Loaded l = ImageSources.pickFile();
            if (l != null) load(l);
        } catch (Exception e) {
            setStatus("Couldn't open that file", e.getMessage(), 0xFFFF5555);
        }
    }

    // ---------------------------------------------------------------- work
    private static synchronized void load(ImageSources.Loaded l) {
        image = l;
        String base = l.name().replaceAll("\\.[A-Za-z0-9]{2,5}$", "").replaceAll("[^A-Za-z0-9_-]+", "_");
        if (base.isBlank() || base.equals("pasted") || base.equals("_")) base = "pasted_" + System.currentTimeMillis() / 1000;
        base = "mapart_" + base;
        String name = base;
        for (int i = 2; Files.exists(schematicsDir().resolve(name + ".nbt")); i++) name = base + "_" + i;
        outName = name;
        convertAndSave();
    }

    private static void convertAndSave() {
        final int gen = GENERATION.incrementAndGet();
        final ImageSources.Loaded img = image;
        final String name = outName;
        final MapArtConverter.Settings s = new MapArtConverter.Settings(mapsWide, mapsTall, staircase, dither, fit);
        setStatus("Converting " + s.width() + "x" + s.height() + "...", "", 0xFFAAAAAA);
        WORKER.execute(() -> {
            if (gen != GENERATION.get()) return; // a newer request is queued
            try {
                MapArtConverter.Result r = MapArtConverter.convert(img.image(), s);
                preview = buildPreview(r, 192);
                if (gen != GENERATION.get()) return;
                Path file = schematicsDir().resolve(name + ".nbt");
                StructureWriter.Summary sum = StructureWriter.write(r, file);
                String shape = s.width() + "x" + s.height() + (s.staircase() ? " staircase, " + sum.sizeY() + " tall" : " flat");
                setStatus("Saved " + name + ".nbt  (" + shape + ")",
                        String.format("%,d blocks, %d kinds - open it in Litematica", sum.blocks(), sum.materials().size()),
                        0xFF55FF55);
            } catch (Throwable t) {
                setStatus("Conversion failed", String.valueOf(t.getMessage()), 0xFFFF5555);
            }
        });
    }

    private static Preview buildPreview(MapArtConverter.Result r, int maxCells) {
        int stride = Math.max(1, (int) Math.ceil(Math.max(r.width, r.height) / (double) maxCells));
        int cols = r.width / stride, rows = r.height / stride;
        int[][] runs = new int[rows][];
        int[] tmp = new int[cols * 3];
        for (int z = 0; z < rows; z++) {
            int n = 0, start = 0, cur = 0;
            for (int x = 0; x <= cols; x++) {
                int c = x < cols ? 0xFF000000 | r.preview[(z * stride) * r.width + x * stride] : 0;
                if (x == 0) { cur = c; continue; }
                if (c != cur) { tmp[n++] = start; tmp[n++] = x; tmp[n++] = cur; start = x; cur = c; }
            }
            runs[z] = java.util.Arrays.copyOf(tmp, n);
        }
        return new Preview(cols, rows, runs);
    }

    private static void openFolder(Path dir) {
        String os = System.getProperty("os.name", "").toLowerCase();
        String[] cmd = os.contains("win") ? new String[]{"explorer", dir.toAbsolutePath().toString()}
                : os.contains("mac") ? new String[]{"open", dir.toAbsolutePath().toString()}
                : new String[]{"xdg-open", dir.toAbsolutePath().toString()};
        try { new ProcessBuilder(cmd).start(); }
        catch (Exception e) { setStatus("Couldn't open the folder", dir.toAbsolutePath().toString(), 0xFFFF5555); }
    }

    private static void setStatus(String a, String b, int color) {
        status = a; status2 = b == null ? "" : b; statusColor = color;
    }

    // ---------------------------------------------------------------- draw
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);

        int B = box(), bx = boxX(), by = boxY();
        g.fill(bx - 1, by - 1, bx + B + 1, by + B + 1, 0xFF555555);
        g.fill(bx, by, bx + B, by + B, 0xFF101010);

        Preview p = preview;
        if (p != null && image != null) {
            int cell = Math.max(1, B / Math.max(p.cols(), p.rows()));
            int ox = bx + (B - p.cols() * cell) / 2, oy = by + (B - p.rows() * cell) / 2;
            for (int z = 0; z < p.rows(); z++) {
                int[] run = p.runs()[z];
                int y0 = oy + z * cell;
                for (int i = 0; i < run.length; i += 3) {
                    g.fill(ox + run[i] * cell, y0, ox + run[i + 1] * cell, y0 + cell, run[i + 2]);
                }
            }
        } else {
            g.drawCenteredString(this.font, "Drop an image here", bx + B / 2, by + B / 2 - 10, 0xFF888888);
            g.drawCenteredString(this.font, "or press " + (ImageSources.MAC ? "Cmd" : "Ctrl") + "+V", bx + B / 2, by + B / 2 + 2, 0xFF888888);
        }

        int ty = by + B + 8;
        g.drawCenteredString(this.font, status, this.width / 2, ty, statusColor);
        if (!status2.isEmpty()) g.drawCenteredString(this.font, status2, this.width / 2, ty + 11, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
