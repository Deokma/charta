package dev.lucaargolo.charta.common.game.impl.tilekingdoms;

import dev.lucaargolo.charta.common.game.api.CardPlayer;
import dev.lucaargolo.charta.common.game.api.GamePlay;
import dev.lucaargolo.charta.common.game.api.card.Deck;
import dev.lucaargolo.charta.common.game.api.game.GameOption;
import dev.lucaargolo.charta.common.network.TileKingdomsActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.*;

public class TileKingdomsGame extends TileKingdomsGameBase {

    public static final int FOLLOWERS_PER_PLAYER = 7;
    public static final int SLOT_N = 0, SLOT_E = 1, SLOT_S = 2, SLOT_W = 3, SLOT_CENTER = 4;
    public static final int PHASE_PLACE = 0, PHASE_CLAIM = 1;

    public final TileKingdomsBoard board = new TileKingdomsBoard();
    private final List<TileType> tileDeck = new ArrayList<>();

    public int[] scores;
    public int[] followersLeft;
    public TileType currentTileType;
    public int currentRotation;
    public int phase = PHASE_PLACE;
    public int lastPlacedX, lastPlacedY;

    public final Map<Long, Integer> claims = new LinkedHashMap<>();
    public int winnerIdx = -1;
    public short[] boardSnapshot;
    public int[] claimsSnapshot = new int[0];
    public boolean boardDirty = true;
    /**
     * Synced to clients via ContainerData. On server = tileDeck.size(). On client = synced value.
     */
    public int clientRemainingTiles = 0;

    public int getRemainingTiles() {
        int n = tileDeck.size();
        clientRemainingTiles = n;
        return n;
    }

    public int getWinnerIdx() {
        return winnerIdx;
    }

    public void setGameReady(boolean v) {
        isGameReady = v;
    }

    public TileKingdomsGame(List<CardPlayer> players, Deck deck) {
        super(players, deck);
        scores = new int[players.size()];
        followersLeft = new int[players.size()];
        Arrays.fill(followersLeft, FOLLOWERS_PER_PLAYER);
    }

    @Override
    public int getMinPlayers() {
        return 2;
    }

    @Override
    public int getMaxPlayers() {
        return 5;
    }

    @Override
    public List<GameOption<?>> getOptions() {
        return List.of();
    }

    @Override
    public void startGame() {
        isGameReady = false;
        for (TileType t : TileType.values())
            for (int i = 0; i < t.count; i++) tileDeck.add(t);
        Collections.shuffle(tileDeck);
        //tileDeck.remove(TileType.START);
        TileType[] values = TileType.values();
        TileType randomTile;
        do {
            randomTile = values[new Random().nextInt(values.length)];
        } while (randomTile != TileType.CITY_FULL);

        board.placeForced(0, 0, randomTile, 0);
        markBoardDirty();
        scores = new int[players.size()];
        followersLeft = new int[players.size()];
        Arrays.fill(followersLeft, FOLLOWERS_PER_PLAYER);
        currentPlayer = players.get(0);
        scheduledActions.add(() -> table(Component.literal("Tile Kingdoms! " + tileDeck.size() + " tiles.").withStyle(ChatFormatting.GOLD)));
    }

    @Override
    public void runGame() {
        if (!isGameReady) return;
        if (tileDeck.isEmpty()) {
            endGame();
            return;
        }
        if (currentTileType != null || phase == PHASE_CLAIM) return;
        drawAndWaitForPlace();
    }

    private void drawAndWaitForPlace() {
        currentTileType = tileDeck.remove(0);
        currentRotation = 0;
        phase = PHASE_PLACE;
        if (!board.hasAnyValidPosition(currentTileType)) {
            table(Component.literal("No valid spot — tile discarded.").withStyle(ChatFormatting.GRAY));
            currentTileType = null;
            advancePlayer();
            return;
        }
        table(Component.translatable("message.charta.its_player_turn", currentPlayer.getColoredName()));
        currentPlayer.resetPlay();
        currentPlayer.afterPlay(play -> {
            if (play != null && play.slot() != TileKingdomsActionPayload.SKIP_CLAIM)
                handlePlace(play.slot());
            else {
                table(Component.literal(currentPlayer.getName().getString() + " discarded tile.").withStyle(ChatFormatting.GRAY));
                currentTileType = null;
                advancePlayer();
            }
        });
    }

