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
            { 0xFFAAAAAA, 0xFF888888 }, // 0 white
            { 0xFFCC2222, 0xFFAA1111 }, // 1 red
            { 0xFF228822, 0xFF117711 }, // 2 green
            { 0xFF222222, 0xFF111111 }, // 3 black
            { 0xFF7722CC, 0xFF6611BB }, // 4 purple
            { 0xFF2255CC, 0xFF1144AA }, // 5 blue
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
                int gameSlotCount    = gameSlotCountObj;
                int foldedMask       = ChartaModClient.TABLE_POKER_FOLDED.getOrDefault(tablePos, 0);
                int allInMask        = ChartaModClient.TABLE_POKER_ALLIN.getOrDefault(tablePos, 0);
                int startingChips    = ChartaModClient.TABLE_POKER_STARTING_CHIPS.getOrDefault(tablePos, 1000);
                if (startingChips <= 0) startingChips = 1000;

                // 3 stacks in a triangle cluster.
                // Offsets are in (side, inward) basis — forms a tight triangle.
                //   stack 0 (red)  – back-left
                //   stack 1 (blue) – back-right
                //   stack 2 (green)– front-centre
                final int[]   STACK_COLORS  = { 1, 5, 2 };          // red, blue, green
                // (sideOffset, inwardOffset) for each stack in table units
                final float[] SIDE_OFF   = { -11f,  11f,   0f };
                final float[] INWARD_OFF = {   8f,   8f, -10f };
                final int     MAX_DISCS  = 12;
                final float   INWARD_DIST = 130f; // anchor distance from hand slot

                for (int pi = 0; pi < chips.length; pi++) {
                    int handSlotIndex = gameSlotCount + pi;
                    if (handSlotIndex >= gameSlots) break;

                    int   totalChips = chips[pi];
                    boolean isFolded = (foldedMask & (1 << pi)) != 0;
                    boolean isAllIn  = (allInMask  & (1 << pi)) != 0;

                    if (totalChips <= 0) continue;

                    // Height as % of starting chips, mapped to [1..MAX_DISCS]
                    float pct     = (float) totalChips / startingChips;
                    int baseDiscs = Math.max(1, Math.round(pct * MAX_DISCS));

                    GameSlot handSlot = blockEntity.getSlot(handSlotIndex);
                    float hx    = handSlot.lerpX(partialTick);
                    float hy    = handSlot.lerpY(partialTick);
                    float angle = handSlot.lerpAngle(partialTick);
                    float rad   = (float) Math.toRadians(angle);

                    float inX   = -(float) Math.sin(rad);
                    float inY   =  (float) Math.cos(rad);
                    float sideX =  (float) Math.cos(rad);
                    float sideY =  (float) Math.sin(rad);

                    float anchorX = hx + inX * INWARD_DIST;
                    float anchorY = hy + inY * INWARD_DIST;

                    // Slightly varied heights: 100 %, 85 %, 70 % — looks natural
                    float[] fractions = { 1.0f, 0.85f, 0.70f };

                    for (int s = 0; s < STACK_COLORS.length; s++) {
                        int discs = Math.max(1, Math.round(baseDiscs * fractions[s]));
                        float cx  = anchorX + sideX * SIDE_OFF[s] + inX * INWARD_OFF[s];
                        float cy  = anchorY + sideY * SIDE_OFF[s] + inY * INWARD_OFF[s];
                        drawChipStack3D(poseStack, bufferSource, packedLight,
                                cx, cy, discs, isFolded, isAllIn, STACK_COLORS[s]);
                    }
                }
            }
        }else if(!deckStack.isEmpty()) {
            poseStack.translate(0.5 + blockEntity.centerOffset.x, 0.275 + blockEntity.centerOffset.y, 0.0);
            context.getItemRenderer().renderStatic(deckStack, ItemDisplayContext.GROUND, packedLight, packedOverlay, poseStack, bufferSource, blockEntity.getLevel(), 1);
        }
        poseStack.popPose();
    }

    /**
     * Draws one denomination stack of chip discs on the 3-D table surface.
     *
     * @param px         table-space X centre
     * @param py         table-space Y centre
     * @param numDiscs   number of discs to draw (already clamped by caller)
     * @param folded     grey-out all discs
     * @param allIn      gold override for all discs
     * @param colorIndex index into CHIP_COLORS (0=white,1=red,2=green,3=black,4=purple)
     */
    private void drawChipStack3D(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                 float px, float py, int numDiscs,
                                 boolean folded, boolean allIn, int colorIndex) {
        if (numDiscs <= 0) return;

        int[] colorPair = folded ? CHIP_FOLDED
                : allIn  ? CHIP_ALLIN
                : CHIP_COLORS[Mth.clamp(colorIndex, 0, CHIP_COLORS.length - 1)];

        final float DISC_R   = 8f;   // radius — was 5, now bigger
        final float DISC_H   = 2.5f; // height per disc layer
        final float DISC_GAP = 0.4f; // tiny gap so layers are visible
        final float STEP     = DISC_H + DISC_GAP;

        poseStack.pushPose();
        poseStack.scale(1f / 160f, 1f / 160f, 1f / 160f);

        VertexConsumer consumer = bufferSource.getBuffer(
                ChartaModClient.getRenderTypeManager().chipStack());

        float x0 = px - DISC_R, x1 = px + DISC_R;
        float y0 = py - DISC_R, y1 = py + DISC_R;

        for (int d = 0; d < numDiscs; d++) {
            float zb = d * STEP;
            float zt = zb + DISC_H;

            int bodyColor = (d % 2 == 0) ? colorPair[0] : colorPair[1];
            float r  = ((bodyColor >> 16) & 0xFF) / 255f;
            float gr = ((bodyColor >>  8) & 0xFF) / 255f;
            float b  = ( bodyColor        & 0xFF) / 255f;

            PoseStack.Pose e = poseStack.last();

            // Side faces (70% brightness for depth)
            drawQuad(e, consumer, x0, y0, zb, x1, y0, zb, x1, y0, zt, x0, y0, zt, r*.7f, gr*.7f, b*.7f, 1f, packedLight);
            drawQuad(e, consumer, x1, y0, zb, x1, y1, zb, x1, y1, zt, x1, y0, zt, r*.7f, gr*.7f, b*.7f, 1f, packedLight);
            drawQuad(e, consumer, x1, y1, zb, x0, y1, zb, x0, y1, zt, x1, y1, zt, r*.7f, gr*.7f, b*.7f, 1f, packedLight);
            drawQuad(e, consumer, x0, y1, zb, x0, y0, zb, x0, y0, zt, x0, y1, zt, r*.7f, gr*.7f, b*.7f, 1f, packedLight);
            // Top face
            drawQuad(e, consumer, x0, y0, zt, x1, y0, zt, x1, y1, zt, x0, y1, zt, r, gr, b, 1f, packedLight);
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