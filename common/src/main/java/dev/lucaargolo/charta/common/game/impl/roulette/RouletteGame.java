package dev.lucaargolo.charta.common.game.impl.roulette;

import dev.lucaargolo.charta.common.block.entity.CardTableBlockEntity;
import dev.lucaargolo.charta.common.game.Ranks;
import dev.lucaargolo.charta.common.game.Suits;
import dev.lucaargolo.charta.common.game.api.CardPlayer;
import dev.lucaargolo.charta.common.game.api.GamePlay;
import dev.lucaargolo.charta.common.game.api.GameSlot;
import dev.lucaargolo.charta.common.game.api.card.Card;
import dev.lucaargolo.charta.common.game.api.card.Deck;
import dev.lucaargolo.charta.common.game.api.card.Rank;
import dev.lucaargolo.charta.common.game.api.card.Suit;
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
 * Roulette — card-based roulette for exactly 2 players.
 *
 * Each round both players independently bet:
 *   - RED  (hearts / diamonds)  → win 2×
 *   - BLACK (spades / clubs)    → win 2×
 *   - Rank ACE…KING             → win 13×
 *
 * Once both bets are placed a card is drawn at random.
 * Winnings paid out, new round starts.
 *
 * Action codes sent from client → server:
 *   BET_RED   = 100
 *   BET_BLACK = 101
 *   BET_RANK + rank_ordinal (ACE=0 … KING=12)  = 200..212
 *   SPIN      = 300  (internal / can be sent after both bets placed)
 */
public class RouletteGame extends Game<RouletteGame, RouletteMenu> {

    // ── Action codes ──────────────────────────────────────────────────────────
    public static final int BET_RED   = 100;
    public static final int BET_BLACK = 101;
    public static final int BET_RANK  = 200;   // BET_RANK + rankIndex (0–12)
    public static final int SPIN      = 300;

    // ── Bet type constants (stored in betTypes[]) ─────────────────────────────
    public static final int BET_NONE  = 0;
    public static final int BET_RED_T  = 1;
    public static final int BET_BLACK_T = 2;
    // 3..15  →  rank index+3  (ACE=3 … KING=15)

    // ── Options ───────────────────────────────────────────────────────────────
    private final GameOption.Number STARTING_CHIPS_OPT = new GameOption.Number(
            10, 1, 20,
            Component.translatable("rule.charta.roulette.starting_chips"),
            Component.translatable("rule.charta.roulette.starting_chips.description"));

    private final GameOption.Number BET_AMOUNT_OPT = new GameOption.Number(
            1, 1, 10,
            Component.translatable("rule.charta.roulette.bet_amount"),
            Component.translatable("rule.charta.roulette.bet_amount.description"));

    // ── Synced state ──────────────────────────────────────────────────────────
    public int[]  chips;
    public int[]  bets;       // current bet amounts  (0 = not bet yet)
    public int[]  betTypes;   // BET_NONE / BET_RED_T / BET_BLACK_T / 3+rankIdx
    public int    revealedRankId   = -1;   // Ranks registry id of drawn card
    public int    revealedSuitId   = -1;   // Suits registry id of drawn card
    public int    phaseOrdinal;

    public enum Phase { BETTING, SPINNING, RESULT }
    public Phase getPhase() { return Phase.values()[phaseOrdinal]; }

    // ── Internal draw pile ────────────────────────────────────────────────────
    private final LinkedList<Card> drawPile = new LinkedList<>();

    /** Slot that displays the last drawn (revealed) card in the centre of the table. */
    public final GameSlot revealSlot;

    // ── Constructor ───────────────────────────────────────────────────────────
    public RouletteGame(List<CardPlayer> players, Deck deck) {
        super(players, deck);

        int n = Math.max(players.size(), 1);
        chips    = new int[n];
        bets     = new int[n];
        betTypes = new int[n];
        phaseOrdinal = Phase.BETTING.ordinal();

        // One visible slot in the centre for the revealed card
        float cx = CardTableBlockEntity.TABLE_WIDTH  / 2f - CardImage.WIDTH  / 2f;
        float cy = CardTableBlockEntity.TABLE_HEIGHT / 2f - CardImage.HEIGHT / 2f;
        revealSlot = addSlot(new GameSlot(new LinkedList<>(), cx, cy, 0f, 0f) {
            @Override public boolean canInsertCard(CardPlayer p, List<Card> c, int i) { return false; }
            @Override public boolean canRemoveCard(CardPlayer p, int i)               { return false; }
        });
    }

