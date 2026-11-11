# MapMaker

Fabric 1.21.11 client mod. Paste, upload or drag an image in and it becomes a map art schematic in your
`schematics` folder, ready for Litematica. No material picker, no questions.

## Use
- Press **J** (rebindable, "MapMaker" category) or the **MapMaker** button on the pause/title screen.
- **Ctrl+V** (Cmd+V on Mac) or **Paste** — works with a copied image, a copied file, a file path, or an image link.
- **Upload...** opens a file picker. You can also **drag an image onto the window**.
- It converts and saves instantly as `schematics/mapart_<name>.nbt`. Changing a setting re-saves the same file.

Defaults: **1x1 map (128x128), Flat, Dither on, Crop to fill.**
- Width/Height: 1-8 maps each (128 to 1024 blocks).
- Mode: **Flat** (one shade, easy to build) or **Staircase** (3 shades, better colours; uses the lowest possible stairs).
- Fit: Crop (fill the frame) or Stretch.
- Blocks / Carpets: carpets use the 16 wool carpet colours, each on a cobblestone support block.
- Key: click it in the menu and press any key to rebind (also in Options -> Controls -> MapMaker).

## Placing it
The schematic is one block longer than the image: the extra row on the **north** edge (cobblestone) is the support row
that makes the top row of the map shade correctly. Line the image area up with the map grid: the first image row and
column go on the map's top-left corner, with the cobblestone row just outside it to the north.

Blocks are solid, non-falling and survival-obtainable (concrete, terracotta, wood, ores, nether blocks, etc.). Water is
never used. Litematica's Material List shows exactly what to gather.

## Build
Push to GitHub — the Actions workflow builds the jar (Actions tab -> latest run -> MapMaker artifact).
Locally: `gradle build` with Java 21 and Gradle 9.2+. The jar is in `build/libs/`.
