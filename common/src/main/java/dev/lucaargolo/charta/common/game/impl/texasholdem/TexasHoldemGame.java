package dev.lucaargolo.charta.common.game.impl.texasholdem;

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
import dev.lucaargolo.charta.common.sound.ModSounds;
import dev.lucaargolo.charta.common.utils.CardImage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.*;
import java.util.function.Predicate;

/**
 * Server-side game logic for Texas Hold em Poker.
 *
 * Hand lifecycle:
 *   startGame -> dealNewHand -> postBlindsAndStart -> runBettingRound
 *   -> advancePhase (repeats) -> doShowdown -> onHandComplete
 *   -> [pause] -> dealNewHand or endGame
 *
 * Action codes encoded in GamePlay.slot():
 *   ACTION_FOLD = 100, ACTION_CALL = 101, ACTION_RAISE_MIN = 102,
 *   ACTION_ALL_IN = 103, ACTION_RAISE_CUSTOM + amount = custom raise
 */
public class TexasHoldemGame extends Game<TexasHoldemGame, TexasHoldemMenu> {

    // =========================================================================
    // Action constants
    // =========================================================================

    public static final int ACTION_FOLD         = 100;
    public static final int ACTION_CALL         = 101;
    public static final int ACTION_RAISE_MIN    = 102;
    public static final int ACTION_ALL_IN       = 103;
    /** Custom raise: encoded as ACTION_RAISE_CUSTOM + chipAmount. */
    public static final int ACTION_RAISE_CUSTOM = 200;

    // =========================================================================
    // Community card slot indices (used by TexasHoldemMenu)
    // =========================================================================

    public static final int SLOT_COMMUNITY_FIRST = 0;
    public static final int SLOT_COMMUNITY_LAST  = 4;

    // =========================================================================
    // Timing constants
    // =========================================================================

    /** Empty ticks added after showdown before onHandComplete is called. */
    private static final int SHOWDOWN_DELAY_TICKS = 60;

    /** How many ticks the between-hands pause lasts before auto-starting. */
    private static final int PAUSE_DURATION_TICKS = 100;

    // =========================================================================
    // Game options
    // =========================================================================

    /**
     * Starting chips — stored as x100 (slider 1-20 shows as 100-2000).
     * displayMultiplier=100 lets the widget show the real amount.
     */
    private final GameOption.Number startingChipsOption = new GameOption.Number(
            5, 1, 20, 100,
            Component.translatable("rule.charta.texas_holdem.starting_chips"),
            Component.translatable("rule.charta.texas_holdem.starting_chips.description"));

    /** Big-blind chip amount (2-50). */
    private final GameOption.Number bigBlindOption = new GameOption.Number(
            10, 2, 50,
            Component.translatable("rule.charta.texas_holdem.big_blind"),
            Component.translatable("rule.charta.texas_holdem.big_blind.description"));

    // =========================================================================
    // Public game state -- synced to clients via ContainerData in TexasHoldemMenu
    // =========================================================================

    public int[]     chips;           // chips per player index
    public int[]     roundBets;       // amount bet this round by each player
    public int[]     totalCommitted;  // total chips committed to pot this hand
    public boolean[] folded;          // true if player i has folded
    public boolean[] allIn;           // true if player i is all-in
    public int  pot;
    public int  currentBet;           // highest bet in the current round
    public int  dealerIndex;
    public int  phaseOrdinal;         // PokerPhase ordinal, synced as int
    public int  communityCardCount;   // how many community cards are face-up (0-5)
    public boolean isPaused;          // true during the between-hands pause
    public int  skipVoteMask;         // bitmask of players who voted to skip the pause

    // =========================================================================
    // Private server-side state
    // =========================================================================

    /** Ordered list of player indices still waiting to act this betting round. */
    private final LinkedList<Integer> pendingActors = new LinkedList<>();

    /** Player indices whose hole cards are revealed at showdown. */
    private final Set<Integer> revealedAtShowdown = new HashSet<>();

    /** The five community card slots on the table surface (indices 0-4). */
    public final GameSlot[] communitySlots = new GameSlot[5];

    /**
     * Private draw pile -- not a GameSlot so it is never rendered on the table
     * and never needs to be synced to clients.
     */
    private final LinkedList<Card> drawPile = new LinkedList<>();

    // =========================================================================
    // Constructor
    // =========================================================================

