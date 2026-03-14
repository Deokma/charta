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

public class TexasHoldemGame extends Game<TexasHoldemGame, TexasHoldemMenu> {

    // --- Action codes encoded in GamePlay.slot() ---
    public static final int ACTION_FOLD      = 100;
    public static final int ACTION_CALL      = 101;
    public static final int ACTION_RAISE_MIN = 102;
    public static final int ACTION_ALL_IN    = 103;

    // --- Game Options ---
    // GameOption.Number stores the value as a Java byte (-128..127).
    // ALL min/max/default values must stay within 0..127 to avoid overflow.
    // We use a 10× scale: displayed value = stored byte × 10 chips.
    //   stored 10  → 100 chips (min)
    //   stored 50  → 500 chips (default)
    //   stored 100 → 1000 chips (max)
    private final GameOption.Number STARTING_CHIPS_OPT = new GameOption.Number(
            50, 10, 100,
            Component.translatable("rule.charta.texas_holdem.starting_chips"),
            Component.translatable("rule.charta.texas_holdem.starting_chips.description"));

    // Big blind stored as direct chip value (2..20), all fit in a byte.
    private final GameOption.Number BIG_BLIND_OPT = new GameOption.Number(
            10, 2, 20,
            Component.translatable("rule.charta.texas_holdem.big_blind"),
            Component.translatable("rule.charta.texas_holdem.big_blind.description"));

    /** Returns the actual starting chip count (stored value × 10). */
    private int getStartingChips() {
        return STARTING_CHIPS_OPT.get() * 10;
    }

    /** Returns the big blind amount (stored directly). */
    private int getBigBlind() {
        return BIG_BLIND_OPT.get();
    }

    // --- Synced game state (public for TexasHoldemMenu ContainerData) ---
    public int[]     chips;      // chips per player index
    public int[]     roundBets;  // amount bet this round by each player index
    public boolean[] folded;     // folded[i] = true if player i has folded
    public boolean[] allIn;      // allIn[i] = true if player i is all-in
    public int  pot;
    public int  currentBet;      // the current highest bet in this round
    public int  dealerIndex;
    public int  phaseOrdinal;    // Phase ordinal, synced as int

    // --- Internal server-side state ---
    private final LinkedList<Integer> pendingActors = new LinkedList<>();
    private final Set<Integer> revealedPlayers = new HashSet<>();

    // --- Slots ---
    // slots 0-4: individual community card positions (board cards), face-up
    // slot  5:   draw pile (visible face-down pile on the left of the board)
    public static final int SLOT_COMMUNITY_FIRST = 0;
    public static final int SLOT_COMMUNITY_LAST  = 4;
    public static final int SLOT_DRAW_PILE       = 5;

    public final GameSlot[] communitySlots = new GameSlot[5];
    private final GameSlot drawPile;

    // How many community cards are currently visible (0-5)
    public int communityCardCount = 0;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------
    public TexasHoldemGame(List<CardPlayer> players, Deck deck) {
        super(players, deck);
        int n = Math.max(players.size(), 1);
        this.chips     = new int[n];
        this.roundBets = new int[n];
        this.folded    = new boolean[n];
        this.allIn     = new boolean[n];
        this.phaseOrdinal = Phase.PREFLOP.ordinal();
        this.dealerIndex  = 0;

        // 5 community card slots spread across the board.
        // TABLE_WIDTH=160, 5 cards×25 + 4 gaps×8 = 157 → startX = 1.5 (max spacing)
        float cardW  = CardImage.WIDTH;    // 25
        float cardH  = CardImage.HEIGHT;   // 35
        float gap    = 8f;
        float totalW = 5 * cardW + 4 * gap; // 157
        float startX = (CardTableBlockEntity.TABLE_WIDTH - totalW) / 2f; // 1.5
        float comY   = CardTableBlockEntity.TABLE_HEIGHT / 2f - cardH / 2f - 5f;

        for (int i = 0; i < 5; i++) {
            final float slotX = startX + i * (cardW + gap);
            communitySlots[i] = addSlot(new GameSlot(new LinkedList<>(), slotX, comY, 0f, 0f) {
                @Override public boolean canInsertCard(CardPlayer p, List<Card> c, int idx) { return false; }
                @Override public boolean canRemoveCard(CardPlayer p, int idx)               { return false; }
            });
        }

        // Draw pile: off-screen — the 3D pile isn't needed visually; server manages cards internally
        this.drawPile = addSlot(new GameSlot(new LinkedList<>(), -200f, -200f, 0f, 0f) {
            @Override public boolean canInsertCard(CardPlayer p, List<Card> c, int idx) { return false; }
            @Override public boolean canRemoveCard(CardPlayer p, int idx)               { return false; }
        });
    }

