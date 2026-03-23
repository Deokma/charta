package dev.lucaargolo.charta.common.game.impl.texasholdem;

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

public class TexasHoldemMenu extends AbstractCardMenu<TexasHoldemGame, TexasHoldemMenu> {

    // -------------------------------------------------------------------------
    // ContainerData index layout
    // -------------------------------------------------------------------------
    // [0..7]   chips[i]           (up to 8 players)
    // [8..15]  roundBets[i]
    // [16]     folded bitmask
    // [17]     allIn  bitmask
    // [18]     pot
    // [19]     currentBet
    // [20]     phaseOrdinal
    // [21]     dealerIndex
    // [22]     communityCardCount
    // [23]     raiseAmount  (computed: raiseMultiplier × bigBlind)
    // Total: 24 tracked integers

    private static final int MAX_PLAYERS = 8;
    private static final int OFFSET_CHIPS = 0;
    private static final int OFFSET_BETS = MAX_PLAYERS;
    private static final int OFFSET_FOLDED = MAX_PLAYERS * 2;
    private static final int OFFSET_ALLIN = MAX_PLAYERS * 2 + 1;
    private static final int OFFSET_POT = MAX_PLAYERS * 2 + 2;
    private static final int OFFSET_BET = MAX_PLAYERS * 2 + 3;
    private static final int OFFSET_PHASE = MAX_PLAYERS * 2 + 4;
    private static final int OFFSET_DEALER = MAX_PLAYERS * 2 + 5;
    private static final int OFFSET_COMMUNITY_CNT = MAX_PLAYERS * 2 + 6;
    private static final int OFFSET_RAISE_AMOUNT = MAX_PLAYERS * 2 + 7;
    private static final int DATA_COUNT = MAX_PLAYERS * 2 + 8;