    public TexasHoldemGame(List<CardPlayer> players, Deck deck) {
        super(players, deck);

        int n = Math.max(players.size(), 1);
        chips          = new int[n];
        roundBets      = new int[n];
        totalCommitted = new int[n];
        folded         = new boolean[n];
        allIn          = new boolean[n];
        phaseOrdinal   = PokerPhase.PREFLOP.ordinal();
        dealerIndex    = 0;

        initCommunitySlots();
    }

    /** Registers the five community card slots, evenly spaced across the table. */
    private void initCommunitySlots() {
        float cardW   = CardImage.WIDTH;    // 25
        float cardH   = CardImage.HEIGHT;   // 35
        float gap     = 8f;
        float totalW  = 5 * cardW + 4 * gap;
        float startX  = (CardTableBlockEntity.TABLE_WIDTH  - totalW) / 2f;
        float centreY = CardTableBlockEntity.TABLE_HEIGHT / 2f - cardH / 2f - 5f;

        for (int i = 0; i < 5; i++) {
            final float slotX = startX + i * (cardW + gap);
            communitySlots[i] = addSlot(new GameSlot(new LinkedList<>(), slotX, centreY, 0f, 0f) {
                @Override public boolean canInsertCard(CardPlayer p, List<Card> c, int idx) { return false; }
                @Override public boolean canRemoveCard(CardPlayer p, int idx)               { return false; }
            });
        }
    }

    // =========================================================================
    // Game option accessors
    // =========================================================================

    private int getStartingChips()    { return startingChipsOption.get() * 100; }
    private int getBigBlind()         { return bigBlindOption.get(); }
    private int getSmallBlind()       { return getBigBlind() / 2; }
    private int getRaiseAmount()      { return getBigBlind(); }

    /** Exposed for ContainerData sync in TexasHoldemMenu. */
    public int getStartingChipsPublic() { return getStartingChips(); }

    /** Exposed for ContainerData sync in TexasHoldemMenu. */
    public int getRaiseAmountPublic()   { return getRaiseAmount(); }

    // =========================================================================
    // Game interface -- metadata
    // =========================================================================

    @Override
    public ModMenuTypeRegistry.AdvancedMenuTypeEntry<TexasHoldemMenu, AbstractCardMenu.Definition> getMenuType() {
        return ModMenuTypes.TEXAS_HOLDEM;
    }

    @Override
    public TexasHoldemMenu createMenu(int containerId, Inventory playerInventory,
                                      AbstractCardMenu.Definition definition) {
        return new TexasHoldemMenu(containerId, playerInventory, definition);
    }

    @Override
    public Predicate<Deck> getDeckPredicate() {
        return deck -> deck.getCards().size() >= 52
                && Suits.STANDARD.containsAll(deck.getSuits())
                && deck.getSuits().containsAll(Suits.STANDARD);
    }

    @Override
    public Predicate<Card> getCardPredicate() {
        return card -> Suits.STANDARD.contains(card.suit())
                && Ranks.STANDARD.contains(card.rank());
    }

    @Override
    public List<GameOption<?>> getOptions() {
        return List.of(startingChipsOption, bigBlindOption);
    }

    @Override public int getMinPlayers() { return 2; }
    @Override public int getMaxPlayers() { return 8; }

    // =========================================================================
    // Hole-card slot -- view-only, managed entirely by game logic
    // =========================================================================

    @Override
    protected GameSlot createPlayerHand(CardPlayer player) {
        return new GameSlot(player.hand()) {
            @Override public boolean canRemoveCard(CardPlayer p, int idx)              { return false; }
            @Override public boolean canInsertCard(CardPlayer p, List<Card> c, int idx){ return false; }
            @Override public boolean removeAll()                                        { return false; }
        };
    }

    // =========================================================================
    // canPlay -- validates actions from humans and bots
    // =========================================================================

    @Override
    public boolean canPlay(CardPlayer player, GamePlay play) {
        if (!isGameReady || isGameOver) return false;
        if (player != currentPlayer)    return false;

        int idx = players.indexOf(player);
        if (idx < 0) return false;

        return switch (play.slot()) {
            case ACTION_FOLD      -> !folded[idx];
            case ACTION_CALL      -> !folded[idx] && !allIn[idx];
            case ACTION_RAISE_MIN -> !folded[idx] && !allIn[idx] && chips[idx] > 0;
            case ACTION_ALL_IN    -> !folded[idx] && !allIn[idx] && chips[idx] > 0;
            default               -> play.slot() >= ACTION_RAISE_CUSTOM
                    && !folded[idx] && !allIn[idx] && chips[idx] > 0;
        };
    }