    // =========================================================================
    // Phase enum
    // =========================================================================
    public enum Phase {
        PREFLOP, FLOP, TURN, RIVER, SHOWDOWN;

        public static Phase fromOrdinal(int ord) {
            Phase[] values = values();
            return (ord >= 0 && ord < values.length) ? values[ord] : PREFLOP;
        }
    }

    // =========================================================================
    // Game interface implementation
    // =========================================================================

    /**
     * Hole cards are read-only — the player can hover to see them but never pick them up.
     * Removal is always blocked; the game manages hand contents directly.
     */
    @Override
    protected GameSlot createPlayerHand(CardPlayer player) {
        return new GameSlot(player.hand()) {
            @Override
            public boolean canRemoveCard(CardPlayer p, int index) {
                return false; // hole cards stay in hand — view-only
            }
            @Override
            public boolean canInsertCard(CardPlayer p, List<Card> cards, int index) {
                return false; // game places cards via dealCards(), not via UI drag
            }
            @Override
            public boolean removeAll() {
                return false;
            }
        };
    }

    @Override
    public ModMenuTypeRegistry.AdvancedMenuTypeEntry<TexasHoldemMenu, AbstractCardMenu.Definition> getMenuType() {
        return ModMenuTypes.TEXAS_HOLDEM;
    }

    @Override
    public TexasHoldemMenu createMenu(int containerId, Inventory playerInventory, AbstractCardMenu.Definition definition) {
        return new TexasHoldemMenu(containerId, playerInventory, definition);
    }

    @Override
    public Predicate<Deck> getDeckPredicate() {
        // Requires a standard 52-card deck
        return deck -> deck.getCards().size() >= 52
                && Suits.STANDARD.containsAll(deck.getSuits())
                && deck.getSuits().containsAll(Suits.STANDARD);
    }

    @Override
    public Predicate<Card> getCardPredicate() {
        return card -> Suits.STANDARD.contains(card.suit()) && Ranks.STANDARD.contains(card.rank());
    }

    @Override
    public List<GameOption<?>> getOptions() {
        return List.of(STARTING_CHIPS_OPT, BIG_BLIND_OPT);
    }

    @Override
    public int getMinPlayers() { return 2; }

    @Override
    public int getMaxPlayers() { return 8; }

    // =========================================================================
    // canPlay – used by the framework for bot/human action validation
    // =========================================================================
    @Override
    public boolean canPlay(CardPlayer player, GamePlay play) {
        if (!isGameReady || isGameOver) return false;
        if (player != currentPlayer) return false;

        int action = play.slot();
        int idx = players.indexOf(player);
        if (idx < 0) return false;

        return switch (action) {
            case ACTION_FOLD      -> !folded[idx];
            case ACTION_CALL      -> !folded[idx] && !allIn[idx];
            case ACTION_RAISE_MIN -> !folded[idx] && !allIn[idx] && chips[idx] > 0;
            case ACTION_ALL_IN    -> !folded[idx] && !allIn[idx] && chips[idx] > 0;
            default               -> false;
        };
    }