    // ── Registration ──────────────────────────────────────────────────────────
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
    public Predicate<Deck> getDeckPredicate() {
        return deck -> deck.getCards().size() >= 52
                && Suits.STANDARD.containsAll(deck.getSuits())
                && deck.getSuits().containsAll(Suits.STANDARD);
    }

    @Override
    public Predicate<Card> getCardPredicate() {
        return card -> Suits.STANDARD.contains(card.suit()) && Ranks.STANDARD.contains(card.rank());
    }

    // ── Player limits ─────────────────────────────────────────────────────────
    @Override public int getMinPlayers() { return 2; }
    @Override public int getMaxPlayers() { return 2; }

    // ── startGame ─────────────────────────────────────────────────────────────
    @Override
    public void startGame() {
        int n = players.size();
        for (int i = 0; i < n; i++) chips[i] = startingChips();
        Arrays.fill(bets,     0);
        Arrays.fill(betTypes, BET_NONE);
        revealedRankId = -1;
        revealedSuitId = -1;
        revealSlot.clear();
        phaseOrdinal = Phase.BETTING.ordinal();
        isGameOver  = false;
        isGameReady = false;
        buildDrawPile();

        table(Component.translatable("message.charta.game_started"));
        table(Component.translatable("message.charta.roulette.place_bets"));

        // Small startup delay, then mark ready
        for (int i = 0; i < 5; i++) scheduledActions.add(() -> {});
    }

    // ── runGame ───────────────────────────────────────────────────────────────
    @Override
    public void runGame() {
        if (isGameOver) return;
        switch (getPhase()) {
            case BETTING  -> checkAllBetsPlaced();
            case SPINNING -> runSpin();
            case RESULT   -> {} // handled by scheduled actions
        }
    }

    // ── BETTING ───────────────────────────────────────────────────────────────
    private void checkAllBetsPlaced() {
        for (int i = 0; i < players.size(); i++) {
            if (chips[i] > 0 && betTypes[i] == BET_NONE) return; // still waiting
        }
        // All active players have bet → spin
        isGameReady = false;
        phaseOrdinal = Phase.SPINNING.ordinal();
        for (int i = 0; i < 10; i++) scheduledActions.add(() -> {});
    }

    public void handleBet(int playerIdx, int betType) {
        if (playerIdx < 0 || playerIdx >= players.size()) return;
        if (betTypes[playerIdx] != BET_NONE) return;  // already bet
        if (chips[playerIdx] <= 0) return;            // bankrupt

        int amount = Math.min(betAmount(), chips[playerIdx]);
        bets[playerIdx]     = amount;
        chips[playerIdx]   -= amount;
        betTypes[playerIdx] = betType;

        String betName = describeBet(betType);
        play(players.get(playerIdx),
                Component.translatable("message.charta.roulette.bet_placed", amount, betName));

        checkAllBetsPlaced();
    }

