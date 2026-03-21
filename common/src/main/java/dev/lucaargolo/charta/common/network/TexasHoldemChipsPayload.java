package dev.lucaargolo.charta.common.network;

import dev.lucaargolo.charta.common.ChartaMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

/**
 * Broadcast from server to all chunk watchers carrying Texas Hold'em chip state.
 * Allows spectators (not just menu holders) to see chip stacks on the 3-D table.
 */
public record TexasHoldemChipsPayload(
        BlockPos pos, int[] chips, int foldedMask, int allInMask,
        int startingChips, int gameSlotCount
) implements CustomPacketPayload {

    public static final Type<TexasHoldemChipsPayload> TYPE =
            new Type<>(ChartaMod.id("texas_holdem_chips"));

    private static final StreamCodec<ByteBuf, int[]> INT_ARRAY_CODEC = new StreamCodec<>() {
        @Override public int @NotNull [] decode(@NotNull ByteBuf buf) {
            int len = buf.readInt();
            int[] arr = new int[len];
            for (int i = 0; i < len; i++) arr[i] = buf.readInt();
            return arr;
        }
        @Override public void encode(@NotNull ByteBuf buf, int @NotNull [] val) {
            buf.writeInt(val.length);
            for (int v : val) buf.writeInt(v);
        }
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, TexasHoldemChipsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,    TexasHoldemChipsPayload::pos,
                    INT_ARRAY_CODEC,          TexasHoldemChipsPayload::chips,
                    ByteBufCodecs.INT,        TexasHoldemChipsPayload::foldedMask,
                    ByteBufCodecs.INT,        TexasHoldemChipsPayload::allInMask,
                    ByteBufCodecs.INT,        TexasHoldemChipsPayload::startingChips,
                    ByteBufCodecs.INT,        TexasHoldemChipsPayload::gameSlotCount,
                    TexasHoldemChipsPayload::new
            );

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}