    // =========================================================================
    // getBestPlay – used by AutoPlayer bots
    // =========================================================================
    @Override
    public GamePlay getBestPlay(CardPlayer player) {
        int idx = players.indexOf(player);
        if (idx < 0 || folded[idx] || allIn[idx]) return null;

        Phase phase = Phase.fromOrdinal(phaseOrdinal);
        List<Card> hand = getPlayerHand(player).stream().toList();

        // Evaluate hand strength (0.0 – 1.0)
        float strength;
        if (phase == Phase.PREFLOP) {
            strength = evaluatePreflopStrength(hand);
        } else {
            List<Card> all7 = new ArrayList<>(hand);
            for (GameSlot s : communitySlots) s.stream().forEach(all7::add);
            strength = (float) HandEvaluator.evaluate(all7) / HandEvaluator.MAX_SCORE;
        }

        int callAmount = currentBet - roundBets[idx];

        // Decision logic based on hand strength
        if (strength < 0.25f) {
            // Weak hand: fold if there's a bet to call, otherwise check
            return callAmount > 0
                    ? new GamePlay(List.of(), ACTION_FOLD)
                    : new GamePlay(List.of(), ACTION_CALL);
        } else if (strength < 0.55f) {
            // Medium hand: call
            return new GamePlay(List.of(), ACTION_CALL);
        } else if (strength < 0.80f) {
            // Good hand: raise min
            return chips[idx] >= getMinRaise()
                    ? new GamePlay(List.of(), ACTION_RAISE_MIN)
                    : new GamePlay(List.of(), ACTION_CALL);
        } else {
            // Great hand: go all-in
            return new GamePlay(List.of(), ACTION_ALL_IN);
        }
    }

    // =========================================================================
    // Game lifecycle
    // =========================================================================

    @Override
    public void startGame() {
        int n = players.size();

        // Initialise chips from option
        int startChips = getStartingChips();
        for (int i = 0; i < n; i++) chips[i] = startChips;

        resetRound();
        isGameReady = false;
        isGameOver  = false;
        table(Component.translatable("message.charta.game_started"));
        dealNewHand();
    }

