package dev.lucaargolo.charta.common.game.impl.blackjack;

import dev.lucaargolo.charta.common.block.entity.CardTableBlockEntity;
import dev.lucaargolo.charta.common.game.Ranks;
import dev.lucaargolo.charta.common.game.Suits;
import dev.lucaargolo.charta.common.game.api.CardPlayer;
import dev.lucaargolo.charta.common.game.api.GamePlay;
import dev.lucaargolo.charta.common.game.api.GameSlot;
import dev.lucaargolo.charta.common.game.api.card.Card;
import dev.lucaargolo.charta.common.game.api.card.Deck;
import dev.lucaargolo.charta.common.game.api.card.Rank;
import dev.lucaargolo.charta.common.game.api.game.Game;
import dev.lucaargolo.charta.common.game.api.game.GameOption;
import dev.lucaargolo.charta.common.menu.AbstractCardMenu;
import dev.lucaargolo.charta.common.menu.ModMenuTypes;
import dev.lucaargolo.charta.common.registry.ModMenuTypeRegistry;
import dev.lucaargolo.charta.common.sound.ModSounds;
import dev.lucaargolo.charta.common.utils.CardImage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.*;
import java.util.function.Predicate;

/**
 * Blackjack — all players bet simultaneously, then play against dealer.
 *
 * Flow:
 * 1. BETTING: all players set their bet (ACTION_BET+amount). When all bets placed, deal.
 * 2. PLAYING: players take turns — Hit, Stand, Double.
 * 3. DEALER: dealer auto-plays (hit ≤16, stand ≥17).
 * 4. RESULT: compare hands, pay out, start new round.
 */
public class BlackjackGame extends Game<BlackjackGame, BlackjackMenu> {

    // Actions sent from client
    public static final int ACTION_HIT    = 200;
    public static final int ACTION_STAND  = 201;
    public static final int ACTION_DOUBLE = 202;
    // Bet: ACTION_BET + amount
    public static final int ACTION_BET    = 300;

    // ── Options ───────────────────────────────────────────────────────────────
    private final GameOption.Number STARTING_CHIPS_OPT = new GameOption.Number(
            10, 1, 20,
            Component.translatable("rule.charta.blackjack.starting_chips"),
            Component.translatable("rule.charta.blackjack.starting_chips.description"));

    private final GameOption.Number MIN_BET_OPT = new GameOption.Number(
            5, 1, 50,
            Component.translatable("rule.charta.blackjack.min_bet"),
            Component.translatable("rule.charta.blackjack.min_bet.description"));

    // ── Synced state (public for ContainerData) ───────────────────────────────
    public int[]     chips;
    public int[]     bets;
    public boolean[] stood;
    public boolean[] busted;
    public int       phaseOrdinal;

    public enum Phase { BETTING, PLAYING, DEALER, RESULT }
    public Phase getPhase() { return Phase.values()[phaseOrdinal]; }

    // ── Slots ─────────────────────────────────────────────────────────────────
    public static final int MAX_DEALER_CARDS = 7;
    public final GameSlot[] dealerSlots = new GameSlot[MAX_DEALER_CARDS];
    // Keep dealerSlot as logical container for hand value calc
    private final java.util.LinkedList<Card> dealerCards = new java.util.LinkedList<>();
    private final LinkedList<Card> drawPile = new LinkedList<>();

    // Which player index is currently acting in PLAYING phase
    private int activePlayerIndex = 0;

    private int startingChips() { return STARTING_CHIPS_OPT.get() * 100; }
    private int minBet()        { return MIN_BET_OPT.get(); }

    // ── Constructor ───────────────────────────────────────────────────────────
    public BlackjackGame(List<CardPlayer> players, Deck deck) {
        super(players, deck);
        int n = Math.max(players.size(), 1);
        chips        = new int[n];
        bets         = new int[n];
        stood        = new boolean[n];
        busted       = new boolean[n];
        phaseOrdinal = Phase.BETTING.ordinal();

        // Register 7 individual dealer card slots, spread horizontally in table centre
        float cardW = CardImage.WIDTH;
        float gapD  = 8f;
        float totalD = MAX_DEALER_CARDS * cardW + (MAX_DEALER_CARDS - 1) * gapD;
        float startX = (CardTableBlockEntity.TABLE_WIDTH - totalD) / 2f;
        float cy = CardTableBlockEntity.TABLE_HEIGHT / 2f - CardImage.HEIGHT / 2f - 30f;
        for (int di = 0; di < MAX_DEALER_CARDS; di++) {
            float cx = startX + di * (cardW + gapD);
            dealerSlots[di] = addSlot(new GameSlot(new LinkedList<>(), cx, cy, 0f, 0f) {
                @Override public boolean canInsertCard(CardPlayer p, List<Card> c, int i) { return false; }
                @Override public boolean canRemoveCard(CardPlayer p, int i)               { return false; }
            });
        }
    }

