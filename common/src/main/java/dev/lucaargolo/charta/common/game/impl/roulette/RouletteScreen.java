package dev.lucaargolo.charta.common.game.impl.roulette;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.lucaargolo.charta.client.render.screen.GameScreen;
import dev.lucaargolo.charta.common.ChartaMod;
import dev.lucaargolo.charta.common.game.api.CardPlayer;
import dev.lucaargolo.charta.common.utils.CardPlayerHead;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Full-screen roulette.
 *
 * Left half: spinning wheel with ball.
 * Right half: betting table (0-36 grid + Red/Black).
 *
 * Spin animation:
 *   BETTING  → wheel still
 *   SPINNING → smooth ease-in (50 ticks), cruise (60 ticks)
 *   RESULT   → smooth ease-out, ball drops into pocket, result shown after full stop
 */
public class RouletteScreen extends GameScreen<RouletteGame, RouletteMenu> {

    // European roulette wheel order (37 pockets: 0, then alternating)
    private static final int[] WHEEL_ORDER = {
        0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26
    };
    private static final int SEGS = 37;

    // Red numbers in European roulette
    private static final boolean[] IS_RED = new boolean[37];
    static {
        for (int n : new int[]{1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36}) IS_RED[n] = true;
    }

    // Pocket colours — flat fill [r,g,b], no gradient
    private static final float[] COL_GREEN = {0.04f,0.30f,0.04f, 0.04f,0.30f,0.04f};
    private static final float[] COL_RED   = {0.60f,0.04f,0.04f, 0.60f,0.04f,0.04f};
    private static final float[] COL_BLACK = {0.08f,0.08f,0.08f, 0.08f,0.08f,0.08f};

    // Colours
    private static final int C_FELT    = 0xFF0E3B1C;
    private static final int C_FELT_LT = 0xFF145024;
    private static final int C_GOLD    = 0xFFD4A820;
    private static final int C_GOLD_DK = 0xFF8A6A10;
    private static final int C_GOLD_LT = 0xFFEDC84A;
    private static final int C_TBL     = 0xFF092010;
    private static final int C_RED_P   = 0xFF8B1212;
    private static final int C_RED_H   = 0xFFBB2020;
    private static final int C_BLK_P   = 0xFF0E0E0E;
    private static final int C_BLK_H   = 0xFF282828;
    private static final int C_GRN_P   = 0xFF0A3A0A;
    private static final int C_GRN_H   = 0xFF145214;
    private static final int C_SEL     = 0xFFB8860B;
    private static final int C_INACT   = 0xFF222222;
    private static final int C_WIN     = 0xFF22CC44;
    private static final int C_LOSE    = 0xFFCC2222;
    private static final int C_P0      = 0xFFFF4444;
    private static final int C_P1      = 0xFF4488FF;

    private static final double TWO_PI = Math.PI * 2.0;
    private static final int TESS = 148; // circle tessellation steps

    // ── Wheel geometry (set in init) ───────────────────────────────────────────
    private int WCX, WCY;
    private float R_FRAME, R_WOOD, R_TRACK, R_PKT_OUT, R_PKT_IN;
    private float R_BOWL_OUT, R_BOWL_IN, R_HUB;
    private float ballR;

    // ── Table geometry (set in init) ───────────────────────────────────────────
    private int TX, TY, TW, TH;
    // Red/Black row
    private int RB_X, RB_Y, RB_W, RB_H;
    // Number grid: 3 columns × 12 rows + zero row
    private int NG_X, NG_Y, NG_CW, NG_CH;
    private static final int NG_GAP = 2;
    private static final int NG_COLS = 3; // classic roulette: 3 columns

    // ── Animation ──────────────────────────────────────────────────────────────
    private float wheelAngle = 0f;
    private float wheelSpeed = 0f;
    private float ballAngle  = (float)(-Math.PI / 2);
    private float ballSpeed  = 0f;
    private float ballR_cur;
    private boolean spinning  = false;
    private boolean stopped   = false;
    private float resultAlpha = 0f;
    private int   winSeg      = -1; // index in WHEEL_ORDER of winning number

    // Smooth spin curve
    private int spinTick = 0;
    private static final float W_MAX  = 0.18f;  // max wheel speed rad/tick
    private static final float B_MAX  = -0.28f; // max ball speed (opposite)
    private static final int   ACCEL  = 55;     // ticks to reach max speed

    public RouletteScreen(RouletteMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth  = 400;
        this.imageHeight = 280;
    }

    @Override
    protected void init() {
        this.imageWidth  = this.width;
        this.imageHeight = this.height;
        super.init();

        int usableH = height - 34 - 4;
        // Wheel occupies left third of screen
        int wheelAreaW = width / 3;
        float r = Math.min(wheelAreaW / 2f - 10, usableH / 2f - 10) * 0.90f;

        WCX = wheelAreaW / 2;
        WCY = 34 + usableH / 2;

        R_FRAME   = r;
        R_WOOD    = r * 0.92f;
        R_TRACK   = r * 0.85f;
        R_PKT_OUT = r * 0.80f;
        R_PKT_IN  = r * 0.54f;
        R_BOWL_OUT= r * 0.52f;
        R_BOWL_IN = r * 0.20f;
        R_HUB     = r * 0.14f;
        ballR     = R_TRACK - r * 0.04f;
        ballR_cur = ballR;

        // Table: right two-thirds
        int m = 6;
        TX = width / 3 + m;
        TY = 34 + m;
        TW = width - width / 3 - m * 2;
        TH = height - 34 - m * 2;

        // Red/Black row at top of table (two equal buttons)
        RB_X = TX + 4;
        RB_Y = TY + 18;
        RB_W = (TW - 12) / 2;
        RB_H = Math.max(22, (int)(TH * 0.11f));

        // Classic roulette table layout:
        //   Left column: "0" (full height of number grid)
        //   Right area: 12 columns × 3 rows (numbers 1-36)
        //   Numbers go: row 0 = 3,6,9...36 (top), row 1 = 2,5,8...35, row 2 = 1,4,7...34 (bottom)
        int gridTop = RB_Y + RB_H + 20;
        int gridH   = TH - (RB_H + 20 + 18 + 4);
        int gridW   = TW - 8;

        // Zero cell: left strip, full grid height
        int zeroCellW = (int)(gridW * 0.065f);
        zeroCellW = Math.max(zeroCellW, 14);

        // Number cells: 12 columns × 3 rows
        int numGridW = gridW - zeroCellW - 4;
        NG_CW = (numGridW - NG_GAP * 11) / 12;
        NG_CH = (gridH - NG_GAP * 2) / 3;

        // Store zero cell geometry in RB fields temporarily — use dedicated fields
        NG_X = TX + 4 + zeroCellW + 4; // number grid starts after zero
        NG_Y = gridTop;

        // Store zero cell position
        // We'll use RB_X/RB_Y for zero cell too — store separately
        // Zero: TX+4, gridTop, zeroCellW, gridH
        // Store in unused fields
        _ZX = TX + 4;
        _ZY = gridTop;
        _ZW = zeroCellW;
        _ZH = gridH;
    }

