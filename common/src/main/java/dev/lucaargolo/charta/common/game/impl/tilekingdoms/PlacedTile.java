package dev.lucaargolo.charta.common.game.impl.tilekingdoms;

/**
 * A tile placed on the board.
 * Encoded as a short: bits 0-4 = TileType ordinal, bits 5-6 = rotation (0-3 clockwise).
 * Special value 0 = empty cell, -1 = not yet allocated.
 */
public class PlacedTile {

    public static final short EMPTY = 0;

    /**
     * Pack type + rotation into a short (1-based ordinal so 0 means empty).
     */
    public static short pack(TileType type, int rotation) {
        return (short) (((type.ordinal() + 1) & 0x1F) | ((rotation & 3) << 5));
    }

    public static boolean isEmpty(short val) {
        return val == EMPTY;
    }

    public static TileType typeOf(short val) {
        int idx = (val & 0x1F) - 1;
        if (idx < 0 || idx >= TileType.values().length) return null;
        return TileType.values()[idx];
    }

    public static int rotationOf(short val) {
        return (val >> 5) & 3;
    }

    /**
     * Returns the effective edge for direction d (0=N,1=E,2=S,3=W) after applying rotation.
     * Rotation is clockwise: rot=1 means the original North edge is now East.
     */
    public static TileType.Edge edgeOf(short val, int dir) {
        TileType type = typeOf(val);
        if (type == null) return TileType.Edge.F;
        int rot = rotationOf(val);
        // To get logical direction accounting for rotation: subtract rotation
        int logicalDir = ((dir - rot) & 3);
        return type.edge(logicalDir);
    }

    /**
     * True if the two tile values have matching edges along the shared border (dirA→dirB).
     */
    public static boolean compatible(short tileA, int dirA, short tileB) {
        if (isEmpty(tileA) || isEmpty(tileB)) return true;
        return edgeOf(tileA, dirA) == edgeOf(tileB, TileType.opposite(dirA));
    }
}