    // ── Registration ──────────────────────────────────────────────────────────
    @Override
    public List<GameOption<?>> getOptions() { return List.of(STARTING_CHIPS_OPT, MIN_BET_OPT); }

    @Override
    public ModMenuTypeRegistry.AdvancedMenuTypeEntry<BlackjackMenu, AbstractCardMenu.Definition> getMenuType() {
        return ModMenuTypes.BLACKJACK;
    }

    @Override
    public BlackjackMenu createMenu(int containerId, Inventory playerInventory, AbstractCardMenu.Definition definition) {
        return new BlackjackMenu(containerId, playerInventory, definition);
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

    // ── startGame ─────────────────────────────────────────────────────────────
    @Override
    public void startGame() {
        int n = players.size();
        Arrays.fill(bets,   0);
        Arrays.fill(stood,  false);
        Arrays.fill(busted, false);
        for (GameSlot s : dealerSlots) s.clear();
        dealerCards.clear();
        drawPile.clear();
        activePlayerIndex = 0;
        waitingForPlayAction = false;

        for (int i = 0; i < n; i++) chips[i] = startingChips();

        // Build 4 shuffled decks
        buildDrawPile();

        for (CardPlayer p : players) { getPlayerHand(p).clear(); getCensoredHand(p).clear(); }

        phaseOrdinal = Phase.BETTING.ordinal();
        isGameReady  = false;
        isGameOver   = false;

        table(Component.translatable("message.charta.game_started"));
        table(Component.translatable("message.charta.blackjack.place_bets"));

        // Small delay then start betting
        for (int i = 0; i < 5; i++) scheduledActions.add(() -> {});
        // tick() will set isGameReady=true and call runGame() → startBettingRound()
    }

    // ── runGame ───────────────────────────────────────────────────────────────
    @Override
    public void runGame() {
        if (isGameOver) return;
        switch (getPhase()) {
            case BETTING -> startBettingRound();
            case PLAYING -> startPlayingRound();
            case DEALER  -> runDealerTurn();
            case RESULT  -> {}
        }
    }

    // ─── BETTING: wait for ALL players to bet ─────────────────────────────────
    // Bets arrive via canPlay/handleBet called from BlackjackActionPayload.
    // runGame() just checks if all bets are in and deals if so.
    private void startBettingRound() {
        // Tell all players it's betting time — everyone can bet simultaneously
        table(Component.translatable("message.charta.blackjack.place_bets"));
        // currentPlayer = null means "everyone can act" — we check per-player in canPlay
        currentPlayer = null;
        // Stay ready so canPlay works and screen shows buttons
    }

    /** Called by handleBet when a player submits their bet. */
    public void handleBet(int playerIdx, int amount) {
        if (playerIdx < 0 || playerIdx >= players.size()) return;
        if (bets[playerIdx] > 0) return; // already bet
        int actual = Math.max(minBet(), Math.min(amount, chips[playerIdx]));
        bets[playerIdx]   = actual;
        chips[playerIdx] -= actual;
        play(players.get(playerIdx), Component.translatable("message.charta.blackjack.bet_placed", actual));

        // Check if ALL players have bet
        boolean allBet = true;
        for (int b : bets) if (b == 0) { allBet = false; break; }
        if (allBet) {
            isGameReady = false;
            // Deal cards via scheduledActions
            scheduledActions.add(this::dealInitialCards);
        }
    }

    // ─── Deal initial cards ────────────────────────────────────────────────────
    private void dealInitialCards() {
        // Round 1: all players get 1 face-up card, dealer gets 1 face-up
        // Round 2: all players get 1 face-up card, dealer gets 1 face-DOWN
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < players.size(); i++) {
                final int pi = i;
                scheduledActions.add(() -> {
                    players.get(pi).playSound(ModSounds.CARD_DRAW.get());
                    dealFaceUp(players.get(pi), 1);
                });
                scheduledActions.add(() -> {});
            }
            final boolean faceDown = (round == 1);
            scheduledActions.add(() -> {
                if (!drawPile.isEmpty()) {
                    Card c = drawPile.removeLast();
                    if (faceDown) {
                        // keep face-down
                    } else {
                        if (c.flipped()) c.flip(); // face-up
                    }
                    int dIdx = 0; for (int _i=0;_i<dealerSlots.length;_i++) { if (dealerSlots[_i].isEmpty()) { dIdx=_i; break; } }
                    dealerSlots[dIdx].add(c);
                    dealerCards.add(c);
                }
            });
            scheduledActions.add(() -> {});
        }

