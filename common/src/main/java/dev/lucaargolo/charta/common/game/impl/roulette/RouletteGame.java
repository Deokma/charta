package dev.lucaargolo.charta.common.game.impl.roulette;

import dev.lucaargolo.charta.common.block.entity.CardTableBlockEntity;
import dev.lucaargolo.charta.common.game.api.CardPlayer;
import dev.lucaargolo.charta.common.game.api.GamePlay;
import dev.lucaargolo.charta.common.game.api.GameSlot;
import dev.lucaargolo.charta.common.game.api.card.Card;
import dev.lucaargolo.charta.common.game.api.card.Deck;
import dev.lucaargolo.charta.common.game.api.game.Game;
import dev.lucaargolo.charta.common.game.api.game.GameOption;
import dev.lucaargolo.charta.common.menu.AbstractCardMenu;
import dev.lucaargolo.charta.common.menu.ModMenuTypes;
import dev.lucaargolo.charta.common.registry.ModMenuTypeRegistry;
import dev.lucaargolo.charta.common.utils.CardImage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.*;
import java.util.function.Predicate;

/**
 * Card-based Roulette for 2 players.
 *
 * Numbers 0-36 are mapped from a shuffled deck (52 cards → 37 used, rest discarded).
 * Each card maps to a roulette number via: number = (rankIndex * 4 + suitIndex) % 37
 * This gives a deterministic but shuffled mapping.
 *
 * Bet types:
 *   BET_NONE    = 0
 *   BET_RED     = 1   (numbers: 1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36) → 1:1
 *   BET_BLACK   = 2   (numbers: 2,4,6,8,10,11,13,15,17,20,22,24,26,28,29,31,33,35) → 1:1
 *   BET_NUMBER  = 10 + number (10..46 for 0..36) → 35:1
 *
 * Action codes (client → server):
 *   500 + number (0..36) = bet on that number
 *   600 = bet red
 *   601 = bet black
 *   602 = bet green (0)  — same as BET_NUMBER+0 but explicit
 */
public class RouletteGame extends Game<RouletteGame, RouletteMenu> {

    // ── Action codes ──────────────────────────────────────────────────────────
    public static final int ACTION_NUMBER = 500; // + number (0..36)
    public static final int ACTION_RED    = 600;
    public static final int ACTION_BLACK  = 601;

    // ── Bet type constants ────────────────────────────────────────────────────
    public static final int BET_NONE  = 0;
    public static final int BET_RED   = 1;
    public static final int BET_BLACK = 2;
    // 10 + number (0..36) = bet on specific number

    // Standard European roulette red numbers
    public static final Set<Integer> RED_NUMBERS = Set.of(
        1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36
    );

    // ── Options ───────────────────────────────────────────────────────────────
    private final GameOption.Number STARTING_CHIPS_OPT = new GameOption.Number(
            100, 10, 500,
            Component.translatable("rule.charta.roulette.starting_chips"),
            Component.translatable("rule.charta.roulette.starting_chips.description"));

    private final GameOption.Number BET_AMOUNT_OPT = new GameOption.Number(
            10, 1, 100,
            Component.translatable("rule.charta.roulette.bet_amount"),
            Component.translatable("rule.charta.roulette.bet_amount.description"));

    // ── Synced state ──────────────────────────────────────────────────────────
    public int[]  chips;
    public int[]  bets;
    public int[]  betTypes;
    public int    revealedNumber = -1; // 0..36, -1 = not revealed
    public int    phaseOrdinal;

    public enum Phase { BETTING, SPINNING, RESULT }
    public Phase getPhase() { return Phase.values()[phaseOrdinal]; }

    // ── Internal ──────────────────────────────────────────────────────────────
    private final LinkedList<Integer> numberPile = new LinkedList<>();
    public final GameSlot revealSlot;

