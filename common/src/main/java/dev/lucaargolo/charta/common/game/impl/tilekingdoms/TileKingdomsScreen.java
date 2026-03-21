package dev.lucaargolo.charta.common.game.impl.tilekingdoms;

import dev.lucaargolo.charta.client.render.screen.GameScreen;
import dev.lucaargolo.charta.common.ChartaMod;
import dev.lucaargolo.charta.common.game.api.CardPlayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TileKingdomsScreen extends GameScreen<TileKingdomsGame, TileKingdomsMenu> {

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int CELL    = 24;  // tile size px
    // Board occupies: topBar(28) .. height-bottomBar(63) vertically, full width horizontally
    private static final int TOP_BAR    = 28;
    private static final int BOTTOM_BAR = 63;

    private int boardW, boardH;

    // Camera
    private float camX = 0f, camY = 0f;
    private double dragStartMX, dragStartMY;
    private float dragStartCamX, dragStartCamY;
    private boolean dragging = false;

    // Colours
    private static final int C_FIELD  = 0xFF4CAF50;
    private static final int C_FIELD2 = 0xFF388E3C;
    private static final int C_ROAD   = 0xFFF0E68C;
    private static final int C_ROAD_EDGE = 0xFFD4C870;
    private static final int C_CITY   = 0xFF8D5A14;
    private static final int C_CITY2  = 0xFFA07820;
    private static final int C_MON    = 0xFF6A1B9A;
    private static final int C_BORDER = 0xFF222222;
    private static final int C_VALID  = 0x5500FF00;
    private static final int C_HOVER  = 0x99FFFF55;
    private static final int C_BAR    = 0xEE0D1A0D;

    private static final int[] P_COL = {0xFFE53935,0xFF1E88E5,0xFF43A047,0xFFFB8C00,0xFF8E24AA};

    public TileKingdomsScreen(TileKingdomsMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        // Large screen — uses full game window area
        imageWidth  = 320;
        imageHeight = 240;
    }

    private void sendAction(int a) {
        ChartaMod.getPacketManager().sendToServer(new TileKingdomsActionPayload(menu.containerId, a));
    }

    // ── Coordinate helpers ────────────────────────────────────────────────────
    // Board starts at screen y = TOP_BAR, width=full, height=height-TOP_BAR-BOTTOM_BAR
    private int boardTop()  { return TOP_BAR; }           // screen y where board starts
    private int boardLeft() { return 0; }                 // screen x where board starts
    private int tileScreenX(int lx) { return boardLeft() + boardW/2 + Math.round((lx - camX) * CELL); }
    private int tileScreenY(int ly) { return boardTop()  + boardH/2 + Math.round((ly - camY) * CELL); }
    private int screenToTileX(double sx) { return (int)Math.floor((sx - boardLeft() - boardW/2.0) / CELL + camX); }
    private int screenToTileY(double sy) { return (int)Math.floor((sy - boardTop()  - boardH/2.0) / CELL + camY); }

    @Override
    protected void init() {
        super.init();
        boardW = width;
        boardH = height - TOP_BAR - BOTTOM_BAR;
        camX = 0; camY = 0;
    }

    // ── renderBg ──────────────────────────────────────────────────────────────
    @Override
    protected void renderBg(@NotNull GuiGraphics g, float pt, int mx, int my) {
        // Top bar
        g.fill(leftPos, topPos, leftPos+imageWidth, topPos+TOP_BAR, C_BAR);
        g.fill(leftPos, topPos+TOP_BAR-1, leftPos+imageWidth, topPos+TOP_BAR, 0xFF445544);
        // Board bg
        g.fill(leftPos, topPos+TOP_BAR, leftPos+imageWidth, topPos+imageHeight, 0xFF1B2A1B);

        short[] grid    = menu.getBoardGrid();
        int[]   claims  = menu.getClaimsArray();
        TileType ct     = menu.getCurrentTile();
        int     rot     = menu.getCurrentRotation();
        boolean myTurn  = isMyTurn();
        int     phase   = menu.getPhase();

        // ── Full-screen background grid ──────────────────────────────────────
        for (int gx = (int)Math.floor(camX - boardW/2.0/CELL) - 1; gx <= camX + boardW/2.0/CELL + 1; gx++) {
            int sx = tileScreenX(gx);
            if (sx >= 0 && sx < width) g.fill(sx, boardTop(), sx+1, height-BOTTOM_BAR, 0x11FFFFFF);
        }
        for (int gy = (int)Math.floor(camY - boardH/2.0/CELL) - 1; gy <= camY + boardH/2.0/CELL + 1; gy++) {
            int sy = tileScreenY(gy);
            if (sy >= boardTop() && sy < height-BOTTOM_BAR) g.fill(0, sy, width, sy+1, 0x11FFFFFF);
        }

        // ── Tiles & valid hints ──────────────────────────────────────────────
        for (int gy=0; gy<TileKingdomsBoard.SIZE; gy++) {
            for (int gx=0; gx<TileKingdomsBoard.SIZE; gx++) {
                int lx=gx-TileKingdomsBoard.HALF, ly=gy-TileKingdomsBoard.HALF;
                int sx=tileScreenX(lx), sy=tileScreenY(ly);
                if(sx+CELL<leftPos||sx>leftPos+boardW||sy+CELL<topPos+TOP_BAR||sy>topPos+imageHeight) continue;
                short val = grid[gy*TileKingdomsBoard.SIZE+gx];
                if (!PlacedTile.isEmpty(val)) {
                    drawTile(g, sx, sy, val);
                    drawFollowersOnTile(g, sx, sy, lx, ly, claims);
                } else if (phase==TileKingdomsGame.PHASE_PLACE && myTurn && ct!=null
                        && isValidPlace(grid,lx,ly,ct,rot)) {
                    g.fill(sx+2,sy+2,sx+CELL-2,sy+CELL-2, C_VALID);
                }
                // Subtle grid
                g.fill(sx,sy,sx+CELL,sy+1,0x11FFFFFF);
                g.fill(sx,sy,sx+1,sy+CELL,0x11FFFFFF);
            }
        }

        // ── Hover ghost ──────────────────────────────────────────────────────
        boolean inBoard = mx>=leftPos&&mx<leftPos+boardW&&my>=topPos+TOP_BAR&&my<topPos+imageHeight;
        if (inBoard && phase==TileKingdomsGame.PHASE_PLACE && myTurn && ct!=null) {
            int hLx=screenToTileX(mx), hLy=screenToTileY(my);
            if (isValidPlace(grid,hLx,hLy,ct,rot)) {
                int sx=tileScreenX(hLx), sy=tileScreenY(hLy);
                g.fill(sx+1,sy+1,sx+CELL-1,sy+CELL-1, C_HOVER);
                drawTileOverlay(g,sx,sy,ct,rot,0xAA);
            }
        }

        // ── Claim buttons ────────────────────────────────────────────────────
        if (phase==TileKingdomsGame.PHASE_CLAIM && myTurn)
            drawClaimButtons(g, mx, my, grid, claims);

        // ── Floating current-tile preview (bottom-right corner of board) ─────
        if (ct!=null && myTurn) {
            int ps=CELL+10;
            int px=width-ps-6, py=height-BOTTOM_BAR-ps-20;
            int rbx=px, rby=py+ps+2;
            // Dark bg
            g.fill(px-3,py-3,px+ps+3,rby+14,0xCC000000);
            drawTilePreview(g,px,py,ps,ct,rot);
            // Rotate button
            boolean rotH=mx>=rbx&&mx<rbx+ps&&my>=rby&&my<rby+12;
            g.fill(rbx,rby,rbx+ps,rby+12, rotH?0xFF667788:0xFF334455);
            g.drawString(font,"↻ Rotate",rbx+(ps-font.width("↻ Rotate"))/2,rby+2,0xFFFFFFFF,false);
        }

        // ── "Skip" button for claim phase ────────────────────────────────────
        if (phase==TileKingdomsGame.PHASE_CLAIM && myTurn) {
            int sbx=width/2-20, sby=height-BOTTOM_BAR-16;
            boolean sh=mx>=sbx&&mx<sbx+40&&my>=sby&&my<sby+12;
            g.fill(sbx,sby,sbx+40,sby+12, sh?0xCC778899:0xCC445566);
            g.drawString(font,"Skip",sbx+(40-font.width("Skip"))/2,sby+2,0xFFFFFFFF,false);
        }

        // ── Game over overlay ─────────────────────────────────────────────────
        if (menu.isGameOver()) drawGameOverOverlay(g);
    }

    // ── drawTile: proper continuous road lines ────────────────────────────────
    private void drawTile(GuiGraphics g, int sx, int sy, short val) {
        TileType type = PlacedTile.typeOf(val);
        if (type==null) return;
        boolean checker = ((sx/CELL + (sy-TOP_BAR)/CELL) & 1)==0;
        g.fill(sx+1,sy+1,sx+CELL-1,sy+CELL-1, checker?C_FIELD:C_FIELD2);

        // ── City fills (full edge bands) ─────────────────────────────────────
        for (int dir=0;dir<4;dir++) {
            if (PlacedTile.edgeOf(val,dir)==TileType.Edge.C) drawCityBand(g,sx,sy,dir);
        }
        if (type.connectedCity) {
            // Fill interior for multi-edge city
            int cityN=0; for(int d=0;d<4;d++) if(PlacedTile.edgeOf(val,d)==TileType.Edge.C) cityN++;
            if(cityN>=2) g.fill(sx+6,sy+6,sx+CELL-6,sy+CELL-6,C_CITY);
        }

        // ── Road lines: draw continuous corridors through the tile ───────────
        // Find all road exits and connect them through the centre
        boolean[] roadExit = new boolean[4];
        int roadCount = 0;
        for(int d=0;d<4;d++) if(PlacedTile.edgeOf(val,d)==TileType.Edge.R){roadExit[d]=true;roadCount++;}

        if (roadCount==1 || type==TileType.ROAD_CROSS || type==TileType.ROAD_T) {
            // All roads connect to centre — star/T/single stub
            for(int d=0;d<4;d++) if(roadExit[d]) drawRoadSegment(g,sx,sy,d,-1);
        } else if (roadCount==2) {
            // Find the two exits and draw a continuous corridor
            int dA=-1,dB=-1;
            for(int d=0;d<4;d++) if(roadExit[d]){if(dA<0)dA=d;else dB=d;}
            if (dA>=0&&dB>=0) {
                // Straight or curved road — draw both halves meeting at centre
                drawRoadCorridor(g,sx,sy,dA,dB);
            }
        }

        // ── Monastery cross ───────────────────────────────────────────────────
        if (type.monastery) {
            int cx=sx+CELL/2, cy=sy+CELL/2, r=4;
            g.fill(cx-r,cy-1,cx+r+1,cy+2, C_MON);
            g.fill(cx-1,cy-r,cx+2,cy+r+1, C_MON);
            // Gold orb centre
            g.fill(cx-1,cy-1,cx+2,cy+2,0xFFFFD700);
        }

        // ── Tile border ───────────────────────────────────────────────────────
        g.fill(sx,sy,sx+CELL,sy+1,C_BORDER);
        g.fill(sx,sy+CELL-1,sx+CELL,sy+CELL,C_BORDER);
        g.fill(sx,sy,sx+1,sy+CELL,C_BORDER);
        g.fill(sx+CELL-1,sy,sx+CELL,sy+CELL,C_BORDER);
    }

    /** Draw a city band (solid fill along one edge). */
    private void drawCityBand(GuiGraphics g, int sx, int sy, int dir) {
        int t = CELL/4; // band thickness
        switch(dir) {
            case 0 -> g.fill(sx+1, sy+1,   sx+CELL-1, sy+t,        C_CITY2);
            case 1 -> g.fill(sx+CELL-t, sy+1, sx+CELL-1, sy+CELL-1, C_CITY2);
            case 2 -> g.fill(sx+1, sy+CELL-t, sx+CELL-1, sy+CELL-1, C_CITY2);
            case 3 -> g.fill(sx+1, sy+1,   sx+t,        sy+CELL-1, C_CITY2);
        }
    }

    /**
     * Draw a continuous road corridor between two exits.
     * Uses a 4px wide corridor with a darker border.
     */
    private void drawRoadCorridor(GuiGraphics g, int sx, int sy, int dA, int dB) {
        int cx = sx+CELL/2, cy = sy+CELL/2;
        int hw = 2; // half-width of road strip

        // Segment A: from edge to centre
        drawRoadHalf(g, sx, sy, cx, cy, dA, hw);
        // Segment B: from centre to edge
        drawRoadHalf(g, sx, sy, cx, cy, dB, hw);
    }

    /** Road from tile edge (direction dir) to centre point (cx,cy). */
    private void drawRoadHalf(GuiGraphics g, int sx, int sy, int cx, int cy, int dir, int hw) {
        switch(dir) {
            case 0 -> { // North edge to centre
                g.fill(cx-hw, sy+1,   cx+hw, cy,      C_ROAD);
                g.fill(cx-hw, sy+1,   cx-hw+1, cy,    C_ROAD_EDGE);
                g.fill(cx+hw-1,sy+1,  cx+hw, cy,      C_ROAD_EDGE);
            }
            case 1 -> { // East edge to centre
                g.fill(cx,    cy-hw,  sx+CELL-1, cy+hw, C_ROAD);
                g.fill(cx,    cy-hw,  sx+CELL-1, cy-hw+1, C_ROAD_EDGE);
                g.fill(cx,    cy+hw-1,sx+CELL-1, cy+hw, C_ROAD_EDGE);
            }
            case 2 -> { // South edge to centre
                g.fill(cx-hw, cy,    cx+hw,  sy+CELL-1, C_ROAD);
                g.fill(cx-hw, cy,    cx-hw+1,sy+CELL-1, C_ROAD_EDGE);
                g.fill(cx+hw-1,cy,   cx+hw,  sy+CELL-1, C_ROAD_EDGE);
            }
            case 3 -> { // West edge to centre
                g.fill(sx+1,  cy-hw, cx,     cy+hw, C_ROAD);
                g.fill(sx+1,  cy-hw, cx,     cy-hw+1, C_ROAD_EDGE);
                g.fill(sx+1,  cy+hw-1,cx,    cy+hw, C_ROAD_EDGE);
            }
        }
    }

    /** Road stub from edge to centre (for cross/T/single-exit). */
    private void drawRoadSegment(GuiGraphics g, int sx, int sy, int dir, int ignored) {
        int cx=sx+CELL/2, cy=sy+CELL/2, hw=2;
        drawRoadHalf(g,sx,sy,cx,cy,dir,hw);
    }

    /** Overlay for ghost preview (semi-transparent edge drawing only). */
    private void drawTileOverlay(GuiGraphics g, int sx, int sy, TileType type, int rot, int alpha) {
        short val = PlacedTile.pack(type,rot);
        for(int d=0;d<4;d++) {
            TileType.Edge e=PlacedTile.edgeOf(val,d);
            if(e==TileType.Edge.C) drawCityBand(g,sx,sy,d);
        }
        boolean[] roadExit=new boolean[4]; int rc=0;
        for(int d=0;d<4;d++) if(PlacedTile.edgeOf(val,d)==TileType.Edge.R){roadExit[d]=true;rc++;}
        if(rc==2){int dA=-1,dB=-1; for(int d=0;d<4;d++)if(roadExit[d]){if(dA<0)dA=d;else dB=d;} if(dA>=0)drawRoadCorridor(g,sx,sy,dA,dB);}
        else for(int d=0;d<4;d++) if(roadExit[d]) drawRoadSegment(g,sx,sy,d,-1);
    }

    /** Full preview tile (larger ps square). */
    private void drawTilePreview(GuiGraphics g, int px, int py, int ps, TileType type, int rot) {
        short val=PlacedTile.pack(type,rot);
        g.fill(px,py,px+ps,py+ps,C_FIELD);
        int savedCell=CELL;
        // Scale road/city drawing to ps size — use the same helpers with offset math
        // Approximate by calling drawTile on a ps×ps area (rebind sx,sy)
        // We'll just draw it at CELL size since we made ps=CELL+10; approximate
        int ox=(ps-CELL)/2;
        drawTile(g, px+ox, py+ox, val);
        // Border for preview box
        g.fill(px,py,px+ps,py+1,C_BORDER);
        g.fill(px,py+ps-1,px+ps,py+ps,C_BORDER);
        g.fill(px,py,px+1,py+ps,C_BORDER);
        g.fill(px+ps-1,py,px+ps,py+ps,C_BORDER);
    }

    // ── Followers on tiles ────────────────────────────────────────────────────
    private void drawFollowersOnTile(GuiGraphics g, int sx, int sy, int lx, int ly, int[] claims) {
        for(int c:claims) {
            int[] p=TileKingdomsGame.unpackClaimInt(c);
            if(p[0]==lx&&p[1]==ly) {
                int col=p[3]<P_COL.length?P_COL[p[3]]:0xFFFFFFFF;
                int[] fp=featurePt(sx,sy,p[2]);
                // Meeple: small filled circle (5×5 with corners cut)
                g.fill(fp[0]-3,fp[1]-2,fp[0]+3,fp[1]+2,0xFF000000);
                g.fill(fp[0]-2,fp[1]-3,fp[0]+2,fp[1]+3,0xFF000000);
                g.fill(fp[0]-2,fp[1]-2,fp[0]+2,fp[1]+2,col);
            }
        }
    }

    private int[] featurePt(int sx, int sy, int slot) {
        int cx=sx+CELL/2, cy=sy+CELL/2;
        return switch(slot) {
            case TileKingdomsGame.SLOT_N -> new int[]{cx, sy+5};
            case TileKingdomsGame.SLOT_E -> new int[]{sx+CELL-5, cy};
            case TileKingdomsGame.SLOT_S -> new int[]{cx, sy+CELL-5};
            case TileKingdomsGame.SLOT_W -> new int[]{sx+5, cy};
            default                      -> new int[]{cx, cy};
        };
    }

    // ── Claim buttons ─────────────────────────────────────────────────────────
    private void drawClaimButtons(GuiGraphics g, int mx, int my, short[] grid, int[] claims) {
        int lx=menu.getLastPlacedX(), ly=menu.getLastPlacedY();
        int sx=tileScreenX(lx), sy=tileScreenY(ly);
        int gi=(ly+TileKingdomsBoard.HALF)*TileKingdomsBoard.SIZE+(lx+TileKingdomsBoard.HALF);
        if(gi<0||gi>=grid.length||PlacedTile.isEmpty(grid[gi])) return;
        short tile=grid[gi];
        TileType type=PlacedTile.typeOf(tile);

        // Highlight the just-placed tile
        g.fill(sx,sy,sx+CELL,sy+1,0xFFFFFF00);
        g.fill(sx,sy+CELL-1,sx+CELL,sy+CELL,0xFFFFFF00);
        g.fill(sx,sy,sx+1,sy+CELL,0xFFFFFF00);
        g.fill(sx+CELL-1,sy,sx+CELL,sy+CELL,0xFFFFFF00);

        for(int slot=0;slot<5;slot++) {
            boolean valid=false;
            if(slot<4) { TileType.Edge e=PlacedTile.edgeOf(tile,slot); valid=(e!=TileType.Edge.F)&&!isClaimed(lx,ly,slot,claims); }
            else valid=type!=null&&type.monastery&&!isClaimed(lx,ly,slot,claims);
            if(!valid) continue;
            int[] fp=featurePt(sx,sy,slot);
            boolean hov=Math.abs(mx-fp[0])<=7&&Math.abs(my-fp[1])<=7;
            g.fill(fp[0]-6,fp[1]-6,fp[0]+6,fp[1]+6, hov?0xEEFFFF00:0xBBFFFFFF);
            g.drawString(font,"+",fp[0]-3,fp[1]-4,0xFF000000,false);
        }
    }

    private boolean isClaimed(int lx,int ly,int slot,int[] claims) {
        for(int c:claims){int[]p=TileKingdomsGame.unpackClaimInt(c);if(p[0]==lx&&p[1]==ly&&p[2]==slot)return true;}
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
            int x = (int)(width / 2f - playersWidth / 2f + i * (totalW + totalW / 10f));
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
                g.fill(x + (int)totalW/2 - 2, 25, x + (int)totalW/2 + 2, 28, 0xFFFFFFFF);
            }
        }
        // Tiles remaining
        String rem = menu.getRemainingTiles() + " tiles";
        g.drawString(font, rem, width - font.width(rem) - 4, 10, 0xFF888888, false);
    }

    // ── renderLabels ──────────────────────────────────────────────────────────
    @Override
    protected void renderLabels(@NotNull net.minecraft.client.gui.GuiGraphics g, int mx, int my) {
        // renderLabels draws in menu-local coords (leftPos/topPos already subtracted by Minecraft)
        // We need screen-space coords for board messages; convert using leftPos/topPos offset
        int phase = menu.getPhase();
        boolean myTurn = isMyTurn();
        // Status message near bottom of board area (above bottom bar)
        // In menu-local coords: board bottom = imageHeight (or use height-topPos-BOTTOM_BAR)
        int msgY = imageHeight - 12;
        if (phase == TileKingdomsGame.PHASE_CLAIM && myTurn) {
            String msg = "Place follower (+) or Skip";
            g.fill(imageWidth/2-font.width(msg)/2-3, msgY-2, imageWidth/2+font.width(msg)/2+3, msgY+10, 0xBB000000);
            g.drawString(font, msg, imageWidth/2-font.width(msg)/2, msgY, 0xFFFFDD00, false);
        } else if (phase == TileKingdomsGame.PHASE_PLACE && myTurn && menu.getCurrentTile() != null) {
            String msg = "Click green to place, right-drag to pan";
            g.fill(imageWidth/2-font.width(msg)/2-3, msgY-2, imageWidth/2+font.width(msg)/2+3, msgY+10, 0xBB000000);
            g.drawString(font, msg, imageWidth/2-font.width(msg)/2, msgY, 0xFF88FF88, false);
        }
    }

    // ── Game over overlay ─────────────────────────────────────────────────────
    private void drawGameOverOverlay(net.minecraft.client.gui.GuiGraphics g) {
        java.util.List<dev.lucaargolo.charta.common.game.api.CardPlayer> players = menu.getGame().getPlayers();
        int n = players.size();
        int winnerIdx = menu.getWinnerIdx();

        // Semi-transparent dark panel in the centre of the board
        int panelW = 200, panelH = 30 + n * 14 + 20;
        int px = width/2 - panelW/2, py = boardTop() + boardH/2 - panelH/2;
        g.fill(px-4, py-4, px+panelW+4, py+panelH+4, 0xEE000000);
        g.fill(px-3, py-3, px+panelW+3, py+panelH+3, 0xBB111122);

        // Title
        String title = "♛ Game Over ♛";
        g.drawString(font, title, px+panelW/2 - font.width(title)/2, py+6, 0xFFFFD700, true);

        // Winner banner
        if (winnerIdx >= 0 && winnerIdx < players.size()) {
            String wname = players.get(winnerIdx).getName().getString();
            int wcol = winnerIdx < P_COL.length ? P_COL[winnerIdx] : 0xFFFFFFFF;
            String winner = "Winner: " + wname + " (" + menu.getScore(winnerIdx) + "pt)";
            g.fill(px+4, py+16, px+panelW-4, py+27, 0x44FFFFFF);
            g.drawString(font, winner, px+panelW/2-font.width(winner)/2, py+18, wcol, true);
        }

        // All scores
        for (int i = 0; i < n; i++) {
            int col = i < P_COL.length ? P_COL[i] : 0xFFFFFFFF;
            String name = players.get(i).getName().getString();
            if (name.length() > 12) name = name.substring(0, 11) + ".";
            String line = (i == winnerIdx ? "★ " : "  ") + name + ":  " + menu.getScore(i) + " pts";
            g.drawString(font, line, px+10, py+30+i*14, col, false);
        }

        // View map instruction
        String hint = "You can look around the map";
        g.drawString(font, hint, px+panelW/2-font.width(hint)/2, py+panelH-10, 0xFF888888, false);
    }


    // ── Input ─────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        boolean inBoard=mx>=boardLeft()&&mx<boardLeft()+boardW&&my>=boardTop()&&my<boardTop()+boardH;
        int phase=menu.getPhase(); boolean myTurn=isMyTurn();

        if(button==0) {
            TileType ct=menu.getCurrentTile();
            // ── Rotate button ────────────────────────────────────────────────
            if(ct!=null&&myTurn&&phase==TileKingdomsGame.PHASE_PLACE) {
                int ps=CELL+10, rbx=width-ps-6, rby=height-BOTTOM_BAR-16;
                if(mx>=rbx&&mx<rbx+ps&&my>=rby&&my<rby+12){sendAction(TileKingdomsActionPayload.ROTATE);return true;}
            }
            // ── Claim skip ───────────────────────────────────────────────────
            if(phase==TileKingdomsGame.PHASE_CLAIM&&myTurn) {
                int sbx=width/2-20, sby=height-BOTTOM_BAR-16;
                if(mx>=width/2-20&&mx<width/2+20&&my>=height-BOTTOM_BAR-16&&my<height-BOTTOM_BAR-4){sendAction(TileKingdomsActionPayload.SKIP_CLAIM);return true;}
                // Claim feature click
                short[] grid=menu.getBoardGrid(); int[] claimsArr=menu.getClaimsArray();
                int lx=menu.getLastPlacedX(), ly=menu.getLastPlacedY();
                int sx=tileScreenX(lx), sy=tileScreenY(ly);
                int gi=(ly+TileKingdomsBoard.HALF)*TileKingdomsBoard.SIZE+(lx+TileKingdomsBoard.HALF);
                if(gi>=0&&gi<grid.length&&!PlacedTile.isEmpty(grid[gi])) {
                    short tile=grid[gi]; TileType type=PlacedTile.typeOf(tile);
                    for(int slot=0;slot<5;slot++) {
                        boolean valid=slot<4?(PlacedTile.edgeOf(tile,slot)!=TileType.Edge.F&&!isClaimed(lx,ly,slot,claimsArr))
                                :(type!=null&&type.monastery&&!isClaimed(lx,ly,slot,claimsArr));
                        if(!valid) continue;
                        int[]fp=featurePt(sx,sy,slot);
                        if(Math.abs(mx-fp[0])<=7&&Math.abs(my-fp[1])<=7){sendAction(TileKingdomsGame.packClaimAction(slot));return true;}
                    }
                }
            }
            // ── Tile place ───────────────────────────────────────────────────
            if(inBoard&&phase==TileKingdomsGame.PHASE_PLACE&&myTurn&&ct!=null) {
                int lx=screenToTileX(mx), ly=screenToTileY(my);
                if(isValidPlace(menu.getBoardGrid(),lx,ly,ct,menu.getCurrentRotation())){
                    sendAction(TileKingdomsGame.packAction(lx,ly,menu.getCurrentRotation()));return true;
                }
            }
        }
        // ── Pan with right drag ───────────────────────────────────────────────
        if(button==1&&inBoard){dragging=true;dragStartMX=mx;dragStartMY=my;dragStartCamX=camX;dragStartCamY=camY;return true;}
        return super.mouseClicked(mx,my,button);
    }

    @Override
    public boolean mouseDragged(double mx,double my,int b,double dx,double dy) {
        if(dragging){camX=dragStartCamX-(float)((mx-dragStartMX)/CELL);camY=dragStartCamY-(float)((my-dragStartMY)/CELL);return true;}
        return super.mouseDragged(mx,my,b,dx,dy);
    }
    @Override
    public boolean mouseReleased(double mx,double my,int b){if(b==1)dragging=false;return super.mouseReleased(mx,my,b);}
    @Override
    public boolean mouseScrolled(double mx,double my,double sx,double sy){camY-=(float)(sy*0.7f);return true;}

    // ── Helpers ───────────────────────────────────────────────────────────────
    private boolean isMyTurn() {
        List<CardPlayer> pl=menu.getGame().getPlayers(); int idx=menu.getCurrentPlayerIdx();
        return idx>=0&&idx<pl.size()&&pl.get(idx).equals(menu.getCardPlayer())&&menu.isReady();
    }

    private boolean isValidPlace(short[] grid,int lx,int ly,TileType type,int rot) {
        int gx=lx+TileKingdomsBoard.HALF, gy=ly+TileKingdomsBoard.HALF;
        if(gx<0||gx>=TileKingdomsBoard.SIZE||gy<0||gy>=TileKingdomsBoard.SIZE) return false;
        int i=gy*TileKingdomsBoard.SIZE+gx;
        if(i>=grid.length||!PlacedTile.isEmpty(grid[i])) return false;
        int[]DX={0,1,0,-1},DY={-1,0,1,0}; boolean hasNb=false;
        short cand=PlacedTile.pack(type,rot);
        for(int d=0;d<4;d++){int nx=gx+DX[d],ny=gy+DY[d];if(nx<0||nx>=TileKingdomsBoard.SIZE||ny<0||ny>=TileKingdomsBoard.SIZE)continue;int ni=ny*TileKingdomsBoard.SIZE+nx;if(ni>=grid.length)continue;short nb=grid[ni];if(!PlacedTile.isEmpty(nb)){hasNb=true;if(!PlacedTile.compatible(cand,d,nb))return false;}}
        return hasNb;
    }
}