    // Zero cell geometry (set in init)
    private int _ZX, _ZY, _ZW, _ZH;

    // ── Tick ───────────────────────────────────────────────────────────────────
    @Override
    public void containerTick() {
        super.containerTick();
        RouletteGame.Phase phase = menu.getPhase();
        switch (phase) {
            case BETTING -> {
                wheelSpeed = 0f; ballSpeed = 0f;
                ballR_cur  = ballR;
                ballAngle  = (float)(-Math.PI / 2);
                spinning   = false; stopped = false; winSeg = -1; spinTick = 0;
                resultAlpha = Mth.lerp(0.10f, resultAlpha, 0f);
            }
            case SPINNING -> {
                spinTick++;
                float t = Math.min(1f, (float)spinTick / ACCEL);
                t = t * t * (3f - 2f * t); // smoothstep ease-in
                wheelSpeed = W_MAX * t;
                ballSpeed  = B_MAX * t;
                wheelAngle = norm(wheelAngle + wheelSpeed);
                ballAngle  = norm(ballAngle  + ballSpeed);
                ballR_cur  = ballR;
                spinning   = true; stopped = false; winSeg = -1;
                resultAlpha = Mth.lerp(0.10f, resultAlpha, 0f);
            }
            case RESULT -> {
                if (spinning) {
                    // Smooth ease-out
                    wheelSpeed = Mth.lerp(0.038f, wheelSpeed, 0f);
                    ballSpeed  = Mth.lerp(0.038f, ballSpeed,  0f);
                    wheelAngle = norm(wheelAngle + wheelSpeed);
                    ballAngle  = norm(ballAngle  + ballSpeed);
                    float targetR = R_PKT_IN + (R_PKT_OUT - R_PKT_IN) * 0.5f;
                    ballR_cur = Mth.lerp(0.05f, ballR_cur, targetR);
                    if (Math.abs(wheelSpeed) < 0.003f && Math.abs(ballSpeed) < 0.003f) {
                        wheelSpeed = 0f; ballSpeed = 0f;
                        spinning = false; stopped = true;
                        snapBall();
                    }
                }
                if (stopped) resultAlpha = Mth.lerp(0.04f, resultAlpha, 0.96f);
            }
        }
    }

    private void snapBall() {
        int num = menu.getRevealedNumber();
        if (num < 0) return;
        for (int i = 0; i < SEGS; i++) {
            if (WHEEL_ORDER[i] == num) { winSeg = i; break; }
        }
        if (winSeg < 0) return;
        float segCentre = (float)((winSeg + 0.5) * TWO_PI / SEGS) + wheelAngle;
        ballAngle = norm(segCentre);
        ballR_cur = R_PKT_IN + (R_PKT_OUT - R_PKT_IN) * 0.5f;
    }

    private static float norm(float a) { return (float)((a % TWO_PI + TWO_PI) % TWO_PI); }