    private void handlePlace(int packed) {
        int[] c = unpackAction(packed);
        int lx = c[0], ly = c[1], rot = c[2];
        if (currentTileType == null) return;
        if (!board.canPlace(lx, ly, currentTileType, rot)) {
            table(Component.literal("Invalid spot!").withStyle(ChatFormatting.RED));
            currentPlayer.resetPlay();
            currentPlayer.afterPlay(play2 -> {
                if (play2 != null && play2.slot() != TileKingdomsActionPayload.SKIP_CLAIM) handlePlace(play2.slot());
                else {
                    currentTileType = null;
                    advancePlayer();
                }
            });
            return;
        }
        board.place(lx, ly, currentTileType, rot);
        lastPlacedX = lx;
        lastPlacedY = ly;
        markBoardDirty();
        // Score and return followers from completed features
        board.scoreAndReturnFollowers(lx, ly, claims, players.size(), scores, followersLeft);
        refreshClaimsSnapshot();
        currentTileType = null;
        // Phase 2: optionally claim
        int pidx = players.indexOf(currentPlayer);
        if (followersLeft[pidx] > 0 && hasFreeFeature(lx, ly)) {
            phase = PHASE_CLAIM;
            boardDirty = true;
            currentPlayer.resetPlay();
            currentPlayer.afterPlay(play -> {
                phase = PHASE_PLACE;
                if (play != null && play.slot() != TileKingdomsActionPayload.SKIP_CLAIM && isClaimAction(play.slot()))
                    handleClaim(lx, ly, unpackClaimSlot(play.slot()), pidx);
                // Always advance regardless (skip or claim)
                advancePlayer();
            });
        } else {
            advancePlayer();
        }
    }

    private void handleClaim(int lx, int ly, int featureSlot, int pidx) {
        short tile = board.get(lx, ly);
        if (PlacedTile.isEmpty(tile)) return;
        TileType type = PlacedTile.typeOf(tile);
        if (featureSlot == SLOT_CENTER) {
            if (type == null || !type.monastery) return;
        } else {
            TileType.Edge e = PlacedTile.edgeOf(tile, featureSlot);
            if (e == TileType.Edge.FIELD) return;
        }
        if (isFeatureOwned(lx, ly, featureSlot, tile)) {
            table(Component.literal("Already claimed!").withStyle(ChatFormatting.RED));
            return;
        }
        claims.put(packPos(lx, ly, featureSlot), pidx);
        followersLeft[pidx]--;
        refreshClaimsSnapshot();
        boardDirty = true;
        play(currentPlayer, Component.literal("Placed follower.").withStyle(ChatFormatting.AQUA));
    }

    private boolean isFeatureOwned(int lx, int ly, int slot, short tile) {
        if (slot == SLOT_CENTER) return claims.containsKey(packPos(lx, ly, SLOT_CENTER));
        Set<Long> region = board.getRegion(lx, ly, slot);
        for (Long pos : region) {
            int rlx = (int) ((pos >> 8) & 0xFF) - 64, rly = (int) ((pos >> 16) & 0xFF) - 64, rslot = (int) (pos & 0xFF);
            if (claims.containsKey(packPos(rlx, rly, rslot))) return true;
        }
        return false;
    }

    /**
     * Returns true if the tile at (lx, ly) has at least one slot that is:
     *   • a non-Field edge (Road or City), or a monastery centre
     *   • NOT already owned by any player (region BFS)
     *   • NOT already completed (closed feature)
     *
     * Uncompleted monastery counts as free if unclaimed and not surrounded.
     */
    private boolean hasFreeFeature(int lx, int ly) {
        short tile = board.get(lx, ly);
        if (PlacedTile.isEmpty(tile)) return false;
        TileType type = PlacedTile.typeOf(tile);
        for (int dir = 0; dir < 4; dir++) {
            if (PlacedTile.edgeOf(tile, dir) != TileType.Edge.FIELD
                    && !isFeatureOwned(lx, ly, dir, tile)
                    && !board.isFeatureComplete(lx, ly, dir)) return true;
        }
        // Monastery centre
        if (type != null && type.monastery
                && !claims.containsKey(packPos(lx, ly, SLOT_CENTER))
                && !board.isMonasteryComplete(lx, ly)) return true;
        return false;
    }


//    private boolean hasFreeFeature(int lx, int ly) {
//        short tile = board.get(lx, ly);
//        if (PlacedTile.isEmpty(tile)) return false;
//        TileType type = PlacedTile.typeOf(tile);
//        for (int dir = 0; dir < 4; dir++) {
//            if (PlacedTile.edgeOf(tile, dir) != TileType.Edge.F && !isFeatureOwned(lx, ly, dir, tile)) return true;
//        }
//        return type != null && type.monastery && !claims.containsKey(packPos(lx, ly, SLOT_CENTER));
//    }