    // =========================================================================
    // getBestPlay -- simple bot AI
    // =========================================================================

    @Override
    public GamePlay getBestPlay(CardPlayer player) {
        int idx = players.indexOf(player);
        if (idx < 0 || folded[idx] || allIn[idx]) return null;

        float strength    = evaluateHandStrength(player);
        int   callAmount  = currentBet - roundBets[idx];

        if (strength < 0.25f) {
            return callAmount > 0
                    ? new GamePlay(List.of(), ACTION_FOLD)
                    : new GamePlay(List.of(), ACTION_CALL);
        } else if (strength < 0.55f) {
            return new GamePlay(List.of(), ACTION_CALL);
        } else if (strength < 0.80f) {
            return chips[idx] >= getRaiseAmount()
                    ? new GamePlay(List.of(), ACTION_RAISE_MIN)
                    : new GamePlay(List.of(), ACTION_CALL);
        } else {
            return new GamePlay(List.of(), ACTION_ALL_IN);
        }
    }

    /**
     * Returns a normalised hand strength in [0.0, 1.0].
     * Pre-flop uses rank heuristics; post-flop uses the full HandEvaluator.
     */
    private float evaluateHandStrength(CardPlayer player) {
        List<Card> holeCards = getPlayerHand(player).stream().toList();

        if (PokerPhase.fromOrdinal(phaseOrdinal) == PokerPhase.PREFLOP) {
            return evaluatePreflopStrength(holeCards);
        }

        List<Card> allCards = new ArrayList<>(holeCards);
        for (GameSlot slot : communitySlots) slot.stream().forEach(allCards::add);
        return (float) HandEvaluator.evaluate(allCards) / HandEvaluator.MAX_SCORE;
    }

    // =========================================================================
    // Game lifecycle -- start / deal
    // =========================================================================

    @Override
    public void startGame() {
        Arrays.fill(chips, getStartingChips());
        isPaused     = false;
        skipVoteMask = 0;
        isGameReady  = false;
        isGameOver   = false;
        table(Component.translatable("message.charta.game_started"));
        dealNewHand();
    }

    /** Resets per-hand state, builds draw pile, schedules deal animation and blinds. */
    private void dealNewHand() {
        clearHandState();
        buildShuffledDrawPile();

        // Bankrupt players sit out
        for (int i = 0; i < players.size(); i++) {
            if (chips[i] == 0) folded[i] = true;
        }

        scheduleDealAnimation();
        scheduledActions.add(this::postBlindsAndStart);

        isGameReady = false;
        isGameOver  = false;
    }

    /** Zeros all per-hand arrays and clears card collections. */
    private void clearHandState() {
        Arrays.fill(roundBets,      0);
        Arrays.fill(totalCommitted, 0);
        Arrays.fill(folded,         false);
        Arrays.fill(allIn,          false);

        pot               = 0;
        currentBet        = 0;
        communityCardCount = 0;
        phaseOrdinal      = PokerPhase.PREFLOP.ordinal();

        revealedAtShowdown.clear();
        pendingActors.clear();

        for (GameSlot slot : communitySlots) slot.clear();
        drawPile.clear();

        for (CardPlayer p : players) {
            getPlayerHand(p).clear();
            getCensoredHand(p).clear();
        }
    }

    /** Copies gameDeck into drawPile, ensures all cards are face-down, then shuffles. */
    private void buildShuffledDrawPile() {
        for (Card template : gameDeck) {
            Card copy = template.copy();
            if (!copy.flipped()) copy.flip();
            drawPile.add(copy);
        }
        Collections.shuffle(drawPile);
    }

