package dev.lucaargolo.charta.common.game.impl.tilekingdoms;

import dev.lucaargolo.charta.client.render.screen.GameScreen;
import dev.lucaargolo.charta.common.ChartaMod;
import dev.lucaargolo.charta.common.game.api.CardPlayer;
import dev.lucaargolo.charta.common.network.TileKingdomsActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TileKingdomsScreen extends GameScreen<TileKingdomsGame, TileKingdomsMenu> {

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int CELL = 24;  // tile size px
    // Board occupies: topBar(28) .. height-bottomBar(63) vertically, full width horizontally
    private static final int TOP_BAR = 28;
    private static final int BOTTOM_BAR = 63;

    private int boardW, boardH;

    // Camera
    private float camX = 0f, camY = 0f;
    private float zoom = 1.0f; // 0.35 .. 2.5, Ctrl+scroll
    private double dragStartMX, dragStartMY;
    private float dragStartCamX, dragStartCamY;
    private boolean dragging = false;

    // Colours
    private static final int C_FIELD = 0xFF4CAF50;
    private static final int C_FIELD2 = 0xFF388E3C;
    private static final int C_ROAD = 0xFFF0E68C;
    private static final int C_ROAD_EDGE = 0xFFD4C870;
    private static final int C_CITY = 0xFF8D5A14;
    private static final int C_CITY2 = 0xFFA07820;
    private static final int C_MON = 0xFF6A1B9A;
    private static final int C_BORDER = 0xFF222222;
    private static final int C_VALID = 0x5500FF00;
    private static final int C_HOVER = 0x99FFFF55;
    private static final int C_BAR = 0xEE0D1A0D;

    public TileKingdomsScreen(TileKingdomsMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        // Large screen — uses full game window area
        imageWidth = 320;
        imageHeight = 240;
    }

    private void sendAction(int a) {
        ChartaMod.getPacketManager().sendToServer(new TileKingdomsActionPayload(menu.containerId, a));
    }

    // ── Coordinate helpers ────────────────────────────────────────────────────
    // Board starts at screen y = TOP_BAR, width=full, height=height-TOP_BAR-BOTTOM_BAR
    private int boardTop() {
        return TOP_BAR;
    }           // screen y where board starts

    private int boardLeft() {
        return 0;
    }                 // screen x where board starts

    private int cs() {
        return Math.max(8, Math.round(CELL * zoom));
    }

    private int tileScreenX(int lx) {
        return boardLeft() + boardW / 2 + Math.round((lx - camX) * cs());
    }

    private int tileScreenY(int ly) {
        return boardTop() + boardH / 2 + Math.round((ly - camY) * cs());
    }

    private int screenToTileX(double sx) {
        return (int) Math.floor((sx - boardLeft() - boardW / 2.0) / cs() + camX);
    }

    private int screenToTileY(double sy) {
        return (int) Math.floor((sy - boardTop() - boardH / 2.0) / cs() + camY);
    }

    @Override
    protected void init() {
        super.init();
        // Clear texture caches in case resource pack changed
        TEX_AVAILABLE.clear();
        TEX_MISSING.clear();
        boardW = width;
        boardH = height - TOP_BAR - BOTTOM_BAR;
        camX = 0;
        camY = 0;
    }

    // ── renderBg ──────────────────────────────────────────────────────────────
    @Override
    protected void renderBg(@NotNull GuiGraphics g, float pt, int mx, int my) {
        // Full-screen background — no separator line
        // Background only below topBar so player heads remain visible
        g.fill(0, TOP_BAR, width, height - BOTTOM_BAR, 0xFF1B2A1B);

        short[] grid = menu.getBoardGrid();
        int[] claims = menu.getClaimsArray();
        TileType ct = menu.getCurrentTile();
        int rot = menu.getCurrentRotation();
        boolean myTurn = isMyTurn();
        int phase = menu.getPhase();

        // ── Full-screen background grid ──────────────────────────────────────
        for (int gx = (int) Math.floor(camX - boardW / 2.0 / cs()) - 1; gx <= camX + boardW / 2.0 / cs() + 1; gx++) {
            int sx = tileScreenX(gx);
            if (sx >= 0 && sx < width) g.fill(sx, boardTop(), sx + 1, height - BOTTOM_BAR, 0x11FFFFFF);
        }
        for (int gy = (int) Math.floor(camY - boardH / 2.0 / cs()) - 1; gy <= camY + boardH / 2.0 / cs() + 1; gy++) {
            int sy = tileScreenY(gy);
            if (sy >= boardTop() && sy < height - BOTTOM_BAR) g.fill(0, sy, width, sy + 1, 0x11FFFFFF);
        }

        // ── Tiles & valid hints ──────────────────────────────────────────────
        int csz = cs();
        for (int gy = 0; gy < TileKingdomsBoard.SIZE; gy++) {
            for (int gx = 0; gx < TileKingdomsBoard.SIZE; gx++) {
                int lx = gx - TileKingdomsBoard.HALF, ly = gy - TileKingdomsBoard.HALF;
                int sx = tileScreenX(lx), sy = tileScreenY(ly);
                if (sx + csz < leftPos || sx > leftPos + boardW || sy + csz < topPos + TOP_BAR || sy > topPos + imageHeight)
                    continue;
                short val = grid[gy * TileKingdomsBoard.SIZE + gx];
                if (!PlacedTile.isEmpty(val)) {
                    drawTile(g, sx, sy, val, csz);
                    drawFollowersOnTile(g, sx, sy, lx, ly, claims, csz);
                } else if (phase == TileKingdomsGame.PHASE_PLACE && myTurn && ct != null
                        && isValidPlace(grid, lx, ly, ct, rot)) {
                    g.fill(sx + 2, sy + 2, sx + csz - 2, sy + csz - 2, C_VALID);
                }
                g.fill(sx, sy, sx + csz, sy + 1, 0x11FFFFFF);
                g.fill(sx, sy, sx + 1, sy + csz, 0x11FFFFFF);
            }
        }

        // ── Hover ghost ──────────────────────────────────────────────────────
        boolean inBoard = mx >= leftPos && mx < leftPos + boardW && my >= topPos + TOP_BAR && my < topPos + imageHeight;
        if (inBoard && phase == TileKingdomsGame.PHASE_PLACE && myTurn && ct != null) {
            int hLx = screenToTileX(mx), hLy = screenToTileY(my);
            if (isValidPlace(grid, hLx, hLy, ct, rot)) {
                int sx = tileScreenX(hLx), sy = tileScreenY(hLy);
                g.fill(sx + 1, sy + 1, sx + csz - 1, sy + csz - 1, C_HOVER);
                drawTileOverlay(g, sx, sy, ct, rot, 0xAA, csz);
            }
        }

        // ── Claim buttons ────────────────────────────────────────────────────
        if (phase == TileKingdomsGame.PHASE_CLAIM && myTurn)
            drawClaimButtons(g, mx, my, grid, claims);

        // ── Floating current-tile preview (bottom-right corner of board) ─────
        if (ct != null && myTurn) {
            int ps = cs() + 8;
            int px = width - ps - 6, py = height - BOTTOM_BAR - ps - 20;
            int rbx = px, rby = py + ps + 2;
            // Dark bg
            g.fill(px - 3, py - 3, px + ps + 3, rby + 14, 0xCC000000);
            drawTilePreview(g, px, py, ps, ct, rot);
            // Rotate button
            boolean rotH = mx >= rbx && mx < rbx + ps && my >= rby && my < rby + 12;
            g.fill(rbx, rby, rbx + ps, rby + 12, rotH ? 0xFF667788 : 0xFF334455);
            g.drawString(font, "↻ Rotate", rbx + (ps - font.width("↻ Rotate")) / 2, rby + 2, 0xFFFFFFFF, false);
        }

        // ── "Skip" button for claim phase ────────────────────────────────────
        if (phase == TileKingdomsGame.PHASE_CLAIM && myTurn) {
            String skipLabel = "\u23e9 Skip";
            int sbw = font.width(skipLabel) + 20, sbh = 18;
            int sbx = width / 2 - sbw / 2, sby = height - BOTTOM_BAR - sbh - 4;
            boolean sh = mx >= sbx && mx < sbx + sbw && my >= sby && my < sby + sbh;
            // Shadow
            g.fill(sbx + 2, sby + 2, sbx + sbw + 2, sby + sbh + 2, 0x66000000);
            // Body
            g.fill(sbx, sby, sbx + sbw, sby + sbh, sh ? 0xEE667788 : 0xEE334455);
            // Top highlight
            g.fill(sbx, sby, sbx + sbw, sby + 1, sh ? 0xFF99BBCC : 0xFF556677);
            // Bottom shadow line
            g.fill(sbx, sby + sbh - 1, sbx + sbw, sby + sbh, 0xFF223344);
            // Border
            g.fill(sbx, sby, sbx + 1, sby + sbh, sh ? 0xFF99BBCC : 0xFF4477AA);
            g.fill(sbx + sbw - 1, sby, sbx + sbw, sby + sbh, sh ? 0xFF99BBCC : 0xFF4477AA);
            g.drawString(font, skipLabel, sbx + (sbw - font.width(skipLabel)) / 2, sby + (sbh - 8) / 2, sh ? 0xFFFFFFFF : 0xFFCCDDEE, false);
        }

        // ── Game over overlay ─────────────────────────────────────────────────
        if (menu.isGameOver()) drawGameOverOverlay(g);
    }

    // ── drawTile ──────────────────────────────────────────────────────────────
    // Texture support: cache known-good and known-missing textures
    private static final java.util.Set<net.minecraft.resources.ResourceLocation> TEX_AVAILABLE = new java.util.HashSet<>();
    private static final java.util.Set<net.minecraft.resources.ResourceLocation> TEX_MISSING = new java.util.HashSet<>();

    private static net.minecraft.resources.ResourceLocation tileTexLoc(TileType type) {
        return dev.lucaargolo.charta.common.ChartaMod.id("textures/tilekingdoms/" + type.name().toLowerCase() + ".png");
    }

    private boolean hasTexture(net.minecraft.resources.ResourceLocation loc) {
        if (TEX_AVAILABLE.contains(loc)) return true;
        if (TEX_MISSING.contains(loc)) return false;
        boolean found = net.minecraft.client.Minecraft.getInstance()
                .getResourceManager().getResource(loc).isPresent();
        if (found) {
            TEX_AVAILABLE.add(loc);
        } else {
            TEX_MISSING.add(loc);
            dev.lucaargolo.charta.common.ChartaMod.LOGGER.warn(
                    "[TileKingdoms] Missing tile texture: {}", loc);
        }
        return found;
    }

    private void drawTile(GuiGraphics g, int sx, int sy, short val) {
        drawTile(g, sx, sy, val, cs());
    }

    private void drawTile(GuiGraphics g, int sx, int sy, short val, int C) {
        TileType type = PlacedTile.typeOf(val);
        if (type == null) return;
        net.minecraft.resources.ResourceLocation tex = tileTexLoc(type);
        if (hasTexture(tex)) {
            int rot = PlacedTile.rotationOf(val);
            g.pose().pushPose();
            g.pose().translate(sx + C / 2f, sy + C / 2f, 0f);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rot * 90f));
            g.pose().translate(-C / 2f, -C / 2f, 0f);
            // blit with explicit texture size so it scales correctly regardless of C
            g.blit(tex, 0, 0, 0, 0, C, C, C, C);
            g.pose().popPose();
            g.fill(sx, sy, sx + C, sy + 1, C_BORDER);
            g.fill(sx, sy + C - 1, sx + C, sy + C, C_BORDER);
            g.fill(sx, sy, sx + 1, sy + C, C_BORDER);
            g.fill(sx + C - 1, sy, sx + C, sy + C, C_BORDER);
            return;
        }
        // Fallback: procedural
        drawTileProc(g, sx, sy, val, C);
    }

    private void drawTileProc(GuiGraphics g, int sx, int sy, short val, int C) {
        TileType type = PlacedTile.typeOf(val);
        if (type == null) return;
        boolean checker = ((sx / Math.max(1, C) + (sy - TOP_BAR) / Math.max(1, C)) & 1) == 0;
        g.fill(sx + 1, sy + 1, sx + C - 1, sy + C - 1, checker ? C_FIELD : C_FIELD2);

        // ── City fills (full edge bands) ─────────────────────────────────────
        for (int dir = 0; dir < 4; dir++) {
            if (PlacedTile.edgeOf(val, dir) == TileType.Edge.C) drawCityBand(g, sx, sy, dir, C);
        }
        if (type.connectedCity) {
            int cityN = 0;
            for (int d = 0; d < 4; d++) if (PlacedTile.edgeOf(val, d) == TileType.Edge.C) cityN++;
            if (cityN >= 2) g.fill(sx + C / 4, sy + C / 4, sx + 3 * C / 4, sy + 3 * C / 4, C_CITY);
        }

        // ── Road lines: draw continuous corridors through the tile ───────────
        // Find all road exits and connect them through the centre
        boolean[] roadExit = new boolean[4];
        int roadCount = 0;
        for (int d = 0; d < 4; d++)
            if (PlacedTile.edgeOf(val, d) == TileType.Edge.R) {
                roadExit[d] = true;
                roadCount++;
            }

        if (roadCount == 1 || type == TileType.ROAD_CROSS || type == TileType.ROAD_T) {
            // All roads connect to centre — star/T/single stub
            for (int d = 0; d < 4; d++) if (roadExit[d]) drawRoadSegment(g, sx, sy, d, -1, C);
        } else if (roadCount == 2) {
            // Find the two exits and draw a continuous corridor
            int dA = -1, dB = -1;
            for (int d = 0; d < 4; d++)
                if (roadExit[d]) {
                    if (dA < 0) dA = d;
                    else dB = d;
                }
            if (dA >= 0 && dB >= 0) {
                // Straight or curved road — draw both halves meeting at centre
                drawRoadCorridor(g, sx, sy, dA, dB, C);
            }
        }

        // ── Monastery cross ───────────────────────────────────────────────────
        if (type.monastery) {
            int cx = sx + C / 2, cy = sy + C / 2, r = Math.max(2, C / 7);
            g.fill(cx - r, cy - 1, cx + r + 1, cy + 2, C_MON);
            g.fill(cx - 1, cy - r, cx + 2, cy + r + 1, C_MON);
            // Gold orb centre
            g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFD700);
        }

        // ── Tile border
        g.fill(sx, sy, sx + C, sy + 1, C_BORDER);
        g.fill(sx, sy + C - 1, sx + C, sy + C, C_BORDER);
        g.fill(sx, sy, sx + 1, sy + C, C_BORDER);
        g.fill(sx + C - 1, sy, sx + C, sy + C, C_BORDER);
    }

    /**
     * Draw a city band (solid fill along one edge).
     */
    private void drawCityBand(GuiGraphics g, int sx, int sy, int dir) {
        drawCityBand(g, sx, sy, dir, cs());
    }

    private void drawCityBand(GuiGraphics g, int sx, int sy, int dir, int C) {
        int t = C / 4; // band thickness
        switch (dir) {
            case 0 -> g.fill(sx + 1, sy + 1, sx + C - 1, sy + t, C_CITY2);
            case 1 -> g.fill(sx + C - t, sy + 1, sx + C - 1, sy + C - 1, C_CITY2);
            case 2 -> g.fill(sx + 1, sy + C - t, sx + C - 1, sy + C - 1, C_CITY2);
            case 3 -> g.fill(sx + 1, sy + 1, sx + t, sy + C - 1, C_CITY2);
        }
    }

    /**
     * Draw a continuous road corridor between two exits.
     * Uses a 4px wide corridor with a darker border.
     */
    private void drawRoadCorridor(GuiGraphics g, int sx, int sy, int dA, int dB) {
        drawRoadCorridor(g, sx, sy, dA, dB, cs());
    }

    private void drawRoadCorridor(GuiGraphics g, int sx, int sy, int dA, int dB, int C) {
        int cx = sx + C / 2, cy = sy + C / 2;
        int hw = Math.max(2, C / 9); // half-width of road strip

        // Segment A: from edge to centre
        drawRoadHalf(g, sx, sy, cx, cy, dA, hw, C);
        drawRoadHalf(g, sx, sy, cx, cy, dB, hw, C);
    }

    /**
     * Road from tile edge (direction dir) to centre point (cx,cy).
     */
    private void drawRoadHalf(GuiGraphics g, int sx, int sy, int cx, int cy, int dir, int hw, int C) {
        switch (dir) {
            case 0 -> { // North edge to centre
                g.fill(cx - hw, sy + 1, cx + hw, cy, C_ROAD);
                g.fill(cx - hw, sy + 1, cx - hw + 1, cy, C_ROAD_EDGE);
                g.fill(cx + hw - 1, sy + 1, cx + hw, cy, C_ROAD_EDGE);
            }
            case 1 -> { // East edge to centre
                g.fill(cx, cy - hw, sx + C - 1, cy + hw, C_ROAD);
                g.fill(cx, cy - hw, sx + C - 1, cy - hw + 1, C_ROAD_EDGE);
                g.fill(cx, cy + hw - 1, sx + C - 1, cy + hw, C_ROAD_EDGE);
            }
            case 2 -> { // South edge to centre
                g.fill(cx - hw, cy, cx + hw, sy + C - 1, C_ROAD);
                g.fill(cx - hw, cy, cx - hw + 1, sy + C - 1, C_ROAD_EDGE);
                g.fill(cx + hw - 1, cy, cx + hw, sy + C - 1, C_ROAD_EDGE);
            }
            case 3 -> { // West edge to centre
                g.fill(sx + 1, cy - hw, cx, cy + hw, C_ROAD);
                g.fill(sx + 1, cy - hw, cx, cy - hw + 1, C_ROAD_EDGE);
                g.fill(sx + 1, cy + hw - 1, cx, cy + hw, C_ROAD_EDGE);
            }
        }
    }

    /**
     * Road stub from edge to centre (for cross/T/single-exit).
     */
    private void drawRoadSegment(GuiGraphics g, int sx, int sy, int dir, int ignored) {
        drawRoadSegment(g, sx, sy, dir, ignored, cs());
    }

    private void drawRoadSegment(GuiGraphics g, int sx, int sy, int dir, int ignored, int C) {
        int cx = sx + C / 2, cy = sy + C / 2, hw = Math.max(2, C / 9);
        drawRoadHalf(g, sx, sy, cx, cy, dir, hw, C);
    }

    /**
     * Overlay for ghost preview (semi-transparent edge drawing only).
     */
    private void drawTileOverlay(GuiGraphics g, int sx, int sy, TileType type, int rot, int alpha) {
        drawTileOverlay(g, sx, sy, type, rot, alpha, cs());
    }

    private void drawTileOverlay(GuiGraphics g, int sx, int sy, TileType type, int rot, int alpha, int C) {
        short val = PlacedTile.pack(type, rot);
        for (int d = 0; d < 4; d++) {
            TileType.Edge e = PlacedTile.edgeOf(val, d);
            if (e == TileType.Edge.C) drawCityBand(g, sx, sy, d, C);
        }
        boolean[] roadExit = new boolean[4];
        int rc = 0;
        for (int d = 0; d < 4; d++)
            if (PlacedTile.edgeOf(val, d) == TileType.Edge.R) {
                roadExit[d] = true;
                rc++;
            }
        if (rc == 2) {
            int dA = -1, dB = -1;
            for (int d = 0; d < 4; d++)
                if (roadExit[d]) {
                    if (dA < 0) dA = d;
                    else dB = d;
                }
            if (dA >= 0) drawRoadCorridor(g, sx, sy, dA, dB);
        } else for (int d = 0; d < 4; d++) if (roadExit[d]) drawRoadSegment(g, sx, sy, d, -1, C);
    }

    /**
     * Full preview tile (larger ps square).
     */
    private void drawTilePreview(GuiGraphics g, int px, int py, int ps, TileType type, int rot) {
        short val = PlacedTile.pack(type, rot);
        g.fill(px, py, px + ps, py + ps, C_FIELD);

        drawTile(g, px, py, val, ps);
        // Border for preview box
        g.fill(px, py, px + ps, py + 1, C_BORDER);
        g.fill(px, py + ps - 1, px + ps, py + ps, C_BORDER);
        g.fill(px, py, px + 1, py + ps, C_BORDER);
        g.fill(px + ps - 1, py, px + ps, py + ps, C_BORDER);
    }

    // ── Followers on tiles ────────────────────────────────────────────────────
    private int playerColor(int idx) {
        java.util.List<dev.lucaargolo.charta.common.game.api.CardPlayer> pl = menu.getGame().getPlayers();
        if (idx < 0 || idx >= pl.size()) return 0xFFFFFFFF;
        return 0xFF000000 | pl.get(idx).getColor().getTextureDiffuseColor();
    }

    private void drawFollowersOnTile(GuiGraphics g, int sx, int sy, int lx, int ly, int[] claims) {
        drawFollowersOnTile(g, sx, sy, lx, ly, claims, cs());
    }

    private void drawFollowersOnTile(GuiGraphics g, int sx, int sy, int lx, int ly, int[] claims, int C) {
        for (int c : claims) {
            int[] p = TileKingdomsGame.unpackClaimInt(c);
            if (p[0] == lx && p[1] == ly) {
                int col = playerColor(p[3]);
                int[] fp = featurePt(sx, sy, p[2]);
                // Simple dot: outline + fill
                g.fill(fp[0] - 3, fp[1] - 3, fp[0] + 3, fp[1] + 3, 0xFF000000);
                g.fill(fp[0] - 2, fp[1] - 2, fp[0] + 2, fp[1] + 2, col);
            }
        }
    }

    private int[] featurePt(int sx, int sy, int slot) {
        return featurePt(sx, sy, slot, cs());
    }

    private int[] featurePt(int sx, int sy, int slot, int C) {
        int cx = sx + C / 2, cy = sy + C / 2;
        int off = Math.max(6, C / 4); // distance from edge
        return switch (slot) {
            case TileKingdomsGame.SLOT_N -> new int[]{cx, sy + off};
            case TileKingdomsGame.SLOT_E -> new int[]{sx + C - off, cy};
            case TileKingdomsGame.SLOT_S -> new int[]{cx, sy + C - off};
            case TileKingdomsGame.SLOT_W -> new int[]{sx + off, cy};
            default -> new int[]{cx, cy};
        };
    }

    // ── Claim buttons ─────────────────────────────────────────────────────────
    private void drawClaimButtons(GuiGraphics g, int mx, int my, short[] grid, int[] claims) {
        int lx = menu.getLastPlacedX(), ly = menu.getLastPlacedY();
        int sx = tileScreenX(lx), sy = tileScreenY(ly);
        int gi = (ly + TileKingdomsBoard.HALF) * TileKingdomsBoard.SIZE + (lx + TileKingdomsBoard.HALF);
        if (gi < 0 || gi >= grid.length || PlacedTile.isEmpty(grid[gi])) return;
        short tile = grid[gi];
        TileType type = PlacedTile.typeOf(tile);
        int C = cs();
        // Highlight the just-placed tile
        g.fill(sx, sy, sx + C, sy + 1, 0xFFFFFF00);
        g.fill(sx, sy + C - 1, sx + C, sy + C, 0xFFFFFF00);
        g.fill(sx, sy, sx + 1, sy + C, 0xFFFFFF00);
        g.fill(sx + C - 1, sy, sx + C, sy + C, 0xFFFFFF00);

        for (int slot = 0; slot < 5; slot++) {
            boolean valid = false;
            if (slot < 4) {
                TileType.Edge e = PlacedTile.edgeOf(tile, slot);
                valid = (e != TileType.Edge.F) && !isClaimed(lx, ly, slot, claims);
            } else valid = type != null && type.monastery && !isClaimed(lx, ly, slot, claims);
            if (!valid) continue;
            int[] fp = featurePt(sx, sy, slot);
            int r = 10; // larger hit radius
            boolean hov = Math.abs(mx - fp[0]) <= r && Math.abs(my - fp[1]) <= r;
            // Outer glow when hovered
            if (hov) g.fill(fp[0] - r - 2, fp[1] - r - 2, fp[0] + r + 2, fp[1] + r + 2, 0x66FFFF00);
            // Circle background
            g.fill(fp[0] - r, fp[1] - r, fp[0] + r, fp[1] + r, hov ? 0xEEFFE040 : 0xCC2255AA);
            // Inner border
            g.fill(fp[0] - r, fp[1] - r, fp[0] + r, fp[1] - r + 1, hov ? 0xFFFFFF99 : 0xFF4488CC);
            g.fill(fp[0] - r, fp[1] + r - 1, fp[0] + r, fp[1] + r, hov ? 0xFFFFFF99 : 0xFF4488CC);
            g.fill(fp[0] - r, fp[1] - r, fp[0] - r + 1, fp[1] + r, hov ? 0xFFFFFF99 : 0xFF4488CC);
            g.fill(fp[0] + r - 1, fp[1] - r, fp[0] + r, fp[1] + r, hov ? 0xFFFFFF99 : 0xFF4488CC);
            // Follower icon: + symbol centred
            int fw = font.width("+");
            g.drawString(font, "+", fp[0] - fw / 2, fp[1] - 4, hov ? 0xFF000000 : 0xFFFFFFFF, false);
        }
    }

    private boolean isClaimed(int lx, int ly, int slot, int[] claims) {
        for (int c : claims) {
            int[] p = TileKingdomsGame.unpackClaimInt(c);
            if (p[0] == lx && p[1] == ly && p[2] == slot) return true;
        }
        return false;
    }

    // ── renderTopBar override — add score/follower info under heads ─────────
    @Override
    public void renderTopBar(@NotNull net.minecraft.client.gui.GuiGraphics g) {
        super.renderTopBar(g);  // Draw standard player heads with colour bars
        // Overlay: score + follower count under each player head
        java.util.List<dev.lucaargolo.charta.common.game.api.CardPlayer> players = menu.getGame().getPlayers();
        int n = players.size();
        float totalW = dev.lucaargolo.charta.common.menu.CardSlot.getWidth(dev.lucaargolo.charta.common.menu.CardSlot.Type.PREVIEW) + 28f;
        float playersWidth = n * totalW + (n - 1f) * (totalW / 10f);
        int curIdx = menu.getCurrentPlayerIdx();
        int[] fl = menu.getFollowersLeft();
        for (int i = 0; i < n; i++) {
            int x = (int) (width / 2f - playersWidth / 2f + i * (totalW + totalW / 10f));
            int score = menu.getScore(i);
            int follow = i < fl.length ? fl[i] : TileKingdomsGame.FOLLOWERS_PER_PLAYER;
            // Score and followers in small text at the bottom of the player's slot
            String info = score + "pt  " + follow + "♟";
            g.pose().pushPose();
            g.pose().translate(x + 26f, 18f, 0f);
            g.pose().scale(0.6f, 0.6f, 0.6f);
            g.drawString(font, info, 0, 0, i == curIdx ? 0xFFFFFF55 : 0xFFBBBBBB, true);
            g.pose().popPose();
            // Current turn indicator dot
            if (i == curIdx) {
                g.fill(x + (int) totalW / 2 - 2, 25, x + (int) totalW / 2 + 2, 28, 0xFFFFFFFF);
            }
        }
        // Tiles remaining
        String rem = menu.getRemainingTiles() + " tiles";
        g.drawString(font, rem, width - font.width(rem) - 4, 10, 0xFF888888, false);
    }

    // ── renderLabels ──────────────────────────────────────────────────────────
    @Override
    protected void renderLabels(@NotNull net.minecraft.client.gui.GuiGraphics g, int mx, int my) {
        // leftPos=topPos=0, so g coords == screen coords here
        int phase = menu.getPhase();
        boolean myTurn = isMyTurn();

        // Remaining tiles — bottom-left, above the bottom bar
        int rem = menu.getRemainingTiles();
        String remStr = rem + " tiles left";
        int remY = height - BOTTOM_BAR - 13;
        g.fill(4, remY - 1, font.width(remStr) + 8, remY + 9, 0xAA000000);
        g.drawString(font, remStr, 6, remY, rem <= 10 ? 0xFFFF8844 : 0xFFAAAAAA, false);

        // Phase hint — bottom-centre
        String hint = "";
        if (phase == TileKingdomsGame.PHASE_CLAIM && myTurn)
            hint = "Click ✚ to claim  •  Skip to pass";
        else if (phase == TileKingdomsGame.PHASE_PLACE && myTurn && menu.getCurrentTile() != null)
            hint = "Placing: " + menu.getCurrentTile().name().replace('_', ' ').toLowerCase();
        else if (!myTurn) {
            java.util.List<dev.lucaargolo.charta.common.game.api.CardPlayer> pl = menu.getGame().getPlayers();
            int ci = menu.getCurrentPlayerIdx();
            if (ci >= 0 && ci < pl.size())
                hint = "Waiting for " + pl.get(ci).getName().getString() + "…";
        }
        if (!hint.isEmpty()) {
            int hx = width / 2 - font.width(hint) / 2;
            g.drawString(font, hint, hx, remY, phase == TileKingdomsGame.PHASE_CLAIM ? 0xFFFFDD00 : 0xFFCCCCCC, true);
        }
    }

    // ── Game over overlay ─────────────────────────────────────────────────────
    private void drawGameOverOverlay(net.minecraft.client.gui.GuiGraphics g) {
        java.util.List<dev.lucaargolo.charta.common.game.api.CardPlayer> players = menu.getGame().getPlayers();
        int n = players.size();
        int winnerIdx = menu.getWinnerIdx();
        // Panel capped within board area — no bigger than needed
        int panelW = Math.min(210, width - 40), panelH = 36 + n * 13 + 16;
        int px = width / 2 - panelW / 2;
        int py = Math.max(boardTop() + 4, Math.min(boardTop() + boardH / 2 - panelH / 2, boardTop() + boardH - panelH - 4));
        g.fill(px - 2, py - 2, px + panelW + 2, py + panelH + 2, 0xFF222244);
        g.fill(px, py, px + panelW, py + panelH, 0xEE0D0D1A);
        g.fill(px, py, px + panelW, py + 13, 0xFF1A1A3A);
        String title = "♛  Game Over  ♛";
        g.drawString(font, title, px + panelW / 2 - font.width(title) / 2, py + 3, 0xFFFFD700, true);
        int cy = py + 17;
        if (winnerIdx >= 0 && winnerIdx < players.size()) {
            int wcol = playerColor(winnerIdx);
            String wname = players.get(winnerIdx).getName().getString();
            String winner = "🏆 " + wname + "  " + menu.getScore(winnerIdx) + "pt";
            g.fill(px + 2, cy, px + panelW - 2, cy + 12, 0x33FFFFFF);
            g.drawString(font, winner, px + panelW / 2 - font.width(winner) / 2, cy + 2, wcol, true);
            cy += 14;
        }
        for (int i = 0; i < n; i++) {
            int col = playerColor(i);
            g.fill(px + 6, cy + 2, px + 12, cy + 8, col);
            String nm = players.get(i).getName().getString();
            if (nm.length() > 11) nm = nm.substring(0, 10) + ".";
            g.drawString(font, nm + "  " + menu.getScore(i) + "pt", px + 16, cy + 1, i == winnerIdx ? 0xFFFFFFFF : 0xFFAAAAAA, false);
            cy += 13;
        }
        String hint = "Right-drag to explore";
        g.drawString(font, hint, px + panelW / 2 - font.width(hint) / 2, py + panelH - 8, 0xFF555566, false);
    }


    // ── Input ─────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        boolean inBoard = mx >= boardLeft() && mx < boardLeft() + boardW && my >= boardTop() && my < boardTop() + boardH;
        int phase = menu.getPhase();
        boolean myTurn = isMyTurn();

        if (button == 0) {
            TileType ct = menu.getCurrentTile();
            // ── Rotate button ────────────────────────────────────────────────
            if (ct != null && myTurn && phase == TileKingdomsGame.PHASE_PLACE) {
                int ps = 56, rbx = width - ps - 8, rby = height - BOTTOM_BAR - 22 + ps + 2;
                if (mx >= rbx && mx < rbx + ps && my >= rby && my < rby + 12) {
                    sendAction(TileKingdomsActionPayload.ROTATE);
                    return true;
                }
            }
            // ── Claim skip ───────────────────────────────────────────────────
            if (phase == TileKingdomsGame.PHASE_CLAIM && myTurn) {
                String skipLabel2 = "\u23e9 Skip";
                int sbw2 = font.width(skipLabel2) + 20, sbh2 = 18;
                int sbx2 = width / 2 - sbw2 / 2, sby2 = height - BOTTOM_BAR - sbh2 - 4;
                if (mx >= sbx2 && mx < sbx2 + sbw2 && my >= sby2 && my < sby2 + sbh2) {
                    sendAction(TileKingdomsActionPayload.SKIP_CLAIM);
                    return true;
                }
                // Claim feature click
                short[] grid = menu.getBoardGrid();
                int[] claimsArr = menu.getClaimsArray();
                int lx = menu.getLastPlacedX(), ly = menu.getLastPlacedY();
                int sx = tileScreenX(lx), sy = tileScreenY(ly);
                int gi = (ly + TileKingdomsBoard.HALF) * TileKingdomsBoard.SIZE + (lx + TileKingdomsBoard.HALF);
                if (gi >= 0 && gi < grid.length && !PlacedTile.isEmpty(grid[gi])) {
                    short tile = grid[gi];
                    TileType type = PlacedTile.typeOf(tile);
                    for (int slot = 0; slot < 5; slot++) {
                        boolean valid = slot < 4 ? (PlacedTile.edgeOf(tile, slot) != TileType.Edge.F && !isClaimed(lx, ly, slot, claimsArr))
                                : (type != null && type.monastery && !isClaimed(lx, ly, slot, claimsArr));
                        if (!valid) continue;
                        int[] fp = featurePt(sx, sy, slot);
                        if (Math.abs(mx - fp[0]) <= 10 && Math.abs(my - fp[1]) <= 10) {
                            sendAction(TileKingdomsGame.packClaimAction(slot));
                            return true;
                        }
                    }
                }
            }
            // ── Tile place ───────────────────────────────────────────────────
            if (inBoard && phase == TileKingdomsGame.PHASE_PLACE && myTurn && ct != null) {
                int lx = screenToTileX(mx), ly = screenToTileY(my);
                if (isValidPlace(menu.getBoardGrid(), lx, ly, ct, menu.getCurrentRotation())) {
                    sendAction(TileKingdomsGame.packAction(lx, ly, menu.getCurrentRotation()));
                    return true;
                }
            }
        }
        // ── Pan with right drag ───────────────────────────────────────────────
        if (button == 1 && inBoard) {
            dragging = true;
            dragStartMX = mx;
            dragStartMY = my;
            dragStartCamX = camX;
            dragStartCamY = camY;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (dragging) {
            camX = dragStartCamX - (float) ((mx - dragStartMX) / cs());
            camY = dragStartCamY - (float) ((my - dragStartMY) / cs());
            return true;
        }
        return super.mouseDragged(mx, my, b, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        if (b == 1) dragging = false;
        return super.mouseReleased(mx, my, b);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (hasControlDown()) {
            zoom = Math.max(0.35f, Math.min(2.5f, zoom + (float) sy * 0.12f));
        } else {
            camY -= (float) (sy * 0.7f);
        }
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private boolean isMyTurn() {
        List<CardPlayer> pl = menu.getGame().getPlayers();
        int idx = menu.getCurrentPlayerIdx();
        return idx >= 0 && idx < pl.size() && pl.get(idx).equals(menu.getCardPlayer()) && menu.isReady();
    }

    private boolean isValidPlace(short[] grid, int lx, int ly, TileType type, int rot) {
        int gx = lx + TileKingdomsBoard.HALF, gy = ly + TileKingdomsBoard.HALF;
        if (gx < 0 || gx >= TileKingdomsBoard.SIZE || gy < 0 || gy >= TileKingdomsBoard.SIZE) return false;
        int i = gy * TileKingdomsBoard.SIZE + gx;
        if (i >= grid.length || !PlacedTile.isEmpty(grid[i])) return false;
        int[] DX = {0, 1, 0, -1}, DY = {-1, 0, 1, 0};
        boolean hasNb = false;
        short cand = PlacedTile.pack(type, rot);
        for (int d = 0; d < 4; d++) {
            int nx = gx + DX[d], ny = gy + DY[d];
            if (nx < 0 || nx >= TileKingdomsBoard.SIZE || ny < 0 || ny >= TileKingdomsBoard.SIZE) continue;
            int ni = ny * TileKingdomsBoard.SIZE + nx;
            if (ni >= grid.length) continue;
            short nb = grid[ni];
            if (!PlacedTile.isEmpty(nb)) {
                hasNb = true;
                if (!PlacedTile.compatible(cand, d, nb)) return false;
            }
        }
        return hasNb;
    }
}