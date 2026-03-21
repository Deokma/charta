package dev.lucaargolo.charta.common.game.impl.tilekingdoms;

import dev.lucaargolo.charta.common.game.Games;
import dev.lucaargolo.charta.common.game.api.game.GameType;
import dev.lucaargolo.charta.common.menu.AbstractCardMenu;
import dev.lucaargolo.charta.common.menu.ModMenuTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class TileKingdomsMenu extends AbstractCardMenu<TileKingdomsGame, TileKingdomsMenu> {

    private static final int MAX_P = 8;
    private static final int MAX_CLAIMS = 60; // max simultaneous claims

    //  0..7  = scores
    //  8..15 = followersLeft
    //  16    = currentTileType (-1=none)
    //  17    = currentRotation
    //  18    = currentPlayerIdx
    //  19    = tilesRemaining
    //  20    = isGameReady
    //  21    = phase
    //  22    = lastPlacedX+64
    //  23    = lastPlacedY+64
    //  24    = claimsCount
    //  25..24+MAX_CLAIMS = claims (packed ints)
    //  25+MAX_CLAIMS .. 25+MAX_CLAIMS+BOARD_INTS-1 = board
    private static final int OFF_SCORES  = 0;
    private static final int OFF_FOLLOW  = MAX_P;
    private static final int OFF_TILE    = MAX_P*2;
    private static final int OFF_ROT     = MAX_P*2+1;
    private static final int OFF_CUR     = MAX_P*2+2;
    private static final int OFF_REMAIN  = MAX_P*2+3;
    private static final int OFF_READY   = MAX_P*2+4;
    private static final int OFF_PHASE   = MAX_P*2+5;
    private static final int OFF_LASTX   = MAX_P*2+6;
    private static final int OFF_LASTY   = MAX_P*2+7;
    private static final int OFF_CLAIMC  = MAX_P*2+8;
    private static final int OFF_CLAIMS  = MAX_P*2+9;
    private static final int OFF_BOARD   = OFF_CLAIMS + MAX_CLAIMS;
    private static final int BOARD_SHORTS= TileKingdomsBoard.SIZE * TileKingdomsBoard.SIZE;
    private static final int BOARD_INTS  = (BOARD_SHORTS+1)/2;
    private static final int OFF_GAMEOVER    = OFF_BOARD + BOARD_INTS;
    private static final int OFF_WINNER      = OFF_BOARD + BOARD_INTS + 1;
    private static final int DATA_COUNT_FULL = OFF_BOARD + BOARD_INTS + 2;


    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            TileKingdomsGame g = game;
            if(index<OFF_FOLLOW) { int i=index-OFF_SCORES; return i<g.scores.length?g.scores[i]:0; }
            if(index<OFF_TILE)   { int i=index-OFF_FOLLOW; return i<g.followersLeft.length?g.followersLeft[i]:0; }
            if(index==OFF_TILE)  return g.currentTileType!=null?g.currentTileType.ordinal():-1;
            if(index==OFF_ROT)   return g.currentRotation;
            if(index==OFF_CUR)   return g.getPlayers().indexOf(g.getCurrentPlayer());
            if(index==OFF_REMAIN)return g.getRemainingTiles();
            if(index==OFF_READY) return g.isGameReady()?1:0;
            if(index==OFF_PHASE) return g.phase;
            if(index==OFF_LASTX) return g.lastPlacedX+64;
            if(index==OFF_LASTY) return g.lastPlacedY+64;
            if(index==OFF_CLAIMC)return g.claimsSnapshot.length;
            if(index>=OFF_CLAIMS&&index<OFF_BOARD) {
                int i=index-OFF_CLAIMS;
                return i<g.claimsSnapshot.length?g.claimsSnapshot[i]:0;
            }
            if(index>=OFF_BOARD) {
                int i=index-OFF_BOARD;
                short[] grid=g.boardSnapshot!=null?g.boardSnapshot:new short[0];
                int base=i*2;
                short a=base<grid.length?grid[base]:0, b=(base+1)<grid.length?grid[base+1]:0;
                return (a&0xFFFF)|((b&0xFFFF)<<16);
            }
            if(index==OFF_GAMEOVER) return g.isGameOver()?1:0;
            if(index==OFF_WINNER)   return g.getWinnerIdx();
            return 0;
        }
        @Override public void set(int index, int value) {
            TileKingdomsGame g=game;
            if(index<OFF_FOLLOW) { int i=index-OFF_SCORES; if(i<g.scores.length) g.scores[i]=value; }
            else if(index<OFF_TILE)   { int i=index-OFF_FOLLOW; if(i<g.followersLeft.length) g.followersLeft[i]=value; }
            else if(index==OFF_TILE)  { g.currentTileType=value>=0&&value<TileType.values().length?TileType.values()[value]:null; }
            else if(index==OFF_ROT)   { g.currentRotation=value; }
            else if(index==OFF_READY) { g.setGameReady(value==1); }
            else if(index==OFF_PHASE) { g.phase=value; }
            else if(index==OFF_LASTX) { g.lastPlacedX=value-64; }
            else if(index==OFF_LASTY) { g.lastPlacedY=value-64; }
            else if(index==OFF_CLAIMC){ /* read-only length */ }
            else if(index>=OFF_CLAIMS&&index<OFF_BOARD) {
                int i=index-OFF_CLAIMS;
                if(g.claimsSnapshot==null||i>=g.claimsSnapshot.length) {
                    int[] nc=new int[Math.max(i+1,g.claimsSnapshot!=null?g.claimsSnapshot.length:0)];
                    if(g.claimsSnapshot!=null) System.arraycopy(g.claimsSnapshot,0,nc,0,g.claimsSnapshot.length);
                    g.claimsSnapshot=nc;
                }
                g.claimsSnapshot[i]=value;
            }
            if(index>=OFF_BOARD && index<OFF_GAMEOVER) {
                int i=index-OFF_BOARD;
                if(g.boardSnapshot==null) g.boardSnapshot=new short[BOARD_SHORTS];
                int base=i*2;
                if(base<g.boardSnapshot.length)   g.boardSnapshot[base]  =(short)(value&0xFFFF);
                if(base+1<g.boardSnapshot.length) g.boardSnapshot[base+1]=(short)((value>>16)&0xFFFF);
            }
        }
        @Override public int getCount() { return DATA_COUNT_FULL; }
    };

    public TileKingdomsMenu(int containerId, Inventory inventory, Definition definition) {
        super(ModMenuTypes.TILE_KINGDOMS.get(), containerId, inventory, definition);
        addDataSlots(data);
    }

    // ── Accessors ──────────────────────────────────────────────────────────────
    public int getScore(int i)       { return data.get(OFF_SCORES+i); }
    public int[] getFollowersLeft()  {
        int[] r=new int[Math.min(MAX_P,game.getPlayers().size())];
        for(int i=0;i<r.length;i++) r[i]=data.get(OFF_FOLLOW+i); return r;
    }
    public TileType getCurrentTile() {
        int v=data.get(OFF_TILE); return v>=0&&v<TileType.values().length?TileType.values()[v]:null;
    }
    public int getCurrentRotation()  { return data.get(OFF_ROT); }
    public int getCurrentPlayerIdx() { return data.get(OFF_CUR); }
    public int getRemainingTiles()   { return data.get(OFF_REMAIN); }
    public boolean isReady()         { return data.get(OFF_READY)==1; }
    public int getPhase()            { return data.get(OFF_PHASE); }
    public int getLastPlacedX()      { return data.get(OFF_LASTX)-64; }
    public int getLastPlacedY()      { return data.get(OFF_LASTY)-64; }

    public int[] getClaimsArray() {
        int cnt=Math.min(data.get(OFF_CLAIMC),MAX_CLAIMS);
        int[] arr=new int[cnt];
        for(int i=0;i<cnt;i++) arr[i]=data.get(OFF_CLAIMS+i);
        return arr;
    }

    public short[] getBoardGrid() {
        short[] grid=new short[BOARD_SHORTS];
        for(int i=0;i<BOARD_INTS;i++) {
            int v=data.get(OFF_BOARD+i); int base=i*2;
            if(base<grid.length)   grid[base]  =(short)(v&0xFFFF);
            if(base+1<grid.length) grid[base+1]=(short)((v>>16)&0xFFFF);
        }
        return grid;
    }

    public boolean isGameOver()     { return data.get(OFF_GAMEOVER)==1; }
    public int    getWinnerIdx()    { return data.get(OFF_WINNER); }

    @Override public GameType<TileKingdomsGame,TileKingdomsMenu> getGameType() { return Games.TILE_KINGDOMS.get(); }
    @Override public @NotNull ItemStack quickMoveStack(@NotNull Player p,int i) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(@NotNull Player p) { return game!=null&&cardPlayer!=null&&!game.isGameOver(); }
}