    public void rotateTile() {
        if (phase == PHASE_PLACE && currentTileType != null) {
            currentRotation = (currentRotation + 1) & 3;
            boardDirty = true;
        }
    }

    private void advancePlayer() {
        int idx = (players.indexOf(currentPlayer) + 1) % players.size();
        currentPlayer = players.get(idx);
        isGameReady = false;
        scheduledActions.add(() -> {
        });
    }

    @Override
    public boolean canPlay(CardPlayer player, GamePlay play) {
        return player == currentPlayer && ((phase == PHASE_PLACE && currentTileType != null) || phase == PHASE_CLAIM);
    }

    @Override
    public void endGame() {
        if (isGameOver) return;
        isGameOver = true;
        board.scoreFinalAll(claims, players.size(), scores, followersLeft);
        int best = -1, bestScore = -1;
        for (int i = 0; i < players.size(); i++)
            if (scores[i] > bestScore) {
                bestScore = scores[i];
                best = i;
            }
        winnerIdx = best;
        StringBuilder sb = new StringBuilder("Final: ");
        for (int i = 0; i < players.size(); i++) {
            sb.append(players.get(i).getName().getString()).append("=").append(scores[i]);
            if (i < players.size() - 1) sb.append(", ");
        }
        table(Component.literal(sb.toString()).withStyle(ChatFormatting.GOLD));
        if (best >= 0) {
            CardPlayer w = players.get(best);
            w.sendTitle(Component.translatable("message.charta.you_won").withStyle(ChatFormatting.GREEN),
                    Component.translatable("message.charta.congratulations"));
            for (int i = 0; i < players.size(); i++)
                if (i != best)
                    players.get(i).sendTitle(Component.translatable("message.charta.you_lost").withStyle(ChatFormatting.RED),
                            Component.translatable("message.charta.won_the_match", w.getName()));
        }
        scheduledActions.clear();
        for (CardPlayer p : players) p.play(null);
    }

    // ── Static helpers ────────────────────────────────────────────────────────
    public static int packAction(int lx, int ly, int rot) {
        return ((lx + 64) & 0xFF) | (((ly + 64) & 0xFF) << 8) | ((rot & 3) << 16);
    }

    public static int[] unpackAction(int v) {
        return new int[]{(v & 0xFF) - 64, ((v >> 8) & 0xFF) - 64, (v >> 16) & 3};
    }

    public static int packClaimAction(int slot) {
        return 0x80000 | (slot & 0xF);
    }

    public static int unpackClaimSlot(int a) {
        return a & 0xF;
    }

    public static boolean isClaimAction(int a) {
        return (a & 0x80000) != 0;
    }

    public static long packPos(int lx, int ly, int slot) {
        return (slot & 0xFF) | (((lx + 64) & 0xFF) << 8L) | (((ly + 64) & 0xFF) << 16L);
    }

    public static int packClaimInt(int lx, int ly, int slot, int pidx) {
        return ((lx + 64) & 0xFF) | (((ly + 64) & 0xFF) << 8) | ((slot & 0xF) << 16) | ((pidx & 0xFF) << 20);
    }

    public static int[] unpackClaimInt(int v) {
        return new int[]{(v & 0xFF) - 64, ((v >> 8) & 0xFF) - 64, (v >> 16) & 0xF, (v >> 20) & 0xFF};
    }

    private void markBoardDirty() {
        boardSnapshot = board.getGridCopy();
        boardDirty = true;
    }

    private void refreshClaimsSnapshot() {
        claimsSnapshot = new int[claims.size()];
        int i = 0;
        for (Map.Entry<Long, Integer> e : claims.entrySet()) {
            long k = e.getKey();
            int slot = (int) (k & 0xFF), lx = (int) ((k >> 8) & 0xFF) - 64, ly = (int) ((k >> 16) & 0xFF) - 64;
            claimsSnapshot[i++] = packClaimInt(lx, ly, slot, e.getValue());
        }
    }


}