    public RouletteGame(List<CardPlayer> players, Deck deck) {
        super(players, deck);
        int n = Math.max(players.size(), 1);
        chips    = new int[n];
        bets     = new int[n];
        betTypes = new int[n];
        phaseOrdinal = Phase.BETTING.ordinal();

        float cx = CardTableBlockEntity.TABLE_WIDTH  / 2f - CardImage.WIDTH  / 2f;
        float cy = CardTableBlockEntity.TABLE_HEIGHT / 2f - CardImage.HEIGHT / 2f;
        revealSlot = addSlot(new GameSlot(new LinkedList<>(), cx, cy, 0f, 0f) {
            @Override public boolean canInsertCard(CardPlayer p, List<Card> c, int i) { return false; }
            @Override public boolean canRemoveCard(CardPlayer p, int i)               { return false; }
        });
    }

    @Override
    public List<GameOption<?>> getOptions() {
        return List.of(STARTING_CHIPS_OPT, BET_AMOUNT_OPT);
    }

    @Override
    public ModMenuTypeRegistry.AdvancedMenuTypeEntry<RouletteMenu, AbstractCardMenu.Definition> getMenuType() {
        return ModMenuTypes.ROULETTE;
    }

    @Override
    public RouletteMenu createMenu(int containerId, Inventory playerInventory, AbstractCardMenu.Definition definition) {
        return new RouletteMenu(containerId, playerInventory, definition);
    }

    @Override
    public Predicate<Deck> getDeckPredicate() { return deck -> deck.getCards().size() >= 37; }

    @Override
    public Predicate<Card> getCardPredicate() { return card -> true; }

    @Override public int getMinPlayers() { return 2; }
    @Override public int getMaxPlayers() { return 2; }

    @Override
    public void startGame() {
        int n = players.size();
        for (int i = 0; i < n; i++) chips[i] = startingChips();
        Arrays.fill(bets, 0);
        Arrays.fill(betTypes, BET_NONE);
        revealedNumber = -1;
        phaseOrdinal   = Phase.BETTING.ordinal();
        isGameOver     = false;
        isGameReady    = false;
        buildNumberPile();
        table(Component.translatable("message.charta.game_started"));
        table(Component.translatable("message.charta.roulette.place_bets"));
        for (int i = 0; i < 5; i++) scheduledActions.add(() -> {});
    }

    @Override
    public void runGame() {
        if (isGameOver) return;
        switch (getPhase()) {
            case BETTING  -> checkAllBetsPlaced();
            case SPINNING -> runSpin();
            case RESULT   -> {}
        }
    }

    private void checkAllBetsPlaced() {
        for (int i = 0; i < players.size(); i++) {
            if (chips[i] > 0 && betTypes[i] == BET_NONE) return;
        }
        isGameReady  = false;
        phaseOrdinal = Phase.SPINNING.ordinal();
        // 160 ticks = 8 seconds — client wheel spins and stops in this window
        for (int i = 0; i < 160; i++) scheduledActions.add(() -> {});
    }

    public void handleBet(int playerIdx, int betType) {
        if (playerIdx < 0 || playerIdx >= players.size()) return;
        if (betTypes[playerIdx] != BET_NONE) return;
        if (chips[playerIdx] <= 0) return;

        int amount = Math.min(betAmount(), chips[playerIdx]);
        bets[playerIdx]     = amount;
        chips[playerIdx]   -= amount;
        betTypes[playerIdx] = betType;

        play(players.get(playerIdx),
                Component.translatable("message.charta.roulette.bet_placed",
                        amount / 100, describeBet(betType)));
        checkAllBetsPlaced();
    }