    private final ContainerData data = new ContainerData() {

        @Override
        public int get(int index) {
            TexasHoldemGame g = game;
            int n = g.getPlayers().size();

            if (index < OFFSET_BETS) {
                int i = index - OFFSET_CHIPS;
                return i < n ? g.chips[i] : 0;
            } else if (index < OFFSET_FOLDED) {
                int i = index - OFFSET_BETS;
                return i < n ? g.roundBets[i] : 0;
            } else if (index == OFFSET_FOLDED) {
                int mask = 0;
                for (int i = 0; i < n; i++) if (g.folded[i]) mask |= (1 << i);
                return mask;
            } else if (index == OFFSET_ALLIN) {
                int mask = 0;
                for (int i = 0; i < n; i++) if (g.allIn[i]) mask |= (1 << i);
                return mask;
            } else if (index == OFFSET_POT) {
                return g.pot;
            } else if (index == OFFSET_BET) {
                return g.currentBet;
            } else if (index == OFFSET_PHASE) {
                return g.phaseOrdinal;
            } else if (index == OFFSET_DEALER) {
                return g.dealerIndex;
            } else if (index == OFFSET_COMMUNITY_CNT) {
                return g.communityCardCount;
            } else if (index == OFFSET_RAISE_AMOUNT) {
                return g.getRaiseAmountPublic();
            }
            return 0;
        }

        @Override
        public void set(int index, int value) {
            TexasHoldemGame g = game;
            int n = g.getPlayers().size();

            if (index < OFFSET_BETS) {
                int i = index - OFFSET_CHIPS;
                if (i < n) g.chips[i] = value;
            } else if (index < OFFSET_FOLDED) {
                int i = index - OFFSET_BETS;
                if (i < n) g.roundBets[i] = value;
            } else if (index == OFFSET_FOLDED) {
                for (int i = 0; i < n; i++) g.folded[i] = (value & (1 << i)) != 0;
            } else if (index == OFFSET_ALLIN) {
                for (int i = 0; i < n; i++) g.allIn[i] = (value & (1 << i)) != 0;
            } else if (index == OFFSET_POT) {
                g.pot = value;
            } else if (index == OFFSET_BET) {
                g.currentBet = value;
            } else if (index == OFFSET_PHASE) {
                g.phaseOrdinal = value;
            } else if (index == OFFSET_DEALER) {
                g.dealerIndex = value;
            } else if (index == OFFSET_COMMUNITY_CNT) {
                g.communityCardCount = value;
            }
            // OFFSET_RAISE_AMOUNT is read-only (computed server-side)
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public TexasHoldemMenu(int containerId, Inventory inventory, Definition definition) {
        super(ModMenuTypes.TEXAS_HOLDEM.get(), containerId, inventory, definition);

        // --- Player hand previews (right of each avatar, not left) ---
        // addTopPreview() uses hardcoded imgW=140 which places cards LEFT of the avatar.
        // We replicate its logic with imgW=256 and shift cards to x = head_x + 26.
        // GameScreen.renderTopBar places head at screen_x = w/2 - playersWidth/2 + i*(slotW+gap)
        // In menu coords: head_x_menu = imgW/2 - playersWidth/2 + i*(slotW+gap)
        {
            float slotW = CardSlot.getWidth(CardSlot.Type.PREVIEW) + 28f; // 69
            int n = definition.players().length;
            float playersWidth = n * slotW + (n - 1f) * (slotW / 10f);
            for (int i = 0; i < n; i++) {
                float headX = 256f / 2f - playersWidth / 2f + i * (slotW + slotW / 10f);
                float cardX = headX + 26f; // place cards to the right of the avatar
                CardPlayer p = this.game.getPlayers().get(i);
                addCardSlot(new CardSlot<>(this.game,
                        g -> g.getCensoredHand(cardPlayer, p),
                        cardX, 7f, CardSlot.Type.PREVIEW));
            }
        }

        // --- 5 community card slots ---
        // imageWidth=256; gap=20px as set by the user.
        float cW = 25f;
        float gapC = 20f;
        float totalC = 5 * cW + 4 * gapC;     // 205
        float startC = (256f - totalC) / 2f;   // 25.5
        for (int i = 0; i < 5; i++) {
            final int slotIdx = TexasHoldemGame.SLOT_COMMUNITY_FIRST + i;
            float slotX = startC + i * (cW + gapC);
            float slotY = 60f;
            addCardSlot(new CardSlot<>(this.game, g -> g.getSlot(slotIdx), slotX, slotY));
        }

        // --- Player's own hole cards at bottom ---
        float handX = (256f - CardSlot.getWidth(CardSlot.Type.HORIZONTAL)) / 2f; // 53
        addCardSlot(new HandSlot<>(this.game, g -> true, this.getCardPlayer(),
                handX, -5f, CardSlot.Type.HORIZONTAL));

        addDataSlots(data);
    }

    // -------------------------------------------------------------------------
    // Convenience accessors for TexasHoldemScreen
    // -------------------------------------------------------------------------

    public int getChips(int playerIndex) {
        return data.get(OFFSET_CHIPS + playerIndex);
    }

    public int getRoundBet(int playerIndex) {
        return data.get(OFFSET_BETS + playerIndex);
    }

    public boolean isFolded(int playerIndex) {
        return (data.get(OFFSET_FOLDED) & (1 << playerIndex)) != 0;
    }

    public boolean isAllIn(int playerIndex) {
        return (data.get(OFFSET_ALLIN) & (1 << playerIndex)) != 0;
    }

    public int getPot() {
        return data.get(OFFSET_POT);
    }

    public int getCurrentBet() {
        return data.get(OFFSET_BET);
    }

    public TexasHoldemGame.Phase getPhase() {
        return TexasHoldemGame.Phase.fromOrdinal(data.get(OFFSET_PHASE));
    }

    public int getDealerIndex() {
        return data.get(OFFSET_DEALER);
    }

    public int getCommunityCardCount() {
        return data.get(OFFSET_COMMUNITY_CNT);
    }

    /**
     * Returns the current raise step size (raiseMultiplier × bigBlind), synced from server.
     */
    public int getRaiseAmount() {
        return data.get(OFFSET_RAISE_AMOUNT);
    }

    /**
     * How many chips the local player still needs to put in to match the current bet.
     */
    public int getCallAmount() {
        int myIdx = game.getPlayers().indexOf(cardPlayer);
        if (myIdx < 0) return 0;
        return Math.max(0, getCurrentBet() - getRoundBet(myIdx));
    }

    // -------------------------------------------------------------------------

    /**
     * Returns the starting chip count for this game (from game options).
     */
    public int getStartingChips() {
        return game.getStartingChipsPublic();
    }

    @Override
    public GameType<TexasHoldemGame, TexasHoldemMenu> getGameType() {
        return Games.TEXAS_HOLDEM.get();
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