package com.dan.brocken.tools;

import com.dan.brocken.ui.AppIcon;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 18 (F24 A): das Programmsymbol als Dateien. Schreibt die gemalten Größen 16 bis 256 px nach
 * {@code src/com/dan/brocken/ui/icon/brocken_NN.png} (die App lädt sie aus dem Jar) und {@code Brocken.ico} in den
 * Projektordner (für die Verknüpfung; jede Größe als PNG im ICO, wie es Windows seit Vista liest). {@code ant symbol}.
 */
public final class Symbol {
    private Symbol() { }

    public static void main(String[] args) throws IOException {
        File dir = new File(args.length > 0 ? args[0] : "src/com/dan/brocken/ui/icon");
        File ico = new File(args.length > 1 ? args[1] : "Brocken.ico");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Ordner " + dir + " lässt sich nicht anlegen");
        List<byte[]> pngs = new ArrayList<>();
        for (int s : AppIcon.SIZES) {
            BufferedImage im = AppIcon.paint(s);
            ImageIO.write(im, "png", new File(dir, "brocken_" + s + ".png"));
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            ImageIO.write(im, "png", b);
            pngs.add(b.toByteArray());
        }
        Files.write(ico.toPath(), ico(AppIcon.SIZES, pngs));
        System.out.println("Symbol: " + AppIcon.SIZES.length + " Größen nach " + dir + ", " + ico + " (" + ico.length() / 1024 + " KB)");
    }

    /** Eine ICO-Datei aus PNGs (Kopf 6 Bytes, je Bild 16 Bytes Verzeichnis, dann die PNGs). */
    public static byte[] ico(int[] sizes, List<byte[]> pngs) {
        int n = sizes.length, off = 6 + 16 * n, total = off;
        for (byte[] p : pngs) total += p.length;
        ByteBuffer bb = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        bb.putShort((short) 0).putShort((short) 1).putShort((short) n);
        for (int i = 0; i < n; i++) {
            int s = sizes[i];
            bb.put((byte) (s >= 256 ? 0 : s)).put((byte) (s >= 256 ? 0 : s)).put((byte) 0).put((byte) 0);
            bb.putShort((short) 1).putShort((short) 32).putInt(pngs.get(i).length).putInt(off);
            off += pngs.get(i).length;
        }
        for (byte[] p : pngs) bb.put(p);
        return bb.array();
    }
}
