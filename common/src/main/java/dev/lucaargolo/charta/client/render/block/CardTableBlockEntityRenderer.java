package dev.lucaargolo.charta.client.render.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.lucaargolo.charta.client.ChartaModClient;
import dev.lucaargolo.charta.client.compat.IrisCompat;
import dev.lucaargolo.charta.common.block.entity.CardTableBlockEntity;
import dev.lucaargolo.charta.common.game.api.GameSlot;
import dev.lucaargolo.charta.common.game.api.card.Card;
import dev.lucaargolo.charta.common.game.api.card.Deck;
import dev.lucaargolo.charta.common.utils.CardImage;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

public class CardTableBlockEntityRenderer implements BlockEntityRenderer<CardTableBlockEntity> {

    // Chip colours by wealth tier (alternating body/shadow)
    private static final int[][] CHIP_COLORS = {
            { 0xFFAAAAAA, 0xFF888888 }, // white  – < 100
            { 0xFFCC2222, 0xFFAA1111 }, // red    – 100-499
            { 0xFF228822, 0xFF117711 }, // green  – 500-999
            { 0xFF222222, 0xFF111111 }, // black  – 1000-1999
            { 0xFF7722CC, 0xFF6611BB }, // purple – 2000+
    };
    private static final int[] CHIP_ALLIN  = { 0xFFFFCC00, 0xFFFFAA00 };
    private static final int[] CHIP_FOLDED = { 0xFF555555, 0xFF444444 };

    private final BlockEntityRendererProvider.Context context;

