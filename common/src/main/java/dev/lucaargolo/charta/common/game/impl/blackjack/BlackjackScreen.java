package dev.lucaargolo.charta.common.game.impl.blackjack;

import dev.lucaargolo.charta.client.render.screen.GameScreen;
import dev.lucaargolo.charta.common.ChartaMod;
import dev.lucaargolo.charta.common.game.api.CardPlayer;
import dev.lucaargolo.charta.common.menu.CardSlot;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BlackjackScreen extends GameScreen<BlackjackGame, BlackjackMenu> {

    private static final int BTN_W   = 52;
    private static final int BTN_H   = 20;
    private static final int BTN_GAP = 6;

    private static final int COLOR_HIT    = 0x228844;
    private static final int COLOR_STAND  = 0xAA2222;
    private static final int COLOR_DOUBLE = 0xBB7700;
    private static final int COLOR_BET    = 0x446688;
    private static final int COLOR_INACTIVE = 0x444444;

    // Betting sub-panel
    private boolean betPanelOpen = false;
    private String  customBetText = "";
    private boolean customBetFocused = false;

    public BlackjackScreen(BlackjackMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth  = 256;
        this.imageHeight = 230;
    }

    // ── Colour helpers ────────────────────────────────────────────────────────
    private static int lighten(int c) {
        int r=Math.min(255,((c>>16)&0xFF)+70), g=Math.min(255,((c>>8)&0xFF)+70), b=Math.min(255,(c&0xFF)+70);
        return (r<<16)|(g<<8)|b;
    }
    private static int darken(int c) {
        int r=Math.max(0,((c>>16)&0xFF)-55), g=Math.max(0,((c>>8)&0xFF)-55), b=Math.max(0,(c&0xFF)-55);
        return (r<<16)|(g<<8)|b;
    }

    // ── renderBg ──────────────────────────────────────────────────────────────
    @Override
    protected void renderBg(@NotNull GuiGraphics g, float pt, int mx, int my) {
        int bgTop    = 40;
        int bgBottom = height - 63;

        // Simple green felt background
        g.fill(0, bgTop, width, bgBottom, 0xFF1B5E20);
        g.fill(2, bgTop+2, width-2, bgBottom-2, 0xFF2E7D32);

        BlackjackGame.Phase phase = menu.getPhase();
        boolean myTurn = menu.isCurrentPlayer() && menu.isGameReady();

        // ── BETTING phase buttons ─────────────────────────────────────────────
        int _myIdx = menu.getGame().getPlayers().indexOf(menu.getCardPlayer());
        boolean _myBetPending = _myIdx >= 0 && menu.getBet(_myIdx) == 0 && menu.getMyChips() > 0;
        if (phase == BlackjackGame.Phase.BETTING && _myBetPending && menu.isGameReady()) {
            int[] quickBets = {10, 25, 50, 100};
            int totalW = quickBets.length * BTN_W + (quickBets.length - 1) * BTN_GAP + BTN_GAP + BTN_W; // + custom
            int bx = (width - totalW) / 2;
            int by = bgBottom - BTN_H - 6;

            for (int qi = 0; qi < quickBets.length; qi++) {
                int qa = quickBets[qi];
                int qx = bx + qi * (BTN_W + BTN_GAP);
                boolean canBet = menu.getMyChips() >= qa;
                drawBtn(g, mx, my, qx, by, BTN_W,
                        Component.literal(qa + "♦"), canBet, canBet ? COLOR_BET : COLOR_INACTIVE);
            }

            // Custom bet field
            int fieldX = bx + quickBets.length * (BTN_W + BTN_GAP);
            int fieldW = BTN_W;
            g.fill(fieldX, by, fieldX + fieldW, by + BTN_H, 0xFF111111);
            g.fill(fieldX+1, by+1, fieldX+fieldW-1, by+BTN_H-1, customBetFocused ? 0xFF334455 : 0xFF222222);
            int minBet = menu.getMyChips() > 0 ? 1 : 0;
            String disp = customBetText.isEmpty() ? "bet..." : customBetText;
            int tc = customBetText.isEmpty() ? 0xFF666666 : 0xFFFFFFFF;
            g.pose().pushPose();
            g.pose().translate(fieldX + 3, by + (BTN_H - 7) / 2f, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, disp, 0, 0, tc, false);
            if (customBetFocused && (System.currentTimeMillis()/500)%2==0) {
                int cw = font.width(customBetText.isEmpty() ? "" : customBetText);
                g.fill(cw+1, 0, cw+2, 7, 0xFFFFFFFF);
            }
            g.pose().popPose();
            if (!customBetText.isEmpty()) {
                int sx = fieldX + fieldW - 14, sy = by + (BTN_H-10)/2;
                g.fill(sx, sy, sx+12, sy+10, 0xFF228844);
                g.drawString(font, "↵", sx+2, sy+1, 0xFFFFFFFF, false);
            }
        }

        // ── PLAYING phase buttons ─────────────────────────────────────────────
        if (phase == BlackjackGame.Phase.PLAYING && myTurn) {
            int myIdx = menu.getGame().getPlayers().indexOf(menu.getCardPlayer());
            boolean stood  = myIdx >= 0 && menu.isStood(myIdx);
            boolean busted = myIdx >= 0 && menu.isBusted(myIdx);

            if (!stood && !busted) {
                boolean canDouble = menu.getMyChips() > 0 && menu.getGame().getPlayerHand(menu.getCardPlayer()).stream().count() == 2;
                int totalW = 3 * BTN_W + 2 * BTN_GAP;
                int bx = (width - totalW) / 2;
                int by = bgBottom - BTN_H - 6;

                drawBtn(g, mx, my, bx, by, BTN_W,
                        Component.translatable("button.charta.blackjack.hit"), true, COLOR_HIT);
                drawBtn(g, mx, my, bx + BTN_W + BTN_GAP, by, BTN_W,
                        Component.translatable("button.charta.blackjack.stand"), true, COLOR_STAND);
                drawBtn(g, mx, my, bx + 2*(BTN_W + BTN_GAP), by, BTN_W,
                        Component.translatable("button.charta.blackjack.double"), canDouble,
                        canDouble ? COLOR_DOUBLE : COLOR_INACTIVE);
            }
        }
    }

    // ── renderLabels ──────────────────────────────────────────────────────────
    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mx, int my) {
        int cx = width / 2 - leftPos;

        // Phase title
        BlackjackGame.Phase phase = menu.getPhase();
        String phaseStr = switch (phase) {
            case BETTING -> "Place Your Bets";
            case PLAYING -> "Playing";
            case DEALER  -> "Dealer's Turn";
            case RESULT  -> "Results";
        };
        Component phaseComp = Component.literal(phaseStr).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        g.drawString(font, phaseComp, cx - font.width(phaseComp)/2, 4, 0xFFFFFF);

        // Dealer value
        if (phase != BlackjackGame.Phase.BETTING) {
            int dv = menu.getDealerValue();
            Component dealerComp = Component.literal("Dealer: " + dv)
                    .withStyle(dv > 21 ? ChatFormatting.RED : dv >= 17 ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
            g.drawString(font, dealerComp, cx - font.width(dealerComp)/2, 14, 0xFFFFFF);
        }

        // Player hand value
        if (phase == BlackjackGame.Phase.PLAYING || phase == BlackjackGame.Phase.RESULT) {
            int hv = menu.getHandValue();
            Component handComp = Component.literal("Your hand: " + hv)
                    .withStyle(hv > 21 ? ChatFormatting.RED : hv == 21 ? ChatFormatting.GOLD : ChatFormatting.WHITE);
            g.drawString(font, handComp, cx - font.width(handComp)/2, 24, 0xFFFFFF);
        }

        // Whose turn
        if (menu.isGameReady() && phase == BlackjackGame.Phase.PLAYING) {
            // Only show turn indicator during PLAYING phase when currentPlayer is set
            try {
                CardPlayer cur = menu.getCurrentPlayer();
                Component turnComp = menu.isCurrentPlayer()
                        ? Component.translatable("message.charta.your_turn").withStyle(ChatFormatting.GREEN)
                        : Component.translatable("message.charta.other_turn", cur.getName())
                        .withStyle(s -> s.withColor(cur.getColor().getTextureDiffuseColor()));
                g.drawString(font, turnComp, cx - font.width(turnComp)/2, 125, 0xFFFFFF);
            } catch (Exception ignored) {}
        } else if (phase == BlackjackGame.Phase.BETTING) {
            Component bettingComp = Component.literal("Place your bets!").withStyle(ChatFormatting.YELLOW);
            g.drawString(font, bettingComp, cx - font.width(bettingComp)/2, 125, 0xFFFFFF);
        }

        // Chips and bet display per player
        List<CardPlayer> players = menu.getGame().getPlayers();
        int n = players.size();
        float slotW = CardSlot.getWidth(CardSlot.Type.PREVIEW) + 28f;
        float totalW = n * slotW + (n-1f)*(slotW/10f);
        for (int i = 0; i < n; i++) {
            float px = width/2f - totalW/2f + i*(slotW + slotW/10f);
            int chips = menu.getChips(i);
            int bet   = menu.getBet(i);
            boolean busted = menu.isBusted(i);
            boolean stood  = menu.isStood(i);
            String info = chips + "♦";
            if (bet > 0) info += " (bet:" + bet + ")";
            if (busted)  info = "BUST";
            if (stood && !busted) info += " ✓";
            g.pose().pushPose();
            g.pose().translate(px + 26f - leftPos, 29f - topPos, 0f);
            g.pose().scale(0.5f, 0.5f, 0.5f);
            g.drawString(font, info, 0, 0, busted ? 0xFF6666 : stood ? 0xAAFFAA : 0xFFFFFF, true);
            g.pose().popPose();
        }
    }

    // ── Input handling ────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        BlackjackGame.Phase phase = menu.getPhase();
        boolean myTurn = menu.isCurrentPlayer() && menu.isGameReady();
        int bgBottom = height - 63;

        int _myIdx = menu.getGame().getPlayers().indexOf(menu.getCardPlayer());
        boolean _myBetPending = _myIdx >= 0 && menu.getBet(_myIdx) == 0 && menu.getMyChips() > 0;
        if (phase == BlackjackGame.Phase.BETTING && _myBetPending && menu.isGameReady()) {
            int[] quickBets = {10, 25, 50, 100};
            int totalW = quickBets.length * BTN_W + (quickBets.length-1)*BTN_GAP + BTN_GAP + BTN_W;
            int bx = (width - totalW) / 2;
            int by = bgBottom - BTN_H - 6;

            for (int qi = 0; qi < quickBets.length; qi++) {
                int qa = quickBets[qi];
                int qx = bx + qi*(BTN_W + BTN_GAP);
                if (menu.getMyChips() >= qa && hit(mx, my, qx, by, BTN_W)) {
                    sendBet(qa); return true;
                }
            }
            // Custom field
            int fieldX = bx + quickBets.length*(BTN_W + BTN_GAP);
            if (hit(mx, my, fieldX, by, BTN_W)) {
                customBetFocused = true;
                if (!customBetText.isEmpty()) {
                    int sx = fieldX + BTN_W - 14, sy = by + (BTN_H-10)/2;
                    if (mx >= sx && mx < sx+12 && my >= sy && my < sy+10) {
                        tryConfirmBet(); return true;
                    }
                }
                return true;
            } else {
                customBetFocused = false;
            }
        }

        if (phase == BlackjackGame.Phase.PLAYING && myTurn) {
            int myIdx = menu.getGame().getPlayers().indexOf(menu.getCardPlayer());
            if (myIdx >= 0 && !menu.isStood(myIdx) && !menu.isBusted(myIdx)) {
                boolean canDouble = menu.getMyChips() > 0 && menu.getGame().getPlayerHand(menu.getCardPlayer()).stream().count() == 2;
                int totalW = 3*BTN_W + 2*BTN_GAP;
                int bx = (width - totalW)/2;
                int by = bgBottom - BTN_H - 6;
                if (hit(mx, my, bx, by, BTN_W)) { sendAction(BlackjackGame.ACTION_HIT);   return true; }
                if (hit(mx, my, bx+BTN_W+BTN_GAP, by, BTN_W)) { sendAction(BlackjackGame.ACTION_STAND);  return true; }
                if (canDouble && hit(mx, my, bx+2*(BTN_W+BTN_GAP), by, BTN_W)) { sendAction(BlackjackGame.ACTION_DOUBLE); return true; }
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (customBetFocused) {
            if (keyCode == 257 || keyCode == 335) { tryConfirmBet(); return true; }
            if (keyCode == 259 && !customBetText.isEmpty()) { customBetText = customBetText.substring(0, customBetText.length()-1); return true; }
            if (keyCode == 256) { customBetFocused = false; customBetText = ""; return true; }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (customBetFocused && Character.isDigit(ch) && customBetText.length() < 6) {
            customBetText += ch; return true;
        }
        return super.charTyped(ch, modifiers);
    }

    private void tryConfirmBet() {
        try {
            int val = Integer.parseInt(customBetText);
            int clamped = Mth.clamp(val, 1, menu.getMyChips());
            sendBet(clamped);
            customBetText = ""; customBetFocused = false;
        } catch (NumberFormatException e) { customBetText = ""; }
    }

    private void sendBet(int amount) {
        ChartaMod.getPacketManager().sendToServer(
                new dev.lucaargolo.charta.common.network.BlackjackActionPayload(BlackjackGame.ACTION_BET + amount));
    }

    private void sendAction(int action) {
        ChartaMod.getPacketManager().sendToServer(
                new dev.lucaargolo.charta.common.network.BlackjackActionPayload(action));
    }

    private void drawBtn(GuiGraphics g, int mx, int my, int ax, int ay, int w,
                         Component label, boolean active, int base) {
        int col  = active ? base : COLOR_INACTIVE;
        int ca   = 0xFF000000 | col;
        int li   = 0xFF000000 | lighten(col);
        int dk   = 0xFF000000 | darken(col);
        g.fill(ax+2, ay+BTN_H-1, ax+w-1, ay+BTN_H,   dk);
        g.fill(ax+w-1, ay+2,     ax+w,   ay+BTN_H,   dk);
        g.fill(ax,   ay,   ax+w-1, ay+1,         li);
        g.fill(ax,   ay+1, ax+1,   ay+BTN_H-1,   li);
        g.fill(ax+1, ay+1, ax+w-1, ay+BTN_H-1,   ca);
        g.fill(ax+1, ay+1, ax+w-1, ay+1+BTN_H/3, 0x22FFFFFF);
        int tx = ax + w/2 - font.width(label)/2;
        int ty = ay + (BTN_H-8)/2;
        g.drawString(font, label, tx+1, ty+1, 0x44000000, false);
        g.drawString(font, label, tx, ty, 0xFFFFFFFF, false);
        if (active && mx>=ax && mx<ax+w && my>=ay && my<ay+BTN_H) {
            g.fill(ax+1, ay+1, ax+w-1, ay+BTN_H-1, 0x33FFFFFF);
        }
    }

    private boolean hit(double mx, double my, int ax, int ay, int w) {
        return mx>=ax && mx<ax+w && my>=ay && my<ay+BTN_H;
    }

    // ── Top/Bottom bars ───────────────────────────────────────────────────────
    @Override
    public void renderTopBar(@NotNull GuiGraphics g) {
        super.renderTopBar(g);
        List<CardPlayer> players = menu.getGame().getPlayers();
        int n = players.size();
        float slotW = CardSlot.getWidth(CardSlot.Type.PREVIEW) + 28f;
        float total = n * slotW + (n-1f)*(slotW/10f);
        for (int i = 0; i < n; i++) {
            float px = width/2f - total/2f + i*(slotW + slotW/10f);
            DyeColor col = players.get(i).getColor();
            g.fill(Mth.floor(px), 28, Mth.ceil(px+slotW), 40, 0x88000000 + col.getTextureDiffuseColor());
        }
        g.fill(0, 28, Mth.floor((width-total)/2f), 40, 0x88000000);
        g.fill(width - Mth.floor((width-total)/2f), 28, width, 40, 0x88000000);
    }

    @Override
    public void renderBottomBar(@NotNull GuiGraphics g) {
        DyeColor col = menu.getCardPlayer().getColor();
        int tw = Mth.floor(CardSlot.getWidth(CardSlot.Type.HORIZONTAL)) + 10;
        g.fill(0, height-63, (width-tw)/2, height, 0x88000000);
        g.fill((width-tw)/2, height-63, (width-tw)/2+tw, height, 0x88000000 + col.getTextureDiffuseColor());
        g.fill((width-tw)/2+tw, height-63, width, height, 0x88000000);
    }
}