        scheduledActions.add(() -> {
            phaseOrdinal = Phase.PLAYING.ordinal();
            Arrays.fill(stood, false);
            Arrays.fill(busted, false);
            activePlayerIndex = 0;
            waitingForPlayAction = false;

            // Auto-stand anyone with blackjack
            for (int i = 0; i < players.size(); i++) {
                if (handValue(getPlayerHand(players.get(i))) == 21) {
                    stood[i] = true;
                    play(players.get(i), Component.translatable("message.charta.blackjack.blackjack").withStyle(ChatFormatting.GOLD));
                }
            }
            // isGameReady remains false; tick() drains scheduledActions then calls runGame() → startPlayingRound()
        });
        scheduledActions.add(() -> {}); // extra tick to ensure runGame() is invoked
    }

    // ─── PLAYING: players take turns ─────────────────────────────────────────
    // Guard so startPlayingRound only runs once per turn (runGame() must not re-enter)
    private boolean waitingForPlayAction = false;

    private void startPlayingRound() {
        if (waitingForPlayAction) return; // already waiting, don't re-register

        // Skip players who are stood or busted
        while (activePlayerIndex < players.size()
                && (stood[activePlayerIndex] || busted[activePlayerIndex])) {
            activePlayerIndex++;
        }

        if (activePlayerIndex >= players.size()) {
            // All players done → dealer turn
            isGameReady = false;
            scheduledActions.add(() -> {
                phaseOrdinal = Phase.DEALER.ordinal();
                java.util.Arrays.stream(dealerSlots).filter(s -> !s.isEmpty()).flatMap(s -> s.stream()).filter(Card::flipped).findFirst().ifPresent(Card::flip);
                table(Component.translatable("message.charta.blackjack.dealer_reveals"));
            });
            return;
        }

        currentPlayer = players.get(activePlayerIndex);
        final int pi = activePlayerIndex;

        // IMPORTANT: resetPlay() MUST come before afterPlay()
        // resetPlay() creates a fresh CompletableFuture; afterPlay() registers on it;
        // play() later completes that same future → callback fires.
        currentPlayer.resetPlay();
        waitingForPlayAction = true;
        table(Component.translatable("message.charta.its_player_turn", currentPlayer.getColoredName()));

        currentPlayer.afterPlay(play -> {
            waitingForPlayAction = false;
            if (play != null) {
                switch (play.slot()) {
                    case ACTION_HIT    -> doHit(pi);
                    case ACTION_STAND  -> doStand(pi);
                    case ACTION_DOUBLE -> doDouble(pi);
                }
            }
            activePlayerIndex++;
            // Schedule next player via tick cycle
            isGameReady = false;
            scheduledActions.add(() -> {});
        });
        // isGameReady stays true so buttons appear and tick() processes afterPlay
    }

    private void doHit(int pi) {
        dealFaceUp(players.get(pi), 1);
        players.get(pi).playSound(ModSounds.CARD_DRAW.get());
        int val = handValue(getPlayerHand(players.get(pi)));
        if (val > 21) {
            busted[pi] = true;
            play(players.get(pi), Component.translatable("message.charta.blackjack.bust").withStyle(ChatFormatting.RED));
        } else if (val == 21) {
            stood[pi] = true;
        }
    }

    private void doStand(int pi) {
        stood[pi] = true;
        play(players.get(pi), Component.translatable("message.charta.blackjack.stand"));
    }

    private void doDouble(int pi) {
        int extra = Math.min(bets[pi], chips[pi]);
        bets[pi]  += extra;
        chips[pi] -= extra;
        play(players.get(pi), Component.translatable("message.charta.blackjack.doubled", bets[pi]));
        doHit(pi);
        if (!busted[pi]) stood[pi] = true;
    }

    // ─── DEALER auto-play ─────────────────────────────────────────────────────
    private void runDealerTurn() {
        GameSlot _tmp = new GameSlot(dealerCards, 0,0,0,0);
        int dv = handValue(_tmp);
        if (dv < 17) {
            isGameReady = false;
            scheduledActions.add(() -> {
                if (!drawPile.isEmpty()) {
                    Card c = drawPile.removeLast();
                    if (c.flipped()) c.flip();
                    int dIdx2 = 0; for (int _i=0;_i<dealerSlots.length;_i++) { if (dealerSlots[_i].isEmpty()) { dIdx2=_i; break; } }
                    dealerSlots[dIdx2].add(c);
                    dealerCards.add(c);
                    GameSlot _tmp2 = new GameSlot(dealerCards, 0,0,0,0);
                    table(Component.translatable("message.charta.blackjack.dealer_hits", handValue(_tmp2)));
                }
                // tick() will call runGame() → runDealerTurn() again
            });
        } else {
            // Dealer done → resolve
            resolveRound(dv);
        }
    }

    // ─── Payout ───────────────────────────────────────────────────────────────
    private void resolveRound(int dealerValue) {
        boolean dealerBust = dealerValue > 21;
        if (dealerBust) table(Component.translatable("message.charta.blackjack.dealer_busts").withStyle(ChatFormatting.GREEN));

        boolean anyWithChips = false;
        for (int i = 0; i < players.size(); i++) {
            CardPlayer p  = players.get(i);
            int pv        = handValue(getPlayerHand(p));
            boolean bjack = pv == 21 && getPlayerHand(p).stream().count() == 2;

            if (busted[i]) {
                play(p, Component.translatable("message.charta.blackjack.lost", bets[i]).withStyle(ChatFormatting.RED));
            } else if (dealerBust || pv > dealerValue) {
                int win = bjack ? (int)(bets[i] * 1.5) : bets[i];
                chips[i] += bets[i] + win;
                play(p, Component.translatable("message.charta.blackjack.won", win).withStyle(ChatFormatting.GREEN));
            } else if (pv == dealerValue) {
                chips[i] += bets[i]; // push
                play(p, Component.translatable("message.charta.blackjack.push").withStyle(ChatFormatting.YELLOW));
            } else {
                play(p, Component.translatable("message.charta.blackjack.lost", bets[i]).withStyle(ChatFormatting.RED));
            }

            if (chips[i] > 0) anyWithChips = true;
        }

        phaseOrdinal = Phase.RESULT.ordinal();
        isGameReady  = false;
        final boolean hasChips = anyWithChips;
        for (int i = 0; i < 60; i++) scheduledActions.add(() -> {});
        scheduledActions.add(() -> {
            if (hasChips) startNewRound();
            else endGame();
        });
    }

    private void startNewRound() {
        int n = players.size();
        Arrays.fill(bets,   0);
        Arrays.fill(stood,  false);
        Arrays.fill(busted, false);
        for (GameSlot s : dealerSlots) s.clear();
        dealerCards.clear();
        activePlayerIndex = 0;
        waitingForPlayAction = false;
        for (CardPlayer p : players) { getPlayerHand(p).clear(); getCensoredHand(p).clear(); }

        if (drawPile.size() < 20 * n) {
            buildDrawPile();
            table(Component.translatable("message.charta.blackjack.reshuffled"));
        }

        phaseOrdinal = Phase.BETTING.ordinal();
        // tick() will call runGame() → startBettingRound()
    }

    // ── canPlay ───────────────────────────────────────────────────────────────
    @Override
    public boolean canPlay(CardPlayer player, GamePlay play) {
        if (isGameOver) return false;
        int idx = players.indexOf(player);
        if (idx < 0) return false;

        int slot = play.slot();
        Phase phase = getPhase();

        if (phase == Phase.BETTING) {
            // Anyone can bet as long as they haven't bet yet and game is ready
            return isGameReady && bets[idx] == 0
                    && slot >= ACTION_BET
                    && (slot - ACTION_BET) >= minBet()
                    && (slot - ACTION_BET) <= chips[idx];
        }

        if (phase == Phase.PLAYING) {
            if (!isGameReady) return false;
            if (player != currentPlayer) return false;
            if (stood[idx] || busted[idx]) return false;
            return slot == ACTION_HIT || slot == ACTION_STAND
                    || (slot == ACTION_DOUBLE && chips[idx] > 0 && getPlayerHand(player).stream().count() == 2);
        }

        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void buildDrawPile() {
        drawPile.clear();
        for (int d = 0; d < 4; d++) {
            for (Card template : gameDeck) {
                Card copy = template.copy();
                if (!copy.flipped()) copy.flip();
                drawPile.add(copy);
            }
        }
        Collections.shuffle(drawPile);
    }

    private void dealFaceUp(CardPlayer player, int count) {
        for (int i = 0; i < count; i++) {
            if (drawPile.isEmpty()) return;
            Card c = drawPile.removeLast();
            if (c.flipped()) c.flip();
            getPlayerHand(player).add(c);
            getCensoredHand(player).add(c.copy());
        }
    }

    public static int handValue(GameSlot slot) {
        int total = 0, aces = 0;
        for (Card c : slot.stream().toList()) {
            int v = cardValue(c.rank());
            if (v == 11) aces++;
            total += v;
        }
        while (total > 21 && aces > 0) { total -= 10; aces--; }
        return total;
    }

    private static int cardValue(Rank rank) {
        if (rank == Ranks.ACE)   return 11;
        if (rank == Ranks.JACK || rank == Ranks.QUEEN || rank == Ranks.KING) return 10;
        int v = rank.ordinal();
        return (v >= 2 && v <= 10) ? v : 10;
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

    @Override public int getMinPlayers() { return 1; }
    public int getDealerValue()          { return handValue(new GameSlot(dealerCards, 0,0,0,0)); }
    public int getStartingChipsPublic()  { return startingChips(); }
}