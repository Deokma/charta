package dev.lucaargolo.charta.common.game.impl.texasholdem;

import com.mojang.blaze3d.systems.RenderSystem;
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

    // Gameplay background (NOT the menu icon — that lives at textures/gui/game/texas_holdem.png)
    private static final ResourceLocation TEXTURE = ChartaMod.id("textures/gui/texas_holdem_bg.png");

    // Button layout – 4 buttons in one row, each 44px wide, 18px tall
    private static final int BTN_W = 44;
    private static final int BTN_H = 18;
    private static final int BTN_GAP = 4;

    private static final int COLOR_ACTIVE   = 0x2d99ff;
    private static final int COLOR_INACTIVE = 0x666666;

    public TexasHoldemScreen(TexasHoldemMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth  = 256;
        this.imageHeight = 200;
    }

    // =========================================================================
    // renderBg – absolute screen coords (NOT pre-translated by leftPos/topPos).
    // Draw the background texture AND the action buttons here (like Solitaire).
    // =========================================================================
    @Override
    protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // Background texture
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // ── Action buttons (absolute screen coords) ──
        // Only visible when it's the local player's betting turn
        boolean myTurn = menu.isCurrentPlayer() && menu.isGameReady()
                && menu.getPhase() != TexasHoldemGame.Phase.SHOWDOWN;
        int myIdx = menu.getGame().getPlayers().indexOf(menu.getCardPlayer());
        boolean isFolded = myIdx >= 0 && menu.isFolded(myIdx);
        boolean isAllIn  = myIdx >= 0 && menu.isAllIn(myIdx);

        if (myTurn && !isFolded && !isAllIn) {
            int callAmount = menu.getCallAmount();
            boolean canCheck = callAmount == 0;
            int myChips = myIdx >= 0 ? menu.getChips(myIdx) : 0;

            // Centre 4 buttons at the bottom of the image
            int totalBtnsW = 4 * BTN_W + 3 * BTN_GAP;   // 188
            int bx = leftPos + (imageWidth - totalBtnsW) / 2; // leftPos + 6
            int by = topPos  + imageHeight - BTN_H - 5;       // topPos + 207

            drawBtn(g, mouseX, mouseY, bx,                           by,
                    Component.translatable("button.charta.texas_holdem.fold"),  true);
            drawBtn(g, mouseX, mouseY, bx + BTN_W + BTN_GAP,         by,
                    canCheck ? Component.translatable("button.charta.texas_holdem.check")
                            : Component.translatable("button.charta.texas_holdem.call")
                            .copy().append(" " + callAmount),         true);
            drawBtn(g, mouseX, mouseY, bx + (BTN_W + BTN_GAP) * 2,  by,
                    Component.translatable("button.charta.texas_holdem.raise"), myChips > 0);
            drawBtn(g, mouseX, mouseY, bx + (BTN_W + BTN_GAP) * 3,  by,
                    Component.translatable("button.charta.texas_holdem.allin"), myChips > 0);
        }
    }

    /** Draws one Solitaire-style button at absolute screen coordinates. */
    private void drawBtn(GuiGraphics g, int mx, int my,
                         int ax, int ay, Component label, boolean active) {
        int color = active ? COLOR_ACTIVE : COLOR_INACTIVE;
        g.fill(ax + 1, ay + 1, ax + BTN_W - 1, ay + BTN_H - 1, 0xFF000000 + color);

        Vec3 cv = Vec3.fromRGB24(color);
        RenderSystem.setShaderColor((float) cv.x, (float) cv.y, (float) cv.z, 1f);
        g.blit(WIDGETS, ax, ay, 59, 0, BTN_W, BTN_H);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        g.drawString(font, label, ax + BTN_W / 2 - font.width(label) / 2, ay + 5, 0xFFFFFFFF);

        if (mx >= ax && mx < ax + BTN_W && my >= ay && my < ay + BTN_H) {
            g.fill(ax + 1, ay + 1, ax + BTN_W - 1, ay + BTN_H - 1, 0x33FFFFFF);
        }
    }

    // =========================================================================
    // renderLabels – coords are RELATIVE to leftPos/topPos (pose is pre-translated).
    // Only text drawn here — NO leftPos/topPos offset needed.
    // =========================================================================
    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        // Phase
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
        g.drawString(font, phaseComp, imageWidth / 2 - font.width(phaseComp) / 2, 4, 0xFFFFFF);

        // Pot
        Component potComp = Component.translatable("message.charta.texas_holdem.pot", menu.getPot());
        g.drawString(font, potComp, imageWidth / 2 - font.width(potComp) / 2, 14, 0xFFD700);

        // Current bet
        int currentBet = menu.getCurrentBet();
        if (currentBet > 0) {
            Component betComp = Component.literal("Bet: " + currentBet)
                    .withStyle(ChatFormatting.WHITE);
            g.drawString(font, betComp, imageWidth / 2 - font.width(betComp) / 2, 24, 0xFFFFFF);
        }

        // Turn indicator (below community cards)
        if (menu.isGameReady()) {
            CardPlayer current = menu.getCurrentPlayer();
            Component turnComp = menu.isCurrentPlayer() && phase != TexasHoldemGame.Phase.SHOWDOWN
                    ? Component.translatable("message.charta.your_turn").withStyle(ChatFormatting.GREEN)
                    : Component.translatable("message.charta.other_turn", current.getName())
                    .withStyle(s -> s.withColor(current.getColor().getTextureDiffuseColor()));
            g.drawString(font, turnComp, imageWidth / 2 - font.width(turnComp) / 2, 125, 0xFFFFFF);
        } else {
            Component dealComp = Component.translatable("message.charta.dealing_cards")
                    .withStyle(ChatFormatting.GOLD);
            g.drawString(font, dealComp, imageWidth / 2 - font.width(dealComp) / 2, 125, 0xFFFFFF);
        }
    }

    // =========================================================================
    // Mouse click — absolute screen coords match renderBg button positions
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
            int bx = leftPos + (imageWidth - totalBtnsW) / 2;
            int by = topPos  + imageHeight - BTN_H - 5;

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
    // Top bar — player names + chip counts + dealer/fold/allin badges
    // =========================================================================
    @Override
    public void renderTopBar(@NotNull GuiGraphics g) {
        // Draw standard top bar (avatars, names, colour stripes) first
        super.renderTopBar(g);

        // Overlay chip / status text below each player's name.
        // Replicate GameScreen's player-x formula exactly.
        List<CardPlayer> players = menu.getGame().getPlayers();
        int n = players.size();
        float playerSlotW = CardSlot.getWidth(CardSlot.Type.PREVIEW) + 28f; // 69px
        float playersWidth = n * playerSlotW + (n - 1f) * (playerSlotW / 10f);

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

            // y=14 is just below player name (which sits at y=2, scale=0.5 → ~7px)
            g.pose().pushPose();
            g.pose().translate(px + 26f, 14f, 0f);
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