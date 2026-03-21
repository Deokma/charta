package dev.lucaargolo.charta.common.game.impl.tilekingdoms;

import dev.lucaargolo.charta.common.ChartaMod;
import dev.lucaargolo.charta.common.game.api.CardPlayer;
import dev.lucaargolo.charta.common.game.api.GamePlay;
import dev.lucaargolo.charta.common.game.api.GameSlot;
import dev.lucaargolo.charta.common.game.api.card.Card;
import dev.lucaargolo.charta.common.game.api.card.Deck;
import dev.lucaargolo.charta.common.game.api.game.Game;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.*;

public class TileKingdomsGame extends TileKingdomsGameBase {

    // ── Game state ─────────────────────────────────────────────────────────────
    public final TileKingdomsBoard board = new TileKingdomsBoard();
    private final List<TileType> tileDeck = new ArrayList<>();

    public int[]  scores;           // per-player score
    public TileType currentTileType;// tile the current player must place
    public int    currentRotation;  // current display rotation (client & server)
    public boolean waitingForPlace; // true while waiting for player to place tile

    // Broadcast to all clients each time something changes
    public short[] boardSnapshot;   // copy sent via ContainerData
    public boolean boardDirty = true;

    public int getRemainingTiles() { return tileDeck.size(); }
    public void setGameReady(boolean v) { isGameReady = v; }

    public TileKingdomsGame(List<CardPlayer> players, Deck deck) {
        super(players, deck);
        scores = new int[players.size()];
    }

    // ── Game options ───────────────────────────────────────────────────────────
    @Override public int getMinPlayers() { return 2; }
    @Override public int getMaxPlayers() { return 5; }
    @Override public List<dev.lucaargolo.charta.common.game.api.GameOption<?>> getOptions() { return List.of(); }

    // ── Lifecycle ──────────────────────────────────────────────────────────────
    @Override
    public void startGame() {
        isGameReady = false;
        // Build & shuffle tile deck
        for (TileType t : TileType.values()) {
            for (int i = 0; i < t.count; i++) tileDeck.add(t);
        }
        Collections.shuffle(tileDeck);
        // Remove START tile, place it at 0,0
        tileDeck.remove(TileType.START);
        board.placeForced(0, 0, TileType.START, 0);
        boardDirty = true;
        boardSnapshot = board.getGridCopy();

        scores = new int[players.size()];
        currentPlayer = players.get(0);
        scheduledActions.add(() -> {
            table(Component.literal("Tile Kingdoms begins! " + tileDeck.size() + " tiles remain.").withStyle(ChatFormatting.GOLD));
        });
    }

    @Override
    public void runGame() {
        if (!isGameReady) return;
        if (tileDeck.isEmpty()) {
            endGame();
            return;
        }
        if (waitingForPlace) return;

        // Draw a tile for the current player
        currentTileType = tileDeck.remove(0);
        currentRotation = 0;
        waitingForPlace = true;
        boardDirty = true;

        table(Component.translatable("message.charta.its_player_turn", currentPlayer.getColoredName())
                .append(Component.literal(" — draw: " + currentTileType.name())));

        currentPlayer.resetPlay();
        currentPlayer.afterPlay(play -> {
            waitingForPlace = false;
            if (play != null) {
                handlePlace(play.slot()); // slot encodes packed (x,y,rotation)
            } else {
                // Player couldn't/didn't place — discard & next
                table(Component.literal(currentPlayer.getName().getString() + " discarded a tile.").withStyle(ChatFormatting.GRAY));
                advancePlayer();
            }
        });
    }

    // ── Placement (called from payload) ───────────────────────────────────────

    /**
     * Packed int: bits 0-7 = tileX+64, bits 8-15 = tileY+64, bits 16-17 = rotation.
     */
    public static int packAction(int lx, int ly, int rotation) {
        return ((lx + 64) & 0xFF) | (((ly + 64) & 0xFF) << 8) | ((rotation & 3) << 16);
    }

    public static int[] unpackAction(int v) {
        int lx = (v & 0xFF) - 64;
        int ly = ((v >> 8) & 0xFF) - 64;
        int rot = (v >> 16) & 3;
        return new int[]{lx, ly, rot};
    }

    private void handlePlace(int packed) {
        int[] coords = unpackAction(packed);
        int lx = coords[0], ly = coords[1], rot = coords[2];

        if (currentTileType == null) return;
        if (!board.canPlace(lx, ly, currentTileType, rot)) {
            table(Component.literal("Invalid placement!").withStyle(ChatFormatting.RED));
            // Give back same tile
            waitingForPlace = true;
            currentPlayer.resetPlay();
            currentPlayer.afterPlay(play -> {
                waitingForPlace = false;
                if (play != null) handlePlace(play.slot());
                else advancePlayer();
            });
            return;
        }

        board.place(lx, ly, currentTileType, rot);
        int gained = board.scoreNewTile(lx, ly);
        int pidx = players.indexOf(currentPlayer);
        if (pidx >= 0 && gained > 0) {
            scores[pidx] += gained;
            play(currentPlayer, Component.literal("+" + gained + " pts! (Total: " + scores[pidx] + ")").withStyle(ChatFormatting.GREEN));
        }
        boardDirty = true;
        boardSnapshot = board.getGridCopy();
        currentTileType = null;
        advancePlayer();
    }

    private void advancePlayer() {
        int idx = (players.indexOf(currentPlayer) + 1) % players.size();
        currentPlayer = players.get(idx);
        isGameReady = false;
        scheduledActions.add(() -> {}); // trigger tick → runGame
    }

    // ── Rotation (called from payload) ────────────────────────────────────────
    public void rotateTile() {
        currentRotation = (currentRotation + 1) & 3;
        boardDirty = true;
    }

    // ── canPlay / afterPlay plumbing (not used directly) ──────────────────────
    @Override
    public boolean canPlay(CardPlayer player, GamePlay play) {
        return player == currentPlayer && waitingForPlace;
    }

    // ── End game ──────────────────────────────────────────────────────────────
    @Override
    public void endGame() {
        if (isGameOver) return;
        isGameOver = true;

        // Final scoring: sum up incomplete features across all placed tiles
        int[] bounds = board.getBounds();
        for (int ly = bounds[1] - 1; ly <= bounds[3] + 1; ly++) {
            for (int lx = bounds[0] - 1; lx <= bounds[2] + 1; lx++) {
                // Final scoring is complex; simplify: +1 per incomplete road tile
                // (proper scoring would need ownership tracking)
            }
        }

        // Determine winner(s)
        int best = -1, bestScore = -1;
        for (int i = 0; i < players.size(); i++) {
            if (scores[i] > bestScore) { bestScore = scores[i]; best = i; }
        }

        StringBuilder sb = new StringBuilder("Final scores: ");
        for (int i = 0; i < players.size(); i++) {
            sb.append(players.get(i).getName().getString()).append("=").append(scores[i]);
            if (i < players.size()-1) sb.append(", ");
        }
        table(Component.literal(sb.toString()).withStyle(ChatFormatting.GOLD));

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
}