    /** Prepares and deals a fresh hand. */
    private void dealNewHand() {
        int n = players.size();

        // Clear state
        Arrays.fill(roundBets, 0);
        Arrays.fill(folded,    false);
        Arrays.fill(allIn,     false);
        pot        = 0;
        currentBet = 0;
        revealedPlayers.clear();
        pendingActors.clear();
        phaseOrdinal = Phase.PREFLOP.ordinal();

        // Clear cards
        communityCardCount = 0;
        for (GameSlot s : communitySlots) s.clear();
        drawPile.clear();
        for (CardPlayer p : players) {
            getPlayerHand(p).clear();
            getCensoredHand(p).clear();
        }

        // Build & shuffle draw pile — copy gameDeck cards fresh every hand so
        // mutations from previous hands (flip calls) don't carry over.
        // Cards must start face-DOWN (flipped=true) for dealCards to flip them face-up.
        for (Card template : gameDeck) {
            Card copy = template.copy();
            // Ensure face-down regardless of whatever state template is in
            if (!copy.flipped()) copy.flip();
            drawPile.add(copy);
        }
        drawPile.shuffle();

        // Schedule card dealing with small animation delays
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < n; i++) {
                final int pi = i;
                scheduledActions.add(() -> {
                    CardPlayer p = players.get(pi);
                    p.playSound(ModSounds.CARD_DRAW.get());
                    dealCards(drawPile, p, 1);
                });
                scheduledActions.add(() -> {}); // delay tick
            }
        }

        // After dealing, post blinds and start betting
        scheduledActions.add(this::postBlindsAndStart);

        isGameReady = false;
        isGameOver  = false;
    }

    /** Posts small & big blinds, then sets up preflop state.
     *  The actual betting round is started by {@link #runGame()} once
     *  all scheduled actions have been processed and {@code isGameReady} is set. */
    private void postBlindsAndStart() {
        int n = players.size();
        int bigBlind   = getBigBlind();
        int smallBlind = bigBlind / 2;

        int sbIdx = (dealerIndex + 1) % n;
        int bbIdx = (dealerIndex + 2) % n;

        postBlind(sbIdx, smallBlind);
        postBlind(bbIdx, bigBlind);

        currentBet = bigBlind;

        // Preflop: action starts to the left of the big blind; BB acts last
        int startIdx = (bbIdx + 1) % n;
        buildPendingActors(startIdx, bbIdx);

        table(Component.translatable("message.charta.texas_holdem.preflop"));
        table(Component.translatable("message.charta.texas_holdem.pot", pot));
        // runBettingRound() will be called by runGame() once isGameReady = true
    }

    private void postBlind(int playerIdx, int amount) {
        CardPlayer p = players.get(playerIdx);
        int actual = Math.min(amount, chips[playerIdx]);
        chips[playerIdx] -= actual;
        roundBets[playerIdx] += actual;
        pot += actual;
        if (chips[playerIdx] == 0) allIn[playerIdx] = true;
        play(p, Component.translatable("message.charta.texas_holdem.posted_blind", actual));
    }

    // =========================================================================
    // Betting round management
    // =========================================================================

    @Override
    public void runGame() {
        // runGame is called once isGameReady becomes true; we use runBettingRound for flow
        // This entry point is only reached at the very start after dealing
        if (!isGameReady) return;
        runBettingRound();
    }

    /**
     * Processes actions for the next player in pendingActors.
     * Recurses (or schedules) until the betting round ends.
     */
    private void runBettingRound() {
        if (isGameOver) return;

        // Check if only one player remains (all others folded)
        long activePlayers = countActivePlayers();
        if (activePlayers <= 1) {
            // Award pot to the last standing player, then deal a new hand or end
            awardPotToLastPlayer();
            return;
        }

        // If no pending actors, advance to next phase
        if (pendingActors.isEmpty()) {
            advancePhase();
            return;
        }

        // Find next pending actor who isn't folded or all-in
        Integer nextIdx = null;
        while (!pendingActors.isEmpty()) {
            Integer candidate = pendingActors.peek();
            if (!folded[candidate] && !allIn[candidate]) {
                nextIdx = pendingActors.poll();
                break;
            }
            pendingActors.poll(); // skip this player
        }

        if (nextIdx == null) {
            // All remaining actors are all-in or folded
            advancePhase();
            return;
        }

        final int actorIdx = nextIdx;
        currentPlayer = players.get(actorIdx);
        currentPlayer.resetPlay();

        table(Component.translatable("message.charta.its_player_turn", currentPlayer.getColoredName()));

        currentPlayer.afterPlay(play -> {
            if (play == null) {
                // Shouldn't happen, but treat as fold
                handleAction(actorIdx, ACTION_FOLD);
            } else {
                handleAction(actorIdx, play.slot());
            }
            runBettingRound();
        });
    }

    /** Processes a betting action for the player at playerIdx. */
    public void handleAction(int playerIdx, int action) {
        if (playerIdx < 0 || playerIdx >= players.size()) return;
        CardPlayer player = players.get(playerIdx);

        switch (action) {
            case ACTION_FOLD -> {
                folded[playerIdx] = true;
                play(player, Component.translatable("message.charta.texas_holdem.folded"));
            }
            case ACTION_CALL -> {
                int owed = currentBet - roundBets[playerIdx];
                if (owed <= 0) {
                    // Check
                    play(player, Component.translatable("message.charta.texas_holdem.checked"));
                } else {
                    int paid = Math.min(owed, chips[playerIdx]);
                    chips[playerIdx]     -= paid;
                    roundBets[playerIdx] += paid;
                    pot                  += paid;
                    if (chips[playerIdx] == 0) {
                        allIn[playerIdx] = true;
                        play(player, Component.translatable("message.charta.texas_holdem.all_in", roundBets[playerIdx]));
                    } else {
                        play(player, Component.translatable("message.charta.texas_holdem.called", paid));
                    }
                }
            }
            case ACTION_RAISE_MIN -> {
                int raiseBy = getMinRaise();
                doRaise(playerIdx, player, raiseBy);
            }
            case ACTION_ALL_IN -> {
                doRaise(playerIdx, player, chips[playerIdx]);
            }
            default -> {
                // Unknown action: treat as fold
                folded[playerIdx] = true;
            }
        }
    }

    private void doRaise(int playerIdx, CardPlayer player, int raiseBy) {
        int newBet = roundBets[playerIdx] + raiseBy;
        int paid   = Math.min(raiseBy, chips[playerIdx]);
        chips[playerIdx]     -= paid;
        roundBets[playerIdx] += paid;
        pot                  += paid;

        if (chips[playerIdx] == 0) {
            allIn[playerIdx] = true;
            play(player, Component.translatable("message.charta.texas_holdem.all_in", roundBets[playerIdx]));
        } else {
            play(player, Component.translatable("message.charta.texas_holdem.raised", roundBets[playerIdx]));
        }

        // Update current bet if this player's total bet exceeds it
        if (roundBets[playerIdx] > currentBet) {
            currentBet = roundBets[playerIdx];
        }

        // Everyone else (not folded, not all-in, not this player) must act again
        rebuildPendingActorsAfterRaise(playerIdx);
        table(Component.translatable("message.charta.texas_holdem.pot", pot));
    }

    private int getMinRaise() {
        int bigBlind = getBigBlind();
        return Math.max(bigBlind, currentBet); // at minimum raise by one big blind
    }

    /** Builds the list of players who must act, starting from startIdx, with lastToActIdx acting last. */
    private void buildPendingActors(int startIdx, int lastToActIdx) {
        pendingActors.clear();
        int n = players.size();
        int idx = startIdx;
        do {
            if (!folded[idx] && !allIn[idx]) {
                pendingActors.add(idx);
            }
            idx = (idx + 1) % n;
        } while (idx != startIdx);

        // Ensure lastToActIdx is at the end if still pending
        if (pendingActors.remove(Integer.valueOf(lastToActIdx))) {
            pendingActors.addLast(lastToActIdx);
        }
    }

    /** After a raise, all non-folded, non-allIn players except the raiser must act again. */
    private void rebuildPendingActorsAfterRaise(int raiserIdx) {
        pendingActors.clear();
        int n = players.size();
        int idx = (raiserIdx + 1) % n;
        while (idx != raiserIdx) {
            if (!folded[idx] && !allIn[idx]) {
                pendingActors.add(idx);
            }
            idx = (idx + 1) % n;
        }
    }

    // =========================================================================
    // Phase progression
    // =========================================================================

    private void advancePhase() {
        Phase current = Phase.fromOrdinal(phaseOrdinal);

        // Collect bets into pot (round bets reset for next phase)
        Arrays.fill(roundBets, 0);
        currentBet = 0;

        switch (current) {
            case PREFLOP -> {
                phaseOrdinal = Phase.FLOP.ordinal();
                dealCommunityCards(3);
                table(Component.translatable("message.charta.texas_holdem.flop"));
                startPostFlopBetting();
            }
            case FLOP -> {
                phaseOrdinal = Phase.TURN.ordinal();
                dealCommunityCards(1);
                table(Component.translatable("message.charta.texas_holdem.turn"));
                startPostFlopBetting();
            }
            case TURN -> {
                phaseOrdinal = Phase.RIVER.ordinal();
                dealCommunityCards(1);
                table(Component.translatable("message.charta.texas_holdem.river"));
                startPostFlopBetting();
            }
            case RIVER -> {
                phaseOrdinal = Phase.SHOWDOWN.ordinal();
                doShowdown();
            }
            default -> onHandComplete();
        }
    }

    private void dealCommunityCards(int count) {
        for (int i = 0; i < count; i++) {
            if (drawPile.isEmpty() || communityCardCount >= 5) break;
            Card c = drawPile.removeLast();
            // Force face-up: community cards are always visible
            if (c.flipped()) c.flip();
            communitySlots[communityCardCount].add(c);
            communityCardCount++;
        }
    }

    /** Sets up betting order for post-flop rounds (first active player left of dealer). */
    private void startPostFlopBetting() {
        int n = players.size();
        int startIdx = (dealerIndex + 1) % n;
        // The last to act is the dealer (or next active to dealer's right)
        buildPendingActors(startIdx, dealerIndex);
        table(Component.translatable("message.charta.texas_holdem.pot", pot));
        runBettingRound();
    }

    // =========================================================================
    // Showdown & winner determination
    // =========================================================================

    private void doShowdown() {
        phaseOrdinal = Phase.SHOWDOWN.ordinal();
        table(Component.translatable("message.charta.texas_holdem.showdown"));

        List<Card> community = new ArrayList<>();
        for (GameSlot s : communitySlots) s.stream().forEach(community::add);

        List<Integer> contenders = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            if (!folded[i]) contenders.add(i);
        }

        // Reveal contender hands
        revealedPlayers.addAll(contenders);

        if (contenders.isEmpty()) {
            onHandComplete();
            return;
        }

        // Find best hand score
        long bestScore = Long.MIN_VALUE;
        for (int idx : contenders) {
            List<Card> allCards = new ArrayList<>(getPlayerHand(players.get(idx)).stream().toList());
            allCards.addAll(community);
            long score = HandEvaluator.evaluate(allCards);
            if (score > bestScore) bestScore = score;
        }

        final long winningScore = bestScore;
        List<Integer> winners = new ArrayList<>();
        for (int idx : contenders) {
            List<Card> allCards = new ArrayList<>(getPlayerHand(players.get(idx)).stream().toList());
            allCards.addAll(community);
            if (HandEvaluator.evaluate(allCards) == winningScore) winners.add(idx);
        }

        // Distribute pot
        int share = pot / winners.size();
        for (int winnerIdx : winners) {
            chips[winnerIdx] += share;
            play(players.get(winnerIdx),
                    Component.translatable("message.charta.texas_holdem.wins_pot", share));
        }
        pot = 0;

        // Announce winning hand name
        for (int winnerIdx : winners) {
            List<Card> allCards = new ArrayList<>(getPlayerHand(players.get(winnerIdx)).stream().toList());
            allCards.addAll(community);
            String handName = HandEvaluator.getHandName(allCards);
            play(players.get(winnerIdx),
                    Component.translatable("message.charta.texas_holdem.hand_name", handName));
        }

        onHandComplete();
    }

    /** Called when all but one player folds mid-round. */
    private void awardPotToLastPlayer() {
        for (int i = 0; i < players.size(); i++) {
            if (!folded[i]) {
                chips[i] += pot;
                play(players.get(i), Component.translatable("message.charta.texas_holdem.wins_pot", pot));
                pot = 0;
                break;
            }
        }
        onHandComplete();
    }

    // =========================================================================
    // Hand-completion routing
    // =========================================================================

    /**
     * Called after each hand resolves (showdown or last-player-wins).
     * Decides whether to start a new hand or end the session.
     * Unlike {@link #endGame()}, this method may schedule another hand.
     */
    private void onHandComplete() {
        long playersWithChips = 0;
        for (int c : chips) if (c > 0) playersWithChips++;

        if (playersWithChips > 1) {
            // Multiple players still have chips → show standings, deal new hand
            for (int i = 0; i < players.size(); i++) {
                if (chips[i] > 0) {
                    play(players.get(i),
                            Component.translatable("message.charta.texas_holdem.chips_remaining", chips[i]));
                }
            }
            advanceDealer();
            scheduledActions.add(this::dealNewHand);
            isGameReady = false;
        } else {
            // Session over
            endGame();
        }
    }

    /**
     * Immediately terminates the game session.
     * Announces the winner and sets {@code isGameOver = true}.
     * Called by the card table when a player leaves, or by {@link #onHandComplete()} at session end.
     */
    @Override
    public void endGame() {
        if (isGameOver) return;
        isGameOver = true;  // set FIRST — prevents re-entrant callbacks from looping

        // Award any unclaimed pot to the last non-folded player
        if (pot > 0) {
            for (int i = 0; i < players.size(); i++) {
                if (!folded[i]) {
                    chips[i] += pot;
                    play(players.get(i),
                            Component.translatable("message.charta.texas_holdem.wins_pot", pot));
                    pot = 0;
                    break;
                }
            }
        }

        // Unblock any pending CompletableFuture so the server thread doesn't hang.
        // isGameOver is already true so their afterPlay callbacks will see it and return early.
        pendingActors.clear();
        scheduledActions.clear();
        for (CardPlayer p : players) p.play(null);

        // Find session winner (highest chip count)
        int sessionWinnerIdx = -1;
        int maxChips = 0;
        int playersWithChips = 0;
        for (int i = 0; i < players.size(); i++) {
            if (chips[i] > 0) {
                playersWithChips++;
                if (chips[i] > maxChips) {
                    maxChips = chips[i];
                    sessionWinnerIdx = i;
                }
            }
        }

        if (playersWithChips == 1 && sessionWinnerIdx >= 0) {
            CardPlayer winner = players.get(sessionWinnerIdx);
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
    // Helpers
    // =========================================================================

    private void resetRound() {
        Arrays.fill(roundBets, 0);
        Arrays.fill(folded,    false);
        Arrays.fill(allIn,     false);
        pot        = 0;
        currentBet = 0;
    }

    private void advanceDealer() {
        int n = players.size();
        dealerIndex = (dealerIndex + 1) % n;
        // Skip players who have busted (chips == 0)
        int tries = 0;
        while (chips[dealerIndex] == 0 && tries < n) {
            dealerIndex = (dealerIndex + 1) % n;
            tries++;
        }
    }

    private long countActivePlayers() {
        long count = 0;
        for (int i = 0; i < players.size(); i++) {
            if (!folded[i]) count++;
        }
        return count;
    }

    /** Preflop hand strength: 0.0–1.0 based on hole card ranks. */
    private float evaluatePreflopStrength(List<Card> hand) {
        if (hand.size() < 2) return 0.3f;

        int r1 = getRankValue(hand.get(0));
        int r2 = getRankValue(hand.get(1));
        boolean suited  = hand.get(0).suit() == hand.get(1).suit();
        boolean paired  = hand.get(0).rank() == hand.get(1).rank();
        boolean connected = Math.abs(r1 - r2) == 1;

        float base = (r1 + r2) / 28f; // normalised by max (14+14=28)
        if (paired)    base += 0.20f;
        if (suited)    base += 0.10f;
        if (connected) base += 0.05f;

        return Math.min(base, 1.0f);
    }

    /** Returns the Ace-high rank value (Ace = 14). */
    private static int getRankValue(Card card) {
        Rank rank = card.rank();
        if (rank == Ranks.ACE) return 14;
        return rank.ordinal(); // TWO=2 … KING=13
    }

    // =========================================================================
    // getCensoredHand override – reveal cards at showdown
    // =========================================================================
    @Override
    public GameSlot getCensoredHand(CardPlayer viewer, CardPlayer player) {
        int playerIdx = players.indexOf(player);
        // At showdown, reveal non-folded players' hands
        if (revealedPlayers.contains(playerIdx)) {
            return hands.getOrDefault(player, new GameSlot());
        }
        return super.getCensoredHand(viewer, player);
    }

    // =========================================================================
    // Inner class: Hand Evaluator
    // =========================================================================

    /**
     * Evaluates the best 5-card poker hand from a set of 2-7 cards.
     * Returns a score where a higher value beats a lower value.
     */
    public static final class HandEvaluator {

        /** Maximum possible score (Royal Flush: 8 * BASE + max kicker). */
        public static final long MAX_SCORE = 9L * 100_000_000L;

        private static final long BASE = 100_000_000L;

        private HandEvaluator() {}

        /**
         * Returns the best 5-card hand score from up to 7 cards.
         * Higher score = better hand.
         */
        public static long evaluate(List<Card> cards) {
            int n = cards.size();
            if (n < 5) return 0L;

            long best = Long.MIN_VALUE;
            // Generate all C(n, 5) combinations
            for (int i = 0; i < n - 4; i++)
                for (int j = i + 1; j < n - 3; j++)
                    for (int k = j + 1; k < n - 2; k++)
                        for (int l = k + 1; l < n - 1; l++)
                            for (int m = l + 1; m < n; m++) {
                                long score = evaluate5(
                                        cards.get(i), cards.get(j), cards.get(k),
                                        cards.get(l), cards.get(m));
                                if (score > best) best = score;
                            }
            return best;
        }

        /** Returns the human-readable name of the best hand. */
        public static String getHandName(List<Card> cards) {
            int n = cards.size();
            if (n < 5) return "High Card";
            long best = Long.MIN_VALUE;
            int[] bestRanks = null;
            boolean[] bestFlush = null;

            for (int i = 0; i < n - 4; i++)
                for (int j = i + 1; j < n - 3; j++)
                    for (int k = j + 1; k < n - 2; k++)
                        for (int l = k + 1; l < n - 1; l++)
                            for (int m = l + 1; m < n; m++) {
                                Card[] five = {cards.get(i), cards.get(j), cards.get(k), cards.get(l), cards.get(m)};
                                long score = evaluate5(five[0], five[1], five[2], five[3], five[4]);
                                if (score > best) {
                                    best      = score;
                                    bestRanks = new int[]{
                                            rankVal(five[0]), rankVal(five[1]), rankVal(five[2]),
                                            rankVal(five[3]), rankVal(five[4])};
                                    bestFlush = new boolean[]{
                                            five[0].suit() == five[1].suit() && five[1].suit() == five[2].suit()
                                                    && five[2].suit() == five[3].suit() && five[3].suit() == five[4].suit()};
                                }
                            }

            if (bestRanks == null) return "High Card";
            long category = best / BASE;
            return switch ((int) category) {
                case 8 -> {
                    Arrays.sort(bestRanks);
                    yield bestRanks[4] == 14 ? "Royal Flush" : "Straight Flush";
                }
                case 7 -> "Four of a Kind";
                case 6 -> "Full House";
                case 5 -> "Flush";
                case 4 -> "Straight";
                case 3 -> "Three of a Kind";
                case 2 -> "Two Pair";
                case 1 -> "One Pair";
                default -> "High Card";
            };
        }

        private static long evaluate5(Card c1, Card c2, Card c3, Card c4, Card c5) {
            int[] r = {rankVal(c1), rankVal(c2), rankVal(c3), rankVal(c4), rankVal(c5)};
            boolean flush = c1.suit() == c2.suit() && c2.suit() == c3.suit()
                    && c3.suit() == c4.suit() && c4.suit() == c5.suit();

            Arrays.sort(r);
            boolean straight = isStraight(r);

            // Count rank occurrences
            Map<Integer, Integer> freq = new HashMap<>();
            for (int rank : r) freq.merge(rank, 1, Integer::sum);

            // Group by count
            List<Integer> pairs     = new ArrayList<>();
            List<Integer> triples   = new ArrayList<>();
            List<Integer> quads     = new ArrayList<>();

            for (Map.Entry<Integer, Integer> e : freq.entrySet()) {
                switch (e.getValue()) {
                    case 2 -> pairs.add(e.getKey());
                    case 3 -> triples.add(e.getKey());
                    case 4 -> quads.add(e.getKey());
                }
            }

            // Determine hand category and encode score
            if (straight && flush)  return 8 * BASE + kicker(r, 5);
            if (!quads.isEmpty())   return 7 * BASE + kicker(new int[]{quads.get(0)}, 1);
            if (!triples.isEmpty() && !pairs.isEmpty())
                return 6 * BASE + kicker(new int[]{triples.get(0), pairs.get(0)}, 2);
            if (flush)              return 5 * BASE + kicker(r, 5);
            if (straight)          return 4 * BASE + kicker(r, 5);
            if (!triples.isEmpty()) return 3 * BASE + kicker(new int[]{triples.get(0)}, 1);
            if (pairs.size() >= 2) {
                pairs.sort(Comparator.reverseOrder());
                return 2 * BASE + kicker(new int[]{pairs.get(0), pairs.get(1)}, 2);
            }
            if (pairs.size() == 1)  return 1 * BASE + kicker(new int[]{pairs.get(0)}, 1)
                    + highCards(r, pairs.get(0), 3);
            return kicker(r, 5); // High card
        }

        private static boolean isStraight(int[] sorted) {
            // Normal straight
            boolean normal = sorted[4] - sorted[0] == 4
                    && sorted[1] == sorted[0] + 1
                    && sorted[2] == sorted[1] + 1
                    && sorted[3] == sorted[2] + 1;
            // Wheel: A-2-3-4-5
            boolean wheel = sorted[4] == 14
                    && sorted[0] == 2 && sorted[1] == 3 && sorted[2] == 4 && sorted[3] == 5;
            return normal || wheel;
        }

        /** Encodes the top N ranks as a long kicker value (big-endian, each rank in 2 digits). */
        private static long kicker(int[] ranks, int count) {
            // Sort descending
            int[] sorted = ranks.clone();
            Arrays.sort(sorted);
            long result = 0;
            int taken = 0;
            for (int i = sorted.length - 1; i >= 0 && taken < count; i--) {
                result = result * 100L + sorted[i];
                taken++;
            }
            return result;
        }

        /** Encodes high-card kickers excluding the excluded rank, up to count cards. */
        private static long highCards(int[] sorted, int exclude, int count) {
            long result = 0;
            int taken = 0;
            for (int i = sorted.length - 1; i >= 0 && taken < count; i--) {
                if (sorted[i] != exclude) {
                    result = result * 100L + sorted[i];
                    taken++;
                }
            }
            return result;
        }

        private static int rankVal(Card card) {
            Rank rank = card.rank();
            if (rank == Ranks.ACE) return 14;
            return rank.ordinal();
        }
    }
}