    // ── SPINNING ──────────────────────────────────────────────────────────────
    private void runSpin() {
        if (drawPile.isEmpty()) buildDrawPile();

        Card drawn = drawPile.removeLast();
        if (drawn.flipped()) drawn.flip(); // face-up

        revealSlot.clear();
        revealSlot.add(drawn);
        revealedRankId = Ranks.getRegistry().getId(drawn.rank());
        revealedSuitId = Suits.getRegistry().getId(drawn.suit());

        boolean isRed   = drawn.suit() == Suits.HEARTS || drawn.suit() == Suits.DIAMONDS;
        int     rankIdx = rankIndex(drawn.rank());   // 0=Ace … 12=King  (-1 if not standard)

        table(Component.translatable("message.charta.roulette.drawn_card",
                drawn.rank().toString(), drawn.suit().toString()));

        boolean anyWithChips = false;
        for (int i = 0; i < players.size(); i++) {
            int type = betTypes[i];
            int bet  = bets[i];
            if (type == BET_NONE || bet == 0) { if (chips[i] > 0) anyWithChips = true; continue; }

            boolean won = false;
            int     win = 0;
            if (type == BET_RED_T  && isRed) {
                won = true; win = bet;     // 2× payout (bet already deducted)
            } else if (type == BET_BLACK_T && !isRed) {
                won = true; win = bet;
            } else if (type >= 3) {
                int guessedRank = type - 3;
                if (guessedRank == rankIdx) {
                    won = true; win = bet * 12; // 13× payout
                }
            }

            if (won) {
                chips[i] += bet + win;
                play(players.get(i), Component.translatable("message.charta.roulette.won", win).withStyle(ChatFormatting.GREEN));
            } else {
                play(players.get(i), Component.translatable("message.charta.roulette.lost", bet).withStyle(ChatFormatting.RED));
            }

            if (chips[i] > 0) anyWithChips = true;
        }

        phaseOrdinal = Phase.RESULT.ordinal();
        isGameReady  = false;
        final boolean hasChips = anyWithChips;

        // Pause on result, then start new round or end game
        for (int i = 0; i < 60; i++) scheduledActions.add(() -> {});
        scheduledActions.add(() -> {
            if (hasChips) startNewRound();
            else endGame();
        });
    }

    private void startNewRound() {
        Arrays.fill(bets,     0);
        Arrays.fill(betTypes, BET_NONE);
        revealedRankId = -1;
        revealedSuitId = -1;
        revealSlot.clear();

        if (drawPile.size() < 10) {
            buildDrawPile();
            table(Component.translatable("message.charta.roulette.reshuffled"));
        }

        phaseOrdinal = Phase.BETTING.ordinal();
        table(Component.translatable("message.charta.roulette.place_bets"));
    }

    // ── canPlay ───────────────────────────────────────────────────────────────
    @Override
    public boolean canPlay(CardPlayer player, GamePlay play) {
        if (isGameOver) return false;
        int idx = players.indexOf(player);
        if (idx < 0) return false;

        int action = play.slot();
        if (getPhase() == Phase.BETTING && isGameReady) {
            if (betTypes[idx] != BET_NONE) return false;  // already bet
            if (chips[idx] <= 0) return false;
            if (action == BET_RED || action == BET_BLACK) return true;
            if (action >= BET_RANK && action <= BET_RANK + 12) return true;
        }
        return false;
    }

    // ── endGame ───────────────────────────────────────────────────────────────
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

    // ── Helpers ───────────────────────────────────────────────────────────────
    private int startingChips() { return STARTING_CHIPS_OPT.get() * 100; }
    private int betAmount()     { return BET_AMOUNT_OPT.get() * 100; }

    private void buildDrawPile() {
        drawPile.clear();
        for (Card template : gameDeck) {
            Card copy = template.copy();
            if (!copy.flipped()) copy.flip();
            drawPile.add(copy);
        }
        Collections.shuffle(drawPile);
    }

    /** Returns 0 (Ace) … 12 (King), or -1 if rank not in STANDARD. */
    public static int rankIndex(Rank rank) {
        // Ranks.STANDARD = {ACE, TWO … KING}
        List<Rank> order = List.of(
                Ranks.ACE, Ranks.TWO, Ranks.THREE, Ranks.FOUR, Ranks.FIVE, Ranks.SIX,
                Ranks.SEVEN, Ranks.EIGHT, Ranks.NINE, Ranks.TEN, Ranks.JACK, Ranks.QUEEN, Ranks.KING);
        return order.indexOf(rank);
    }

    public static String describeBet(int betType) {
        if (betType == BET_RED_T)  return "Red";
        if (betType == BET_BLACK_T) return "Black";
        if (betType >= 3 && betType <= 15) {
            int idx = betType - 3;
            List<String> names = List.of("Ace","2","3","4","5","6","7","8","9","10","Jack","Queen","King");
            if (idx < names.size()) return names.get(idx);
        }
        return "?";
    }

    public int getBetAmountPublic() { return betAmount(); }
}
