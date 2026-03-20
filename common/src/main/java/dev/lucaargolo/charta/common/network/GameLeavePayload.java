package dev.lucaargolo.charta.common.network;

import dev.lucaargolo.charta.common.ChartaMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

public record GameLeavePayload() implements CustomPacketPayload {

    public static final Type<GameLeavePayload> TYPE = new Type<>(ChartaMod.id("game_leave"));

    public static StreamCodec<ByteBuf, GameLeavePayload> STREAM_CODEC = StreamCodec.unit(new GameLeavePayload());

    public static void handleServer(GameLeavePayload payload, ServerPlayer player, Executor executor) {
        executor.execute(player::stopRiding);
    }

    @Override
    public @NotNull CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
