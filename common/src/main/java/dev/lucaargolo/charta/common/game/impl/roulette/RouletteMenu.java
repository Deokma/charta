package dev.lucaargolo.charta.common.game.impl.roulette;

import dev.lucaargolo.charta.common.game.Games;
import dev.lucaargolo.charta.common.game.api.game.GameType;
import dev.lucaargolo.charta.common.menu.AbstractCardMenu;
import dev.lucaargolo.charta.common.menu.CardSlot;
import dev.lucaargolo.charta.common.menu.ModMenuTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class RouletteMenu extends AbstractCardMenu<RouletteGame, RouletteMenu> {

    // ContainerData layout:
    // [0..1]  chips[i]     (2 players)
    // [2..3]  bets[i]
    // [4..5]  betTypes[i]
    // [6]     revealedRankId
    // [7]     revealedSuitId
    // [8]     phaseOrdinal
    // Total: 9

    private static final int OFF_CHIPS    = 0;
    private static final int OFF_BETS     = 2;
    private static final int OFF_BET_TYPES = 4;
    private static final int OFF_RANK     = 6;
    private static final int OFF_SUIT     = 7;
    private static final int OFF_PHASE    = 8;
    private static final int DATA_COUNT   = 9;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            RouletteGame g = game;
            int n = g.getPlayers().size();
            if (index < OFF_BETS)       return index < n ? g.chips[index]    : 0;
            if (index < OFF_BET_TYPES)  { int i = index - OFF_BETS;     return i < n ? g.bets[i]     : 0; }
            if (index < OFF_RANK)       { int i = index - OFF_BET_TYPES; return i < n ? g.betTypes[i] : 0; }
            if (index == OFF_RANK)  return g.revealedRankId;
            if (index == OFF_SUIT)  return g.revealedSuitId;
            if (index == OFF_PHASE) return g.phaseOrdinal;
            return 0;
        }

        @Override
        public void set(int index, int value) {
            RouletteGame g = game;
            int n = g.getPlayers().size();
            if (index < OFF_BETS)       { if (index < n) g.chips[index]            = value; }
            else if (index < OFF_BET_TYPES) { int i = index - OFF_BETS;     if (i < n) g.bets[i]     = value; }
            else if (index < OFF_RANK)  { int i = index - OFF_BET_TYPES; if (i < n) g.betTypes[i] = value; }
            else if (index == OFF_RANK)  g.revealedRankId  = value;
            else if (index == OFF_SUIT)  g.revealedSuitId  = value;
            else if (index == OFF_PHASE) g.phaseOrdinal    = value;
        }

        @Override
        public int getCount() { return DATA_COUNT; }
    };

    public RouletteMenu(int containerId, Inventory inventory, Definition definition) {
        super(ModMenuTypes.ROULETTE.get(), containerId, inventory, definition);

        // Revealed card slot — positioned off-screen; result card is drawn manually in RouletteScreen
        addCardSlot(new CardSlot<>(this.game, g -> g.revealSlot, -500f, -500f));

        addDataSlots(data);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────
    public int getChips(int i)    { return data.get(OFF_CHIPS    + i); }
    public int getBet(int i)      { return data.get(OFF_BETS     + i); }
    public int getBetType(int i)  { return data.get(OFF_BET_TYPES + i); }
    public int getRevealedRankId(){ return data.get(OFF_RANK); }
    public int getRevealedSuitId(){ return data.get(OFF_SUIT); }
    public RouletteGame.Phase getPhase() {
        int ord = data.get(OFF_PHASE);
        RouletteGame.Phase[] phases = RouletteGame.Phase.values();
        return (ord >= 0 && ord < phases.length) ? phases[ord] : RouletteGame.Phase.BETTING;
    }

    public int getMyIdx()   { return game.getPlayers().indexOf(getCardPlayer()); }
    public int getMyChips() { int i = getMyIdx(); return i >= 0 ? getChips(i) : 0; }
    public int getMyBet()   { int i = getMyIdx(); return i >= 0 ? getBet(i)   : 0; }
    public int getMyBetType(){ int i = getMyIdx(); return i >= 0 ? getBetType(i) : 0; }

    @Override
    public GameType<RouletteGame, RouletteMenu> getGameType() { return Games.ROULETTE.get(); }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return this.game != null && this.cardPlayer != null && !this.game.isGameOver();
    }
}