    public CardTableBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.context = context;
    }

    @Override
    public void render(@NotNull CardTableBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Deck deck = blockEntity.getDeck();
        ItemStack deckStack = blockEntity.getDeckStack();
        poseStack.pushPose();
        poseStack.translate(0.0, 0.85, 1.0);
        poseStack.mulPose(Axis.XN.rotationDegrees(90f));
        int gameSlots = blockEntity.getSlotCount();
        if(deck != null && gameSlots > 0) {
            for(int i = 0; i < gameSlots; i++) {
                GameSlot slot = blockEntity.getSlot(i);
                float x = slot.lerpX(partialTick);
                float y = slot.lerpY(partialTick);
                float z = slot.lerpZ(partialTick);
                float angle = slot.lerpAngle(partialTick);
                Direction stackDirection = slot.getStackDirection();

                int cards = slot.size();

                float maxWidth = stackDirection.getAxis().isVertical() ? 0 : angle % 180 == 0 && stackDirection.getAxis() == Direction.Axis.Z ? 0 : slot.getMaxStack();
                float cardWidth = stackDirection.getAxis().isVertical() ? 0 : angle % 180 == 0 && stackDirection.getAxis() == Direction.Axis.Z ? 0 : CardImage.WIDTH;
                float maxLeftOffset = cardWidth + cardWidth / 10f;

                float maxHeight = stackDirection.getAxis().isVertical() ? 0 : angle % 180 == 0 && stackDirection.getAxis() == Direction.Axis.Z ? slot.getMaxStack() : 0;
                float cardHeight = stackDirection.getAxis().isVertical() ? 0 : angle % 180 == 0 && stackDirection.getAxis() == Direction.Axis.Z ? CardImage.HEIGHT : 0;
                float maxTopOffset = cardHeight + cardHeight / 10f;

                float left = 0f, leftOffset;
                float top = 0f, topOffset;

                if(slot.isCentered()) {
                    leftOffset = cardWidth + Math.max(0f, maxWidth - (cards * cardWidth) / (float) cards);
                    float totalWidth = cardWidth + (leftOffset * (cards - 1f));
                    float leftExcess = totalWidth - maxWidth;
                    if (leftExcess > 0) {
                        leftOffset -= leftExcess / (cards - 1f);
                    }

                    totalWidth = cardWidth + (maxLeftOffset * (cards - 1f));
                    left = 0;
                    if (leftOffset > maxLeftOffset) {
                        left = Math.max(leftOffset - maxLeftOffset, (maxWidth - totalWidth));
                        leftOffset = maxLeftOffset;
                    }

                    topOffset = cardHeight + Math.max(0f, maxHeight - (cards * cardHeight) / (float) cards);
                    float totalHeight = cardHeight + (topOffset * (cards - 1f));
                    float topExcess = totalHeight - maxHeight;
                    if (topExcess > 0) {
                        topOffset -= topExcess / (cards - 1f);
                    }

                    totalHeight = cardHeight + (maxTopOffset * (cards - 1f));
                    top = 0;
                    if (topOffset > maxTopOffset) {
                        top = Math.max(topOffset - maxTopOffset, (maxHeight - totalHeight));
                        topOffset = maxTopOffset;
                    }
                }else{
                    leftOffset = stackDirection.getAxis().isVertical() ? 0 : angle % 180 == 0 && stackDirection.getAxis() == Direction.Axis.Z ? 0f : 5f;
                    if(leftOffset * (slot.size() - 1) + cardWidth > maxWidth) {
                        leftOffset = (maxWidth - cardWidth) / (slot.size() - 1);
                    }
                    topOffset = stackDirection.getAxis().isVertical() ? 0 : angle % 180 == 0 && stackDirection.getAxis() == Direction.Axis.Z ? 7.5f : 0f;
                    if(topOffset * (slot.size() - 1) + cardHeight > maxHeight) {
                        topOffset = (maxHeight - cardHeight) / (slot.size() - 1);
                    }
                }

                Vector3f normal = new Vector3f(0f, -1f, 0f);
                normal.rotateAxis(90f, -1f, 0f, 0f);
                normal.rotateAxis(angle, 0f, 0f, -1f);

                int o = 0;
                for(Card card : slot.getCards()) {
                    poseStack.pushPose();
                    poseStack.scale(1/160f, 1/160f, 1/160f);
                    poseStack.translate(x, y, z + (o*0.01f));
                    if(stackDirection.getAxis().isVertical()) {
                        poseStack.translate(0, 0, (o*0.25f)*stackDirection.getNormal().getY());
                    }
                    poseStack.mulPose(Axis.ZN.rotationDegrees(angle));
                    poseStack.translate(o*leftOffset, o*topOffset, 0.0);
                    poseStack.scale(160f, 160f, 160f);
                    if(slot.isCentered()) {
                        drawCard(deck, card, packedLight, packedOverlay, poseStack, bufferSource, left / 2, top / 2, normal);
                    }else{
                        drawCard(deck, card, packedLight, packedOverlay, poseStack, bufferSource, 0f, 0f, normal);
                    }
                    poseStack.popPose();
                    o++;
                }
            }

            // ── Chip stacks on the 3-D table ──────────────────────────────────
            BlockPos tablePos = blockEntity.getBlockPos();
            int[] chips = ChartaModClient.TABLE_POKER_CHIPS.get(tablePos);
            Integer gameSlotCountObj = ChartaModClient.TABLE_POKER_GAME_SLOT_COUNT.get(tablePos);
            if (chips != null && gameSlotCountObj != null) {
                int gameSlotCount = gameSlotCountObj;
                int foldedMask = ChartaModClient.TABLE_POKER_FOLDED.getOrDefault(tablePos, 0);
                int allInMask  = ChartaModClient.TABLE_POKER_ALLIN.getOrDefault(tablePos, 0);
                // Hand slots follow game slots. Each hand slot index corresponds to player[i].
                for (int pi = 0; pi < chips.length; pi++) {
                    int handSlotIndex = gameSlotCount + pi;
                    if (handSlotIndex >= gameSlots) break;

                    GameSlot handSlot = blockEntity.getSlot(handSlotIndex);
                    float hx = handSlot.lerpX(partialTick);
                    float hy = handSlot.lerpY(partialTick);
                    float angle = handSlot.lerpAngle(partialTick);

                    // Place chip stack beside the hand: offset perpendicular to angle
                    // (to the right of the cards from the player's perspective)
                    float angleRad = (float) Math.toRadians(angle);
                    float perpX =  (float) Math.cos(angleRad);
                    float perpY = -(float) Math.sin(angleRad);

                    // Offset: ~20 units to the side of the hand, in table units (1/160 block)
                    float chipX = hx + perpX * 28f;
                    float chipY = hy + perpY * 28f;

                    drawChipStack3D(poseStack, bufferSource, packedLight,
                            chipX, chipY, chips[pi],
                            (foldedMask & (1 << pi)) != 0,
                            (allInMask  & (1 << pi)) != 0,
                            angle);
                }
            }
        }else if(!deckStack.isEmpty()) {
            poseStack.translate(0.5 + blockEntity.centerOffset.x, 0.275 + blockEntity.centerOffset.y, 0.0);
            context.getItemRenderer().renderStatic(deckStack, ItemDisplayContext.GROUND, packedLight, packedOverlay, poseStack, bufferSource, blockEntity.getLevel(), 1);
        }
        poseStack.popPose();
    }

    /**
     * Draws a stack of chip discs in 3D world space (table surface).
     * Coordinates are in "table units" (same space as GameSlot x/y, scale 1/160 of a block).
     * Discs are quads lying flat on the table; the stack grows along the +Z axis (up from table).
     *
     * @param px      table-space X centre of stack
     * @param py      table-space Y centre of stack
     * @param chips   chip count
     * @param angle   rotation angle of the hand slot (degrees) — not used for quads, kept for future use
     */
    private void drawChipStack3D(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                 float px, float py, int chips,
                                 boolean folded, boolean allIn, float angle) {
        if (chips <= 0) return;

        // Number of discs: 1 per 100 chips, capped at 10
        int numDiscs = Mth.clamp(chips / 100, 1, 10);

        // Pick colour tier
        int[] colorPair;
        if (folded) {
            colorPair = CHIP_FOLDED;
        } else if (allIn) {
            colorPair = CHIP_ALLIN;
        } else if (chips >= 2000) {
            colorPair = CHIP_COLORS[4];
        } else if (chips >= 1000) {
            colorPair = CHIP_COLORS[3];
        } else if (chips >= 500) {
            colorPair = CHIP_COLORS[2];
        } else if (chips >= 100) {
            colorPair = CHIP_COLORS[1];
        } else {
            colorPair = CHIP_COLORS[0];
        }

        // Disc dimensions in table units (table is 160×160 units = 1 block)
        final float DISC_R  = 5f;   // radius
        final float DISC_H  = 1.5f; // height of each disc layer
        final float DISC_GAP = 0.5f; // gap between layers
        final float STEP = DISC_H + DISC_GAP;

        // We render in the coordinate space that's already active in render():
        // scale(1/160), translate(x,y,z), scale(160) around each card.
        // For chips we push a separate pose at 1/160 scale.
        poseStack.pushPose();
        poseStack.scale(1f / 160f, 1f / 160f, 1f / 160f);

        VertexConsumer consumer = bufferSource.getBuffer(
                ChartaModClient.getRenderTypeManager().chipStack());

        for (int d = 0; d < numDiscs; d++) {
            float zBase = d * STEP;
            int bodyColor = (d % 2 == 0) ? colorPair[0] : colorPair[1];
            float r = ((bodyColor >> 16) & 0xFF) / 255f;
            float gr = ((bodyColor >> 8)  & 0xFF) / 255f;
            float b  = ( bodyColor        & 0xFF) / 255f;

            float zt = zBase + DISC_H;
            PoseStack.Pose e = poseStack.last();

            float x0 = px - DISC_R, x1 = px + DISC_R;
            float y0 = py - DISC_R, y1 = py + DISC_R;

            float zb = zBase;
            // Side faces (darker)
            drawQuad(e, consumer, x0, y0, zb, x1, y0, zb, x1, y0, zt, x0, y0, zt,
                    r * 0.7f, gr * 0.7f, b * 0.7f, 1f, packedLight);
            drawQuad(e, consumer, x1, y0, zb, x1, y1, zb, x1, y1, zt, x1, y0, zt,
                    r * 0.7f, gr * 0.7f, b * 0.7f, 1f, packedLight);
            drawQuad(e, consumer, x1, y1, zb, x0, y1, zb, x0, y1, zt, x1, y1, zt,
                    r * 0.7f, gr * 0.7f, b * 0.7f, 1f, packedLight);
            drawQuad(e, consumer, x0, y1, zb, x0, y0, zb, x0, y0, zt, x0, y1, zt,
                    r * 0.7f, gr * 0.7f, b * 0.7f, 1f, packedLight);
            // Top face (full colour)
            drawQuad(e, consumer, x0, y0, zt, x1, y0, zt, x1, y1, zt, x0, y1, zt,
                    r, gr, b, 1f, packedLight);
        }

        poseStack.popPose();
    }

    /** Emit 4 vertices forming a QUADS quad for POSITION_COLOR_LIGHTMAP format. */
    private static void drawQuad(PoseStack.Pose e, VertexConsumer vc,
                                 float x0, float y0, float z0,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float x3, float y3, float z3,
                                 float r, float g, float b, float a,
                                 int light) {
        vc.addVertex(e.pose(), x0, y0, z0).setColor(r, g, b, a).setLight(light);
        vc.addVertex(e.pose(), x1, y1, z1).setColor(r, g, b, a).setLight(light);
        vc.addVertex(e.pose(), x2, y2, z2).setColor(r, g, b, a).setLight(light);
        vc.addVertex(e.pose(), x3, y3, z3).setColor(r, g, b, a).setLight(light);
    }

    public static void drawCard(Deck deck, Card card, int packedLight, int packedOverlay, PoseStack poseStack, MultiBufferSource bufferSource, float x, float y, Vector3f normal) {
        PoseStack.Pose entry = poseStack.last();

        if(IrisCompat.isPresent()) {
            ResourceLocation glowTexture = card.flipped() ? deck.getTexture(true) : deck.getCardTexture(card, true);
            RenderType glowType = RenderType.entityTranslucentEmissive(glowTexture);
            VertexConsumer glowConsumer = bufferSource.getBuffer(glowType);
            glowConsumer.addVertex(entry.pose(), (x+CardImage.WIDTH)/160f, y/160f, 0).setColor(1f, 1f, 1f, 1f).setUv(1f, 1f).setOverlay(packedOverlay).setLight(LightTexture.FULL_BRIGHT).setNormal(entry, normal.x, normal.y, normal.z);
            glowConsumer.addVertex(entry.pose(), (x+CardImage.WIDTH)/160f, (y+CardImage.HEIGHT)/160f, 0).setColor(1f, 1f, 1f, 1f).setUv(1f, 0f).setOverlay(packedOverlay).setLight(LightTexture.FULL_BRIGHT).setNormal(entry, normal.x, normal.y, normal.z);
            glowConsumer.addVertex(entry.pose(), x/160f, (y+CardImage.HEIGHT)/160f, 0).setColor(1f, 1f, 1f, 1f).setUv(0f, 0f).setOverlay(packedOverlay).setLight(LightTexture.FULL_BRIGHT).setNormal(entry, normal.x, normal.y, normal.z);
            glowConsumer.addVertex(entry.pose(), x/160f, y/160f, 0).setColor(1f, 1f, 1f, 1f).setUv(0f, 1f).setOverlay(packedOverlay).setLight(LightTexture.FULL_BRIGHT).setNormal(entry, normal.x, normal.y, normal.z);
        }

        ResourceLocation texture = card.flipped() ? deck.getTexture(false) : deck.getCardTexture(card, false);
        RenderType type = IrisCompat.isPresent() ? RenderType.entityTranslucent(texture) : ChartaModClient.getRenderTypeManager().entityCard(texture);
        VertexConsumer consumer = bufferSource.getBuffer(type);

        consumer.addVertex(entry.pose(), (x+CardImage.WIDTH)/160f, y/160f, 0).setColor(1f, 1f, 1f, 1f).setUv(1f, 1f).setOverlay(packedOverlay).setLight(packedLight).setNormal(entry, normal.x, normal.y, normal.z);
        consumer.addVertex(entry.pose(), (x+CardImage.WIDTH)/160f, (y+CardImage.HEIGHT)/160f, 0).setColor(1f, 1f, 1f, 1f).setUv(1f, 0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(entry, normal.x, normal.y, normal.z);
        consumer.addVertex(entry.pose(), x/160f, (y+CardImage.HEIGHT)/160f, 0).setColor(1f, 1f, 1f, 1f).setUv(0f, 0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(entry, normal.x, normal.y, normal.z);
        consumer.addVertex(entry.pose(), x/160f, y/160f, 0).setColor(1f, 1f, 1f, 1f).setUv(0f, 1f).setOverlay(packedOverlay).setLight(packedLight).setNormal(entry, normal.x, normal.y, normal.z);
    }

}