    private void runSpin() {
        if (numberPile.isEmpty()) buildNumberPile();
        int number = numberPile.removeLast();
        revealedNumber = number;

        boolean isRed   = RED_NUMBERS.contains(number);
        boolean isGreen = (number == 0);
        boolean isBlack = !isRed && !isGreen;

        table(Component.translatable("message.charta.roulette.drawn_card",
                String.valueOf(number), isGreen ? "Green" : isRed ? "Red" : "Black"));

        boolean anyWithChips = false;
        for (int i = 0; i < players.size(); i++) {
            int type = betTypes[i];
            int bet  = bets[i];
            if (type == BET_NONE || bet == 0) { if (chips[i] > 0) anyWithChips = true; continue; }

            boolean won = false;
            int     win = 0;

            if (type == BET_RED && isRed) {
                won = true; win = bet;          // 1:1 → get back bet + win
            } else if (type == BET_BLACK && isBlack) {
                won = true; win = bet;
            } else if (type >= 10) {
                int guessed = type - 10;
                if (guessed == number) {
                    won = true; win = bet * 35; // 35:1
                }
            }

            if (won) {
                chips[i] += bet + win;
                play(players.get(i),
                        Component.translatable("message.charta.roulette.won", win / 100)
                                .withStyle(ChatFormatting.GREEN));
            } else {
                play(players.get(i),
                        Component.translatable("message.charta.roulette.lost", bet / 100)
                                .withStyle(ChatFormatting.RED));
            }
            if (chips[i] > 0) anyWithChips = true;
        }

        phaseOrdinal = Phase.RESULT.ordinal();
        isGameReady  = false;
        final boolean hasChips = anyWithChips;
        // Show result for 4 seconds, then next round
        for (int i = 0; i < 80; i++) scheduledActions.add(() -> {});
        scheduledActions.add(() -> { if (hasChips) startNewRound(); else endGame(); });
    }

    private void startNewRound() {
        Arrays.fill(bets, 0);
        Arrays.fill(betTypes, BET_NONE);
        revealedNumber = -1;
        if (numberPile.size() < 5) buildNumberPile();
        phaseOrdinal = Phase.BETTING.ordinal();
        table(Component.translatable("message.charta.roulette.place_bets"));
    }

    @Override
    public boolean canPlay(CardPlayer player, GamePlay play) {
        if (isGameOver) return false;
        int idx = players.indexOf(player);
        if (idx < 0) return false;
        int action = play.slot();
        if (getPhase() == Phase.BETTING && isGameReady) {
            if (betTypes[idx] != BET_NONE || chips[idx] <= 0) return false;
            if (action == ACTION_RED || action == ACTION_BLACK) return true;
            if (action >= ACTION_NUMBER && action <= ACTION_NUMBER + 36) return true;
        }
        return false;
    }

    @Override
    public void endGame() {
        if (isGameOver) return;
        isGameOver = true;
        int best = -1, bestChips = 0;
        for (int i = 0; i < players.size(); i++) {
            if (chips[i] > bestChips) { bestChips = chips[i]; best = i; }
        }
        if (best >= 0) {
            CardPlayer winner = players.get(best);
            winner.sendTitle(
                    Component.translatable("message.charta.you_won").withStyle(ChatFormatting.GREEN),
                    Component.translatable("message.charta.congratulations"));
            for (int i = 0; i < players.size(); i++) {
                if (i != best) players.get(i).sendTitle(
                        Component.translatable("message.charta.you_lost").withStyle(ChatFormatting.RED),
                        Component.translatable("message.charta.won_the_match", winner.getName()));
            }
        }
        scheduledActions.clear();
        for (CardPlayer p : players) p.play(null);
    }

    private int startingChips() { return STARTING_CHIPS_OPT.get() * 100; }
    private int betAmount()     { return BET_AMOUNT_OPT.get() * 100; }

    /** Build a shuffled pile of numbers 0-36. */
    private void buildNumberPile() {
        numberPile.clear();
        for (int i = 0; i <= 36; i++) numberPile.add(i);
        Collections.shuffle(numberPile);
    }

    public static String describeBet(int betType) {
        if (betType == BET_RED)   return "Red";
        if (betType == BET_BLACK) return "Black";
        if (betType >= 10 && betType <= 46) return String.valueOf(betType - 10);
        return "?";
    }

    public int getBetAmountPublic() { return betAmount(); }
}
