package dev.lucaargolo.charta.common.game.impl.tilekingdoms;

import dev.lucaargolo.charta.client.render.screen.GameScreen;
import dev.lucaargolo.charta.common.ChartaMod;
import dev.lucaargolo.charta.common.game.api.CardPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TileKingdomsScreen extends GameScreen<TileKingdomsGame, TileKingdomsMenu> {

    // Board display constants
    private static final int CELL  = 16;   // px per tile cell
    private static final int BOARD_OFFSET_X = 8;
    private static final int BOARD_OFFSET_Y = 20;

    // Tile edge colours
    private static final int COL_FIELD  = 0xFF4CAF50;
    private static final int COL_ROAD   = 0xFFF5F5DC;
    private static final int COL_CITY   = 0xFF8B6914;
    private static final int COL_MON    = 0xFF7B1FA2;
    private static final int COL_EMPTY  = 0xFF1B5E20;
    private static final int COL_BORDER = 0xFF333333;
    private static final int COL_VALID  = 0x6600FF00;
    private static final int COL_HOVER  = 0x88FFFF00;

    // Rotation button
    private static final int ROT_BTN_W  = 22;
    private static final int ROT_BTN_H  = 12;

    private int hoverBoardX = Integer.MIN_VALUE;
    private int hoverBoardY = Integer.MIN_VALUE;

    public TileKingdomsScreen(TileKingdomsMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth  = 256;
        this.imageHeight = 230;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendAction(int action) {
        ChartaMod.getPacketManager().sendToServer(
                new TileKingdomsActionPayload(menu.containerId, action));
    }

    /** Board pixel origin in screen coordinates. */
    private int boardScreenX() { return leftPos + BOARD_OFFSET_X; }
    private int boardScreenY() { return topPos  + BOARD_OFFSET_Y; }

    /** Convert screen pos to logical board coords. */
    private int screenToLogicalX(int sx) {
        int bx = sx - boardScreenX();
        return bx / CELL - TileKingdomsBoard.HALF;
    }
    private int screenToLogicalY(int sy) {
        int by = sy - boardScreenY();
        return by / CELL - TileKingdomsBoard.HALF;
    }

    /** Number of visible cells in each axis. */
    private int visibleCells() { return (imageWidth - BOARD_OFFSET_X * 2) / CELL; }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    protected void renderBg(@NotNull GuiGraphics g, float pt, int mx, int my) {
        // Dark background for board area
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF1A1A1A);
        g.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFF263238);

        short[] grid = menu.getBoardGrid();
        TileType drawTile = menu.getCurrentTile();
        int rot = menu.getCurrentRotation();
        boolean myTurn = isMyTurn();

        // --- Draw board cells ---
        int cells = visibleCells();
        int bsx = boardScreenX(), bsy = boardScreenY();

        for (int cy = 0; cy < cells; cy++) {
            for (int cx = 0; cx < cells; cx++) {
                int lx = cx - TileKingdomsBoard.HALF;
                int ly = cy - TileKingdomsBoard.HALF;
                int sx = bsx + cx * CELL;
                int sy = bsy + cy * CELL;
                int idx = cy * TileKingdomsBoard.SIZE + cx;
                short val = idx < grid.length ? grid[idx] : PlacedTile.EMPTY;

                if (!PlacedTile.isEmpty(val)) {
                    drawTileCell(g, sx, sy, val);
                } else {
                    // Empty cell — show green if valid placement
                    if (myTurn && drawTile != null) {
                        if (canPlaceHere(grid, lx, ly, drawTile, rot)) {
                            g.fill(sx + 1, sy + 1, sx + CELL - 1, sy + CELL - 1, COL_VALID);
                        }
                    }
                }
                // Cell border
                g.fill(sx, sy, sx + CELL, sy + 1, COL_BORDER);
                g.fill(sx, sy, sx + 1, sy + CELL, COL_BORDER);
            }
        }

        // --- Hover highlight ---
        int hLx = screenToLogicalX(mx), hLy = screenToLogicalY(my);
        int hGridX = hLx + TileKingdomsBoard.HALF, hGridY = hLy + TileKingdomsBoard.HALF;
        if (hGridX >= 0 && hGridX < cells && hGridY >= 0 && hGridY < cells) {
            hoverBoardX = hLx; hoverBoardY = hLy;
            short hval = grid[hGridY * TileKingdomsBoard.SIZE + hGridX];
            if (PlacedTile.isEmpty(hval) && myTurn && drawTile != null &&
                    canPlaceHere(grid, hLx, hLy, drawTile, rot)) {
                int sx = bsx + hGridX * CELL;
                int sy = bsy + hGridY * CELL;
                // Ghost tile preview
                g.fill(sx + 1, sy + 1, sx + CELL - 1, sy + CELL - 1, COL_HOVER);
                drawTilePreviewSmall(g, sx, sy, drawTile, rot, 0x88FFFFFF);
            }
        } else {
            hoverBoardX = Integer.MIN_VALUE;
        }

        // --- Current tile preview (bottom-right corner) ---
        int previewX = leftPos + imageWidth - 50;
        int previewY = topPos + imageHeight - 50;
        if (drawTile != null) {
            int previewSize = 40;
            g.fill(previewX - 2, previewY - 2, previewX + previewSize + 2, previewY + previewSize + 2, 0xFF111111);
            drawTilePreviewLarge(g, previewX, previewY, previewSize, drawTile, rot);
            // Rotate button
            int rbx = previewX, rby = previewY + previewSize + 4;
            boolean rotHover = mx >= rbx && mx < rbx + ROT_BTN_W && my >= rby && my < rby + ROT_BTN_H;
            g.fill(rbx, rby, rbx + ROT_BTN_W, rby + ROT_BTN_H, rotHover ? 0xFF556677 : 0xFF334455);
            g.drawString(font, "↻ Rot", rbx + 2, rby + 2, 0xFFFFFFFF, false);
        }
    }

    /** Draw a placed tile cell at screen position (sx, sy). */
    private void drawTileCell(GuiGraphics g, int sx, int sy, short val) {
        TileType type = PlacedTile.typeOf(val);
        if (type == null) return;
        int inset = 3;

        // Background (field)
        g.fill(sx + 1, sy + 1, sx + CELL - 1, sy + CELL - 1, COL_FIELD);

        // Monastery dot
        if (type.monastery) {
            g.fill(sx + 5, sy + 5, sx + CELL - 5, sy + CELL - 5, COL_MON);
        }

        // North edge
        drawEdgeStrip(g, sx, sy, PlacedTile.edgeOf(val, 0), 0); // N
        drawEdgeStrip(g, sx, sy, PlacedTile.edgeOf(val, 1), 1); // E
        drawEdgeStrip(g, sx, sy, PlacedTile.edgeOf(val, 2), 2); // S
        drawEdgeStrip(g, sx, sy, PlacedTile.edgeOf(val, 3), 3); // W
    }

    /** Draws an edge strip for direction dir (0=N,1=E,2=S,3=W). */
    private void drawEdgeStrip(GuiGraphics g, int sx, int sy, TileType.Edge edge, int dir) {
        int c = switch (edge) {
            case C -> COL_CITY;
            case R -> COL_ROAD;
            case F -> -1; // no strip for field
        };
        if (c == -1) return;
        int t = 4; // strip thickness
        int m = (CELL - t) / 2; // midpoint for road
        switch (dir) {
            case 0 -> { // N
                if (edge == TileType.Edge.R) g.fill(sx + m, sy + 1, sx + m + t, sy + t + 1, c);
                else g.fill(sx + 1, sy + 1, sx + CELL - 1, sy + t + 1, c);
            }
            case 1 -> { // E
                if (edge == TileType.Edge.R) g.fill(sx + CELL - t - 1, sy + m, sx + CELL - 1, sy + m + t, c);
                else g.fill(sx + CELL - t - 1, sy + 1, sx + CELL - 1, sy + CELL - 1, c);
            }
            case 2 -> { // S
                if (edge == TileType.Edge.R) g.fill(sx + m, sy + CELL - t - 1, sx + m + t, sy + CELL - 1, c);
                else g.fill(sx + 1, sy + CELL - t - 1, sx + CELL - 1, sy + CELL - 1, c);
            }
            case 3 -> { // W
                if (edge == TileType.Edge.R) g.fill(sx + 1, sy + m, sx + t + 1, sy + m + t, c);
                else g.fill(sx + 1, sy + 1, sx + t + 1, sy + CELL - 1, c);
            }
        }
    }

    /** Small ghost preview overlay. */
    private void drawTilePreviewSmall(GuiGraphics g, int sx, int sy, TileType type, int rot, int tint) {
        short val = PlacedTile.pack(type, rot);
        drawTileCell(g, sx, sy, val);
    }

    /** Large preview tile in the corner. */
    private void drawTilePreviewLarge(GuiGraphics g, int px, int py, int size, TileType type, int rot) {
        g.fill(px + 1, py + 1, px + size - 1, py + size - 1, COL_FIELD);
        if (type.monastery) g.fill(px + size/4, py + size/4, px + 3*size/4, py + 3*size/4, COL_MON);
        int t = size / 5;
        int m = (size - t) / 2;
        for (int dir = 0; dir < 4; dir++) {
            TileType.Edge edge = PlacedTile.edgeOf(PlacedTile.pack(type, rot), dir);
            int c = switch (edge) { case C -> COL_CITY; case R -> COL_ROAD; case F -> -1; };
            if (c == -1) continue;
            switch (dir) {
                case 0 -> { if (edge==TileType.Edge.R) g.fill(px+m,py+1,px+m+t,py+t+1,c); else g.fill(px+1,py+1,px+size-1,py+t+1,c); }
                case 1 -> { if (edge==TileType.Edge.R) g.fill(px+size-t-1,py+m,px+size-1,py+m+t,c); else g.fill(px+size-t-1,py+1,px+size-1,py+size-1,c); }
                case 2 -> { if (edge==TileType.Edge.R) g.fill(px+m,py+size-t-1,px+m+t,py+size-1,c); else g.fill(px+1,py+size-t-1,px+size-1,py+size-1,c); }
                case 3 -> { if (edge==TileType.Edge.R) g.fill(px+1,py+m,px+t+1,py+m+t,c); else g.fill(px+1,py+1,px+t+1,py+size-1,c); }
            }
        }
    }

    // ── renderLabels ──────────────────────────────────────────────────────────

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mx, int my) {
        int cx = imageWidth / 2;

        // Title
        String title = "Tile Kingdoms";
        g.drawString(font, title, cx - font.width(title)/2, 4, 0xFFD700);

        // Tiles remaining
        g.drawString(font, menu.getRemainingTiles() + " tiles left", 8, 4, 0xAAAAAA);

        // Scores
        List<CardPlayer> players = menu.getGame().getPlayers();
        int scoreY = imageHeight - 56;
        for (int i = 0; i < players.size(); i++) {
            String name = players.get(i).getName().getString();
            if (name.length() > 8) name = name.substring(0, 7) + ".";
            boolean isCurrent = (i == menu.getCurrentPlayerIdx());
            String line = name + ": " + menu.getScore(i);
            int colour = isCurrent ? 0xFFFFFF : 0xAAAAAA;
            int col = i * (imageWidth / Math.max(1, players.size()));
            g.drawString(font, line, leftPos > 0 ? col + 8 : col + 8, scoreY, colour, true);
        }

        // Turn indicator
        boolean myTurn = isMyTurn();
        if (myTurn && menu.getCurrentTile() != null) {
            Component turnMsg = Component.literal("Your turn — click to place, ↻ to rotate").withStyle(ChatFormatting.GREEN);
            g.drawString(font, turnMsg, cx - font.width(turnMsg)/2, imageHeight - 44, 0xFFFFFF);
        } else if (!myTurn && menu.isReady()) {
            int idx = menu.getCurrentPlayerIdx();
            String name = idx >= 0 && idx < players.size() ? players.get(idx).getName().getString() : "...";
            Component waitMsg = Component.literal("Waiting for " + name + "…").withStyle(ChatFormatting.GRAY);
            g.drawString(font, waitMsg, cx - font.width(waitMsg)/2, imageHeight - 44, 0xFFFFFF);
        }

        // Tile type name preview
        TileType ct = menu.getCurrentTile();
        if (ct != null && myTurn) {
            String tileName = ct.name().replace('_', ' ');
            g.drawString(font, tileName, leftPos > 0 ? imageWidth - 52 : imageWidth - 52,
                    imageHeight - 58, 0xCCCCCC, false);
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isMyTurn() || menu.getCurrentTile() == null) return super.mouseClicked(mx, my, button);

        // Rotate button hit
        int previewX = leftPos + imageWidth - 50;
        int previewY = topPos  + imageHeight - 50;
        int rbx = previewX, rby = previewY + 42;
        if (mx >= rbx && mx < rbx + ROT_BTN_W && my >= rby && my < rby + ROT_BTN_H) {
            sendAction(TileKingdomsActionPayload.ROTATE);
            return true;
        }

        // Board click
        int lx = (int)((mx - boardScreenX()) / CELL) - TileKingdomsBoard.HALF;
        int ly = (int)((my - boardScreenY()) / CELL) - TileKingdomsBoard.HALF;

        short[] grid = menu.getBoardGrid();
        int gx = lx + TileKingdomsBoard.HALF, gy = ly + TileKingdomsBoard.HALF;
        if (gx >= 0 && gx < TileKingdomsBoard.SIZE && gy >= 0 && gy < TileKingdomsBoard.SIZE) {
            TileType ct = menu.getCurrentTile();
            int rot = menu.getCurrentRotation();
            if (ct != null && canPlaceHere(grid, lx, ly, ct, rot)) {
                sendAction(TileKingdomsGame.packAction(lx, ly, rot));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isMyTurn() {
        List<CardPlayer> players = menu.getGame().getPlayers();
        int idx = menu.getCurrentPlayerIdx();
        if (idx < 0 || idx >= players.size()) return false;
        return players.get(idx).equals(menu.getCardPlayer()) && menu.isReady();
    }

    private boolean canPlaceHere(short[] grid, int lx, int ly, TileType type, int rot) {
        int gx = lx + TileKingdomsBoard.HALF, gy = ly + TileKingdomsBoard.HALF;
        if (gx < 0 || gx >= TileKingdomsBoard.SIZE || gy < 0 || gy >= TileKingdomsBoard.SIZE) return false;
        int idx = gy * TileKingdomsBoard.SIZE + gx;
        if (idx >= grid.length || !PlacedTile.isEmpty(grid[idx])) return false;

        // Check adjacency and compatibility using client grid
        int[] DX = {0,1,0,-1}, DY = {-1,0,1,0};
        boolean hasNeighbour = false;
        short candidate = PlacedTile.pack(type, rot);
        for (int dir = 0; dir < 4; dir++) {
            int nx = gx + DX[dir], ny = gy + DY[dir];
            if (nx < 0 || nx >= TileKingdomsBoard.SIZE || ny < 0 || ny >= TileKingdomsBoard.SIZE) continue;
            short nb = grid[ny * TileKingdomsBoard.SIZE + nx];
            if (!PlacedTile.isEmpty(nb)) {
                hasNeighbour = true;
                if (!PlacedTile.compatible(candidate, dir, nb)) return false;
            }
        }
        return hasNeighbour;
    }
}