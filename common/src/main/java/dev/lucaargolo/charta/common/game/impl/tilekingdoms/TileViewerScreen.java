package dev.lucaargolo.charta.common.game.impl.tilekingdoms;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class TileViewerScreen extends Screen {

    private static final int CELL = 36;  // tile preview size
    private static final int GAP = 4;
    private static final int COLS = 8;
    private static final int PAD = 12;

    private int scrollY = 0;
    private final Screen parent;

    // Same colours as TileKingdomsScreen
    private static final int C_FIELD = 0xFF4CAF50;
    private static final int C_FIELD2 = 0xFF388E3C;
    private static final int C_ROAD = 0xFFF0E68C;
    private static final int C_ROAD_EDGE = 0xFFD4C870;
    private static final int C_CITY = 0xFF8D5A14;
    private static final int C_CITY2 = 0xFFA07820;
    private static final int C_MON = 0xFF6A1B9A;
    private static final int C_BORDER = 0xFF222222;

    public TileViewerScreen(Screen parent) {
        super(Component.literal("Tile Kingdoms — Tile Types"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        scrollY = 0;
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        TileType[] types = TileType.values();
        int rows = (types.length + COLS - 1) / COLS;
        int totalH = rows * (CELL + GAP) + PAD * 2 + 24;

        // Panel
        int panelX = (width - COLS * (CELL + GAP) + GAP - 2 * PAD) / 2;
        int panelY = Math.max(20, height / 2 - totalH / 2);

        g.fill(panelX - PAD, panelY - PAD, panelX + COLS * (CELL + GAP) - GAP + PAD, panelY + totalH, 0xEE111111);
        g.fill(panelX - PAD, panelY - PAD, panelX + COLS * (CELL + GAP) - GAP + PAD, panelY - PAD + 1, 0xFF444444);

        // Title
        String title = "Tile Kingdoms — " + types.length + " tile types";
        g.drawString(font, title, panelX, panelY - 14, 0xFFFFD700, true);

        // Tiles
        for (int i = 0; i < types.length; i++) {
            TileType type = types[i];
            int col = i % COLS, row = i / COLS;
            int tx = panelX + col * (CELL + GAP);
            int ty = panelY + row * (CELL + GAP) - scrollY;
            if (ty + CELL < 0 || ty > height) continue;

            // Draw tile with all 4 rotations hint
            drawTilePreview(g, tx, ty, CELL, type, 0);

            // Count badge
            if (type.count > 1) {
                String cnt = "×" + type.count;
                g.fill(tx, ty + CELL - 8, tx + font.width(cnt) + 2, ty + CELL, 0xBB000000);
                g.drawString(font, cnt, tx + 1, ty + CELL - 7, 0xFFFFFF55, false);
            }

            // Hover: show name
            if (mx >= tx && mx < tx + CELL && my >= ty && my < ty + CELL) {
                String name = type.name().replace('_', ' ').toLowerCase();
                g.fill(mx + 4, my - 12, mx + 4 + font.width(name) + 4, my, 0xDD000000);
                g.drawString(font, name, mx + 6, my - 11, 0xFFFFFFFF, false);
            }
        }

        // Instructions
        g.drawString(font, "ESC to close  •  Scroll to browse", panelX, panelY + totalH - 10, 0xFF888888, false);
        super.render(g, mx, my, pt);
    }

    private void drawTilePreview(GuiGraphics g, int px, int py, int ps, TileType type, int rot) {
        short val = PlacedTile.pack(type, rot);
        boolean checker = ((px / ps + py / ps) & 1) == 0;
        g.fill(px + 1, py + 1, px + ps - 1, py + ps - 1, checker ? C_FIELD : C_FIELD2);

        // City bands
        for (int dir = 0; dir < 4; dir++) {
            if (PlacedTile.edgeOf(val, dir) == TileType.Edge.CITY) drawCityBand(g, px, py, dir, ps);
        }
        if (type.connectedCity) {
            int ce = 0;
            for (int d = 0; d < 4; d++) if (PlacedTile.edgeOf(val, d) == TileType.Edge.CITY) ce++;
            if (ce >= 2) g.fill(px + ps / 4, py + ps / 4, px + 3 * ps / 4, py + 3 * ps / 4, C_CITY);
        }

        // Roads
        boolean[] re = new boolean[4];
        int rc = 0;
        for (int d = 0; d < 4; d++)
            if (PlacedTile.edgeOf(val, d) == TileType.Edge.ROAD) {
                re[d] = true;
                rc++;
            }
        if (rc == 2) {
            int dA = -1, dB = -1;
            for (int d = 0; d < 4; d++)
                if (re[d]) {
                    if (dA < 0) dA = d;
                    else dB = d;
                }
            drawRoadCorridor(g, px, py, ps, dA, dB);
        } else if (rc > 0) {
            for (int d = 0; d < 4; d++) if (re[d]) drawRoadHalf(g, px, py, ps, d);
        }

        // Monastery
        if (type.monastery) {
            int cx = px + ps / 2, cy = py + ps / 2, r = ps / 6;
            g.fill(cx - r, cy - 1, cx + r + 1, cy + 2, C_MON);
            g.fill(cx - 1, cy - r, cx + 2, cy + r + 1, C_MON);
            g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFD700);
        }

        // Border
        g.fill(px, py, px + ps, py + 1, C_BORDER);
        g.fill(px, py + ps - 1, px + ps, py + ps, C_BORDER);
        g.fill(px, py, px + 1, py + ps, C_BORDER);
        g.fill(px + ps - 1, py, px + ps, py + ps, C_BORDER);
    }

    private void drawCityBand(GuiGraphics g, int px, int py, int dir, int ps) {
        int t = ps / 4;
        switch (dir) {
            case 0 -> g.fill(px + 1, py + 1, px + ps - 1, py + t, C_CITY2);
            case 1 -> g.fill(px + ps - t, py + 1, px + ps - 1, py + ps - 1, C_CITY2);
            case 2 -> g.fill(px + 1, py + ps - t, px + ps - 1, py + ps - 1, C_CITY2);
            case 3 -> g.fill(px + 1, py + 1, px + t, py + ps - 1, C_CITY2);
        }
    }

    private void drawRoadCorridor(GuiGraphics g, int px, int py, int ps, int dA, int dB) {
        drawRoadHalf(g, px, py, ps, dA);
        drawRoadHalf(g, px, py, ps, dB);
    }

    private void drawRoadHalf(GuiGraphics g, int px, int py, int ps, int dir) {
        int cx = px + ps / 2, cy = py + ps / 2, hw = ps / 10 + 1, t = ps / 4;
        switch (dir) {
            case 0 -> {
                g.fill(cx - hw, py + 1, cx + hw, cy, C_ROAD);
                g.fill(cx - hw, py + 1, cx - hw + 1, cy, C_ROAD_EDGE);
                g.fill(cx + hw - 1, py + 1, cx + hw, cy, C_ROAD_EDGE);
            }
            case 1 -> {
                g.fill(cx, cy - hw, px + ps - 1, cy + hw, C_ROAD);
                g.fill(cx, cy - hw, px + ps - 1, cy - hw + 1, C_ROAD_EDGE);
                g.fill(cx, cy + hw - 1, px + ps - 1, cy + hw, C_ROAD_EDGE);
            }
            case 2 -> {
                g.fill(cx - hw, cy, cx + hw, py + ps - 1, C_ROAD);
                g.fill(cx - hw, cy, cx - hw + 1, py + ps - 1, C_ROAD_EDGE);
                g.fill(cx + hw - 1, cy, cx + hw, py + ps - 1, C_ROAD_EDGE);
            }
            case 3 -> {
                g.fill(px + 1, cy - hw, cx, cy + hw, C_ROAD);
                g.fill(px + 1, cy - hw, cx, cy - hw + 1, C_ROAD_EDGE);
                g.fill(px + 1, cy + hw - 1, cx, cy + hw, C_ROAD_EDGE);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scrollY -= (int) (sy * 10);
        scrollY = Math.max(0, scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) { // ESC
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}