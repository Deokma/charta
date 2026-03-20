package dev.lucaargolo.charta.common.network;

import dev.lucaargolo.charta.common.ChartaMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

public record GameSlotResetPayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<GameSlotResetPayload> TYPE = new Type<>(ChartaMod.id("game_slot_reset"));

    public static StreamCodec<ByteBuf, GameSlotResetPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        GameSlotResetPayload::pos,
        GameSlotResetPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
