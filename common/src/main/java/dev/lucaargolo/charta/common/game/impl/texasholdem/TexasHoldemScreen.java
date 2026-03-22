package dev.lucaargolo.charta.common.game.impl.texasholdem;

import dev.lucaargolo.charta.client.ChartaModClient;
import dev.lucaargolo.charta.client.render.screen.GameScreen;
import dev.lucaargolo.charta.common.ChartaMod;
import dev.lucaargolo.charta.common.game.api.CardPlayer;
import dev.lucaargolo.charta.common.menu.CardSlot;
import dev.lucaargolo.charta.common.network.TexasHoldemActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TexasHoldemScreen extends GameScreen<TexasHoldemGame, TexasHoldemMenu> {

    private static final ResourceLocation TEXTURE = ChartaMod.id("textures/gui/texas_holdem_bg.png");

    // Main action buttons (3): Fold, Check/Call, All-In
    private static final int BTN_W   = 56;
    private static final int BTN_H   = 20;
    private static final int BTN_GAP = 6;

    // Raise button (separate, opens sub-panel)
    private static final int RAISE_BTN_W = 48;

    // Raise sub-panel quick chips
    private static final int QW = 40; // quick-raise button width
    private static final int QH = 18;
    private static final int QG = 4;

    private static final int COLOR_FOLD     = 0xAA2222;
    private static final int COLOR_CHECK    = 0x228844;
    private static final int COLOR_RAISE    = 0xBB7700;
    private static final int COLOR_ALLIN    = 0x7722AA;
    private static final int COLOR_INACTIVE = 0x444444;
    private static final int COLOR_QUICK    = 0x445588;

    /** Whether the raise sub-panel is open. */
    private boolean raiseMenuOpen = false;
    /** Custom raise field value being typed. */
    private String customRaiseText = "";
    /** Whether the custom field is focused. */
    private boolean customFieldFocused = false;

    public TexasHoldemScreen(TexasHoldemMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth  = 256;
        this.imageHeight = 230;
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        BlockPos tablePos = menu.getBlockPos();
        if (tablePos != null) {
            List<CardPlayer> players = menu.getGame().getPlayers();
            int n = players.size();
            int[] chips = new int[n];
            int foldedMask = 0, allInMask = 0;
            for (int i = 0; i < n; i++) {
                chips[i] = menu.getChips(i);
                if (menu.isFolded(i)) foldedMask |= (1 << i);
                if (menu.isAllIn(i))  allInMask  |= (1 << i);
            }
            ChartaModClient.TABLE_POKER_CHIPS.put(tablePos, chips);
            ChartaModClient.TABLE_POKER_GAME_SLOT_COUNT.put(tablePos, menu.getGame().getSlots().size());
            ChartaModClient.TABLE_POKER_FOLDED.put(tablePos, foldedMask);
            ChartaModClient.TABLE_POKER_ALLIN.put(tablePos, allInMask);
            ChartaModClient.TABLE_POKER_STARTING_CHIPS.put(tablePos, menu.getStartingChips());
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    // ── colour helpers ─────────────────────────────────────────────────────────
    private static int lighten(int rgb) {
        int r = Math.min(255, ((rgb >> 16) & 0xFF) + 70);
        int g2= Math.min(255, ((rgb >>  8) & 0xFF) + 70);
        int b = Math.min(255, ( rgb        & 0xFF) + 70);
        return (r << 16) | (g2 << 8) | b;
    }
    private static int darken(int rgb) {
        int r = Math.max(0, ((rgb >> 16) & 0xFF) - 55);
        int g2= Math.max(0, ((rgb >>  8) & 0xFF) - 55);
        int b = Math.max(0, ( rgb        & 0xFF) - 55);
        return (r << 16) | (g2 << 8) | b;
    }

    // ── renderBg ───────────────────────────────────────────────────────────────
    @Override
    protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int bgTop    = 40;
        int bgBottom = height - 63;

        g.blit(TEXTURE, 0, bgTop, 0, 0, width, bgBottom - bgTop, width, bgBottom - bgTop);

        boolean myTurn = menu.isCurrentPlayer() && menu.isGameReady()
                && menu.getPhase() != PokerPhase.SHOWDOWN;
        int myIdx = menu.getGame().getPlayers().indexOf(menu.getCardPlayer());
        boolean isFolded = myIdx >= 0 && menu.isFolded(myIdx);
        boolean isAllIn  = myIdx >= 0 && menu.isAllIn(myIdx);

        if (!myTurn || isFolded || isAllIn) {
            raiseMenuOpen = false;
            return;
        }

        int callAmount = menu.getCallAmount();
        boolean canCheck = callAmount == 0;
        int myChips = myIdx >= 0 ? menu.getChips(myIdx) : 0;
        int minRaise = menu.getRaiseAmount();

        // ── Layout: [Fold] [Check/Call] [Raise ▲] [All-In] ──────────────────
        int totalW = BTN_W + BTN_GAP + BTN_W + BTN_GAP + RAISE_BTN_W + BTN_GAP + BTN_W;
        int bx = (width - totalW) / 2;
        int by = bgBottom - BTN_H - 6;

        int foldX   = bx;
        int callX   = bx + BTN_W + BTN_GAP;
        int raiseX  = callX + BTN_W + BTN_GAP;
        int allInX  = raiseX + RAISE_BTN_W + BTN_GAP;

        drawBtn(g, mouseX, mouseY, foldX, by, BTN_W,
                Component.translatable("button.charta.texas_holdem.fold"), true, COLOR_FOLD);

        drawBtn(g, mouseX, mouseY, callX, by, BTN_W,
                canCheck ? Component.translatable("button.charta.texas_holdem.check")
                        : Component.translatable("button.charta.texas_holdem.call").copy().append(" " + callAmount),
                true, COLOR_CHECK);

        Component raiseLabel = Component.literal(raiseMenuOpen ? "Raise ▼" : "Raise ▲");
        drawBtn(g, mouseX, mouseY, raiseX, by, RAISE_BTN_W,
                raiseLabel, myChips > 0, myChips > 0 ? COLOR_RAISE : COLOR_INACTIVE);

        drawBtn(g, mouseX, mouseY, allInX, by, BTN_W,
                Component.translatable("button.charta.texas_holdem.allin"),
                myChips > 0, myChips > 0 ? COLOR_ALLIN : COLOR_INACTIVE);

        // ── Raise sub-panel ─────────────────────────────────────────────────
        if (raiseMenuOpen && myChips > 0) {
            // Panel sits just above the raise button
            int[] quickAmounts = {10, 25, 50};
            int panelW = 3 * QW + 2 * QG + QG + 80 + QG; // quick buttons + custom field
            int panelH = QH + 8;
            int px = raiseX + RAISE_BTN_W / 2 - panelW / 2;
            int py = by - panelH - 4;

            // Panel background
            g.fill(px - 2, py - 2, px + panelW + 2, py + panelH + 2, 0xCC000000);
            g.fill(px - 1, py - 1, px + panelW + 1, py + panelH + 1, 0x88222222);

            // +10 / +25 / +50 quick buttons
            for (int qi = 0; qi < quickAmounts.length; qi++) {
                int qa = quickAmounts[qi];
                int qx = px + qi * (QW + QG);
                int qy = py + 4;
                boolean canRaise = myChips >= qa;
                drawBtn(g, mouseX, mouseY, qx, qy, QW,
                        Component.literal("+" + qa), canRaise,
                        canRaise ? COLOR_QUICK : COLOR_INACTIVE);
            }

            // Custom input field (80 px wide)
            int fieldX = px + 3 * (QW + QG);
            int fieldY = py + 4;
            int fieldW = 80;
            g.fill(fieldX, fieldY, fieldX + fieldW, fieldY + QH, 0xFF111111);
            g.fill(fieldX + 1, fieldY + 1, fieldX + fieldW - 1, fieldY + QH - 1,
                    customFieldFocused ? 0xFF334455 : 0xFF222222);
            String display = customRaiseText.isEmpty() ? ("min " + minRaise) : customRaiseText;
            int textColor = customRaiseText.isEmpty() ? 0xFF666666 : 0xFFFFFFFF;
            g.pose().pushPose();
            g.pose().translate(fieldX + 3, fieldY + (QH - 7) / 2f, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, display, 0, 0, textColor, false);
            // Cursor blink
            if (customFieldFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cw = font.width(customRaiseText.isEmpty() ? "" : customRaiseText);
                g.fill(cw + 1, 0, cw + 2, 7, 0xFFFFFFFF);
            }
            g.pose().popPose();
            // Confirm button inside field (right side)
            if (!customRaiseText.isEmpty()) {
                int sendX = fieldX + fieldW - 14;
                int sendY = fieldY + (QH - 10) / 2;
                g.fill(sendX, sendY, sendX + 12, sendY + 10, 0xFF228844);
                g.drawString(font, "↵", sendX + 2, sendY + 1, 0xFFFFFFFF, false);
            }
        }
    }

    /** Draw a button with variable width. */
    private void drawBtn(GuiGraphics g, int mx, int my, int ax, int ay, int w,
                         Component label, boolean active, int baseColor) {
        int color      = active ? baseColor : COLOR_INACTIVE;
        int colorAlpha = 0xFF000000 | color;
        int light      = 0xFF000000 | lighten(color);
        int dark       = 0xFF000000 | darken(color);

        g.fill(ax + 2, ay + BTN_H - 1, ax + w - 1, ay + BTN_H,   dark);
        g.fill(ax + w - 1, ay + 2,     ax + w,      ay + BTN_H,   dark);
        g.fill(ax,     ay,     ax + w - 1, ay + 1,         light);
        g.fill(ax,     ay + 1, ax + 1,     ay + BTN_H - 1, light);
        g.fill(ax + 1, ay + 1, ax + w - 1, ay + BTN_H - 1, colorAlpha);
        g.fill(ax + 1, ay + 1, ax + w - 1, ay + 1 + BTN_H / 3, 0x22FFFFFF);

        int tx = ax + w / 2 - font.width(label) / 2;
        int ty = ay + (BTN_H - 8) / 2;
        g.drawString(font, label, tx + 1, ty + 1, 0x44000000, false);
        g.drawString(font, label, tx, ty, 0xFFFFFFFF, false);

        if (mx >= ax && mx < ax + w && my >= ay && my < ay + BTN_H && active) {
            g.fill(ax + 1, ay + 1, ax + w - 1, ay + BTN_H - 1, 0x33FFFFFF);
            g.fill(ax + 1, ay + 1, ax + w - 1, ay + 2, 0x44FFFFFF);
        }
    }

    // ── renderLabels ───────────────────────────────────────────────────────────
    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        int cx = width / 2 - leftPos;

        PokerPhase phase = menu.getPhase();
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
            Component betComp = Component.literal("Bet: " + currentBet).withStyle(ChatFormatting.WHITE);
            g.drawString(font, betComp, cx - font.width(betComp) / 2, 24, 0xFFFFFF);
        }

        if (phase == PokerPhase.SHOWDOWN) {
            Component bannerMsg = null;
            var history = ChartaModClient.LOCAL_HISTORY;
            for (int i = history.size() - 1; i >= 0; i--) {
                var entry = history.get(i);
                if (entry.getLeft().getString().isEmpty()) { bannerMsg = entry.getRight(); break; }
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
        } else if (menu.isPaused()) {
            Component nextComp = Component.literal("Next hand starting...")
                    .withStyle(ChatFormatting.YELLOW);
            g.drawString(font, nextComp, cx - font.width(nextComp) / 2, 125, 0xFFFFFF);
        } else {
            Component dealComp = Component.translatable("message.charta.dealing_cards").withStyle(ChatFormatting.GOLD);
            g.drawString(font, dealComp, cx - font.width(dealComp) / 2, 125, 0xFFFFFF);
        }
    }

    // ── Input handling ─────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean myTurn = menu.isCurrentPlayer() && menu.isGameReady()
                && menu.getPhase() != PokerPhase.SHOWDOWN;
        int myIdx = menu.getGame().getPlayers().indexOf(menu.getCardPlayer());
        boolean isFolded = myIdx >= 0 && menu.isFolded(myIdx);
        boolean isAllIn  = myIdx >= 0 && menu.isAllIn(myIdx);

        if (myTurn && !isFolded && !isAllIn) {
            int myChips  = myIdx >= 0 ? menu.getChips(myIdx) : 0;
            int minRaise = menu.getRaiseAmount();
            int bgBottom = height - 63;

            int totalW  = BTN_W + BTN_GAP + BTN_W + BTN_GAP + RAISE_BTN_W + BTN_GAP + BTN_W;
            int bx      = (width - totalW) / 2;
            int by      = bgBottom - BTN_H - 6;
            int callX   = bx + BTN_W + BTN_GAP;
            int raiseX  = callX + BTN_W + BTN_GAP;
            int allInX  = raiseX + RAISE_BTN_W + BTN_GAP;

            // Fold
            if (hit(mouseX, mouseY, bx, by, BTN_W)) {
                raiseMenuOpen = false; sendAction(TexasHoldemGame.ACTION_FOLD); return true;
            }
            // Call / Check
            if (hit(mouseX, mouseY, callX, by, BTN_W)) {
                raiseMenuOpen = false; sendAction(TexasHoldemGame.ACTION_CALL); return true;
            }
            // Toggle raise panel
            if (myChips > 0 && hit(mouseX, mouseY, raiseX, by, RAISE_BTN_W)) {
                raiseMenuOpen = !raiseMenuOpen;
                customRaiseText = "";
                customFieldFocused = false;
                return true;
            }
            // All-In
            if (myChips > 0 && hit(mouseX, mouseY, allInX, by, BTN_W)) {
                raiseMenuOpen = false; sendAction(TexasHoldemGame.ACTION_ALL_IN); return true;
            }

            // Raise sub-panel interactions
            if (raiseMenuOpen && myChips > 0) {
                int[] quickAmounts = {10, 25, 50};
                int panelW = 3 * QW + 2 * QG + QG + 80 + QG;
                int px = raiseX + RAISE_BTN_W / 2 - panelW / 2;
                int py = by - (QH + 8) - 4;

                // Quick buttons
                for (int qi = 0; qi < quickAmounts.length; qi++) {
                    int qa = quickAmounts[qi];
                    int qx = px + qi * (QW + QG);
                    int qy = py + 4;
                    if (myChips >= qa && hit(mouseX, mouseY, qx, qy, QW)) {
                        int raise = Math.max(minRaise, qa);
                        raiseMenuOpen = false;
                        customRaiseText = "";
                        sendAction(TexasHoldemGame.ACTION_RAISE_CUSTOM + raise);
                        return true;
                    }
                }

                // Custom field click
                int fieldX = px + 3 * (QW + QG);
                int fieldY = py + 4;
                int fieldW = 80;
                if (hit(mouseX, mouseY, fieldX, fieldY, fieldW)) {
                    customFieldFocused = true;
                    // Check confirm button
                    if (!customRaiseText.isEmpty()) {
                        int sendX = fieldX + fieldW - 14;
                        int sendY = fieldY + (QH - 10) / 2;
                        if (mouseX >= sendX && mouseX < sendX + 12 && mouseY >= sendY && mouseY < sendY + 10) {
                            tryConfirmCustomRaise(minRaise, myChips);
                            return true;
                        }
                    }
                    return true;
                } else {
                    customFieldFocused = false;
                }

                // Click outside panel closes it
                if (!hit(mouseX, mouseY, px - 2, py - 2, panelW + 4)) {
                    raiseMenuOpen = false;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (raiseMenuOpen && customFieldFocused) {
            if (keyCode == 257 || keyCode == 335) { // Enter
                int myIdx = menu.getGame().getPlayers().indexOf(menu.getCardPlayer());
                int myChips = myIdx >= 0 ? menu.getChips(myIdx) : 0;
                tryConfirmCustomRaise(menu.getRaiseAmount(), myChips);
                return true;
            }
            if (keyCode == 259 && !customRaiseText.isEmpty()) { // Backspace
                customRaiseText = customRaiseText.substring(0, customRaiseText.length() - 1);
                return true;
            }
            if (keyCode == 256) { // Escape
                raiseMenuOpen = false; customRaiseText = ""; customFieldFocused = false;
                return true;
            }
            return true; // consume all keys while field is focused
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (raiseMenuOpen && customFieldFocused) {
            if (Character.isDigit(ch) && customRaiseText.length() < 6) {
                customRaiseText += ch;
                return true;
            }
            return true;
        }
        return super.charTyped(ch, modifiers);
    }

    private void tryConfirmCustomRaise(int minRaise, int myChips) {
        try {
            int val = Integer.parseInt(customRaiseText);
            int raise = Mth.clamp(Math.max(val, minRaise), minRaise, myChips);
            raiseMenuOpen = false;
            customRaiseText = "";
            customFieldFocused = false;
            sendAction(TexasHoldemGame.ACTION_RAISE_CUSTOM + raise);
        } catch (NumberFormatException ignored) {
            customRaiseText = "";
        }
    }

    private boolean hit(double mx, double my, int ax, int ay, int w) {
        return mx >= ax && mx < ax + w && my >= ay && my < ay + BTN_H;
    }

    private void sendAction(int action) {
        ChartaMod.getPacketManager().sendToServer(new TexasHoldemActionPayload(action));
    }

    // ── Top/Bottom bars ────────────────────────────────────────────────────────
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