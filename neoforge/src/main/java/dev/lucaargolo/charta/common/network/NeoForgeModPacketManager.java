package dev.lucaargolo.charta.common.network;

import com.mojang.datafixers.util.Pair;
import dev.lucaargolo.charta.common.NeoForgeChartaMod;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

public class NeoForgeModPacketManager extends ModPacketManager {

    private final List<Pair<PacketInfo, Class<? extends CustomPacketPayload>>> payloadsToRegister = new ArrayList<>();

    /**
     * Client-side handlers registered by ClientPayloadHandlers.register() during client init.
     * Never populated on a dedicated server — the key is the payload Class, value is the handler.
     * Using Object keys so this class has zero client-class imports.
     */
    private final Map<Class<?>, BiConsumer<Object, Executor>> clientHandlers = new HashMap<>();

    @SuppressWarnings("unchecked")
    public <T extends CustomPacketPayload> void registerClientHandler(Class<T> payloadClass, BiConsumer<T, Executor> handler) {
        clientHandlers.put(payloadClass, (BiConsumer<Object, Executor>) (BiConsumer<?, ?>) handler);
    }

    @Override
    public void init() {
        super.init();
        NeoForgeChartaMod.getModBus().register(this);
    }

    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void onPayloadRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        payloadsToRegister.forEach(pair -> {
            PacketInfo info = pair.getFirst();
            Class<? extends CustomPacketPayload> payloadClass = pair.getSecond();
            try {
                CustomPacketPayload.Type payloadType = (CustomPacketPayload.Type) payloadClass.getField("TYPE").get(null);
                StreamCodec payloadCodec = (StreamCodec) payloadClass.getField("STREAM_CODEC").get(null);

                // Server handler resolved via reflection — safe (no client imports in server handlers)
                Lazy<Method> serverHandler = Lazy.of(() -> {
                    try {
                        return payloadClass.getMethod("handleServer", payloadClass, ServerPlayer.class, Executor.class);
                    } catch (NoSuchMethodException e) {
                        return null; // payload has no server handler
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

                switch (info) {
                    case PLAY_TO_CLIENT -> registrar.playToClient(payloadType, payloadCodec, (payload, context) -> {
                        BiConsumer<Object, Executor> handler = clientHandlers.get(payloadClass);
                        if (handler != null) {
                            handler.accept(payload, context::enqueueWork);
                        }
                    });
                    case PLAY_TO_SERVER -> registrar.playToServer(payloadType, payloadCodec, (payload, context) -> {
                        try {
                            Method m = serverHandler.get();
                            if (m != null) m.invoke(null, payload, context.player(), (Executor) context::enqueueWork);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                    case PLAY_TO_BOTH -> registrar.playBidirectional(payloadType, payloadCodec, (payload, context) -> {
                        try {
                            if (context.flow().isClientbound()) {
                                BiConsumer<Object, Executor> handler = clientHandlers.get(payloadClass);
                                if (handler != null) handler.accept(payload, context::enqueueWork);
                            } else {
                                Method m = serverHandler.get();
                                if (m != null) m.invoke(null, payload, context.player(), (Executor) context::enqueueWork);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    protected <T extends CustomPacketPayload> void register(PacketInfo info, Class<T> klass) {
        payloadsToRegister.add(Pair.of(info, klass));
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToPlayersInDimension(ServerLevel level, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersInDimension(level, payload);
    }

    @Override
    public void sendToPlayersNear(ServerLevel level, @Nullable ServerPlayer excluded, double x, double y, double z, double radius, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersNear(level, excluded, x, y, z, radius, payload);
    }

    @Override
    public void sendToAllPlayers(MinecraftServer server, CustomPacketPayload payload) {
        PacketDistributor.sendToAllPlayers(payload);
    }

    @Override
    public void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
    }

    @Override
    public void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
    }

    @Override
    public void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos chunkPos, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersTrackingChunk(level, chunkPos, payload);
    }
}