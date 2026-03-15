package dev.lucaargolo.charta.common.game.impl.texasholdem;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lucaargolo.charta.client.ChartaModClient;
import dev.lucaargolo.charta.client.render.screen.GameScreen;
import dev.lucaargolo.charta.common.ChartaMod;
import dev.lucaargolo.charta.common.game.api.CardPlayer;
import dev.lucaargolo.charta.common.menu.CardSlot;
import dev.lucaargolo.charta.common.network.TexasHoldemActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TexasHoldemScreen extends GameScreen<TexasHoldemGame, TexasHoldemMenu> {

    // Gameplay background
    private static final ResourceLocation TEXTURE = ChartaMod.id("textures/gui/texas_holdem_bg.png");

    // Button layout – 4 buttons in one row
    private static final int BTN_W = 48;
    private static final int BTN_H = 20;
    private static final int BTN_GAP = 5;

    // Per-action semantic colours (RGB24, no alpha)
    private static final int COLOR_FOLD     = 0xAA2222; // red    – negative action
    private static final int COLOR_CHECK    = 0x228844; // green  – neutral/safe
    private static final int COLOR_RAISE    = 0xBB7700; // amber  – aggressive bet
    private static final int COLOR_ALLIN    = 0x7722AA; // purple – maximum risk
    private static final int COLOR_INACTIVE = 0x444444; // grey   – disabled

    public TexasHoldemScreen(TexasHoldemMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth  = 256;
        this.imageHeight = 230;
    }

    // =========================================================================
    // Colour helpers for 3-D border effect
    // =========================================================================

    /** Brighten an RGB24 value by ~60 per channel. */
    private static int lighten(int rgb) {
        int r = Math.min(255, ((rgb >> 16) & 0xFF) + 70);
        int g = Math.min(255, ((rgb >>  8) & 0xFF) + 70);
        int b = Math.min(255, ( rgb        & 0xFF) + 70);
        return (r << 16) | (g << 8) | b;
    }

    /** Darken an RGB24 value by ~50 per channel. */
    private static int darken(int rgb) {
        int r = Math.max(0, ((rgb >> 16) & 0xFF) - 55);
        int g = Math.max(0, ((rgb >>  8) & 0xFF) - 55);
        int b = Math.max(0, ( rgb        & 0xFF) - 55);
        return (r << 16) | (g << 8) | b;
    }

    // =========================================================================
    // renderBg – absolute screen coords.
    // =========================================================================
    @Override
    protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int bgTop    = 40;
        int bgBottom = height - 63;

        g.blit(TEXTURE, 0, bgTop, 0, 0, width, bgBottom - bgTop, width, bgBottom - bgTop);

        // ── Action buttons ──
        boolean myTurn = menu.isCurrentPlayer() && menu.isGameReady()
                && menu.getPhase() != TexasHoldemGame.Phase.SHOWDOWN;
        int myIdx = menu.getGame().getPlayers().indexOf(menu.getCardPlayer());
        boolean isFolded = myIdx >= 0 && menu.isFolded(myIdx);
        boolean isAllIn  = myIdx >= 0 && menu.isAllIn(myIdx);

        if (myTurn && !isFolded && !isAllIn) {
            int callAmount = menu.getCallAmount();
            boolean canCheck = callAmount == 0;
            int myChips = myIdx >= 0 ? menu.getChips(myIdx) : 0;

            int totalBtnsW = 4 * BTN_W + 3 * BTN_GAP;
            int bx = (width - totalBtnsW) / 2;
            int by = bgBottom - BTN_H - 6;

            // Fold – red
            drawBtn(g, mouseX, mouseY, bx, by,
                    Component.translatable("button.charta.texas_holdem.fold"),
                    true, COLOR_FOLD);

            // Check / Call – green
            Component checkCallLabel = canCheck
                    ? Component.translatable("button.charta.texas_holdem.check")
                    : Component.translatable("button.charta.texas_holdem.call")
                    .copy().append(" " + callAmount);
            drawBtn(g, mouseX, mouseY, bx + BTN_W + BTN_GAP, by,
                    checkCallLabel, true, COLOR_CHECK);

            // Raise – amber
            drawBtn(g, mouseX, mouseY, bx + (BTN_W + BTN_GAP) * 2, by,
                    Component.translatable("button.charta.texas_holdem.raise")
                            .copy().append(" +" + menu.getRaiseAmount()),
                    myChips > 0, myChips > 0 ? COLOR_RAISE : COLOR_INACTIVE);

            // All-In – purple
            drawBtn(g, mouseX, mouseY, bx + (BTN_W + BTN_GAP) * 3, by,
                    Component.translatable("button.charta.texas_holdem.allin"),
                    myChips > 0, myChips > 0 ? COLOR_ALLIN : COLOR_INACTIVE);
        }
    }

    /**
     * Draws a single styled action button at absolute screen coordinates.
     *
     * Visual layers (back-to-front):
     *   1. Dark shadow strip on bottom + right edges  → depth
     *   2. Light highlight strip on top + left edges  → raised look
     *   3. Main fill in the button's semantic colour
     *   4. Inner top-row tint (lighter stripe) → subtle gloss
     *   5. Text, centred & white with drop shadow
     *   6. Semi-transparent hover overlay (only when active)
     */
    private void drawBtn(GuiGraphics g, int mx, int my,
                         int ax, int ay, Component label,
                         boolean active, int baseColor) {

        int color    = active ? baseColor : COLOR_INACTIVE;
        int colorAlpha = 0xFF000000 | color;
        int light    = 0xFF000000 | lighten(color);
        int dark     = 0xFF000000 | darken(color);

        // 1 · Outer shadow (bottom-right, 1 px)
        g.fill(ax + 2, ay + BTN_H - 1, ax + BTN_W - 1, ay + BTN_H,   dark);  // bottom
        g.fill(ax + BTN_W - 1, ay + 2, ax + BTN_W,     ay + BTN_H,   dark);  // right

        // 2 · Outer highlight (top-left, 1 px)
        g.fill(ax,     ay,     ax + BTN_W - 1, ay + 1,         light);  // top
        g.fill(ax,     ay + 1, ax + 1,         ay + BTN_H - 1, light);  // left

        // 3 · Main fill
        g.fill(ax + 1, ay + 1, ax + BTN_W - 1, ay + BTN_H - 1, colorAlpha);

        // 4 · Subtle gloss: top 1/3 of the button is slightly lighter
        int glossH = BTN_H / 3;
        g.fill(ax + 1, ay + 1, ax + BTN_W - 1, ay + 1 + glossH, 0x22FFFFFF);

        // 5 · Label – centred, white with shadow
        int tx = ax + BTN_W / 2 - font.width(label) / 2;
        int ty = ay + (BTN_H - 8) / 2;
        // Drop shadow (1 px offset)
        g.drawString(font, label, tx + 1, ty + 1, 0x44000000, false);
        // Main text
        g.drawString(font, label, tx, ty, 0xFFFFFFFF, false);

        // 6 · Hover overlay
        boolean hovered = mx >= ax && mx < ax + BTN_W && my >= ay && my < ay + BTN_H;
        if (hovered && active) {
            g.fill(ax + 1, ay + 1, ax + BTN_W - 1, ay + BTN_H - 1, 0x33FFFFFF);
            // Extra bright edge at top when hovered
            g.fill(ax + 1, ay + 1, ax + BTN_W - 1, ay + 2, 0x44FFFFFF);
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        int cx = width / 2 - leftPos;

        TexasHoldemGame.Phase phase = menu.getPhase();
        String phaseName = switch (phase) {
            case PREFLOP  -> "Pre-Flop";
            case FLOP     -> "Flop";
            case TURN     -> "Turn";
            case RIVER    -> "River";
            case SHOWDOWN -> "Showdown";
        };
        Component phaseComp = Component.literal(phaseName)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        g.drawString(font, phaseComp, cx - font.width(phaseComp) / 2, 4, 0xFFFFFF);

        Component potComp = Component.translatable("message.charta.texas_holdem.pot", menu.getPot());
        g.drawString(font, potComp, cx - font.width(potComp) / 2, 14, 0xFFD700);

        int currentBet = menu.getCurrentBet();
        if (currentBet > 0) {
            Component betComp = Component.literal("Bet: " + currentBet)
                    .withStyle(ChatFormatting.WHITE);
            g.drawString(font, betComp, cx - font.width(betComp) / 2, 24, 0xFFFFFF);
        }

        if (phase == TexasHoldemGame.Phase.SHOWDOWN) {
            Component bannerMsg = null;
            var history = ChartaModClient.LOCAL_HISTORY;
            for (int i = history.size() - 1; i >= 0; i--) {
                var entry = history.get(i);
                if (entry.getLeft().getString().isEmpty()) {
                    bannerMsg = entry.getRight();
                    break;
                }
            }
            if (bannerMsg != null) {
                int bw = font.width(bannerMsg) + 12;
                int bx = cx - bw / 2;
                g.fill(bx - 1, 117, bx + bw + 1, 133, 0xCC000000);
                g.fill(bx, 118, bx + bw, 132, 0x88004400);
                g.drawString(font, bannerMsg, cx - font.width(bannerMsg) / 2, 121, 0xFFD700);
            }
        } else if (menu.isGameReady()) {
            CardPlayer current = menu.getCurrentPlayer();
            Component turnComp = menu.isCurrentPlayer()
                    ? Component.translatable("message.charta.your_turn").withStyle(ChatFormatting.GREEN)
                    : Component.translatable("message.charta.other_turn", current.getName())
                    .withStyle(s -> s.withColor(current.getColor().getTextureDiffuseColor()));
            g.drawString(font, turnComp, cx - font.width(turnComp) / 2, 125, 0xFFFFFF);
        } else {
            Component dealComp = Component.translatable("message.charta.dealing_cards")
                    .withStyle(ChatFormatting.GOLD);
            g.drawString(font, dealComp, cx - font.width(dealComp) / 2, 125, 0xFFFFFF);
        }
    }

    // =========================================================================
    // Mouse click — must mirror the renderBg button positions exactly
    // =========================================================================
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean myTurn = menu.isCurrentPlayer() && menu.isGameReady()
                && menu.getPhase() != TexasHoldemGame.Phase.SHOWDOWN;
        int myIdx = menu.getGame().getPlayers().indexOf(menu.getCardPlayer());
        boolean isFolded = myIdx >= 0 && menu.isFolded(myIdx);
        boolean isAllIn  = myIdx >= 0 && menu.isAllIn(myIdx);

        if (myTurn && !isFolded && !isAllIn) {
            int myChips = myIdx >= 0 ? menu.getChips(myIdx) : 0;

            int totalBtnsW = 4 * BTN_W + 3 * BTN_GAP;
            int bx = (width - totalBtnsW) / 2;
            int by = height - 63 - BTN_H - 6;

            if (hit(mouseX, mouseY, bx, by)) {
                sendAction(TexasHoldemGame.ACTION_FOLD); return true;
            }
            if (hit(mouseX, mouseY, bx + BTN_W + BTN_GAP, by)) {
                sendAction(TexasHoldemGame.ACTION_CALL); return true;
            }
            if (myChips > 0 && hit(mouseX, mouseY, bx + (BTN_W + BTN_GAP) * 2, by)) {
                sendAction(TexasHoldemGame.ACTION_RAISE_MIN); return true;
            }
            if (myChips > 0 && hit(mouseX, mouseY, bx + (BTN_W + BTN_GAP) * 3, by)) {
                sendAction(TexasHoldemGame.ACTION_ALL_IN); return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean hit(double mx, double my, int ax, int ay) {
        return mx >= ax && mx < ax + BTN_W && my >= ay && my < ay + BTN_H;
    }

    private void sendAction(int action) {
        ChartaMod.getPacketManager().sendToServer(new TexasHoldemActionPayload(action));
    }

    // =========================================================================
    // Top bar
    // =========================================================================
    @Override
    public void renderTopBar(@NotNull GuiGraphics g) {
        super.renderTopBar(g);

        List<CardPlayer> players = menu.getGame().getPlayers();
        int n = players.size();
        float playerSlotW = CardSlot.getWidth(CardSlot.Type.PREVIEW) + 28f;
        float playersWidth = n * playerSlotW + (n - 1f) * (playerSlotW / 10f);

        for (int i = 0; i < n; i++) {
            float px = width / 2f - playersWidth / 2f + i * (playerSlotW + playerSlotW / 10f);
            DyeColor color = players.get(i).getColor();
            g.fill(Mth.floor(px), 28, Mth.ceil(px + playerSlotW), 40,
                    0x88000000 + color.getTextureDiffuseColor());
            if (i < n - 1) {
                g.fill(Mth.ceil(px + playerSlotW), 28,
                        Mth.floor(px + playerSlotW + playerSlotW / 10f), 40, 0x88000000);
            }
        }
        g.fill(0, 28, Mth.floor((width - playersWidth) / 2f), 40, 0x88000000);
        g.fill(width - Mth.floor((width - playersWidth) / 2f), 28, width, 40, 0x88000000);

        for (int i = 0; i < n; i++) {
            float px = width / 2f - playersWidth / 2f + i * (playerSlotW + playerSlotW / 10f);
            boolean fd     = menu.isFolded(i);
            boolean ai     = menu.isAllIn(i);
            boolean dealer = i == menu.getDealerIndex();
            int chips      = menu.getChips(i);
            int bet        = menu.getRoundBet(i);

            String chipStr = fd ? "Fold" : ai ? "All-In" : chips + "♦";
            if (dealer)          chipStr = "[D] " + chipStr;
            if (bet > 0 && !fd)  chipStr += "(" + bet + ")";

            int textColor = fd ? 0xAAAAAA : ai ? 0xFFD700 : 0xFFFFFF;

            g.pose().pushPose();
            g.pose().translate(px + 26f, 29f, 0f);
            g.pose().scale(0.5f, 0.5f, 0.5f);
            g.drawString(font, chipStr, 0, 0, textColor, true);
            g.pose().popPose();
        }
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        net.minecraft.core.BlockPos tablePos = menu.getBlockPos();
        if (tablePos != null) {
            List<dev.lucaargolo.charta.common.game.api.CardPlayer> players = menu.getGame().getPlayers();
            int n = players.size();
            int[] chips = new int[n];

            for (int i = 0; i < n; i++) {
                chips[i] = menu.getChips(i);
            }

            dev.lucaargolo.charta.client.ChartaModClient.TABLE_POKER_CHIPS.put(tablePos, chips);

            int gameSlotCount = menu.getGame().getSlots().size();
            dev.lucaargolo.charta.client.ChartaModClient.TABLE_POKER_GAME_SLOT_COUNT.put(tablePos, gameSlotCount);

            int foldedMask = 0;
            int allInMask = 0;
            for (int i = 0; i < n; i++) {
                if (menu.isFolded(i)) foldedMask |= (1 << i);
                if (menu.isAllIn(i)) allInMask |= (1 << i);
            }

            dev.lucaargolo.charta.client.ChartaModClient.TABLE_POKER_FOLDED.put(tablePos, foldedMask);
            dev.lucaargolo.charta.client.ChartaModClient.TABLE_POKER_ALLIN.put(tablePos, allInMask);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void removed() {
        super.removed();

        net.minecraft.core.BlockPos tablePos = menu.getBlockPos();
        if (tablePos != null) {
            dev.lucaargolo.charta.client.ChartaModClient.TABLE_POKER_CHIPS.remove(tablePos);
            dev.lucaargolo.charta.client.ChartaModClient.TABLE_POKER_GAME_SLOT_COUNT.remove(tablePos);
            dev.lucaargolo.charta.client.ChartaModClient.TABLE_POKER_FOLDED.remove(tablePos);
            dev.lucaargolo.charta.client.ChartaModClient.TABLE_POKER_ALLIN.remove(tablePos);
        }
    }

    @Override
    public void renderBottomBar(@NotNull GuiGraphics g) {
        CardPlayer player = menu.getCardPlayer();
        DyeColor color = player.getColor();
        int totalWidth = Mth.floor(CardSlot.getWidth(CardSlot.Type.HORIZONTAL)) + 10;
        g.fill(0, height - 63, (width - totalWidth) / 2, height, 0x88000000);
        g.fill((width - totalWidth) / 2, height - 63,
                (width - totalWidth) / 2 + totalWidth, height,
                0x88000000 + color.getTextureDiffuseColor());
        g.fill((width - totalWidth) / 2 + totalWidth, height - 63, width, height, 0x88000000);
    }
}