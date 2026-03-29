package dev.lucaargolo.charta.common.game.impl.roulette;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.lucaargolo.charta.client.render.screen.GameScreen;
import dev.lucaargolo.charta.common.ChartaMod;
import dev.lucaargolo.charta.common.game.Ranks;
import dev.lucaargolo.charta.common.game.Suits;
import dev.lucaargolo.charta.common.game.api.CardPlayer;
import dev.lucaargolo.charta.common.game.api.card.Rank;
import dev.lucaargolo.charta.common.game.api.card.Suit;
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
 * Roulette screen — real casino-style layout.
 *
 * Left half: spinning wheel with ball.
 * Right half: betting table (0, Red/Black, 13 ranks).
 *
 * Coordinate notes:
 *   renderBg  → absolute screen coords
 *   renderLabels → pre-translated by (leftPos, topPos), use relative coords
 *   mouseClicked → absolute, subtract leftPos/topPos for relative
 */
public class RouletteScreen extends GameScreen<RouletteGame, RouletteMenu> {

    // ── GUI dimensions ─────────────────────────────────────────────────────────
    private static final int W = 320;
    private static final int H = 240;

    // ── Wheel (relative to leftPos/topPos) ────────────────────────────────────
    private static final int WHL_CX   = 80;   // wheel centre X (relative)
    private static final int WHL_CY   = 130;  // wheel centre Y (relative)
    private static final int WHL_OR   = 68;   // outer radius of segments
    private static final int WHL_IR   = 22;   // inner hub radius
    private static final int WHL_RING = 8;    // gold outer track width
    private static final int WHL_BALL_TRACK = WHL_OR + WHL_RING / 2; // ball orbit radius
    private static final int SEGS     = 14;   // 0=green, 1-13=ranks

