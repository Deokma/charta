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

    private static final int BTN_W = 52;
    private static final int BTN_H = 20;
    private static final int BTN_GAP = 6;

    private static final int COLOR_HIT = 0x228844;
    private static final int COLOR_STAND = 0xAA2222;
    private static final int COLOR_DOUBLE = 0xBB7700;
    private static final int COLOR_BET = 0x446688;
    private static final int COLOR_INACTIVE = 0x444444;

    // Betting sub-panel
    private boolean betPanelOpen = false;
    private String customBetText = "";
    private boolean customBetFocused = false;

    public BlackjackScreen(BlackjackMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 256;
        this.imageHeight = 230;
    }

    // ── Colour helpers ────────────────────────────────────────────────────────
    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + 70), g = Math.min(255, ((c >> 8) & 0xFF) + 70), b = Math.min(255, (c & 0xFF) + 70);
        return (r << 16) | (g << 8) | b;
    }

    private static int darken(int c) {
        int r = Math.max(0, ((c >> 16) & 0xFF) - 55), g = Math.max(0, ((c >> 8) & 0xFF) - 55), b = Math.max(0, (c & 0xFF) - 55);
        return (r << 16) | (g << 8) | b;
    }

    // ── renderBg ──────────────────────────────────────────────────────────────
    @Override
    protected void renderBg(@NotNull GuiGraphics g, float pt, int mx, int my) {
        int bgTop = 40;
        int bgBottom = height - 63;

        // Simple green felt background
        g.fill(0, bgTop, width, bgBottom, 0xFF1B5E20);
        g.fill(2, bgTop + 2, width - 2, bgBottom - 2, 0xFF2E7D32);

        // ── Recentre dealer card slots dynamically ────────────────────────────
        // Count how many dealer slots have cards (for visual centering context)
        int filledDealer = 0;
        for (int di = 0; di < BlackjackGame.MAX_DEALER_CARDS; di++) {
            if (!menu.getGame().dealerSlots[di].isEmpty()) filledDealer++;
        }

        // ── Hide ALL dealer slot borders (paint felt over the 38×53 WIDGETS blit) ──
        for (int di = 0; di < BlackjackGame.MAX_DEALER_CARDS; di++) {
            var slot = menu.cardSlots.get(menu.dealerSlotStart + di);
            int sx = leftPos + (int) slot.x;
            int sy = topPos + (int) slot.y;
            // GameScreen blits WIDGETS texture at (sx-0, sy-0) size 38×53 for DEFAULT slots
            g.fill(sx - 1, sy - 1, sx + 39, sy + 54, 0xFF2E7D32);
        }

        BlackjackGame.Phase phase = menu.getPhase();
        boolean myTurn = menu.isCurrentPlayer() && menu.isGameReady();


        // ── Per-player info + card area panels ───────────────────────────────
        // ── Player info strip — full table width, below dealer cards ────────────
        if (phase != BlackjackGame.Phase.BETTING) {
            List<CardPlayer> plrs = menu.getGame().getPlayers();
            int n = plrs.size();

            // Strip between "It's Your Turn" label (topPos+125) and bottom buttons (bgBottom - BTN_H - 6)
            // Place it just above the buttons area, with room for 2 lines of text
            int stripY = bgBottom - BTN_H - 6 - 26;

            // Divide full screen width into n equal columns
            int colW = width / n;

            for (int i = 0; i < n; i++) {
                if (menu.isPlayerLeft(i)) continue; // already left — don't show
                int chips = menu.getChips(i);
                int bet = menu.getBet(i);
                boolean busted = menu.isBusted(i);
                boolean stood = menu.isStood(i);

                // Hand value with ace detection
                var hand = menu.getGame().getCensoredHand(menu.getCardPlayer(), plrs.get(i));
                int rawSum = 0, aceCount = 0;
                for (var c : hand.stream().toList()) {
                    if (c.rank() == dev.lucaargolo.charta.common.game.Ranks.ACE) {
                        rawSum += 11;
                        aceCount++;
                    } else {
                        int ord = c.rank().ordinal();
                        rawSum += (ord >= 2 && ord <= 10) ? ord : 10;
                    }
                }
                int hv = rawSum;
                int acesReduced = 0;
                while (hv > 21 && aceCount > 0) {
                    hv -= 10;
                    aceCount--;
                    acesReduced++;
                }

                String nameStr = plrs.get(i).getName().getString();
                if (nameStr.length() > 8) nameStr = nameStr.substring(0, 7) + ".";

                // Line 1: name + score
                String hvStr = busted ? hv + " BUST" : hv == 21 ? hv + " ★" : acesReduced > 0 ? hv + " (A=1)" : String.valueOf(hv);
                int line1Color = busted ? 0xFF5555 : hv == 21 ? 0xFFD700 : 0xFFFFFF;
                String line1 = nameStr + "  " + hvStr;

                // Line 2: chips + bet
                String line2 = chips + "♦";
                int line2Color = 0xAAAAAA;
                if (busted) {
                    line2 = chips + "♦  lost " + bet + "♦";
                    line2Color = 0xFF7777;
                } else if (stood) {
                    line2 = chips + "♦  bet " + bet + "♦  Stand";
                    line2Color = 0xAAFFAA;
                } else if (bet > 0) {
                    line2 = chips + "♦  bet " + bet + "♦";
                    line2Color = 0xDDDDDD;
                }

                // Centre text within column
                int cx = i * colW + colW / 2;
                int w1 = font.width(line1), w2 = font.width(line2);
                g.drawString(font, line1, cx - w1 / 2, stripY, line1Color, true);
                g.drawString(font, line2, cx - w2 / 2, stripY + 10, line2Color, true);
            }
        }

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
            g.fill(fieldX + 1, by + 1, fieldX + fieldW - 1, by + BTN_H - 1, customBetFocused ? 0xFF334455 : 0xFF222222);
            int minBet = menu.getMyChips() > 0 ? 1 : 0;
            String disp = customBetText.isEmpty() ? "bet..." : customBetText;
            int tc = customBetText.isEmpty() ? 0xFF666666 : 0xFFFFFFFF;
            g.pose().pushPose();
            g.pose().translate(fieldX + 3, by + (BTN_H - 7) / 2f, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, disp, 0, 0, tc, false);
            if (customBetFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cw = font.width(customBetText.isEmpty() ? "" : customBetText);
                g.fill(cw + 1, 0, cw + 2, 7, 0xFFFFFFFF);
            }
            g.pose().popPose();
            if (!customBetText.isEmpty()) {
                int sx = fieldX + fieldW - 14, sy = by + (BTN_H - 10) / 2;
                g.fill(sx, sy, sx + 12, sy + 10, 0xFF228844);
                g.drawString(font, "↵", sx + 2, sy + 1, 0xFFFFFFFF, false);
            }
        }

        // ── PLAYING phase buttons ─────────────────────────────────────────────
        if (phase == BlackjackGame.Phase.PLAYING && myTurn) {
            int myIdx = menu.getGame().getPlayers().indexOf(menu.getCardPlayer());
            boolean stood = myIdx >= 0 && menu.isStood(myIdx);
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
                drawBtn(g, mx, my, bx + 2 * (BTN_W + BTN_GAP), by, BTN_W,
                        Component.translatable("button.charta.blackjack.double"), canDouble,
                        canDouble ? COLOR_DOUBLE : COLOR_INACTIVE);
            }
        }
    }

    // ── renderLabels ──────────────────────────────────────────────────────────
    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mx, int my) {
        int cx = width / 2 - leftPos;
        BlackjackGame.Phase phase = menu.getPhase();

        // ── Phase title ───────────────────────────────────────────────────────
        String phaseStr = switch (phase) {
            case BETTING -> "♠ Place Your Bets ♠";
            case PLAYING -> "♠ Your Turn ♠";
            case DEALER -> "♠ Dealer's Turn ♠";
            case RESULT -> "♠ Results ♠";
        };
        Component phaseComp = Component.literal(phaseStr).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        g.drawString(font, phaseComp, cx - font.width(phaseComp) / 2, 4, 0xFFFFFF);

        // ── Dealer info ───────────────────────────────────────────────────────
        if (phase != BlackjackGame.Phase.BETTING) {
            String dealerStr;
            int dealerColor;
            if (menu.dealerHasHoleCard()) {
                // PLAYING: one card still hidden
                int vis = menu.getDealerVisibleValue();
                dealerStr = "Dealer: " + vis + " + ?";
                dealerColor = 0xFFFFFF;
            } else {
                // DEALER / RESULT: all cards revealed
                int dv = menu.getDealerValue();
                if (dv > 21) {
                    dealerStr = "Dealer: " + dv + " — BUST!";
                    dealerColor = 0xFF4444;
                } else if (dv == 21) {
                    dealerStr = "Dealer: " + dv + " ★";
                    dealerColor = 0xFFD700;
                } else {
                    dealerStr = "Dealer: " + dv;
                    dealerColor = dv >= 17 ? 0xFFFF55 : 0xFFFFFF;
                }
            }
            g.fill(cx - font.width(dealerStr) / 2 - 2, 13, cx + font.width(dealerStr) / 2 + 2, 24, 0x66000000);
            g.drawString(font, dealerStr, cx - font.width(dealerStr) / 2, 14, dealerColor);
        }

        // ── Result banner (RESULT phase) ─────────────────────────────────────
        if (phase == BlackjackGame.Phase.RESULT) {
            int myIdx = menu.getGame().getPlayers().indexOf(menu.getCardPlayer());
            int hv = menu.getHandValue();
            int dv = menu.getDealerValue();
            boolean bust = myIdx >= 0 && menu.isBusted(myIdx);
            int bet = myIdx >= 0 ? menu.getBet(myIdx) : 0;
            int chipsNow = myIdx >= 0 ? menu.getChips(myIdx) : 0;
            boolean bj = hv == 21 && menu.getGame().getPlayerHand(menu.getCardPlayer()).stream().count() == 2;
            String resultStr;
            int resultColor;
            if (bust) {
                resultStr = "You bust (" + hv + ") — Dealer wins";
                resultColor = 0xFF4444;
            } else if (dv > 21) {
                int win = bj ? (int) (bet * 1.5) : bet;
                resultStr = "Dealer busts! You win +" + win + "♦";
                resultColor = 0x55FF55;
            } else if (hv > dv) {
                int win = bj ? (int) (bet * 1.5) : bet;
                resultStr = (bj ? "BLACKJACK! " : "You win! ") + "+" + win + "♦";
                resultColor = 0x55FF55;
            } else if (hv == dv) {
                resultStr = "Push — bet returned (" + chipsNow + "♦)";
                resultColor = 0xFFFF55;
            } else {
                resultStr = "Dealer wins  " + dv + " > " + hv;
                resultColor = 0xFF4444;
            }
            int rw = font.width(resultStr);
            g.fill(cx - rw / 2 - 4, 33, cx + rw / 2 + 4, 47, 0xCC000000);
            g.drawString(font, resultStr, cx - rw / 2, 35, resultColor, true);
        }

        // ── My hand value ─────────────────────────────────────────────────────
        if (phase != BlackjackGame.Phase.BETTING) {
            int myIdx = menu.getGame().getPlayers().indexOf(menu.getCardPlayer());
            int hv = menu.getHandValue();
            boolean isBust = myIdx >= 0 && menu.isBusted(myIdx);
            boolean isStood = myIdx >= 0 && menu.isStood(myIdx);
            String handStr = "Your hand: " + hv;
            if (hv == 21 && !isBust) handStr += " ★ 21!";
            if (isBust) handStr = "Your hand: " + hv + " — BUST";
            if (isStood && !isBust) handStr += " (stood)";
            int handColor = isBust ? 0xFF4444 : hv == 21 ? 0xFFD700 : 0xFFFFFF;
            g.drawString(font, handStr, cx - font.width(handStr) / 2, 24, handColor);
        }

        // ── Turn / status message ─────────────────────────────────────────────
        int statusY = 125;
        if (phase == BlackjackGame.Phase.PLAYING && menu.isGameReady()) {
            try {
                CardPlayer cur = menu.getCurrentPlayer();
                Component turnComp = menu.isCurrentPlayer()
                        ? Component.translatable("message.charta.your_turn").withStyle(ChatFormatting.GREEN)
                        : Component.translatable("message.charta.other_turn", cur.getName())
                        .withStyle(s -> s.withColor(cur.getColor().getTextureDiffuseColor()));
                g.drawString(font, turnComp, cx - font.width(turnComp) / 2, statusY, 0xFFFFFF);
            } catch (Exception ignored) {
            }
        } else if (phase == BlackjackGame.Phase.BETTING) {
            int myIdx = menu.getGame().getPlayers().indexOf(menu.getCardPlayer());
            int myBet = menu.getBet(myIdx);
            if (myBet > 0) {
                Component betComp = Component.literal("Bet placed: " + myBet + "♦  Waiting for others...").withStyle(ChatFormatting.YELLOW);
                g.drawString(font, betComp, cx - font.width(betComp) / 2, statusY, 0xFFFFFF);
            } else {
                Component bettingComp = Component.literal("Choose your bet!").withStyle(ChatFormatting.YELLOW);
                g.drawString(font, bettingComp, cx - font.width(bettingComp) / 2, statusY, 0xFFFFFF);
            }
        } else if (phase == BlackjackGame.Phase.DEALER) {
            Component dealerComp = Component.literal("Dealer is playing...").withStyle(ChatFormatting.GRAY);
            g.drawString(font, dealerComp, cx - font.width(dealerComp) / 2, statusY, 0xFFFFFF);
        }

        // ── Player info under avatars (topbar) ────────────────────────────────
        List<CardPlayer> players = menu.getGame().getPlayers();
        int n = players.size();
        float slotW = CardSlot.getWidth(CardSlot.Type.PREVIEW) + 28f;
        float totalW = n * slotW + (n - 1f) * (slotW / 10f);
        for (int i = 0; i < n; i++) {
            float px = width / 2f - totalW / 2f + i * (slotW + slotW / 10f);
            int chips = menu.getChips(i);
            int bet = menu.getBet(i);
            boolean busted = menu.isBusted(i);
            boolean stood = menu.isStood(i);

            // Line 1: chips
            String chipStr = chips + "♦";
            int chipColor = chips == 0 ? 0x888888 : 0xFFFFFF;

            // Line 2: bet + status
            String statusStr = "";
            int statusColor = 0xFFFFFF;
            if (bet > 0 && phase != BlackjackGame.Phase.BETTING) {
                statusStr = "Bet: " + bet;
            } else if (bet > 0) {
                statusStr = "✓ " + bet + "♦";
                statusColor = 0xAAFFAA;
            }
            if (busted) {
                statusStr = "BUST!";
                statusColor = 0xFF4444;
            } else if (stood) {
                statusStr = "Stand ✓";
                statusColor = 0xAAFFAA;
            }

            g.pose().pushPose();
            g.pose().translate(px + 26f - leftPos, 29f - topPos, 0f);
            g.pose().scale(0.5f, 0.5f, 0.5f);
            g.drawString(font, chipStr, 0, 0, chipColor, true);
            if (!statusStr.isEmpty()) g.drawString(font, statusStr, 0, 10, statusColor, true);
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
            int totalW = quickBets.length * BTN_W + (quickBets.length - 1) * BTN_GAP + BTN_GAP + BTN_W;
            int bx = (width - totalW) / 2;
            int by = bgBottom - BTN_H - 6;

            for (int qi = 0; qi < quickBets.length; qi++) {
                int qa = quickBets[qi];
                int qx = bx + qi * (BTN_W + BTN_GAP);
                if (menu.getMyChips() >= qa && hit(mx, my, qx, by, BTN_W)) {
                    sendBet(qa);
                    return true;
                }
            }
            // Custom field
            int fieldX = bx + quickBets.length * (BTN_W + BTN_GAP);
            if (hit(mx, my, fieldX, by, BTN_W)) {
                customBetFocused = true;
                if (!customBetText.isEmpty()) {
                    int sx = fieldX + BTN_W - 14, sy = by + (BTN_H - 10) / 2;
                    if (mx >= sx && mx < sx + 12 && my >= sy && my < sy + 10) {
                        tryConfirmBet();
                        return true;
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
                int totalW = 3 * BTN_W + 2 * BTN_GAP;
                int bx = (width - totalW) / 2;
                int by = bgBottom - BTN_H - 6;
                if (hit(mx, my, bx, by, BTN_W)) {
                    sendAction(BlackjackGame.ACTION_HIT);
                    return true;
                }
                if (hit(mx, my, bx + BTN_W + BTN_GAP, by, BTN_W)) {
                    sendAction(BlackjackGame.ACTION_STAND);
                    return true;
                }
                if (canDouble && hit(mx, my, bx + 2 * (BTN_W + BTN_GAP), by, BTN_W)) {
                    sendAction(BlackjackGame.ACTION_DOUBLE);
                    return true;
                }
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (customBetFocused) {
            if (keyCode == 257 || keyCode == 335) {
                tryConfirmBet();
                return true;
            }
            if (keyCode == 259 && !customBetText.isEmpty()) {
                customBetText = customBetText.substring(0, customBetText.length() - 1);
                return true;
            }
            if (keyCode == 256) {
                customBetFocused = false;
                customBetText = "";
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (customBetFocused && Character.isDigit(ch) && customBetText.length() < 6) {
            customBetText += ch;
            return true;
        }
        return super.charTyped(ch, modifiers);
    }

    private void tryConfirmBet() {
        try {
            int val = Integer.parseInt(customBetText);
            int clamped = Mth.clamp(val, 1, menu.getMyChips());
            sendBet(clamped);
            customBetText = "";
            customBetFocused = false;
        } catch (NumberFormatException e) {
            customBetText = "";
        }
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
        int col = active ? base : COLOR_INACTIVE;
        int ca = 0xFF000000 | col;
        int li = 0xFF000000 | lighten(col);
        int dk = 0xFF000000 | darken(col);
        g.fill(ax + 2, ay + BTN_H - 1, ax + w - 1, ay + BTN_H, dk);
        g.fill(ax + w - 1, ay + 2, ax + w, ay + BTN_H, dk);
        g.fill(ax, ay, ax + w - 1, ay + 1, li);
        g.fill(ax, ay + 1, ax + 1, ay + BTN_H - 1, li);
        g.fill(ax + 1, ay + 1, ax + w - 1, ay + BTN_H - 1, ca);
        g.fill(ax + 1, ay + 1, ax + w - 1, ay + 1 + BTN_H / 3, 0x22FFFFFF);
        int tx = ax + w / 2 - font.width(label) / 2;
        int ty = ay + (BTN_H - 8) / 2;
        g.drawString(font, label, tx + 1, ty + 1, 0x44000000, false);
        g.drawString(font, label, tx, ty, 0xFFFFFFFF, false);
        if (active && mx >= ax && mx < ax + w && my >= ay && my < ay + BTN_H) {
            g.fill(ax + 1, ay + 1, ax + w - 1, ay + BTN_H - 1, 0x33FFFFFF);
        }
    }

    private boolean hit(double mx, double my, int ax, int ay, int w) {
        return mx >= ax && mx < ax + w && my >= ay && my < ay + BTN_H;
    }

    // ── Top/Bottom bars ───────────────────────────────────────────────────────
    @Override
    public void renderTopBar(@NotNull GuiGraphics g) {
        super.renderTopBar(g);
        List<CardPlayer> players = menu.getGame().getPlayers();
        int n = players.size();
        float slotW = CardSlot.getWidth(CardSlot.Type.PREVIEW) + 28f;
        float total = n * slotW + (n - 1f) * (slotW / 10f);
        for (int i = 0; i < n; i++) {
            float px = width / 2f - total / 2f + i * (slotW + slotW / 10f);
            DyeColor col = players.get(i).getColor();
            g.fill(Mth.floor(px), 28, Mth.ceil(px + slotW), 40, 0x88000000 + col.getTextureDiffuseColor());
        }
        g.fill(0, 28, Mth.floor((width - total) / 2f), 40, 0x88000000);
        g.fill(width - Mth.floor((width - total) / 2f), 28, width, 40, 0x88000000);
    }

    @Override
    public void renderBottomBar(@NotNull GuiGraphics g) {
        DyeColor col = menu.getCardPlayer().getColor();
        int tw = Mth.floor(CardSlot.getWidth(CardSlot.Type.HORIZONTAL)) + 10;
        g.fill(0, height - 63, (width - tw) / 2, height, 0x88000000);
        g.fill((width - tw) / 2, height - 63, (width - tw) / 2 + tw, height, 0x88000000 + col.getTextureDiffuseColor());
        g.fill((width - tw) / 2 + tw, height - 63, width, height, 0x88000000);
    }
}