    /**
     * Enqueues two deal passes for all active players, with one empty tick
     * between each card for a natural table-animation feel.
     */
    private void scheduleDealAnimation() {
        int n = players.size();
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < n; i++) {
                final int pi = i;
                if (chips[pi] == 0) continue;
                scheduledActions.add(() -> {
                    CardPlayer p = players.get(pi);
                    p.playSound(ModSounds.CARD_DRAW.get());
                    dealOneCardFromPile(p);
                });
                scheduledActions.add(() -> {}); // animation gap
            }
        }
    }

    /** Removes the top card from the draw pile and places it in the player's hand. */
    private void dealOneCardFromPile(CardPlayer player) {
        if (drawPile.isEmpty()) return;
        Card card = drawPile.removeLast();
        if (card.flipped()) card.flip();
        getPlayerHand(player).add(card);
        getCensoredHand(player).add(new Card());
    }

    // =========================================================================
    // Blinds
    // =========================================================================

    /** Posts small and big blinds, sets preflop betting order, and announces the phase. */
    private void postBlindsAndStart() {
        int n     = players.size();
        int sbIdx = (dealerIndex + 1) % n;
        int bbIdx = (dealerIndex + 2) % n;

        postBlind(sbIdx, getSmallBlind());
        postBlind(bbIdx, getBigBlind());
        currentBet = getBigBlind();

        int startIdx = (bbIdx + 1) % n;
        buildPendingActors(startIdx, bbIdx);

        table(Component.translatable("message.charta.texas_holdem.preflop"));
        table(Component.translatable("message.charta.texas_holdem.pot", pot));
    }

    private void postBlind(int playerIndex, int amount) {
        CardPlayer player = players.get(playerIndex);
        int actual = Math.min(amount, chips[playerIndex]);

        commitChips(playerIndex, actual);
        if (chips[playerIndex] == 0) allIn[playerIndex] = true;

        play(player, Component.translatable("message.charta.texas_holdem.posted_blind", actual));
    }

    // =========================================================================
    // Betting round engine
    // =========================================================================

    @Override
    public void runGame() {
        if (!isGameReady) return;
        runBettingRound();
    }

    /**
     * Processes the next pending actor. Loops (via callback) until the round ends,
     * then calls advancePhase().
     */
    private void runBettingRound() {
        if (isGameOver) return;

        if (countActivePlayers() <= 1) {
            awardPotToLastPlayer();
            return;
        }

        Integer nextActorIdx = pollNextBettingActor();
        if (nextActorIdx == null) {
            advancePhase();
            return;
        }

        final int actorIdx = nextActorIdx;
        currentPlayer = players.get(actorIdx);
        currentPlayer.resetPlay();
        table(Component.translatable("message.charta.its_player_turn", currentPlayer.getColoredName()));

        currentPlayer.afterPlay(play -> {
            handleAction(actorIdx, play != null ? play.slot() : ACTION_FOLD);
            runBettingRound();
        });
    }

    /**
     * Returns the next player from pendingActors who can still bet,
     * skipping folded and all-in players. Returns null if none remain.
     */
    private Integer pollNextBettingActor() {
        while (!pendingActors.isEmpty()) {
            Integer candidate = pendingActors.poll();
            if (!folded[candidate] && !allIn[candidate]) return candidate;
        }
        return null;
    }

    // =========================================================================
    // Action handler
    // =========================================================================

    /** Applies the given action for the player at playerIndex. */
    public void handleAction(int playerIndex, int action) {
        if (playerIndex < 0 || playerIndex >= players.size()) return;

        switch (action) {
            case ACTION_FOLD      -> applyFold(playerIndex);
            case ACTION_CALL      -> applyCallOrCheck(playerIndex);
            case ACTION_RAISE_MIN -> applyRaise(playerIndex, getRaiseAmount());
            case ACTION_ALL_IN    -> applyRaise(playerIndex, chips[playerIndex]);
            default -> {
                if (action >= ACTION_RAISE_CUSTOM) {
                    int amount = Math.max(getRaiseAmount(), action - ACTION_RAISE_CUSTOM);
                    applyRaise(playerIndex, Math.min(amount, chips[playerIndex]));
                } else {
                    applyFold(playerIndex);
                }
            }
        }
    }

    private void applyFold(int playerIndex) {
        folded[playerIndex] = true;
        play(players.get(playerIndex),
                Component.translatable("message.charta.texas_holdem.folded"));
    }

    private void applyCallOrCheck(int playerIndex) {
        CardPlayer player = players.get(playerIndex);
        int owed = currentBet - roundBets[playerIndex];

        if (owed <= 0) {
            play(player, Component.translatable("message.charta.texas_holdem.checked"));
            return;
        }

        int paid = Math.min(owed, chips[playerIndex]);
        commitChips(playerIndex, paid);

        if (chips[playerIndex] == 0) {
            allIn[playerIndex] = true;
            play(player, Component.translatable("message.charta.texas_holdem.all_in",
                    roundBets[playerIndex]));
        } else {
            play(player, Component.translatable("message.charta.texas_holdem.called", paid));
        }
    }

    private void applyRaise(int playerIndex, int raiseBy) {
        int paid = Math.min(raiseBy, chips[playerIndex]);
        commitChips(playerIndex, paid);

        if (chips[playerIndex] == 0) {
            allIn[playerIndex] = true;
            play(players.get(playerIndex), Component.translatable("message.charta.texas_holdem.all_in",
                    roundBets[playerIndex]));
        } else {
            play(players.get(playerIndex), Component.translatable("message.charta.texas_holdem.raised",
                    roundBets[playerIndex]));
        }

        if (roundBets[playerIndex] > currentBet) currentBet = roundBets[playerIndex];
        rebuildPendingActorsAfterRaise(playerIndex);
        table(Component.translatable("message.charta.texas_holdem.pot", pot));
    }

    /** Moves amount chips from the player's stack into the pot, updating all trackers. */
    private void commitChips(int playerIndex, int amount) {
        chips[playerIndex]          -= amount;
        roundBets[playerIndex]       += amount;
        totalCommitted[playerIndex]  += amount;
        pot                          += amount;
    }

    // =========================================================================
    // Pending-actor list management
    // =========================================================================

    /**
     * Builds the ordered list of players who must act this round.
     * lastToActIdx is moved to the end of the queue if they are still active.
     */
    private void buildPendingActors(int startIdx, int lastToActIdx) {
        pendingActors.clear();
        int n = players.size();
        int idx = startIdx;
        do {
            if (!folded[idx] && !allIn[idx]) pendingActors.add(idx);
            idx = (idx + 1) % n;
        } while (idx != startIdx);

        if (pendingActors.remove(Integer.valueOf(lastToActIdx))) {
            pendingActors.addLast(lastToActIdx);
        }
    }

    /** After a raise, all non-folded, non-all-in players (except the raiser) act again. */
    private void rebuildPendingActorsAfterRaise(int raiserIndex) {
        pendingActors.clear();
        int n   = players.size();
        int idx = (raiserIndex + 1) % n;
        while (idx != raiserIndex) {
            if (!folded[idx] && !allIn[idx]) pendingActors.add(idx);
            idx = (idx + 1) % n;
        }
    }

    // =========================================================================
    // Phase progression
    // =========================================================================

    /** Resets round bets, then transitions to the next phase. */
    private void advancePhase() {
        Arrays.fill(roundBets, 0);
        currentBet = 0;

        switch (PokerPhase.fromOrdinal(phaseOrdinal)) {
            case PREFLOP -> transitionToFlop();
            case FLOP    -> transitionToTurn();
            case TURN    -> transitionToRiver();
            case RIVER   -> transitionToShowdown();
            default      -> onHandComplete();
        }
    }

    private void transitionToFlop() {
        phaseOrdinal = PokerPhase.FLOP.ordinal();
        dealCommunityCards(3);
        table(Component.translatable("message.charta.texas_holdem.flop"));
        startPostFlopBetting();
    }

    private void transitionToTurn() {
        phaseOrdinal = PokerPhase.TURN.ordinal();
        dealCommunityCards(1);
        table(Component.translatable("message.charta.texas_holdem.turn"));
        startPostFlopBetting();
    }

    private void transitionToRiver() {
        phaseOrdinal = PokerPhase.RIVER.ordinal();
        dealCommunityCards(1);
        table(Component.translatable("message.charta.texas_holdem.river"));
        startPostFlopBetting();
    }

    private void transitionToShowdown() {
        phaseOrdinal = PokerPhase.SHOWDOWN.ordinal();
        doShowdown();
    }

    /** Deals count cards face-up to consecutive community card slots. */
    private void dealCommunityCards(int count) {
        for (int i = 0; i < count && !drawPile.isEmpty() && communityCardCount < 5; i++) {
            Card card = drawPile.removeLast();
            if (card.flipped()) card.flip();
            communitySlots[communityCardCount].add(card);
            communityCardCount++;
        }
    }

    /** First active player left of dealer acts first in post-flop rounds. */
    private void startPostFlopBetting() {
        int startIdx = (dealerIndex + 1) % players.size();
        buildPendingActors(startIdx, dealerIndex);
        table(Component.translatable("message.charta.texas_holdem.pot", pot));
        runBettingRound();
    }

    // =========================================================================
    // Showdown
    // =========================================================================

    private void doShowdown() {
        table(Component.translatable("message.charta.texas_holdem.showdown"));

        List<Card>    community  = collectCommunityCards();
        List<Integer> contenders = activePlayerIndices();
        revealedAtShowdown.addAll(contenders);

        if (contenders.isEmpty()) {
            onHandComplete();
            return;
        }

        distributeSidePots(contenders, community);

        for (int i = 0; i < SHOWDOWN_DELAY_TICKS; i++) scheduledActions.add(() -> {});
        scheduledActions.add(this::onHandComplete);
        isGameReady = false;
    }

    /**
     * Distributes the pot using side-pot logic: a player can only win up to
     * their own contribution from each opponent.
     */
    private void distributeSidePots(List<Integer> contenders, List<Card> community) {
        List<Integer> sorted = new ArrayList<>(contenders);
        sorted.sort(Comparator.comparingInt(i -> totalCommitted[i]));

        List<Integer> eligible = new ArrayList<>(contenders);
        int distributed = 0;
        int prevCap     = 0;

        for (Integer capPlayerIdx : sorted) {
            int cap = totalCommitted[capPlayerIdx];
            if (cap <= prevCap) continue;

            int sidePot = computeSidePot(prevCap, cap);
            if (sidePot <= 0) { prevCap = cap; continue; }

            distributed += awardSidePot(sidePot, eligible, community);
            eligible.removeIf(idx -> totalCommitted[idx] <= cap);
            prevCap = cap;
        }

        // Safety net: if due to rounding any chips remain in pot, give to first contender
        int remainder = pot - distributed;
        if (remainder > 0 && !contenders.isEmpty()) {
            chips[contenders.get(0)] += remainder;
        }
        pot = 0;
    }

    private int computeSidePot(int prevCap, int cap) {
        int sidePot = 0;
        for (int i = 0; i < players.size(); i++) {
            sidePot += Math.min(Math.max(0, totalCommitted[i] - prevCap), cap - prevCap);
        }
        return sidePot;
    }

    private int awardSidePot(int sidePot, List<Integer> eligible, List<Card> community) {
        // Cache evaluations — evaluate each hand only ONCE to guarantee consistent comparison
        Map<Integer, Long> scores = new LinkedHashMap<>();
        for (Integer idx : eligible) {
            scores.put(idx, HandEvaluator.evaluate(allSevenCards(idx, community)));
        }

        long bestScore = scores.values().stream()
                .mapToLong(Long::longValue)
                .max().orElse(Long.MIN_VALUE);

        List<Integer> winners = eligible.stream()
                .filter(idx -> scores.get(idx) == bestScore)
                .toList();

        int share     = sidePot / winners.size();
        // Odd chip (e.g. pot=761, 2 winners → remainder=1): goes to the first winner
        // by standard poker rules — announced so the player knows about it
        int remainder = sidePot - share * winners.size();
        int distributed = 0;

        for (int i = 0; i < winners.size(); i++) {
            int winnerIdx  = winners.get(i);
            int actualGain = share + (i == 0 ? remainder : 0);
            chips[winnerIdx] += actualGain;
            distributed      += actualGain;
            announceWinner(winnerIdx, actualGain, community);
        }
        for (int idx : eligible) {
            if (!winners.contains(idx)) announceLoser(idx, community);
        }

        return distributed;
    }

    private void announceWinner(int playerIdx, int share, List<Card> community) {
        String    handName = HandEvaluator.getHandName(allSevenCards(playerIdx, community));
        Component handComp = Component.literal(handName).withStyle(ChatFormatting.AQUA);

        table(Component.translatable("message.charta.texas_holdem.showdown_result",
                players.get(playerIdx).getColoredName(),
                Component.literal(String.valueOf(share)).withStyle(ChatFormatting.GOLD),
                handComp));
        play(players.get(playerIdx), Component.translatable(
                "message.charta.texas_holdem.showdown_result_short",
                Component.literal("+" + share + "\u2666").withStyle(ChatFormatting.GOLD),
                handComp));
    }

    private void announceLoser(int playerIdx, List<Card> community) {
        String handName = HandEvaluator.getHandName(allSevenCards(playerIdx, community));
        play(players.get(playerIdx), Component.translatable(
                "message.charta.texas_holdem.hand_name",
                Component.literal(handName).withStyle(ChatFormatting.GRAY)));
    }

    /** Combines a player's hole cards with the given community cards. */
    private List<Card> allSevenCards(int playerIdx, List<Card> community) {
        List<Card> cards = new ArrayList<>(getPlayerHand(players.get(playerIdx)).stream().toList());
        cards.addAll(community);
        return cards;
    }

    private List<Card> collectCommunityCards() {
        List<Card> cards = new ArrayList<>();
        for (GameSlot slot : communitySlots) slot.stream().forEach(cards::add);
        return cards;
    }

    // =========================================================================
    // Last-player-wins (everyone else folded)
    // =========================================================================

    private void awardPotToLastPlayer() {
        int lastIdx = findLastActivePlayer();
        if (lastIdx < 0) { onHandComplete(); return; }

        int cap      = totalCommitted[lastIdx];
        int winnable = 0;
        int[] refunds = new int[players.size()];

        for (int i = 0; i < players.size(); i++) {
            int take   = Math.min(totalCommitted[i], cap);
            winnable  += take;
            refunds[i] = totalCommitted[i] - take;
        }

        chips[lastIdx] += winnable;
        play(players.get(lastIdx),
                Component.translatable("message.charta.texas_holdem.wins_pot", winnable));

        for (int i = 0; i < players.size(); i++) {
            if (refunds[i] > 0) {
                chips[i] += refunds[i];
                play(players.get(i),
                        Component.translatable("message.charta.texas_holdem.refunded", refunds[i]));
            }
        }

        pot = 0;
        onHandComplete();
    }

    private int findLastActivePlayer() {
        for (int i = 0; i < players.size(); i++) {
            if (!folded[i]) return i;
        }
        return -1;
    }

    // =========================================================================
    // Between-hands pause
    // =========================================================================

    private void onHandComplete() {
        int playersWithChips = countPlayersWithChips();
        if (playersWithChips > 1) {
            broadcastChipStandings();
            advanceDealer();
            enterPause();
        } else {
            endGame();
        }
    }

    private void enterPause() {
        isPaused     = true;
        skipVoteMask = 0;
        isGameReady  = false;

        final int[] ticksLeft = { PAUSE_DURATION_TICKS };
        Runnable pauseTick = new Runnable() {
            @Override public void run() {
                if (!isPaused) return;
                ticksLeft[0]--;
                if (ticksLeft[0] > 0) scheduledActions.addFirst(this);
                else                  startNextHandFromPause();
            }
        };
        scheduledActions.add(pauseTick);
    }

    private void startNextHandFromPause() {
        isPaused     = false;
        skipVoteMask = 0;
        dealNewHand();
    }

    /**
     * Registers a skip-pause vote for the given player.
     * If all active players have voted, the next hand starts immediately.
     */
    public void voteSkipPause(int playerIndex) {
        if (!isPaused || playerIndex < 0 || playerIndex >= players.size()) return;

        skipVoteMask |= (1 << playerIndex);

        int required = countPlayersWithChips();
        int votes    = 0;
        for (int i = 0; i < players.size(); i++) {
            if (chips[i] > 0 && (skipVoteMask & (1 << i)) != 0) votes++;
        }

        if (votes >= required) {
            scheduledActions.clear();
            startNextHandFromPause();
        }
    }

    private void broadcastChipStandings() {
        for (int i = 0; i < players.size(); i++) {
            if (chips[i] > 0) {
                play(players.get(i), Component.translatable(
                        "message.charta.texas_holdem.chips_remaining", chips[i]));
            }
        }
    }

    // =========================================================================
    // Session end
    // =========================================================================

    @Override
    public void endGame() {
        if (isGameOver) return;
        isGameOver = true;

        refundRemainingPot();
        pendingActors.clear();
        scheduledActions.clear();
        players.forEach(p -> p.play(null));

        announceSessionWinner();
    }

    private void refundRemainingPot() {
        if (pot <= 0) return;
        for (int i = 0; i < players.size(); i++) {
            if (!folded[i]) {
                chips[i] += pot;
                play(players.get(i),
                        Component.translatable("message.charta.texas_holdem.wins_pot", pot));
                pot = 0;
                return;
            }
        }
    }

    private void announceSessionWinner() {
        int winnerIdx = -1;
        int maxChips  = 0;
        for (int i = 0; i < players.size(); i++) {
            if (chips[i] > maxChips) { maxChips = chips[i]; winnerIdx = i; }
        }

        if (winnerIdx >= 0) {
            CardPlayer winner = players.get(winnerIdx);
            winner.sendTitle(
                    Component.translatable("message.charta.you_won").withStyle(ChatFormatting.GREEN),
                    Component.translatable("message.charta.congratulations"));
            for (CardPlayer p : players) {
                if (p != winner) {
                    p.sendTitle(
                            Component.translatable("message.charta.you_lost").withStyle(ChatFormatting.RED),
                            Component.translatable("message.charta.won_the_match", winner.getName()));
                }
            }
            table(Component.translatable("message.charta.won_the_match", winner.getColoredName()));
        } else {
            players.forEach(p -> p.sendTitle(
                    Component.translatable("message.charta.draw").withStyle(ChatFormatting.YELLOW),
                    Component.translatable("message.charta.no_winner")));
        }
    }

    // =========================================================================
    // Showdown reveal override
    // =========================================================================

    /** Exposes hole cards of non-folded players at showdown. */
    @Override
    public GameSlot getCensoredHand(CardPlayer viewer, CardPlayer player) {
        int idx = players.indexOf(player);
        if (revealedAtShowdown.contains(idx)) {
            return hands.getOrDefault(player, new GameSlot());
        }
        return super.getCensoredHand(viewer, player);
    }

    // =========================================================================
    // Utility helpers
    // =========================================================================

    /** Players not yet folded (includes all-in players still in the hand). */
    private long countActivePlayers() {
        long count = 0;
        for (boolean f : folded) if (!f) count++;
        return count;
    }

    /** Players who still have at least one chip (not eliminated). */
    private int countPlayersWithChips() {
        int count = 0;
        for (int c : chips) if (c > 0) count++;
        return count;
    }

    /** Indices of all non-folded players (contenders at showdown). */
    private List<Integer> activePlayerIndices() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            if (!folded[i]) result.add(i);
        }
        return result;
    }

    /**
     * Moves the dealer button to the next player who still has chips.
     * At most n iterations to guard against a fully-bankrupt table.
     */
    private void advanceDealer() {
        int n = players.size();
        for (int tries = 0; tries < n; tries++) {
            dealerIndex = (dealerIndex + 1) % n;
            if (chips[dealerIndex] > 0) return;
        }
    }

    // =========================================================================
    // Pre-flop hand-strength heuristic
    // =========================================================================

    /**
     * Returns a hand-strength estimate in [0.0, 1.0] based on hole card ranks,
     * suitedness, pairedness, and connectedness.
     */
    private static float evaluatePreflopStrength(List<Card> holeCards) {
        if (holeCards.size() < 2) return 0.3f;

        int r1 = HandEvaluator.rankValue(holeCards.get(0));
        int r2 = HandEvaluator.rankValue(holeCards.get(1));

        boolean suited    = holeCards.get(0).suit() == holeCards.get(1).suit();
        boolean paired    = holeCards.get(0).rank() == holeCards.get(1).rank();
        boolean connected = Math.abs(r1 - r2) == 1;

        float base = (r1 + r2) / 28f; // max = (14+14) = 28
        if (paired)    base += 0.20f;
        if (suited)    base += 0.10f;
        if (connected) base += 0.05f;

        return Math.min(base, 1.0f);
    }

    // =========================================================================
    // Backward-compatibility shim
    // =========================================================================

    /**
     * Kept so that TexasHoldemMenu and TexasHoldemScreen can still reference
     * {@code TexasHoldemGame.Phase} without modification.
     * All constants are delegates to PokerPhase.
     */
    public static final class Phase {

        private Phase() {}

        public static PokerPhase fromOrdinal(int ordinal) { return PokerPhase.fromOrdinal(ordinal); }

        public static final PokerPhase PREFLOP  = PokerPhase.PREFLOP;
        public static final PokerPhase FLOP     = PokerPhase.FLOP;
        public static final PokerPhase TURN     = PokerPhase.TURN;
        public static final PokerPhase RIVER    = PokerPhase.RIVER;
        public static final PokerPhase SHOWDOWN = PokerPhase.SHOWDOWN;
    }
}