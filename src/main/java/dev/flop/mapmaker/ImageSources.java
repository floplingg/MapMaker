package dev.flop.mapmaker;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** Everywhere an image can come from: clipboard image, copied file, copied path/URL, file picker, drag-drop. */
public final class ImageSources {

    public record Loaded(BufferedImage image, String name) {}

    public static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");

    /** Minecraft starts Java "headless", which blocks the system clipboard. Undo that (not on macOS, where it clashes with GLFW). */
    public static void enableAwtClipboard() {
        if (!MAC) System.setProperty("java.awt.headless", "false");
    }

    public static Loaded fromClipboard() throws Exception {
        // 1) real image / copied file via the system clipboard
        if (!MAC) {
            try {
                Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
                if (cb.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                    Image img = (Image) cb.getData(DataFlavor.imageFlavor);
                    BufferedImage bi = toBuffered(img);
                    if (bi != null) return new Loaded(bi, "pasted");
                }
                if (cb.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) cb.getData(DataFlavor.javaFileListFlavor);
                    for (File f : files) {
                        Loaded l = fromFile(f.toPath());
                        if (l != null) return l;
                    }
                }
            } catch (Throwable ignored) {
                // fall through to text clipboard
            }
        }
        // 2) text: a file path, file:// URI, image URL or data: URI
        String text = GLFW.glfwGetClipboardString(GLFW.glfwGetCurrentContext());
        if (text != null && !(text = text.trim().replace("\"", "")).isEmpty()) {
            Loaded l = fromText(text);
            if (l != null) return l;
        }
        throw new Exception(MAC
                ? "Copy the image's file or link (Cmd+C in Finder / 'Copy Image Address'), or use Upload"
                : "No image on the clipboard");
    }

    private static Loaded fromText(String text) throws Exception {
        if (text.startsWith("data:image")) {
            byte[] bytes = Base64.getDecoder().decode(text.substring(text.indexOf(',') + 1));
            return new Loaded(ImageIO.read(new ByteArrayInputStream(bytes)), "pasted");
        }
        if (text.startsWith("http://") || text.startsWith("https://")) {
            HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(text)).timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Mozilla/5.0 MapMaker").build();
            byte[] bytes = http.send(req, HttpResponse.BodyHandlers.ofByteArray()).body();
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) throw new Exception("That link isn't a PNG/JPG/GIF/BMP image");
            String name = text.replaceAll("[?#].*$", "");
            return new Loaded(img, name.substring(name.lastIndexOf('/') + 1));
        }
        Path p = text.startsWith("file:") ? Path.of(URI.create(text)) : Path.of(text);
        return Files.isRegularFile(p) ? fromFile(p) : null;
    }

    public static Loaded fromFile(Path p) throws Exception {
        BufferedImage img = ImageIO.read(p.toFile());
        if (img == null) throw new Exception(p.getFileName() + " isn't a PNG/JPG/GIF/BMP image");
        return new Loaded(img, p.getFileName().toString());
    }

    /** Native "Open file" dialog (bundled with Minecraft's LWJGL). Returns null if cancelled. */
    public static Loaded pickFile() throws Exception {
        String chosen;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String[] pats = {"*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"};
            PointerBuffer filters = stack.mallocPointer(pats.length);
            for (String s : pats) filters.put(stack.UTF8(s));
            filters.flip();
            chosen = TinyFileDialogs.tinyfd_openFileDialog("Choose an image for MapMaker",
                    System.getProperty("user.home") + File.separator, filters, "Images", false);
        }
        return chosen == null ? null : fromFile(Path.of(chosen));
    }

    private static BufferedImage toBuffered(Image img) {
        if (img instanceof BufferedImage bi) return bi;
        int w = img.getWidth(null), h = img.getHeight(null);
        if (w <= 0 || h <= 0) return null;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return out;
    }

    private ImageSources() {}
}
