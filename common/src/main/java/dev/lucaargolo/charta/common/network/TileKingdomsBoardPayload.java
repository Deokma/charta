package dev.lucaargolo.charta.common.network;

import dev.lucaargolo.charta.common.ChartaMod;
import dev.lucaargolo.charta.common.game.impl.tilekingdoms.TileKingdomsMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

/**
 * Server->Client: sends the full board grid + claims array.
 * Sent whenever boardDirty or claimsDirty is set in TileKingdomsGame.
 */
public record TileKingdomsBoardPayload(
        int containerId,
        short[] grid,
        int[]   claims
) implements CustomPacketPayload {

    public static final Type<TileKingdomsBoardPayload> TYPE =
            new Type<>(ChartaMod.id("tile_kingdoms_board"));

    // Custom short[] codec
    private static StreamCodec<ByteBuf, short[]> shortArrayCodec() {
        return new StreamCodec<>() {
            @Override public short[] decode(ByteBuf buf) {
                int len = buf.readInt();
                short[] arr = new short[len];
                for (int i=0;i<len;i++) arr[i]=buf.readShort();
                return arr;
            }
            @Override public void encode(ByteBuf buf, short[] arr) {
                buf.writeInt(arr.length);
                for (short s:arr) buf.writeShort(s);
            }
        };
    }

    private static StreamCodec<ByteBuf, int[]> intArrayCodec() {
        return new StreamCodec<>() {
            @Override public int[] decode(ByteBuf buf) {
                int len = buf.readInt();
                int[] arr = new int[len];
                for (int i=0;i<len;i++) arr[i]=buf.readInt();
                return arr;
            }
            @Override public void encode(ByteBuf buf, int[] arr) {
                buf.writeInt(arr.length);
                for (int v:arr) buf.writeInt(v);
            }
        };
    }

    public static final StreamCodec<ByteBuf, TileKingdomsBoardPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,    TileKingdomsBoardPayload::containerId,
                    shortArrayCodec(),    TileKingdomsBoardPayload::grid,
                    intArrayCodec(),      TileKingdomsBoardPayload::claims,
                    TileKingdomsBoardPayload::new
            );

    /** Called on client: update the local menu's board/claims snapshots. */
    public static void handleClient(TileKingdomsBoardPayload payload, Executor executor) {
        executor.execute(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null
                    && mc.player.containerMenu instanceof TileKingdomsMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.receiveBoardUpdate(payload.grid(), payload.claims());
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}