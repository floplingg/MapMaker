package dev.flop.mapmaker.core;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Writes a vanilla structure (.nbt) file, which Litematica loads directly from the schematics folder.
 * Layout: X = image columns, Z = image rows (north -> south), with a 1-block support row
 * ("noobline") at z = 0 so the top row of the map shades correctly.
 */
public final class StructureWriter {

    /** Minecraft 1.21 data version; newer games upgrade it automatically on load. */
    public static final int DATA_VERSION = 3953;

    public record Summary(int blocks, int sizeX, int sizeY, int sizeZ, Map<String, Integer> materials) {}

    public static Summary write(MapArtConverter.Result res, Path file) throws IOException {
        int w = res.width, h = res.height;

        // palette: state index per colour entry, plus the noobline block
        List<String[]> palette = new ArrayList<>();
        int[] stateOf = new int[MapColors.ENTRIES.size()];
        java.util.Arrays.fill(stateOf, -1);
        Map<String, Integer> materials = new LinkedHashMap<>();
        for (int i = 0; i < w * h; i++) {
            int e = res.entry[i];
            if (stateOf[e] < 0) {
                MapColors.Entry en = MapColors.ENTRIES.get(e);
                stateOf[e] = palette.size();
                palette.add(new String[]{en.block(), en.props()});
            }
            materials.merge(MapColors.ENTRIES.get(e).block(), 1, Integer::sum);
        }
        int noobState = -1;
        for (int p = 0; p < palette.size(); p++) if (palette.get(p)[0].equals(MapColors.NOOBLINE_BLOCK)) noobState = p;
        if (noobState < 0) { noobState = palette.size(); palette.add(new String[]{MapColors.NOOBLINE_BLOCK, ""}); }
        materials.merge(MapColors.NOOBLINE_BLOCK, w, Integer::sum);

        int total = w * h + w;
        int sizeY = res.maxY + 1, sizeZ = h + 1;

        file = file.toAbsolutePath();
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream fo = Files.newOutputStream(tmp);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(fo, 1 << 16), 1 << 16))) {
            out.writeByte(10); str(out, "");               // root compound

            out.writeByte(3); str(out, "DataVersion"); out.writeInt(DATA_VERSION);

            out.writeByte(9); str(out, "size"); out.writeByte(3); out.writeInt(3);
            out.writeInt(w); out.writeInt(sizeY); out.writeInt(sizeZ);

            out.writeByte(9); str(out, "palette"); out.writeByte(10); out.writeInt(palette.size());
            for (String[] p : palette) {
                out.writeByte(8); str(out, "Name"); str(out, p[0]);
                if (!p[1].isEmpty()) {
                    out.writeByte(10); str(out, "Properties");
                    for (String kv : p[1].split(",")) {
                        String[] parts = kv.split("=");
                        out.writeByte(8); str(out, parts[0]); str(out, parts[1]);
                    }
                    out.writeByte(0);
                }
                out.writeByte(0);
            }

            out.writeByte(9); str(out, "blocks"); out.writeByte(10); out.writeInt(total);
            for (int x = 0; x < w; x++) block(out, x, res.nooblineY[x], 0, noobState);
            for (int z = 0; z < h; z++) {
                for (int x = 0; x < w; x++) {
                    int i = z * w + x;
                    block(out, x, res.y[i], z + 1, stateOf[res.entry[i]]);
                }
            }

            out.writeByte(9); str(out, "entities"); out.writeByte(0); out.writeInt(0);
            out.writeByte(0); // end root
        }
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        // sort materials, most-used first
        Map<String, Integer> sorted = new LinkedHashMap<>();
        materials.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(en -> sorted.put(en.getKey(), en.getValue()));
        return new Summary(total, w, sizeY, sizeZ, sorted);
    }

    private static void block(DataOutputStream out, int x, int y, int z, int state) throws IOException {
        out.writeByte(9); str(out, "pos"); out.writeByte(3); out.writeInt(3);
        out.writeInt(x); out.writeInt(y); out.writeInt(z);
        out.writeByte(3); str(out, "state"); out.writeInt(state);
        out.writeByte(0);
    }

    private static void str(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.writeShort(b.length);
        out.write(b);
    }

    private StructureWriter() {}
}