    // Segment order on wheel (alternating red/black, green at 0)
    // Index = segment position on wheel, value = rank index (0=green, 1=Ace…13=King)
    private static final int[] WHEEL_ORDER = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13};
    private static final String[] SEG_LABELS = {"0","A","2","3","4","5","6","7","8","9","10","J","Q","K"};

    // Segment colours [r,g,b]
    private static final float[][] SEG_FILL = {
        {0.05f,0.32f,0.05f}, // 0  green
        {0.08f,0.08f,0.08f}, // 1  A  black
        {0.52f,0.06f,0.06f}, // 2  2  red
        {0.08f,0.08f,0.08f}, // 3  3  black
        {0.52f,0.06f,0.06f}, // 4  4  red
        {0.08f,0.08f,0.08f}, // 5  5  black
        {0.52f,0.06f,0.06f}, // 6  6  red
        {0.08f,0.08f,0.08f}, // 7  7  black
        {0.52f,0.06f,0.06f}, // 8  8  red
        {0.08f,0.08f,0.08f}, // 9  9  black
        {0.52f,0.06f,0.06f}, // 10 10 red
        {0.08f,0.08f,0.08f}, // 11 J  black
        {0.52f,0.06f,0.06f}, // 12 Q  red
        {0.08f,0.08f,0.08f}, // 13 K  black
    };
    private static final float[][] SEG_EDGE = {
        {0.09f,0.50f,0.09f},
        {0.18f,0.18f,0.18f},
        {0.80f,0.12f,0.12f},
        {0.18f,0.18f,0.18f},
        {0.80f,0.12f,0.12f},
        {0.18f,0.18f,0.18f},
        {0.80f,0.12f,0.12f},
        {0.18f,0.18f,0.18f},
        {0.80f,0.12f,0.12f},
        {0.18f,0.18f,0.18f},
        {0.80f,0.12f,0.12f},
        {0.18f,0.18f,0.18f},
        {0.80f,0.12f,0.12f},
        {0.18f,0.18f,0.18f},
    };

    // ── Betting table (relative) ───────────────────────────────────────────────
    private static final int TBL_X  = 168;
    private static final int TBL_Y  = 36;
    private static final int TBL_W  = 144;
    private static final int TBL_H  = 196;

    // Zero cell
    private static final int ZERO_X = TBL_X + 4;
    private static final int ZERO_Y = TBL_Y + 4;
    private static final int ZERO_W = 28;
    private static final int ZERO_H = 40;

    // Red / Black cells
    private static final int RB_X   = TBL_X + 36;
    private static final int RB_Y   = TBL_Y + 4;
    private static final int RB_W   = 50;
    private static final int RB_H   = 40;

    // Rank grid (4 cols × 4 rows = 16 cells, 13 used)
    private static final int RNK_X  = TBL_X + 4;
    private static final int RNK_Y  = TBL_Y + 52;
    private static final int RNK_CW = 33;
    private static final int RNK_CH = 34;
    private static final int RNK_GAP = 2;
    private static final int RNK_COLS = 4;

    // ── Colours ────────────────────────────────────────────────────────────────
    private static final int C_FELT    = 0xFF1A4A2A;
    private static final int C_FELT_LT = 0xFF215A34;
    private static final int C_GOLD    = 0xFFC8A22A;
    private static final int C_GOLD_DK = 0xFF8A6A10;
    private static final int C_TBL_BG  = 0xFF0C2416;
    private static final int C_RED_BET = 0xFF8B1111;
    private static final int C_RED_HOV = 0xFFBB2222;
    private static final int C_BLK_BET = 0xFF141414;
    private static final int C_BLK_HOV = 0xFF2A2A2A;
    private static final int C_RNK_RED = 0xFF3D0E0E;
    private static final int C_RNK_BLK = 0xFF0E1A2E;
    private static final int C_INACT   = 0xFF2A2A2A;
    private static final int C_SEL     = 0xFFB8860B;
    private static final int C_WIN     = 0xFF22CC44;
    private static final int C_LOSE    = 0xFFCC2222;
    private static final int C_CHIP_P0 = 0xFFFF4444;
    private static final int C_CHIP_P1 = 0xFF44AAFF;

    private static final String[] RANK_LABELS = {"A","2","3","4","5","6","7","8","9","10","J","Q","K"};
    private static final boolean[] RANK_RED   = {false,false,false,false,false,false,false,false,false,false,true,true,true};

    private static final double TWO_PI = Math.PI * 2.0;

    // ── Animation state ────────────────────────────────────────────────────────
    private float wheelAngle  = 0f;
    private float wheelSpeed  = 0f;
    private float ballAngle   = 0f;   // ball position on outer track
    private float ballSpeed   = 0f;   // ball angular speed (opposite direction)
    private float ballR       = WHL_BALL_TRACK; // ball orbit radius (drops inward on stop)
    private boolean wasSpin   = false;
    private float resultAlpha = 0f;
    private int   winSegment  = -1;   // which segment index won (-1 = none)

    // ── Constructor ────────────────────────────────────────────────────────────
    public RouletteScreen(RouletteMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth  = W;
        this.imageHeight = H;
    }

    // ── Tick ───────────────────────────────────────────────────────────────────
    @Override
    public void containerTick() {
        super.containerTick();
        RouletteGame.Phase phase = menu.getPhase();
        switch (phase) {
            case BETTING -> {
                // Slow idle spin
                wheelSpeed = Mth.lerp(0.08f, wheelSpeed, 0.015f);
                wheelAngle += wheelSpeed;
                // Ball rests at top of track
                ballSpeed = 0f;
                ballR     = WHL_BALL_TRACK;
                ballAngle = -(float)(Math.PI / 2); // 12 o'clock
                wasSpin   = false;
                resultAlpha = Mth.lerp(0.12f, resultAlpha, 0f);
                winSegment  = -1;
            }
            case SPINNING -> {
                // Wheel accelerates, ball spins opposite direction fast
                wheelSpeed = Mth.lerp(0.03f, wheelSpeed, 8f);
                wheelAngle += wheelSpeed;
                ballSpeed  = Mth.lerp(0.03f, ballSpeed, -12f); // opposite direction
                ballAngle += ballSpeed;
                ballR      = WHL_BALL_TRACK;
                wasSpin    = true;
                resultAlpha = Mth.lerp(0.12f, resultAlpha, 0f);
                winSegment  = -1;
            }
            case RESULT -> {
                if (wasSpin) {
                    // Wheel decelerates
                    wheelSpeed = Mth.lerp(0.06f, wheelSpeed, 0f);
                    wheelAngle += wheelSpeed;
                    // Ball decelerates and drops inward
                    ballSpeed = Mth.lerp(0.06f, ballSpeed, 0f);
                    ballAngle += ballSpeed;
                    float targetR = WHL_OR - (WHL_OR - WHL_IR) * 0.55f;
                    ballR = Mth.lerp(0.08f, ballR, targetR);
                    if (Math.abs(ballSpeed) < 0.01f) {
                        wasSpin = false;
                        snapBallToWinSegment();
                    }
                }
                resultAlpha = Mth.lerp(0.05f, resultAlpha, 0.92f);
                // Determine winning segment from revealed card
                if (winSegment < 0) computeWinSegment();
            }
        }
        wheelAngle = (float)((wheelAngle % TWO_PI + TWO_PI) % TWO_PI);
        ballAngle  = (float)((ballAngle  % TWO_PI + TWO_PI) % TWO_PI);
    }

    private void computeWinSegment() {
        int rkId = menu.getRevealedRankId();
        if (rkId < 0) return;
        Rank rank = Ranks.getRegistry().byId(rkId);
        if (rank == null) return;
        int ri = RouletteGame.rankIndex(rank);
        winSegment = (ri < 0) ? 0 : ri + 1; // 0=green, 1-13=ranks
    }

    private void snapBallToWinSegment() {
        if (winSegment < 0) return;
        // Angle of the centre of the winning segment on the wheel
        float segMid = (float)((winSegment + 0.5) * TWO_PI / SEGS) + wheelAngle;
        ballAngle = segMid;
        float targetR = WHL_OR - (WHL_OR - WHL_IR) * 0.55f;
        ballR = targetR;
    }

    // ── Top bar ────────────────────────────────────────────────────────────────
    @Override
    public void renderTopBar(@NotNull GuiGraphics g) {
        g.fill(0, 0, width, 34, 0xCC000000);
        g.fill(0, 33, width, 34, C_GOLD);

        List<CardPlayer> players = menu.getGame().getPlayers();
        for (int i = 0; i < players.size(); i++) {
            CardPlayer p   = players.get(i);
            DyeColor   dc  = p.getColor();
            boolean    isMe = (i == menu.getMyIdx());
            int px = (i == 0) ? leftPos : leftPos + W / 2 + 2;
            int pw = W / 2 - 6;
            g.fill(px, 2, px + pw, 31, 0x88000000 + dc.getTextureDiffuseColor());
            CardPlayerHead.renderHead(g, px + 2, 3, p);
            // Name
            g.pose().pushPose();
            g.pose().translate(px + 28f, 4f, 0f);
            g.pose().scale(0.85f, 0.85f, 1f);
            g.drawString(font, p.getName(), 0, 0, isMe ? 0xFFFFDD44 : 0xFFFFFFFF, true);
            g.pose().popPose();
            // Chips
            int chips = menu.getChips(i);
            g.pose().pushPose();
            g.pose().translate(px + 28f, 14f, 0f);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, "\u2666 " + (chips / 100), 0, 0,
                    chips == 0 ? 0xFF888888 : 0xFFFFDD66, false);
            g.pose().popPose();
            // Bet status
            int bt = menu.getBetType(i);
            String bstatus = "";
            int    bcol    = 0xFFFFFFFF;
            if (bt != RouletteGame.BET_NONE) {
                bstatus = "\u2713 " + RouletteGame.describeBet(bt);
                bcol    = bt == RouletteGame.BET_RED_T ? 0xFFFF6666
                        : bt == RouletteGame.BET_BLACK_T ? 0xFFAAAAAA : 0xFF88FFCC;
            } else if (menu.getPhase() == RouletteGame.Phase.BETTING && chips > 0) {
                bstatus = isMe ? "Place your bet!" : "Waiting\u2026";
                bcol    = 0xFFFFAA44;
            }
            if (!bstatus.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(px + 28f, 22f, 0f);
                g.pose().scale(0.70f, 0.70f, 1f);
                g.drawString(font, bstatus, 0, 0, bcol, false);
                g.pose().popPose();
            }
        }
    }

    @Override
    public void renderBottomBar(@NotNull GuiGraphics g) {
        g.fill(0, height - 2, width, height, C_GOLD_DK);
    }

    // ── Background ─────────────────────────────────────────────────────────────
    @Override
    protected void renderBg(@NotNull GuiGraphics g, float pt, int mx, int my) {
        // Felt background
        g.fill(0, 34, width, height, C_FELT);
        g.fill(2, 36, width - 2, height - 2, C_FELT_LT);
        rect(g, leftPos, topPos + 34, leftPos + W, topPos + H, C_GOLD_DK);

        // Divider
        g.fill(leftPos + 162, topPos + 36, leftPos + 164, topPos + H - 2, C_GOLD_DK);

        // Wheel panel background
        int wpx = leftPos + 4, wpy = topPos + 36;
        g.fill(wpx, wpy, wpx + 156, wpy + TBL_H + 4, 0x33000000);
        rect(g, wpx, wpy, wpx + 156, wpy + TBL_H + 4, C_GOLD_DK);

        // Draw wheel
        int wcx = leftPos + WHL_CX;
        int wcy = topPos  + WHL_CY;
        drawWheel(g, wcx, wcy);

        // Phase label below wheel
        String phaseStr = switch (menu.getPhase()) {
            case BETTING  -> "Place your bet";
            case SPINNING -> "Spinning\u2026";
            case RESULT   -> "Results!";
        };
        int phaseCol = menu.getPhase() == RouletteGame.Phase.SPINNING ? 0xFF44DDFF
                     : menu.getPhase() == RouletteGame.Phase.RESULT   ? 0xFFFFDD44
                     : 0xFFAAFFAA;
        int pw = font.width(phaseStr);
        g.drawString(font, phaseStr, wcx - pw / 2, wcy + WHL_OR + WHL_RING + 10, phaseCol, true);

        // Bet amount hint
        int betAmt = menu.getGame().getBetAmountPublic() / 100;
        String betHint = "Bet: " + betAmt + " chip" + (betAmt != 1 ? "s" : "") + " per round";
        g.pose().pushPose();
        g.pose().translate(wpx + 4f, wcy + WHL_OR + WHL_RING + 22f, 0f);
        g.pose().scale(0.70f, 0.70f, 1f);
        g.drawString(font, betHint, 0, 0, 0xFF99BBAA, false);
        g.pose().popPose();

        // Betting table
        drawTable(g, mx, my);

        // Result overlay
        if (resultAlpha > 0.01f) drawResult(g);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mx, int my) {
        // Title above wheel (relative coords)
        g.drawCenteredString(font, "\u2756 ROULETTE \u2756", WHL_CX, 38, 0xFFFFDD44);
    }

    // ── Wheel rendering ────────────────────────────────────────────────────────
    private void drawWheel(GuiGraphics g, int cx, int cy) {
        Matrix4f mat = g.pose().last().pose();
        Tesselator tess = Tesselator.getInstance();
        final int SUB = 10; // subdivisions per segment
        final int TOTAL = SEGS * SUB;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // ── Outer track (dark wood ring) ──────────────────────────────────────
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder track = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int trackOuter = WHL_OR + WHL_RING;
        for (int t = 0; t < TOTAL; t++) {
            float a0 = (float)(t     * TWO_PI / TOTAL);
            float a1 = (float)((t+1) * TWO_PI / TOTAL);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            // Outer track: dark mahogany
            track.addVertex(mat, cx + WHL_OR*c0, cy + WHL_OR*s0, 0).setColor(0.22f,0.12f,0.04f,1f);
            track.addVertex(mat, cx + trackOuter*c0, cy + trackOuter*s0, 0).setColor(0.35f,0.20f,0.07f,1f);
            track.addVertex(mat, cx + trackOuter*c1, cy + trackOuter*s1, 0).setColor(0.35f,0.20f,0.07f,1f);
            track.addVertex(mat, cx + WHL_OR*c0, cy + WHL_OR*s0, 0).setColor(0.22f,0.12f,0.04f,1f);
            track.addVertex(mat, cx + trackOuter*c1, cy + trackOuter*s1, 0).setColor(0.35f,0.20f,0.07f,1f);
            track.addVertex(mat, cx + WHL_OR*c1, cy + WHL_OR*s1, 0).setColor(0.22f,0.12f,0.04f,1f);
        }
        BufferUploader.drawWithShader(track.buildOrThrow());

        // ── Gold outer rim ────────────────────────────────────────────────────
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder rim = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int rimOuter = trackOuter + 3;
        for (int t = 0; t < TOTAL; t++) {
            float a0 = (float)(t     * TWO_PI / TOTAL);
            float a1 = (float)((t+1) * TWO_PI / TOTAL);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            rim.addVertex(mat, cx + trackOuter*c0, cy + trackOuter*s0, 0).setColor(0.78f,0.63f,0.16f,1f);
            rim.addVertex(mat, cx + rimOuter*c0,   cy + rimOuter*s0,   0).setColor(0.95f,0.80f,0.30f,1f);
            rim.addVertex(mat, cx + rimOuter*c1,   cy + rimOuter*s1,   0).setColor(0.95f,0.80f,0.30f,1f);
            rim.addVertex(mat, cx + trackOuter*c0, cy + trackOuter*s0, 0).setColor(0.78f,0.63f,0.16f,1f);
            rim.addVertex(mat, cx + rimOuter*c1,   cy + rimOuter*s1,   0).setColor(0.95f,0.80f,0.30f,1f);
            rim.addVertex(mat, cx + trackOuter*c1, cy + trackOuter*s1, 0).setColor(0.78f,0.63f,0.16f,1f);
        }
        BufferUploader.drawWithShader(rim.buildOrThrow());

        // ── Coloured segments ─────────────────────────────────────────────────
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder seg = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int s = 0; s < SEGS; s++) {
            int wi = WHEEL_ORDER[s];
            float[] fc = SEG_FILL[wi];
            float[] ec = SEG_EDGE[wi];
            // Highlight winning segment
            boolean isWin = (winSegment >= 0 && wi == winSegment);
            float boost = isWin ? 0.25f : 0f;
            for (int t = 0; t < SUB; t++) {
                float a0 = (float)((s * SUB + t)     * TWO_PI / TOTAL) + wheelAngle;
                float a1 = (float)((s * SUB + t + 1) * TWO_PI / TOTAL) + wheelAngle;
                float x0 = Mth.cos(a0), y0 = Mth.sin(a0);
                float x1 = Mth.cos(a1), y1 = Mth.sin(a1);
                seg.addVertex(mat, cx + WHL_IR*x0, cy + WHL_IR*y0, 0)
                        .setColor(fc[0]+boost, fc[1]+boost, fc[2]+boost, 1f);
                seg.addVertex(mat, cx + WHL_OR*x0, cy + WHL_OR*y0, 0)
                        .setColor(ec[0]+boost, ec[1]+boost, ec[2]+boost, 1f);
                seg.addVertex(mat, cx + WHL_OR*x1, cy + WHL_OR*y1, 0)
                        .setColor(ec[0]+boost, ec[1]+boost, ec[2]+boost, 1f);
                seg.addVertex(mat, cx + WHL_IR*x0, cy + WHL_IR*y0, 0)
                        .setColor(fc[0]+boost, fc[1]+boost, fc[2]+boost, 1f);
                seg.addVertex(mat, cx + WHL_OR*x1, cy + WHL_OR*y1, 0)
                        .setColor(ec[0]+boost, ec[1]+boost, ec[2]+boost, 1f);
                seg.addVertex(mat, cx + WHL_IR*x1, cy + WHL_IR*y1, 0)
                        .setColor(fc[0]+boost, fc[1]+boost, fc[2]+boost, 1f);
            }
        }
        BufferUploader.drawWithShader(seg.buildOrThrow());

        // ── Gold divider lines between segments ───────────────────────────────
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder div = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float hw = 0.7f;
        for (int s = 0; s < SEGS; s++) {
            float a  = (float)(s * TWO_PI / SEGS) + wheelAngle;
            float ca = Mth.cos(a), sa = Mth.sin(a);
            float pa = Mth.cos(a + (float)(Math.PI/2)), ps = Mth.sin(a + (float)(Math.PI/2));
            div.addVertex(mat, cx+WHL_IR*ca+pa*hw, cy+WHL_IR*sa+ps*hw, 0).setColor(0.78f,0.63f,0.16f,0.9f);
            div.addVertex(mat, cx+WHL_OR*ca+pa*hw, cy+WHL_OR*sa+ps*hw, 0).setColor(0.78f,0.63f,0.16f,0.9f);
            div.addVertex(mat, cx+WHL_OR*ca-pa*hw, cy+WHL_OR*sa-ps*hw, 0).setColor(0.78f,0.63f,0.16f,0.9f);
            div.addVertex(mat, cx+WHL_IR*ca+pa*hw, cy+WHL_IR*sa+ps*hw, 0).setColor(0.78f,0.63f,0.16f,0.9f);
            div.addVertex(mat, cx+WHL_OR*ca-pa*hw, cy+WHL_OR*sa-ps*hw, 0).setColor(0.78f,0.63f,0.16f,0.9f);
            div.addVertex(mat, cx+WHL_IR*ca-pa*hw, cy+WHL_IR*sa-ps*hw, 0).setColor(0.78f,0.63f,0.16f,0.9f);
        }
        BufferUploader.drawWithShader(div.buildOrThrow());

        // ── Hub (gold centre cap) ─────────────────────────────────────────────
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder hub = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int HS = 32;
        for (int t = 0; t < HS; t++) {
            float a0 = (float)(t * TWO_PI / HS), a1 = (float)((t+1) * TWO_PI / HS);
            hub.addVertex(mat, cx, cy, 0).setColor(0.55f,0.35f,0.08f,1f);
            hub.addVertex(mat, cx+WHL_IR*Mth.cos(a0), cy+WHL_IR*Mth.sin(a0), 0).setColor(0.85f,0.70f,0.22f,1f);
            hub.addVertex(mat, cx+WHL_IR*Mth.cos(a1), cy+WHL_IR*Mth.sin(a1), 0).setColor(0.85f,0.70f,0.22f,1f);
        }
        BufferUploader.drawWithShader(hub.buildOrThrow());

        // ── Ball ──────────────────────────────────────────────────────────────
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder ball = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float bx = cx + ballR * Mth.cos(ballAngle);
        float by = cy + ballR * Mth.sin(ballAngle);
        int BR = 4, BS = 16;
        for (int t = 0; t < BS; t++) {
            float a0 = (float)(t * TWO_PI / BS), a1 = (float)((t+1) * TWO_PI / BS);
            ball.addVertex(mat, bx, by, 0).setColor(0.95f,0.95f,0.95f,1f);
            ball.addVertex(mat, bx+BR*Mth.cos(a0), by+BR*Mth.sin(a0), 0).setColor(0.65f,0.65f,0.65f,1f);
            ball.addVertex(mat, bx+BR*Mth.cos(a1), by+BR*Mth.sin(a1), 0).setColor(0.65f,0.65f,0.65f,1f);
        }
        BufferUploader.drawWithShader(ball.buildOrThrow());

        RenderSystem.disableBlend();

        // ── Segment labels (text) ─────────────────────────────────────────────
        for (int s = 0; s < SEGS; s++) {
            int wi = WHEEL_ORDER[s];
            float amid  = (float)((s + 0.5) * TWO_PI / SEGS) + wheelAngle;
            float labelR = (WHL_IR + WHL_OR) * 0.60f;
            int lx = cx + (int)(labelR * Mth.cos(amid));
            int ly = cy + (int)(labelR * Mth.sin(amid));
            String lbl = SEG_LABELS[wi];
            boolean isWin = (winSegment >= 0 && wi == winSegment);
            int textCol = isWin ? 0xFFFFFF44 : 0xFFFFFFFF;
            g.pose().pushPose();
            g.pose().translate(lx, ly, 0f);
            g.pose().scale(0.55f, 0.55f, 1f);
            g.drawString(font, lbl, -font.width(lbl) / 2, -4, textCol, true);
            g.pose().popPose();
        }

        // ── Pointer (fixed arrow at top) ──────────────────────────────────────
        int px = cx, py = cy - (WHL_OR + WHL_RING + 3);
        g.fill(px - 1, py - 6, px + 1, py + 2, C_GOLD);
        g.fill(px - 3, py - 4, px + 3, py - 2, C_GOLD);
        g.fill(px - 2, py - 2, px + 2, py,     C_GOLD);
    }

    // ── Betting table ──────────────────────────────────────────────────────────
    private void drawTable(GuiGraphics g, int mx, int my) {
        RouletteGame.Phase phase = menu.getPhase();
        boolean canBet = phase == RouletteGame.Phase.BETTING && menu.isGameReady()
                && menu.getMyBetType() == RouletteGame.BET_NONE && menu.getMyChips() > 0;
        int myBt = menu.getMyBetType();

        // Table background
        int tx = leftPos + TBL_X, ty = topPos + TBL_Y;
        g.fill(tx, ty, tx + TBL_W, ty + TBL_H, C_TBL_BG);
        rect(g, tx, ty, tx + TBL_W, ty + TBL_H, C_GOLD_DK);

        // ── Header label ──────────────────────────────────────────────────────
        g.pose().pushPose();
        g.pose().translate(tx + TBL_W / 2f, ty + 2f, 0f);
        g.pose().scale(0.65f, 0.65f, 1f);
        g.drawString(font, "PLACE YOUR BETS", -font.width("PLACE YOUR BETS") / 2, 0, C_GOLD & 0xFFFFFF, true);
        g.pose().popPose();

        // ── Zero cell ─────────────────────────────────────────────────────────
        int zx = leftPos + ZERO_X, zy = topPos + ZERO_Y;
        boolean zeroSel = false; // no zero bet in this game
        tableCell(g, mx, my, zx, zy, ZERO_W, ZERO_H, "0", false, zeroSel, 0xFF0A3A0A, 0xFF22AA22, false);
        g.pose().pushPose();
        g.pose().translate(zx + ZERO_W / 2f, zy + ZERO_H + 2f, 0f);
        g.pose().scale(0.55f, 0.55f, 1f);
        g.drawString(font, "GREEN", -font.width("GREEN") / 2, 0, 0xFF88FF88, false);
        g.pose().popPose();

        // ── Red cell ──────────────────────────────────────────────────────────
        int rx = leftPos + RB_X, ry = topPos + RB_Y;
        boolean redSel = myBt == RouletteGame.BET_RED_T;
        tableCell(g, mx, my, rx, ry, RB_W, RB_H,
                "\u2665 RED \u2666", canBet, redSel, C_RED_BET, C_RED_HOV, false);
        // Payout label
        g.pose().pushPose();
        g.pose().translate(rx + RB_W / 2f, ry + RB_H + 2f, 0f);
        g.pose().scale(0.60f, 0.60f, 1f);
        g.drawString(font, "\u00d72 payout", -font.width("\u00d72 payout") / 2, 0, 0xFFFF9999, false);
        g.pose().popPose();
        // Player chips on red
        drawChipsOnCell(g, rx, ry, RB_W, RB_H, RouletteGame.BET_RED_T);

        // ── Black cell ────────────────────────────────────────────────────────
        int bkx = leftPos + RB_X + RB_W + 4, bky = topPos + RB_Y;
        boolean blkSel = myBt == RouletteGame.BET_BLACK_T;
        tableCell(g, mx, my, bkx, bky, RB_W, RB_H,
                "\u2660 BLACK \u2663", canBet, blkSel, C_BLK_BET, C_BLK_HOV, false);
        g.pose().pushPose();
        g.pose().translate(bkx + RB_W / 2f, bky + RB_H + 2f, 0f);
        g.pose().scale(0.60f, 0.60f, 1f);
        g.drawString(font, "\u00d72 payout", -font.width("\u00d72 payout") / 2, 0, 0xFFAAAAAA, false);
        g.pose().popPose();
        drawChipsOnCell(g, bkx, bky, RB_W, RB_H, RouletteGame.BET_BLACK_T);

        // ── Rank grid header ──────────────────────────────────────────────────
        g.pose().pushPose();
        g.pose().translate(leftPos + RNK_X, topPos + RNK_Y - 10f, 0f);
        g.pose().scale(0.65f, 0.65f, 1f);
        g.drawString(font, "RANK BETS  (\u00d713 payout)", 0, 0, 0xFFCCBB44, false);
        g.pose().popPose();

        // ── Rank cells ────────────────────────────────────────────────────────
        for (int ri = 0; ri < 13; ri++) {
            int col = ri % RNK_COLS, row = ri / RNK_COLS;
            int cx = leftPos + RNK_X + col * (RNK_CW + RNK_GAP);
            int cy = topPos  + RNK_Y + row * (RNK_CH + RNK_GAP);
            boolean sel = myBt == ri + 3;
            boolean red = RANK_RED[ri];
            int base = red ? C_RNK_RED : C_RNK_BLK;
            int hov  = red ? 0xFF5A1A1A : 0xFF1A2A4A;
            tableCell(g, mx, my, cx, cy, RNK_CW, RNK_CH, RANK_LABELS[ri], canBet, sel, base, hov, red);
            drawChipsOnCell(g, cx, cy, RNK_CW, RNK_CH, ri + 3);
        }

        // ── Waiting overlay ───────────────────────────────────────────────────
        if (!canBet && phase == RouletteGame.Phase.BETTING && myBt != RouletteGame.BET_NONE) {
            g.fill(tx + 2, ty + 2, tx + TBL_W - 2, ty + TBL_H - 2, 0xAA000000);
            String wt = "Waiting for opponent\u2026";
            g.drawString(font, wt, tx + TBL_W / 2 - font.width(wt) / 2,
                    ty + TBL_H / 2 - 4, 0xFFFFAA44, true);
        }
    }

    /** Draw player chip tokens on a betting cell. */
    private void drawChipsOnCell(GuiGraphics g, int cx, int cy, int cw, int ch, int betType) {
        List<CardPlayer> players = menu.getGame().getPlayers();
        int offset = 0;
        for (int i = 0; i < players.size(); i++) {
            if (menu.getBetType(i) == betType) {
                int chipX = cx + cw - 8 - offset;
                int chipY = cy + ch - 8;
                int chipCol = (i == 0) ? C_CHIP_P0 : C_CHIP_P1;
                // Chip circle
                g.fill(chipX - 1, chipY - 1, chipX + 7, chipY + 7, 0x88000000);
                g.fill(chipX, chipY, chipX + 6, chipY + 6, chipCol);
                g.fill(chipX + 1, chipY, chipX + 5, chipY + 1, lighten(chipCol, 60));
                g.fill(chipX, chipY + 1, chipX + 1, chipY + 5, lighten(chipCol, 60));
                // Player initial
                String init = players.get(i).getName().getString().substring(0, 1).toUpperCase();
                g.pose().pushPose();
                g.pose().translate(chipX + 3f, chipY + 1f, 0f);
                g.pose().scale(0.5f, 0.5f, 1f);
                g.drawString(font, init, -font.width(init) / 2, 0, 0xFFFFFFFF, false);
                g.pose().popPose();
                offset += 8;
            }
        }
    }

    /** Render a single table cell with hover/select effects. */
    private void tableCell(GuiGraphics g, int mx, int my,
                           int x, int y, int w, int h,
                           String lbl, boolean active, boolean sel,
                           int base, int hovCol, boolean red) {
        boolean hov = active && mx >= x && mx < x + w && my >= y && my < y + h;
        int col = !active ? C_INACT : sel ? C_SEL : (hov ? hovCol : base);
        // Shadow
        g.fill(x + 2, y + h, x + w, y + h + 2, 0x66000000);
        g.fill(x + w, y + 2, x + w + 2, y + h, 0x66000000);
        // Body
        g.fill(x, y, x + w, y + h, col);
        // Highlight top/left
        g.fill(x, y, x + w, y + 2, lighten(col, 50));
        g.fill(x, y, x + 2, y + h, lighten(col, 50));
        // Shadow bottom/right
        g.fill(x, y + h - 2, x + w, y + h, darken(col, 40));
        g.fill(x + w - 2, y, x + w, y + h, darken(col, 40));
        // Sheen
        g.fill(x + 2, y + 2, x + w - 2, y + h / 3, 0x18FFFFFF);
        // Border
        if (sel) rect(g, x, y, x + w, y + h, C_GOLD);
        else     rect(g, x, y, x + w, y + h, darken(col, 20));
        // Label
        int lw = font.width(lbl);
        int lx = x + (w - lw) / 2;
        int ly = y + (h - 8) / 2;
        int textCol = !active ? 0xFF666666 : red ? 0xFFFF9999 : 0xFFFFFFFF;
        g.drawString(font, lbl, lx + 1, ly + 1, 0x44000000, false);
        g.drawString(font, lbl, lx, ly, textCol, false);
    }

    // ── Result overlay ─────────────────────────────────────────────────────────
    private void drawResult(GuiGraphics g) {
        int alpha = (int)(resultAlpha * 180);
        g.fill(leftPos, topPos + 34, leftPos + W, topPos + H, (alpha << 24));

        int rkId = menu.getRevealedRankId(), stId = menu.getRevealedSuitId();
        Rank rank = rkId >= 0 ? Ranks.getRegistry().byId(rkId) : null;
        Suit suit = stId >= 0 ? Suits.getRegistry().byId(stId) : null;
        if (rank == null || suit == null) return;

        boolean isRed = suit == Suits.HEARTS || suit == Suits.DIAMONDS;
        String suitSym = suit == Suits.HEARTS   ? "\u2665"
                       : suit == Suits.DIAMONDS ? "\u2666"
                       : suit == Suits.SPADES   ? "\u2660" : "\u2663";
        int ri = RouletteGame.rankIndex(rank);
        String rankLabel = (ri >= 0 && ri < RANK_LABELS.length) ? RANK_LABELS[ri] : "?";

        int myBt = menu.getMyBetType();
        boolean won = false;
        if (myBt != RouletteGame.BET_NONE) {
            if      (myBt == RouletteGame.BET_RED_T)   won = isRed;
            else if (myBt == RouletteGame.BET_BLACK_T) won = !isRed;
            else                                        won = (myBt - 3 == ri);
        }

        // ── Card display (centred in left wheel panel) ────────────────────────
        int cbx = leftPos + WHL_CX - 28;
        int cby = topPos  + WHL_CY - 38;
        int cbw = 56, cbh = 76;

        // Drop shadow
        g.fill(cbx + 4, cby + 4, cbx + cbw + 4, cby + cbh + 4, 0xAA000000);
        // Card face
        g.fill(cbx, cby, cbx + cbw, cby + cbh, 0xFFF8F8F8);
        rect(g, cbx, cby, cbx + cbw, cby + cbh, isRed ? 0xFFCC2222 : 0xFF222222);
        g.fill(cbx + 3, cby + 3, cbx + cbw - 3, cby + cbh - 3, 0xFFFFFFFF);
        rect(g, cbx + 3, cby + 3, cbx + cbw - 3, cby + cbh - 3, isRed ? 0xFFDD3333 : 0xFF444444);

        int cardCol = isRed ? 0xFFCC1111 : 0xFF111111;
        // Big rank
        g.pose().pushPose();
        g.pose().translate(cbx + cbw / 2f, cby + cbh / 2f - 10f, 0f);
        g.pose().scale(2.2f, 2.2f, 1f);
        g.drawString(font, rankLabel, -font.width(rankLabel) / 2, -4, cardCol, false);
        g.pose().popPose();
        // Suit corners
        g.pose().pushPose();
        g.pose().translate(cbx + 5f, cby + 5f, 0f);
        g.pose().scale(1.0f, 1.0f, 1f);
        g.drawString(font, suitSym, 0, 0, cardCol, false);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(cbx + cbw - 5f - font.width(suitSym), cby + cbh - 13f, 0f);
        g.pose().scale(1.0f, 1.0f, 1f);
        g.drawString(font, suitSym, 0, 0, cardCol, false);
        g.pose().popPose();

        // ── Win/Lose banner ───────────────────────────────────────────────────
        if (myBt != RouletteGame.BET_NONE) {
            String banner = won ? "YOU WIN!" : "YOU LOSE";
            int bannerCol = won ? C_WIN : C_LOSE;
            if (won) {
                float fl = (float)(Math.sin(net.minecraft.Util.getMillis() / 260.0) * 0.5 + 0.5);
                bannerCol = lerpCol(C_WIN, 0xFFFFFF44, fl * 0.45f);
            }
            g.pose().pushPose();
            g.pose().translate(leftPos + WHL_CX, cby - 18f, 0f);
            g.pose().scale(1.8f, 1.8f, 1f);
            int bw = font.width(banner);
            g.drawString(font, banner, -bw / 2 + 1, 1, 0xFF000000, false);
            g.drawString(font, banner, -bw / 2,     0, bannerCol,  false);
            g.pose().popPose();
        }

        // ── Card name ─────────────────────────────────────────────────────────
        String cf = rankLabel + " of " + cap(suit.toString());
        g.pose().pushPose();
        g.pose().translate(leftPos + WHL_CX, cby + cbh + 7f, 0f);
        g.pose().scale(0.85f, 0.85f, 1f);
        g.drawString(font, cf, -font.width(cf) / 2, 0, isRed ? 0xFFFF8888 : 0xFFCCCCCC, true);
        g.pose().popPose();

        // ── Payout detail ─────────────────────────────────────────────────────
        if (myBt != RouletteGame.BET_NONE) {
            int bet = menu.getMyBet();
            String det = won
                    ? "+" + ((myBt >= 3 ? bet * 12 : bet) / 100) + " chips (\u00d7" + (myBt >= 3 ? "13" : "2") + ")"
                    : "-" + (bet / 100) + " chips";
            g.pose().pushPose();
            g.pose().translate(leftPos + WHL_CX, cby + cbh + 18f, 0f);
            g.pose().scale(0.85f, 0.85f, 1f);
            g.drawString(font, det, -font.width(det) / 2, 0, won ? 0xFF66FF88 : 0xFFFF6666, true);
            g.pose().popPose();
        }
    }

    // ── Mouse click ────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (menu.getPhase() == RouletteGame.Phase.BETTING && menu.isGameReady()
                && menu.getMyBetType() == RouletteGame.BET_NONE && menu.getMyChips() > 0) {

            int rx = (int)mx - leftPos;
            int ry = (int)my - topPos;

            // Red
            if (hitRel(rx, ry, RB_X, RB_Y, RB_W, RB_H)) {
                sendBet(RouletteGame.BET_RED); return true;
            }
            // Black
            if (hitRel(rx, ry, RB_X + RB_W + 4, RB_Y, RB_W, RB_H)) {
                sendBet(RouletteGame.BET_BLACK); return true;
            }
            // Rank grid
            for (int ri = 0; ri < 13; ri++) {
                int col = ri % RNK_COLS, row = ri / RNK_COLS;
                int cx = RNK_X + col * (RNK_CW + RNK_GAP);
                int cy = RNK_Y + row * (RNK_CH + RNK_GAP);
                if (hitRel(rx, ry, cx, cy, RNK_CW, RNK_CH)) {
                    sendBet(RouletteGame.BET_RANK + ri); return true;
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
        g.fill(x, y, x2, y + 1, c);      g.fill(x, y2 - 1, x2, y2, c);
        g.fill(x, y + 1, x + 1, y2 - 1, c); g.fill(x2 - 1, y + 1, x2, y2 - 1, c);
    }
    private static boolean hitRel(int rx, int ry, int x, int y, int w, int h) {
        return rx >= x && rx < x + w && ry >= y && ry < y + h;
    }
    private static int lighten(int c, int a) {
        return 0xFF000000
                | (Math.min(255, ((c >> 16) & 0xFF) + a) << 16)
                | (Math.min(255, ((c >>  8) & 0xFF) + a) <<  8)
                |  Math.min(255,  (c        & 0xFF) + a);
    }
    private static int darken(int c, int a) {
        return 0xFF000000
                | (Math.max(0, ((c >> 16) & 0xFF) - a) << 16)
                | (Math.max(0, ((c >>  8) & 0xFF) - a) <<  8)
                |  Math.max(0,  (c        & 0xFF) - a);
    }
    private static int lerpCol(int a, int b, float t) {
        int ar=(a>>16)&0xFF, ag=(a>>8)&0xFF, ab=a&0xFF;
        int br=(b>>16)&0xFF, bg=(b>>8)&0xFF, bb=b&0xFF;
        return 0xFF000000
                | ((int)(ar + (br - ar) * t) << 16)
                | ((int)(ag + (bg - ag) * t) <<  8)
                |  (int)(ab + (bb - ab) * t);
    }
    private static String cap(String s) {
        return (s == null || s.isEmpty()) ? s
                : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
