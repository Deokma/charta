package dev.lucaargolo.charta.common.network;

import dev.lucaargolo.charta.common.ChartaMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

public record GameSlotPositionPayload(BlockPos pos, int index, float x, float y, float z, float angle) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GameSlotPositionPayload> TYPE = new CustomPacketPayload.Type<>(ChartaMod.id("game_slot_position"));

    public static StreamCodec<ByteBuf, GameSlotPositionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            GameSlotPositionPayload::pos,
            ByteBufCodecs.INT,
            GameSlotPositionPayload::index,
            ByteBufCodecs.FLOAT,
            GameSlotPositionPayload::x,
            ByteBufCodecs.FLOAT,
            GameSlotPositionPayload::y,
            ByteBufCodecs.FLOAT,
            GameSlotPositionPayload::z,
            ByteBufCodecs.FLOAT,
            GameSlotPositionPayload::angle,
            GameSlotPositionPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
