package dev.lucaargolo.charta.common.network;

import dev.lucaargolo.charta.common.ChartaMod;
import dev.lucaargolo.charta.common.game.api.card.Card;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

public record UpdateCardContainerCarriedPayload(int containerId, int stateId, List<Card> cards) implements CustomPacketPayload {

    public static final Type<UpdateCardContainerCarriedPayload> TYPE = new Type<>(ChartaMod.id("update_card_container_carried"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateCardContainerCarriedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            UpdateCardContainerCarriedPayload::containerId,
            ByteBufCodecs.INT,
            UpdateCardContainerCarriedPayload::stateId,
            ByteBufCodecs.collection(i -> new LinkedList<>(), Card.STREAM_CODEC),
            UpdateCardContainerCarriedPayload::cards,
            UpdateCardContainerCarriedPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
