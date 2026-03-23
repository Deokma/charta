package dev.lucaargolo.charta.common.game.impl.tilekingdoms;

import dev.lucaargolo.charta.common.game.Games;
import dev.lucaargolo.charta.common.game.api.game.GameType;
import dev.lucaargolo.charta.common.menu.AbstractCardMenu;
import dev.lucaargolo.charta.common.menu.ModMenuTypes;
import dev.lucaargolo.charta.common.network.TileKingdomsBoardPayload;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class TileKingdomsMenu extends AbstractCardMenu<TileKingdomsGame, TileKingdomsMenu> {

    private static final int MAX_P = 8;
    private static final int MAX_CLAIMS = 60;

    // ContainerData indices (kept small — board sent via TileKingdomsBoardPayload)
    //  0..7    scores
    //  8..15   followersLeft
    //  16      currentTileType ordinal (-1=none)
    //  17      currentRotation
    //  18      currentPlayerIdx
    //  19      tilesRemaining
    //  20      isGameReady
    //  21      phase
    //  22      lastPlacedX+64
    //  23      lastPlacedY+64
    //  24      claimsCount
    //  25..84  claims (MAX_CLAIMS=60 ints)
    //  85      isGameOver
    //  86      winnerIdx
    private static final int OFF_SCORES = 0;
    private static final int OFF_FOLLOW = MAX_P;
    private static final int OFF_TILE = MAX_P * 2;
    private static final int OFF_ROT = MAX_P * 2 + 1;
    private static final int OFF_CUR = MAX_P * 2 + 2;
    private static final int OFF_REMAIN = MAX_P * 2 + 3;
    private static final int OFF_READY = MAX_P * 2 + 4;
    private static final int OFF_PHASE = MAX_P * 2 + 5;
    private static final int OFF_LASTX = MAX_P * 2 + 6;
    private static final int OFF_LASTY = MAX_P * 2 + 7;
    private static final int OFF_CLAIMC = MAX_P * 2 + 8;
    private static final int OFF_CLAIMS = MAX_P * 2 + 9;          // 25
    private static final int OFF_GAMEOVER = OFF_CLAIMS + MAX_CLAIMS; // 85
    private static final int OFF_WINNER = OFF_GAMEOVER + 1;        // 86
    private static final int DATA_COUNT = OFF_WINNER + 1;          // 87 — well within 128

    // Board/claims stored client-side, received via TileKingdomsBoardPayload
    private short[] clientBoardGrid = new short[TileKingdomsBoard.SIZE * TileKingdomsBoard.SIZE];
    private int[] clientClaims = new int[0];

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            TileKingdomsGame g = game;
            if (g == null) return 0;
            if (index < OFF_FOLLOW) {
                int i = index - OFF_SCORES;
                return i < g.scores.length ? g.scores[i] : 0;
            }
            if (index < OFF_TILE) {
                int i = index - OFF_FOLLOW;
                return i < g.followersLeft.length ? g.followersLeft[i] : 0;
            }
            if (index == OFF_TILE) return g.currentTileType != null ? g.currentTileType.ordinal() : -1;
            if (index == OFF_ROT) return g.currentRotation;
            if (index == OFF_CUR) return g.getPlayers().indexOf(g.getCurrentPlayer());
            if (index == OFF_REMAIN) return g.getRemainingTiles();
            if (index == OFF_READY) return g.isGameReady() ? 1 : 0;
            if (index == OFF_PHASE) return g.phase;
            if (index == OFF_LASTX) return g.lastPlacedX + 64;
            if (index == OFF_LASTY) return g.lastPlacedY + 64;
            if (index == OFF_CLAIMC)
                return Math.min(g.claimsSnapshot != null ? g.claimsSnapshot.length : 0, MAX_CLAIMS);
            if (index >= OFF_CLAIMS && index < OFF_GAMEOVER) {
                int i = index - OFF_CLAIMS;
                return (g.claimsSnapshot != null && i < g.claimsSnapshot.length) ? g.claimsSnapshot[i] : 0;
            }
            if (index == OFF_GAMEOVER) return g.isGameOver() ? 1 : 0;
            if (index == OFF_WINNER) return g.getWinnerIdx();
            return 0;
        }

        @Override
        public void set(int index, int value) {
            TileKingdomsGame g = game;
            if (g == null) return;
            if (index < OFF_FOLLOW) {
                int i = index - OFF_SCORES;
                if (i < g.scores.length) g.scores[i] = value;
            } else if (index < OFF_TILE) {
                int i = index - OFF_FOLLOW;
                if (i < g.followersLeft.length) g.followersLeft[i] = value;
            } else if (index == OFF_TILE) {
                g.currentTileType = (value >= 0 && value < TileType.values().length) ? TileType.values()[value] : null;
            } else if (index == OFF_ROT) {
                g.currentRotation = value;
            } else if (index == OFF_REMAIN) {
                g.clientRemainingTiles = value;
            } else if (index == OFF_READY) {
                g.setGameReady(value == 1);
            } else if (index == OFF_PHASE) {
                g.phase = value;
            } else if (index == OFF_LASTX) {
                g.lastPlacedX = value - 64;
            } else if (index == OFF_LASTY) {
                g.lastPlacedY = value - 64;
            } else if (index >= OFF_CLAIMS && index < OFF_GAMEOVER) {
                int i = index - OFF_CLAIMS;
                if (g.claimsSnapshot == null || i >= g.claimsSnapshot.length) {
                    int[] nc = new int[Math.max(i + 1, g.claimsSnapshot != null ? g.claimsSnapshot.length : 0)];
                    if (g.claimsSnapshot != null) System.arraycopy(g.claimsSnapshot, 0, nc, 0, g.claimsSnapshot.length);
                    g.claimsSnapshot = nc;
                }
                g.claimsSnapshot[i] = value;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public TileKingdomsMenu(int containerId, Inventory inventory, Definition definition) {
        super(ModMenuTypes.TILE_KINGDOMS.get(), containerId, inventory, definition);
        addDataSlots(data);
    }

    // ── Board packet (server→client) ──────────────────────────────────────────
    /**
     * Called server-side each tick to push dirty board data.
     * Returns a payload to send, or null if nothing changed.
     */
    private boolean menuBoardDirty = true; // per-menu flag, true = need to send

    /**
     * Called by CardTableBlockEntity when game marks board dirty.
     */
    public void markBoardDirty() {
        menuBoardDirty = true;
    }

    public TileKingdomsBoardPayload pollBoardPayload() {
        TileKingdomsGame g = game;
        if (g == null || !menuBoardDirty) return null;
        menuBoardDirty = false;
        short[] grid = g.boardSnapshot != null ? g.boardSnapshot.clone()
                : new short[TileKingdomsBoard.SIZE * TileKingdomsBoard.SIZE];
        int[] claims = g.claimsSnapshot != null ? g.claimsSnapshot.clone() : new int[0];
        return new TileKingdomsBoardPayload(containerId, grid, claims);
    }

    /**
     * Called on client when TileKingdomsBoardPayload is received.
     */
    public void receiveBoardUpdate(short[] grid, int[] claims) {
        this.clientBoardGrid = grid;
        this.clientClaims = claims;
        // Also push into game snapshot for any server-side code that reads it
        if (game != null) {
            game.boardSnapshot = grid;
            game.claimsSnapshot = claims;
        }
    }

    // ── Client accessors ──────────────────────────────────────────────────────
    public short[] getBoardGrid() {
        return clientBoardGrid;
    }

    public int[] getClaimsArray() {
        int cnt = Math.min(data.get(OFF_CLAIMC), MAX_CLAIMS);
        // Use client-side claims if available (set via packet), else read from ContainerData
        if (clientClaims.length > 0) return clientClaims;
        int[] arr = new int[cnt];
        for (int i = 0; i < cnt; i++) arr[i] = data.get(OFF_CLAIMS + i);
        return arr;
    }

    public int getScore(int i) {
        return data.get(OFF_SCORES + i);
    }

    public int[] getFollowersLeft() {
        int[] r = new int[Math.min(MAX_P, game != null ? game.getPlayers().size() : 0)];
        for (int i = 0; i < r.length; i++) r[i] = data.get(OFF_FOLLOW + i);
        return r;
    }

    public TileType getCurrentTile() {
        int v = data.get(OFF_TILE);
        return (v >= 0 && v < TileType.values().length) ? TileType.values()[v] : null;
    }

    public int getCurrentRotation() {
        return data.get(OFF_ROT);
    }

    public int getCurrentPlayerIdx() {
        return data.get(OFF_CUR);
    }

    public int getRemainingTiles() {
        return game != null ? game.clientRemainingTiles : 0;
    }

    public boolean isReady() {
        return data.get(OFF_READY) == 1;
    }

    public int getPhase() {
        return data.get(OFF_PHASE);
    }

    public int getLastPlacedX() {
        return data.get(OFF_LASTX) - 64;
    }

    public int getLastPlacedY() {
        return data.get(OFF_LASTY) - 64;
    }

    public boolean isGameOver() {
        return data.get(OFF_GAMEOVER) == 1;
    }

    public int getWinnerIdx() {
        return data.get(OFF_WINNER);
    }

    @Override
    public GameType<TileKingdomsGame, TileKingdomsMenu> getGameType() {
        return Games.TILE_KINGDOMS.get();
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player p, int i) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player p) {
        return game != null && cardPlayer != null && !game.isGameOver();
    }
}