    // ── Top bar ────────────────────────────────────────────────────────────────
    @Override
    public void renderTopBar(@NotNull GuiGraphics g) {
        g.fill(0, 0, width, 34, 0xDD000000);
        g.fill(0, 33, width, 34, C_GOLD);
        List<CardPlayer> players = menu.getGame().getPlayers();
        for (int i = 0; i < players.size(); i++) {
            CardPlayer p = players.get(i);
            DyeColor dc = p.getColor();
            boolean isMe = (i == menu.getMyIdx());
            int px = (i == 0) ? 4 : width / 2 + 4;
            int pw = width / 2 - 10;
            g.fill(px, 2, px + pw, 31, 0x88000000 + dc.getTextureDiffuseColor());
            rect(g, px, 2, px + pw, 31, isMe ? C_GOLD : C_GOLD_DK);
            CardPlayerHead.renderHead(g, px + 2, 3, p);
            g.pose().pushPose();
            g.pose().translate(px + 30f, 4f, 0f); g.pose().scale(0.85f, 0.85f, 1f);
            g.drawString(font, p.getName(), 0, 0, isMe ? 0xFFFFDD44 : 0xFFFFFFFF, true);
            g.pose().popPose();
            int chips = menu.getChips(i);
            g.pose().pushPose();
            g.pose().translate(px + 30f, 14f, 0f); g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, "\u2666 " + chips / 100, 0, 0, chips == 0 ? 0xFF666666 : 0xFFFFDD66, false);
            g.pose().popPose();
            int bt = menu.getBetType(i);
            String bs = ""; int bc = 0xFFFFFFFF;
            if (bt != RouletteGame.BET_NONE) {
                bs = "\u2713 " + RouletteGame.describeBet(bt);
                bc = bt == RouletteGame.BET_RED ? 0xFFFF7777 : bt == RouletteGame.BET_BLACK ? 0xFFBBBBBB : 0xFF88FFCC;
            } else if (menu.getPhase() == RouletteGame.Phase.BETTING && chips > 0) {
                bs = isMe ? "Place your bet!" : "Waiting\u2026";
                bc = 0xFFFFAA44;
            }
            if (!bs.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(px + 30f, 22f, 0f); g.pose().scale(0.68f, 0.68f, 1f);
                g.drawString(font, bs, 0, 0, bc, false);
                g.pose().popPose();
            }
        }
    }

    @Override
    public void renderBottomBar(@NotNull GuiGraphics g) {
        g.fill(0, height - 2, width, height, C_GOLD_DK);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics g, float pt, int mx, int my) {
        g.fill(0, 34, width, height, C_FELT);
        g.fill(2, 36, width - 2, height - 2, C_FELT_LT);
        rect(g, 0, 34, width, height, C_GOLD_DK);
        // Divider at 1/3
        g.fill(width/3 - 1, 36, width/3 + 1, height - 2, C_GOLD_DK);
        g.fill(4, 36, width/3 - 2, height - 2, 0x44000000);

        String title = "\u2756  R O U L E T T E  \u2756";
        g.drawString(font, title, WCX - font.width(title)/2, 40, C_GOLD, true);

        drawWheel(g, WCX, WCY);
        drawWheelStatus(g);
        drawTable(g, mx, my);
        if (resultAlpha > 0.01f) drawResult(g);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mx, int my) {}

    // ── Wheel ──────────────────────────────────────────────────────────────────
    private void drawWheel(GuiGraphics g, int cx, int cy) {
        Matrix4f mat = g.pose().last().pose();
        Tesselator tess = Tesselator.getInstance();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Shadow
        fillRing(tess,mat,cx,cy, R_FRAME, R_FRAME+7, 0.02f,0.02f,0.02f, 0.02f,0.02f,0.02f, 0.70f);
        // Outer gold rim
        fillRing(tess,mat,cx,cy, R_FRAME-3, R_FRAME, 0.72f,0.58f,0.12f, 0.92f,0.76f,0.26f, 1f);
        // Outer wood frame
        fillRing(tess,mat,cx,cy, R_WOOD, R_FRAME-3, 0.18f,0.09f,0.02f, 0.28f,0.15f,0.04f, 1f);
        // Inner gold edge of wood
        fillRing(tess,mat,cx,cy, R_WOOD, R_WOOD+2, 0.70f,0.56f,0.12f, 0.88f,0.72f,0.22f, 1f);
        // Ball track (dark polished wood)
        fillRing(tess,mat,cx,cy, R_TRACK, R_WOOD, 0.10f,0.05f,0.01f, 0.20f,0.10f,0.02f, 1f);
        // Inner edge of ball track
        fillRing(tess,mat,cx,cy, R_TRACK, R_TRACK+2, 0.60f,0.46f,0.10f, 0.78f,0.62f,0.18f, 1f);

        // ── Coloured pockets (flat colour, rotate with wheel) ─────────────────
        final int SUB = 8, TOTAL = SEGS * SUB;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder pkt = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int s = 0; s < SEGS; s++) {
            int num = WHEEL_ORDER[s];
            float[] c = num == 0 ? COL_GREEN : IS_RED[num] ? COL_RED : COL_BLACK;
            boolean win = (winSeg == s);
            float b = win ? 0.25f : 0f;
            float r = Math.min(1f, c[0]+b), gr = Math.min(1f, c[1]+b), bl = Math.min(1f, c[2]+b);
            for (int t = 0; t < SUB; t++) {
                float a0 = (float)((s*SUB+t)   * TWO_PI / TOTAL) + wheelAngle;
                float a1 = (float)((s*SUB+t+1) * TWO_PI / TOTAL) + wheelAngle;
                float x0=Mth.cos(a0),y0=Mth.sin(a0),x1=Mth.cos(a1),y1=Mth.sin(a1);
                // Flat colour — same at inner and outer edge
                pkt.addVertex(mat,cx+R_PKT_IN*x0, cy+R_PKT_IN*y0, 0).setColor(r,gr,bl,1f);
                pkt.addVertex(mat,cx+R_PKT_OUT*x0,cy+R_PKT_OUT*y0,0).setColor(r,gr,bl,1f);
                pkt.addVertex(mat,cx+R_PKT_OUT*x1,cy+R_PKT_OUT*y1,0).setColor(r,gr,bl,1f);
                pkt.addVertex(mat,cx+R_PKT_IN*x0, cy+R_PKT_IN*y0, 0).setColor(r,gr,bl,1f);
                pkt.addVertex(mat,cx+R_PKT_OUT*x1,cy+R_PKT_OUT*y1,0).setColor(r,gr,bl,1f);
                pkt.addVertex(mat,cx+R_PKT_IN*x1, cy+R_PKT_IN*y1, 0).setColor(r,gr,bl,1f);
            }
        }
        BufferUploader.drawWithShader(pkt.buildOrThrow());

        // ── Gold dividers between pockets ─────────────────────────────────────
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder div = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float hw = 0.9f;
        for (int s = 0; s < SEGS; s++) {
            float a=(float)(s*TWO_PI/SEGS)+wheelAngle, ca=Mth.cos(a), sa=Mth.sin(a);
            float pa=Mth.cos(a+(float)(Math.PI/2)), ps=Mth.sin(a+(float)(Math.PI/2));
            div.addVertex(mat,cx+R_PKT_IN*ca+pa*hw, cy+R_PKT_IN*sa+ps*hw, 0).setColor(0.75f,0.60f,0.14f,1f);
            div.addVertex(mat,cx+R_PKT_OUT*ca+pa*hw,cy+R_PKT_OUT*sa+ps*hw,0).setColor(0.75f,0.60f,0.14f,1f);
            div.addVertex(mat,cx+R_PKT_OUT*ca-pa*hw,cy+R_PKT_OUT*sa-ps*hw,0).setColor(0.75f,0.60f,0.14f,1f);
            div.addVertex(mat,cx+R_PKT_IN*ca+pa*hw, cy+R_PKT_IN*sa+ps*hw, 0).setColor(0.75f,0.60f,0.14f,1f);
            div.addVertex(mat,cx+R_PKT_OUT*ca-pa*hw,cy+R_PKT_OUT*sa-ps*hw,0).setColor(0.75f,0.60f,0.14f,1f);
            div.addVertex(mat,cx+R_PKT_IN*ca-pa*hw, cy+R_PKT_IN*sa-ps*hw, 0).setColor(0.75f,0.60f,0.14f,1f);
        }
        BufferUploader.drawWithShader(div.buildOrThrow());

        // ── Two decorative rings that cover the jagged colour borders ─────────
        // Outer ring: sits at R_PKT_OUT, covers the outer edge of pockets
        fillRing(tess,mat,cx,cy, R_PKT_OUT-1, R_PKT_OUT+3,
                0.65f,0.50f,0.10f, 0.82f,0.66f,0.18f, 1f);
        // Inner ring: sits at R_PKT_IN, covers the inner edge of pockets
        fillRing(tess,mat,cx,cy, R_PKT_IN-2, R_PKT_IN+2,
                0.65f,0.50f,0.10f, 0.82f,0.66f,0.18f, 1f);

        // ── Inner bowl + gold ring ────────────────────────────────────────────
        fillRing(tess,mat,cx,cy, R_BOWL_OUT, R_PKT_IN-2, 0.60f,0.46f,0.10f, 0.80f,0.64f,0.18f, 1f);
        fillRing(tess,mat,cx,cy, R_BOWL_IN, R_BOWL_OUT, 0.20f,0.11f,0.03f, 0.30f,0.17f,0.05f, 1f);

        // ── Spokes ────────────────────────────────────────────────────────────
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder spk = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float sw = 1.4f;
        for (int sp = 0; sp < 8; sp++) {
            float a=(float)(sp*TWO_PI/8)+wheelAngle, ca=Mth.cos(a), sa=Mth.sin(a);
            float pa=Mth.cos(a+(float)(Math.PI/2)), ps=Mth.sin(a+(float)(Math.PI/2));
            spk.addVertex(mat,cx+R_HUB*ca+pa*sw,    cy+R_HUB*sa+ps*sw,    0).setColor(0.72f,0.56f,0.12f,1f);
            spk.addVertex(mat,cx+R_BOWL_IN*ca+pa*sw, cy+R_BOWL_IN*sa+ps*sw,0).setColor(0.52f,0.38f,0.07f,1f);
            spk.addVertex(mat,cx+R_BOWL_IN*ca-pa*sw, cy+R_BOWL_IN*sa-ps*sw,0).setColor(0.52f,0.38f,0.07f,1f);
            spk.addVertex(mat,cx+R_HUB*ca+pa*sw,    cy+R_HUB*sa+ps*sw,    0).setColor(0.72f,0.56f,0.12f,1f);
            spk.addVertex(mat,cx+R_BOWL_IN*ca-pa*sw, cy+R_BOWL_IN*sa-ps*sw,0).setColor(0.52f,0.38f,0.07f,1f);
            spk.addVertex(mat,cx+R_HUB*ca-pa*sw,    cy+R_HUB*sa-ps*sw,    0).setColor(0.72f,0.56f,0.12f,1f);
        }
        BufferUploader.drawWithShader(spk.buildOrThrow());

        // ── Hub ───────────────────────────────────────────────────────────────
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder hub = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int t = 0; t < 32; t++) {
            float a0=(float)(t*TWO_PI/32)+wheelAngle, a1=(float)((t+1)*TWO_PI/32)+wheelAngle;
            hub.addVertex(mat,cx,cy,0).setColor(0.52f,0.33f,0.06f,1f);
            hub.addVertex(mat,cx+R_HUB*Mth.cos(a0),cy+R_HUB*Mth.sin(a0),0).setColor(0.86f,0.70f,0.20f,1f);
            hub.addVertex(mat,cx+R_HUB*Mth.cos(a1),cy+R_HUB*Mth.sin(a1),0).setColor(0.86f,0.70f,0.20f,1f);
        }
        BufferUploader.drawWithShader(hub.buildOrThrow());

        // ── Ball ──────────────────────────────────────────────────────────────
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder ball = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float bx = cx + ballR_cur * Mth.cos(ballAngle);
        float by = cy + ballR_cur * Mth.sin(ballAngle);
        float BR = R_FRAME * 0.055f;
        for (int t = 0; t < 24; t++) {
            float a0=(float)(t*TWO_PI/24), a1=(float)((t+1)*TWO_PI/24);
            float shade = 0.55f + 0.40f*(float)Math.max(0, Math.cos(a0 - Math.PI*0.75));
            ball.addVertex(mat,bx,by,0).setColor(0.95f,0.95f,0.95f,1f);
            ball.addVertex(mat,bx+BR*Mth.cos(a0),by+BR*Mth.sin(a0),0).setColor(shade,shade,shade,1f);
            ball.addVertex(mat,bx+BR*Mth.cos(a1),by+BR*Mth.sin(a1),0).setColor(shade,shade,shade,1f);
        }
        BufferUploader.drawWithShader(ball.buildOrThrow());
        RenderSystem.disableBlend();

        // ── Pocket labels with coloured background ────────────────────────────
        float ls = Math.max(0.40f, Math.min(0.65f, R_FRAME / 130f));
        // Background rect size in label-space (before scale)
        float bgW = 14f, bgH = 10f;
        for (int s = 0; s < SEGS; s++) {
            int num = WHEEL_ORDER[s];
            float amid = (float)((s+0.5)*TWO_PI/SEGS) + wheelAngle;
            float lr = (R_PKT_IN + R_PKT_OUT) * 0.56f;
            int lx = cx + (int)(lr * Mth.cos(amid));
            int ly = cy + (int)(lr * Mth.sin(amid));
            String lbl = String.valueOf(num);
            boolean isGreen = num == 0;
            boolean isRed   = num > 0 && IS_RED[num];
            // Coloured background behind the number
            int bgCol = isGreen ? 0xFF0A3A0A : isRed ? 0xFF8B1212 : 0xFF111111;
            if (winSeg == s) bgCol = lighten(bgCol, 60);
            int textCol = (winSeg == s) ? 0xFFFFFF44 : 0xFFFFFFFF;
            g.pose().pushPose();
            g.pose().translate(lx, ly, 0f);
            g.pose().scale(ls, ls, 1f);
            int fw = font.width(lbl);
            int lbgX = -fw/2 - 2, lbgY = -6;
            int lbgW = fw + 4, lbgH = 11;
            g.fill(lbgX, lbgY, lbgX+lbgW, lbgY+lbgH, bgCol);
            g.fill(lbgX, lbgY, lbgX+lbgW, lbgY+1, lighten(bgCol, 40)); // top edge
            g.drawString(font, lbl, -fw/2, -4, textCol, false);
            g.pose().popPose();
        }

        // ── Fixed pointer at top ──────────────────────────────────────────────
        int px = cx, py = (int)(cy - R_FRAME - 5);
        g.fill(px-1, py-8, px+1, py, C_GOLD);
        g.fill(px-3, py-6, px+3, py-4, C_GOLD);
        g.fill(px-2, py-4, px+2, py-2, C_GOLD);
    }

    private void fillRing(Tesselator tess, Matrix4f mat, int cx, int cy,
                          float r0, float r1,
                          float fr, float fg, float fb,
                          float er, float eg, float eb, float alpha) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int t = 0; t < TESS; t++) {
            float a0=(float)(t*TWO_PI/TESS), a1=(float)((t+1)*TWO_PI/TESS);
            float c0=Mth.cos(a0),s0=Mth.sin(a0),c1=Mth.cos(a1),s1=Mth.sin(a1);
            bb.addVertex(mat,cx+r0*c0,cy+r0*s0,0).setColor(fr,fg,fb,alpha);
            bb.addVertex(mat,cx+r1*c0,cy+r1*s0,0).setColor(er,eg,eb,alpha);
            bb.addVertex(mat,cx+r1*c1,cy+r1*s1,0).setColor(er,eg,eb,alpha);
            bb.addVertex(mat,cx+r0*c0,cy+r0*s0,0).setColor(fr,fg,fb,alpha);
            bb.addVertex(mat,cx+r1*c1,cy+r1*s1,0).setColor(er,eg,eb,alpha);
            bb.addVertex(mat,cx+r0*c1,cy+r0*s1,0).setColor(fr,fg,fb,alpha);
        }
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    private void drawWheelStatus(GuiGraphics g) {
        int by = (int)(WCY + R_FRAME + 12);
        RouletteGame.Phase phase = menu.getPhase();
        String s; int col;
        switch (phase) {
            case BETTING  -> { s = "Place your bet";  col = 0xFFAAFFAA; }
            case SPINNING -> { s = "Spinning\u2026";  col = 0xFF44DDFF; }
            case RESULT   -> { s = stopped ? "Result!" : "Slowing\u2026"; col = stopped ? 0xFFFFDD44 : 0xFF88CCFF; }
            default       -> { s = ""; col = 0xFFFFFFFF; }
        }
        g.drawString(font, s, WCX - font.width(s)/2, by, col, true);

        // Show winning number + colour after stop
        if (phase == RouletteGame.Phase.RESULT && stopped) {
            int num = menu.getRevealedNumber();
            if (num >= 0) {
                boolean isGreen = num == 0;
                boolean isRed   = num > 0 && IS_RED[num];
                String colorName = isGreen ? "Green" : isRed ? "Red" : "Black";
                int numCol  = isGreen ? 0xFF44FF44 : isRed ? 0xFFFF5555 : 0xFFCCCCCC;
                String line = num + "  \u2014  " + colorName;
                g.pose().pushPose();
                g.pose().translate(WCX, by + 12f, 0f); g.pose().scale(1.1f, 1.1f, 1f);
                g.drawString(font, line, -font.width(line)/2, 0, numCol, true);
                g.pose().popPose();
            }
        } else {
            int betAmt = menu.getGame().getBetAmountPublic() / 100;
            String hint = "Bet: " + betAmt + " chip" + (betAmt != 1 ? "s" : "") + " / round";
            g.pose().pushPose(); g.pose().translate(WCX, by+12f, 0f); g.pose().scale(0.70f,0.70f,1f);
            g.drawString(font, hint, -font.width(hint)/2, 0, 0xFF88AA88, false); g.pose().popPose();
        }
    }

    // ── Betting table ──────────────────────────────────────────────────────────
    private void drawTable(GuiGraphics g, int mx, int my) {
        RouletteGame.Phase phase = menu.getPhase();
        boolean canBet = phase == RouletteGame.Phase.BETTING && menu.isGameReady()
                && menu.getMyBetType() == RouletteGame.BET_NONE && menu.getMyChips() > 0;
        int myBt = menu.getMyBetType();

        g.fill(TX, TY, TX+TW, TY+TH, C_TBL);
        rect(g, TX, TY, TX+TW, TY+TH, C_GOLD_DK);

        // Header
        lbl(g, TX+TW/2, TY+5, "B E T T I N G   T A B L E", C_GOLD & 0xFFFFFF, 0.70f);
        g.fill(TX+4, TY+15, TX+TW-4, TY+16, C_GOLD_DK);

        // ── Red / Black outside bets ──────────────────────────────────────────
        outsideBet(g, mx, my, RB_X, RB_Y, RB_W, RB_H,
                "\u2665 RED \u2666", "\u00d72", canBet,
                myBt == RouletteGame.BET_RED, C_RED_P, C_RED_H, 0xFFFF9999);
        chips(g, RB_X, RB_Y, RB_W, RB_H, RouletteGame.BET_RED);

        outsideBet(g, mx, my, RB_X + RB_W + 4, RB_Y, RB_W, RB_H,
                "\u2660 BLACK \u2663", "\u00d72", canBet,
                myBt == RouletteGame.BET_BLACK, C_BLK_P, C_BLK_H, 0xFFBBBBBB);
        chips(g, RB_X + RB_W + 4, RB_Y, RB_W, RB_H, RouletteGame.BET_BLACK);

        // Separator
        g.fill(TX+4, _ZY - 14, TX+TW-4, _ZY - 13, C_GOLD_DK);
        lbl(g, TX+TW/2, _ZY - 11, "NUMBER BETS  (\u00d736 payout)", 0xFFCCBB44, 0.60f);

        // ── Zero cell (left strip, full grid height) ──────────────────────────
        numCell(g, mx, my, _ZX, _ZY, _ZW, _ZH, 0, canBet, myBt == 10);
        chips(g, _ZX, _ZY, _ZW, _ZH, 10);

        // ── Number grid: classic roulette layout ──────────────────────────────
        // Row 0 (top):    3, 6, 9, 12, 15, 18, 21, 24, 27, 30, 33, 36
        // Row 1 (middle): 2, 5, 8, 11, 14, 17, 20, 23, 26, 29, 32, 35
        // Row 2 (bottom): 1, 4, 7, 10, 13, 16, 19, 22, 25, 28, 31, 34
        for (int col = 0; col < 12; col++) {
            for (int row = 0; row < 3; row++) {
                int num = col * 3 + (2 - row) + 1; // row0=top=3,6,9... row2=bottom=1,4,7...
                int cx = NG_X + col * (NG_CW + NG_GAP);
                int cy = NG_Y + row * (NG_CH + NG_GAP);
                int betType = 10 + num;
                numCell(g, mx, my, cx, cy, NG_CW, NG_CH, num, canBet, myBt == betType);
                chips(g, cx, cy, NG_CW, NG_CH, betType);
            }
        }

        // ── Column labels (2:1) at the right edge ─────────────────────────────
        int col2x = NG_X + 12 * (NG_CW + NG_GAP);
        int col2w = Math.max(12, TX + TW - col2x - 4);
        for (int row = 0; row < 3; row++) {
            int cy = NG_Y + row * (NG_CH + NG_GAP);
            // Column bet not implemented — just decorative label
            g.fill(col2x, cy, col2x + col2w, cy + NG_CH, 0xFF0A1A0A);
            rect(g, col2x, cy, col2x + col2w, cy + NG_CH, C_GOLD_DK);
            g.pose().pushPose();
            g.pose().translate(col2x + col2w/2f, cy + NG_CH/2f - 4f, 0f);
            g.pose().scale(0.50f, 0.50f, 1f);
            g.drawString(font, "2:1", -font.width("2:1")/2, 0, 0xFF88AA88, false);
            g.pose().popPose();
        }

        // Overlays
        if (!canBet && phase == RouletteGame.Phase.BETTING && myBt != RouletteGame.BET_NONE) {
            g.fill(TX+2, TY+2, TX+TW-2, TY+TH-2, 0xAA000000);
            String wt = "Waiting for opponent\u2026";
            g.drawString(font, wt, TX+TW/2-font.width(wt)/2, TY+TH/2-4, 0xFFFFAA44, true);
        }
        if (phase == RouletteGame.Phase.SPINNING) {
            g.fill(TX+2, TY+2, TX+TW-2, TY+TH-2, 0xBB000000);
            String sp = "Spinning\u2026";
            g.drawString(font, sp, TX+TW/2-font.width(sp)/2, TY+TH/2-4, 0xFF44DDFF, true);
        }
    }

    /**
     * Outside bet button — flat colour background, white text, gold border when selected.
     */
    private void outsideBet(GuiGraphics g, int mx, int my,
                            int x, int y, int w, int h,
                            String mainLbl, String payoutLbl,
                            boolean active, boolean sel,
                            int base, int hovCol, int textCol) {
        boolean hov = active && mx>=x && mx<x+w && my>=y && my<y+h;
        int col = !active ? C_INACT : sel ? C_SEL : (hov ? hovCol : base);

        // Flat body
        g.fill(x, y, x+w, y+h, col);
        // Subtle top highlight
        g.fill(x, y, x+w, y+1, lighten(col, 40));
        // Border
        if (sel) { rect(g,x,y,x+w,y+h,C_GOLD); rect(g,x+2,y+2,x+w-2,y+h-2,C_GOLD_LT); }
        else       rect(g,x,y,x+w,y+h,darken(col,30));
        if (hov)   g.fill(x,y,x+w,y+h,0x22FFFFFF);

        // Main label — white, centred
        int lw = font.width(mainLbl);
        int lx = x + (w - lw) / 2;
        int ly = y + h/2 - 9;
        g.drawString(font, mainLbl, lx+1, ly+1, 0x44000000, false);
        g.drawString(font, mainLbl, lx,   ly,   0xFFFFFFFF, false);

        // Payout sub-label
        g.pose().pushPose();
        g.pose().translate(x + w/2f, y + h/2f + 2f, 0f); g.pose().scale(0.65f,0.65f,1f);
        g.drawString(font, payoutLbl, -font.width(payoutLbl)/2, 0, 0xFFCCCCCC, false);
        g.pose().popPose();
    }

    /**
     * Number cell — square, coloured background matching roulette colour,
     * with a bright colour stripe at the bottom and the number centred.
     */
    private void numCell(GuiGraphics g, int mx, int my,
                         int x, int y, int w, int h,
                         int num, boolean active, boolean sel) {
        boolean isGreen = (num == 0);
        boolean isRed   = num > 0 && IS_RED[num];

        // Base colour: dark version of the pocket colour
        int base, hov, stripe, textCol;
        if (isGreen) {
            base    = 0xFF0A2A0A;
            hov     = 0xFF145214;
            stripe  = 0xFF22CC44;
            textCol = 0xFF88FF88;
        } else if (isRed) {
            base    = 0xFF2A0808;
            hov     = 0xFF5A1010;
            stripe  = 0xFFCC2222;
            textCol = 0xFFFF9999;
        } else {
            base    = 0xFF0A0A0A;
            hov     = 0xFF222222;
            stripe  = 0xFF888888;
            textCol = 0xFFDDDDDD;
        }

        boolean hovering = active && mx>=x && mx<x+w && my>=y && my<y+h;
        int col = !active ? C_INACT : sel ? C_SEL : (hovering ? hov : base);

        // Body (no drop shadow)
        g.fill(x, y, x+w, y+h, col);
        // Top highlight
        g.fill(x, y, x+w, y+2, lighten(col, 55));
        g.fill(x, y, x+2, y+h, lighten(col, 55));
        // Bottom/right shadow
        g.fill(x, y+h-2, x+w, y+h, darken(col, 45));
        g.fill(x+w-2, y, x+w, y+h, darken(col, 45));
        // Colour stripe at bottom (3px)
        int stripeH = Math.max(3, h / 6);
        if (!active) {
            g.fill(x+1, y+h-stripeH-1, x+w-1, y+h-1, darken(stripe, 60));
        } else if (sel) {
            g.fill(x+1, y+h-stripeH-1, x+w-1, y+h-1, C_GOLD);
        } else {
            g.fill(x+1, y+h-stripeH-1, x+w-1, y+h-1, stripe);
        }
        // Border
        if (sel) { rect(g,x,y,x+w,y+h,C_GOLD); rect(g,x+2,y+2,x+w-2,y+h-2,C_GOLD_LT); }
        else       rect(g,x,y,x+w,y+h,darken(col,20));
        if (hovering) g.fill(x,y,x+w,y+h,0x28FFFFFF);

        // Number text — centred, slightly above stripe
        String lbl = String.valueOf(num);
        int lw = font.width(lbl);
        int lx = x + (w - lw) / 2;
        int ly = y + (h - stripeH - 10) / 2;
        int tc = !active ? 0xFF444444 : sel ? 0xFFFFDD44 : textCol;
        g.drawString(font, lbl, lx+1, ly+1, 0x44000000, false);
        g.drawString(font, lbl, lx,   ly,   tc,          false);
    }

    private void lbl(GuiGraphics g, int cx, int y, String txt, int col, float scale) {
        g.pose().pushPose(); g.pose().translate(cx, y, 0f); g.pose().scale(scale,scale,1f);
        g.drawString(font, txt, -font.width(txt)/2, 0, col, false); g.pose().popPose();
    }

    private void chips(GuiGraphics g, int cx, int cy, int cw, int ch, int betType) {
        List<CardPlayer> players = menu.getGame().getPlayers();
        int off = 0;
        for (int i = 0; i < players.size(); i++) {
            if (menu.getBetType(i) != betType) continue;
            int chipX=cx+cw-9-off, chipY=cy+ch-9;
            int cc=(i==0)?C_P0:C_P1;
            g.fill(chipX-1,chipY-1,chipX+8,chipY+8,0x77000000);
            g.fill(chipX,chipY,chipX+7,chipY+7,cc);
            g.fill(chipX+1,chipY,chipX+6,chipY+1,lighten(cc,70));
            g.fill(chipX,chipY+1,chipX+1,chipY+6,lighten(cc,70));
            String init=players.get(i).getName().getString().substring(0,1).toUpperCase();
            g.pose().pushPose(); g.pose().translate(chipX+3.5f,chipY+1f,0f); g.pose().scale(0.5f,0.5f,1f);
            g.drawString(font,init,-font.width(init)/2,0,0xFFFFFFFF,false); g.pose().popPose();
            off+=9;
        }
    }

    // ── Result overlay ─────────────────────────────────────────────────────────
    private void drawResult(GuiGraphics g) {
        int alpha = (int)(resultAlpha * 175);
        g.fill(0, 34, width, height, (alpha << 24));

        int num = menu.getRevealedNumber();
        if (num < 0) return;

        boolean isRed   = num > 0 && IS_RED[num];
        boolean isGreen = num == 0;
        String numStr   = String.valueOf(num);
        String colorStr = isGreen ? "Green" : isRed ? "Red" : "Black";
        int numBgCol    = isGreen ? 0xFF0A3A0A : isRed ? 0xFF8B1212 : 0xFF111111;
        int numFgCol    = isGreen ? 0xFF66FF66 : isRed ? 0xFFFF8888 : 0xFFCCCCCC;

        // Big number circle centred on wheel
        int cr = (int)(R_FRAME * 0.30f);
        int cbx = WCX - cr, cby = WCY - cr;

        // Draw circle background
        Matrix4f mat = g.pose().last().pose();
        Tesselator tess = Tesselator.getInstance();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float[] fc = isGreen ? COL_GREEN : isRed ? COL_RED : COL_BLACK;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder circ = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int t = 0; t < 48; t++) {
            float a0=(float)(t*TWO_PI/48), a1=(float)((t+1)*TWO_PI/48);
            circ.addVertex(mat,WCX,WCY,0).setColor(fc[0],fc[1],fc[2],0.95f);
            circ.addVertex(mat,WCX+cr*Mth.cos(a0),WCY+cr*Mth.sin(a0),0).setColor(fc[3],fc[4],fc[5],0.95f);
            circ.addVertex(mat,WCX+cr*Mth.cos(a1),WCY+cr*Mth.sin(a1),0).setColor(fc[3],fc[4],fc[5],0.95f);
        }
        BufferUploader.drawWithShader(circ.buildOrThrow());
        // Gold border ring
        fillRing(tess,mat,WCX,WCY, cr, cr+4, 0.72f,0.58f,0.12f, 0.92f,0.76f,0.26f, 1f);
        RenderSystem.disableBlend();

        // Number text
        float numScale = cr > 30 ? 3.0f : 2.0f;
        g.pose().pushPose(); g.pose().translate(WCX, WCY-4f, 0f); g.pose().scale(numScale,numScale,1f);
        g.drawString(font, numStr, -font.width(numStr)/2, -4, numFgCol, true); g.pose().popPose();

        // Colour label below number
        g.pose().pushPose(); g.pose().translate(WCX, WCY+cr+8f, 0f); g.pose().scale(0.90f,0.90f,1f);
        g.drawString(font, colorStr, -font.width(colorStr)/2, 0, numFgCol, true); g.pose().popPose();

        // Per-player result
        List<CardPlayer> players = menu.getGame().getPlayers();
        int myIdx = menu.getMyIdx();
        int lineY = WCY - cr - 30;

        for (int i = 0; i < players.size(); i++) {
            int bt = menu.getBetType(i);
            if (bt == RouletteGame.BET_NONE) continue;
            boolean won;
            if      (bt == RouletteGame.BET_RED)   won = isRed;
            else if (bt == RouletteGame.BET_BLACK)  won = !isRed && !isGreen;
            else                                    won = (bt - 10 == num);

            String name = players.get(i).getName().getString();
            int bet = menu.getBet(i);
            String msg;
            int msgCol;
            if (won) {
                int mult = (bt >= 10) ? 35 : 1;
                int gain = (bet * mult) / 100;
                msg = name + " WON +" + gain + " chips!";
                msgCol = (i == myIdx) ? C_WIN : 0xFF44AA44;
                if (i == myIdx) {
                    float fl = (float)(Math.sin(net.minecraft.Util.getMillis() / 260.0) * 0.5 + 0.5);
                    msgCol = lerpCol(C_WIN, 0xFFFFFF44, fl * 0.45f);
                }
            } else {
                int loss = bet / 100;
                msg = name + " lost -" + loss + " chips";
                msgCol = (i == myIdx) ? C_LOSE : 0xFFAA4444;
            }

            // Big banner for my result
            if (i == myIdx) {
                String banner = won ? "YOU WIN!" : "YOU LOSE";
                int bannerCol = won ? msgCol : C_LOSE;
                g.pose().pushPose(); g.pose().translate(WCX, lineY - 22f, 0f); g.pose().scale(2.0f,2.0f,1f);
                int bw = font.width(banner);
                g.drawString(font, banner, -bw/2+1, 1, 0xFF000000, false);
                g.drawString(font, banner, -bw/2,   0, bannerCol,  false);
                g.pose().popPose();
            }

            g.pose().pushPose(); g.pose().translate(WCX, lineY + i*13f, 0f); g.pose().scale(0.88f,0.88f,1f);
            g.drawString(font, msg, -font.width(msg)/2, 0, msgCol, true); g.pose().popPose();
        }
    }

    // ── Mouse click ────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (menu.getPhase() == RouletteGame.Phase.BETTING && menu.isGameReady()
                && menu.getMyBetType() == RouletteGame.BET_NONE && menu.getMyChips() > 0) {
            int rx = (int)mx, ry = (int)my;
            // Red
            if (hit(rx,ry, RB_X, RB_Y, RB_W, RB_H)) { sendBet(RouletteGame.ACTION_RED);   return true; }
            // Black
            if (hit(rx,ry, RB_X+RB_W+4, RB_Y, RB_W, RB_H)) { sendBet(RouletteGame.ACTION_BLACK); return true; }
            // Zero
            if (hit(rx,ry, _ZX, _ZY, _ZW, _ZH)) { sendBet(RouletteGame.ACTION_NUMBER + 0); return true; }
            // Numbers 1-36 (classic layout: 12 cols × 3 rows)
            for (int col = 0; col < 12; col++) {
                for (int row = 0; row < 3; row++) {
                    int num = col * 3 + (2 - row) + 1;
                    int cx = NG_X + col * (NG_CW + NG_GAP);
                    int cy = NG_Y + row * (NG_CH + NG_GAP);
                    if (hit(rx,ry, cx, cy, NG_CW, NG_CH)) { sendBet(RouletteGame.ACTION_NUMBER + num); return true; }
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    private void sendBet(int action) {
        ChartaMod.getPacketManager().sendToServer(
                new dev.lucaargolo.charta.common.network.RouletteActionPayload(action));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private static void rect(GuiGraphics g, int x, int y, int x2, int y2, int c) {
        g.fill(x,y,x2,y+1,c); g.fill(x,y2-1,x2,y2,c);
        g.fill(x,y+1,x+1,y2-1,c); g.fill(x2-1,y+1,x2,y2-1,c);
    }
    private static boolean hit(int rx, int ry, int x, int y, int w, int h) {
        return rx>=x && rx<x+w && ry>=y && ry<y+h;
    }
    private static int lighten(int c, int a) {
        return 0xFF000000|(Math.min(255,((c>>16)&0xFF)+a)<<16)|(Math.min(255,((c>>8)&0xFF)+a)<<8)|Math.min(255,(c&0xFF)+a);
    }
    private static int darken(int c, int a) {
        return 0xFF000000|(Math.max(0,((c>>16)&0xFF)-a)<<16)|(Math.max(0,((c>>8)&0xFF)-a)<<8)|Math.max(0,(c&0xFF)-a);
    }
    private static int lerpCol(int a, int b, float t) {
        int ar=(a>>16)&0xFF,ag=(a>>8)&0xFF,ab=a&0xFF,br=(b>>16)&0xFF,bg=(b>>8)&0xFF,bb=b&0xFF;
        return 0xFF000000|((int)(ar+(br-ar)*t)<<16)|((int)(ag+(bg-ag)*t)<<8)|(int)(ab+(bb-ab)*t);
    }
}
