package dev.lucaargolo.charta.common.network;

import dev.lucaargolo.charta.common.ChartaMod;
import dev.lucaargolo.charta.common.game.impl.fun.FunMenu;
import dev.lucaargolo.charta.mixed.LivingEntityMixed;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

public record LastFunPayload(ItemStack deckStack) implements CustomPacketPayload {

    public LastFunPayload() {
        this(ItemStack.EMPTY);
    }

    public static final Type<LastFunPayload> TYPE = new Type<>(ChartaMod.id("last_fun"));

    private static final StreamCodec<ByteBuf, ItemStack> STACK_STREAM = ByteBufCodecs.fromCodecTrusted(ItemStack.OPTIONAL_CODEC);
    public static StreamCodec<ByteBuf, LastFunPayload> STREAM_CODEC = StreamCodec.composite(
            STACK_STREAM,
            LastFunPayload::deckStack,
            LastFunPayload::new
    );

    public static void handleServer(LastFunPayload payload, ServerPlayer player, Executor executor) {
        executor.execute(() -> {
            if(player.containerMenu instanceof FunMenu funMenu) {
                funMenu.getGame().sayLast(((LivingEntityMixed) player).charta_getCardPlayer());
            }
        });
    }

    @Override
    public @NotNull CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
