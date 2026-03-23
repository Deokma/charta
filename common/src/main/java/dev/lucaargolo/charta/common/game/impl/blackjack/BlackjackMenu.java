package dev.lucaargolo.charta.common.game.impl.blackjack;

import dev.lucaargolo.charta.common.game.Games;
import dev.lucaargolo.charta.common.game.api.CardPlayer;
import dev.lucaargolo.charta.common.game.api.game.GameType;
import dev.lucaargolo.charta.common.menu.AbstractCardMenu;
import dev.lucaargolo.charta.common.menu.CardSlot;
import dev.lucaargolo.charta.common.menu.HandSlot;
import dev.lucaargolo.charta.common.menu.ModMenuTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class BlackjackMenu extends AbstractCardMenu<BlackjackGame, BlackjackMenu> {

    // ContainerData layout:
    // [0..7]  chips[i]       (up to 8 players)
    // [8..15] bets[i]
    // [16]    stood bitmask
    // [17]    busted bitmask
    // [18]    phaseOrdinal
    // [19]    dealerValue
    // Total: 20

    private static final int MAX_P = 8;
    private static final int OFF_CHIPS = 0;
    private static final int OFF_BETS = MAX_P;
    private static final int OFF_STOOD = MAX_P * 2;
    private static final int OFF_BUSTED = MAX_P * 2 + 1;
    private static final int OFF_PHASE = MAX_P * 2 + 2;
    private static final int OFF_DEALER_VAL = MAX_P * 2 + 3;
    private static final int OFF_DEALER_VIS = MAX_P * 2 + 4; // value of face-up dealer cards only
    private static final int DATA_COUNT = MAX_P * 2 + 5;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            BlackjackGame g = game;
            int n = g.getPlayers().size();
            if (index < OFF_BETS) return index < n ? g.chips[index] : 0;
            if (index < OFF_STOOD) {
                int i = index - OFF_BETS;
                return i < n ? g.bets[i] : 0;
            }
            if (index == OFF_STOOD) {
                int m = 0;
                for (int i = 0; i < n; i++) if (g.stood[i]) m |= (1 << i);
                return m;
            }
            if (index == OFF_BUSTED) {
                int m = 0;
                for (int i = 0; i < n; i++) if (g.busted[i]) m |= (1 << i);
                return m;
            }
            if (index == OFF_PHASE) return g.phaseOrdinal;
            if (index == OFF_DEALER_VAL) return g.getDealerValue();
            if (index == OFF_DEALER_VIS) {
                // Sum only face-up cards (not flipped)
                java.util.LinkedList<dev.lucaargolo.charta.common.game.api.card.Card> visible = new java.util.LinkedList<>();
                for (var s : g.dealerSlots) s.stream().filter(c -> !c.flipped()).forEach(visible::add);
                return BlackjackGame.handValue(new dev.lucaargolo.charta.common.game.api.GameSlot(visible, 0, 0, 0, 0));
            }
            return 0;
        }

        @Override
        public void set(int index, int value) {
            BlackjackGame g = game;
            int n = g.getPlayers().size();
            if (index < OFF_BETS) {
                if (index < n) g.chips[index] = value;
            } else if (index < OFF_STOOD) {
                int i = index - OFF_BETS;
                if (i < n) g.bets[i] = value;
            } else if (index == OFF_STOOD) {
                for (int i = 0; i < n; i++) g.stood[i] = (value & (1 << i)) != 0;
            } else if (index == OFF_BUSTED) {
                for (int i = 0; i < n; i++) g.busted[i] = (value & (1 << i)) != 0;
            } else if (index == OFF_PHASE) {
                g.phaseOrdinal = value;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    /**
     * Index of the first dealer CardSlot in menu.cardSlots
     */
    public final int dealerSlotStart;
    public static final float DEALER_CARD_W = 27f;
    public static final float DEALER_CARD_GAP = 6f;
    public static final float DEALER_Y = 70f;

    public BlackjackMenu(int containerId, Inventory inventory, Definition definition) {
        super(ModMenuTypes.BLACKJACK.get(), containerId, inventory, definition);

        // Player previews — use imageWidth=256 so cards sit to the RIGHT of the avatar head
        {
            float slotW = CardSlot.getWidth(CardSlot.Type.PREVIEW) + 28f;
            int n = definition.players().length;
            float playersWidth = n * slotW + (n - 1f) * (slotW / 10f);
            for (int i = 0; i < n; i++) {
                float headX = 256f / 2f - playersWidth / 2f + i * (slotW + slotW / 10f);
                float cardX = headX + 26f; // RIGHT of the avatar
                CardPlayer p = this.game.getPlayers().get(i);
                addCardSlot(new CardSlot<>(this.game,
                        g -> g.getCensoredHand(cardPlayer, p),
                        cardX, 7f, CardSlot.Type.PREVIEW));
            }
        }

        // Dealer card slots — each slot is individually registered in the game and synced to client
        dealerSlotStart = this.cardSlots.size();
        {
            float cardW = DEALER_CARD_W;
            float gap = DEALER_CARD_GAP;
            int n = BlackjackGame.MAX_DEALER_CARDS;
            float totalW = n * cardW + (n - 1) * gap;
            float startX = (256f - totalW) / 2f;
            float dealerY = DEALER_Y;
            for (int di = 0; di < n; di++) {
                final int idx = di;
                addCardSlot(new CardSlot<>(this.game, g -> g.dealerSlots[idx],
                        startX + di * (cardW + gap), dealerY));
            }
        }

        // Player hand
        addCardSlot(new HandSlot<>(this.game, g -> g.getPhase() == BlackjackGame.Phase.PLAYING,
                this.getCardPlayer(),
                (256f - CardSlot.getWidth(CardSlot.Type.HORIZONTAL)) / 2f,
                -5f, CardSlot.Type.HORIZONTAL));

        addDataSlots(data);
    }

    // ── Accessors for screen ──────────────────────────────────────────────────
    public int getChips(int i) {
        return i < game.chips.length ? data.get(OFF_CHIPS + i) : 0;
    }

    public int getBet(int i) {
        return i < game.bets.length ? data.get(OFF_BETS + i) : 0;
    }

    /**
     * True if this player has left the table (bankrupt sentinel bets[i]==-1 AND chips==0).
     */
    public boolean isPlayerLeft(int i) {
        return getChips(i) == 0 && getBet(i) < 0;
    }

    public boolean isStood(int i) {
        return (data.get(OFF_STOOD) & (1 << i)) != 0;
    }

    public boolean isBusted(int i) {
        return (data.get(OFF_BUSTED) & (1 << i)) != 0;
    }

    public BlackjackGame.Phase getPhase() {
        return BlackjackGame.Phase.values()[data.get(OFF_PHASE)];
    }

    public int getDealerValue() {
        return data.get(OFF_DEALER_VAL);
    }

    public int getDealerVisibleValue() {
        return data.get(OFF_DEALER_VIS);
    }

    /**
     * True only during PLAYING phase — dealer has one hidden hole card
     */
    public boolean dealerHasHoleCard() {
        return getPhase() == BlackjackGame.Phase.PLAYING;
    }

    public int getMyBet() {
        int idx = game.getPlayers().indexOf(getCardPlayer());
        return idx >= 0 ? getBet(idx) : 0;
    }

    public int getMyChips() {
        int idx = game.getPlayers().indexOf(getCardPlayer());
        return idx >= 0 ? getChips(idx) : 0;
    }

    public int getHandValue() {
        int idx = game.getPlayers().indexOf(getCardPlayer());
        return idx >= 0 ? BlackjackGame.handValue(game.getPlayerHand(getCardPlayer())) : 0;
    }

    @Override
    public GameType<BlackjackGame, BlackjackMenu> getGameType() {
        return Games.BLACKJACK.get();
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return this.game != null && this.cardPlayer != null && !this.game.isGameOver();
    }
}