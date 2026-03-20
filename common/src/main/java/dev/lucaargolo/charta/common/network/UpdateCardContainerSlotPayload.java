package dev.lucaargolo.charta.common.network;

import dev.lucaargolo.charta.common.ChartaMod;
import dev.lucaargolo.charta.common.game.api.card.Card;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record UpdateCardContainerSlotPayload(int containerId, int stateId, int slotId, List<Card> cards) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateCardContainerSlotPayload> TYPE = new CustomPacketPayload.Type<>(ChartaMod.id("update_card_container_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateCardContainerSlotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            UpdateCardContainerSlotPayload::containerId,
            ByteBufCodecs.INT,
            UpdateCardContainerSlotPayload::stateId,
            ByteBufCodecs.INT,
            UpdateCardContainerSlotPayload::slotId,
            ByteBufCodecs.collection(NonNullList::createWithCapacity, Card.STREAM_CODEC),
            UpdateCardContainerSlotPayload::cards,
            UpdateCardContainerSlotPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
