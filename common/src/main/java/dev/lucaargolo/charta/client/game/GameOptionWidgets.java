package dev.lucaargolo.charta.client.game;

import dev.lucaargolo.charta.common.game.api.game.GameOption;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Client-only factory for GameOption GUI widgets.
 * Lives in the client package so the server never loads it.
 */
public class GameOptionWidgets {

    public static Widget createWidget(GameOption<?> option, Consumer<Object> consumer, Font font,
                                      int width, int height, boolean showcase) {
        if (option instanceof GameOption.Bool boolOption) {
            return createBoolWidget(boolOption, castConsumer(consumer), font, width, height, showcase);
        } else if (option instanceof GameOption.Number numOption) {
            int mult = numOption.getDisplayMultiplier();
            Function<Integer, Component> labelFn = mult > 1
                    ? i -> numOption.getTitle().copy().append(": ").append(Integer.toString(i * mult))
                    : i -> numOption.getTitle().copy().append(": ").append(Integer.toString(i));
            return createNumberWidget(numOption, castConsumer(consumer), font, width, height, showcase, labelFn);
        }
        throw new IllegalArgumentException("Unknown GameOption type: " + option.getClass());
    }

    @SuppressWarnings("unchecked")
    private static <T> Consumer<T> castConsumer(Consumer<Object> consumer) {
        return (Consumer<T>) consumer;
    }

    public static Widget createBoolWidget(GameOption.Bool option, Consumer<Boolean> consumer,
                                          Font font, int width, int height, boolean showcase) {
        Checkbox.Builder builder = Checkbox.builder(option.getTitle(), font);
        builder.tooltip(Tooltip.create(option.getDescription()));
        builder.maxWidth(width);
        builder.selected(option.get());
        builder.onValueChange((checkbox, value) -> option.set(value));
        Checkbox checkbox = builder.build();
        option.consumer = b -> {
            if (b != checkbox.selected()) checkbox.onPress();
            consumer.accept(option.get());
        };
        checkbox.active = !showcase;
        return new Widget(checkbox);
    }

    public static Widget createNumberWidget(GameOption.Number option, Consumer<Integer> consumer,
                                            Font font, int width, int height, boolean showcase,
                                            Function<Integer, Component> labelFn) {
        int min = option.getMin();
        int max = option.getMax();
        double initPos = (max > min) ? (double) (option.get() - min) / (max - min) : 0.0;

        AbstractSliderButton slider = new AbstractSliderButton(0, 0, width, height, labelFn.apply(option.get()), initPos) {
            private static final ResourceLocation SLIDER_HANDLE_SPRITE =
                    ResourceLocation.withDefaultNamespace("widget/slider_handle");

            @Override
            public @NotNull ResourceLocation getHandleSprite() {
                return showcase ? SLIDER_HANDLE_SPRITE : super.getHandleSprite();
            }

            @Override
            protected void updateMessage() {
                this.setMessage(labelFn.apply(option.get()));
            }

            @Override
            protected void applyValue() {
                option.set(Mth.floor(Mth.lerp(this.value, min, max)));
            }

            @Override
            protected void renderScrollingString(@NotNull GuiGraphics guiGraphics,
                                                 @NotNull Font font, int width, int color) {
                super.renderScrollingString(guiGraphics, font, width,
                        16777215 | Mth.ceil(this.alpha * 255.0F) << 24);
            }
        };
        slider.setTooltip(Tooltip.create(option.getDescription()));
        option.consumer = i -> {
            slider.setValue((max > min) ? (double) (i - min) / (max - min) : 0.0);
            consumer.accept(i);
        };
        slider.active = !showcase;
        return new Widget(slider);
    }

    public static class Widget extends ContainerObjectSelectionList.Entry<Widget> {

        private final AbstractWidget widget;

        public Widget(AbstractWidget widget) {
            this.widget = widget;
        }

        @Override
        public @NotNull List<? extends NarratableEntry> narratables() {
            return List.of(widget);
        }

        @Override
        public @NotNull List<? extends GuiEventListener> children() {
            return List.of(widget);
        }

        @Override
        public void render(@NotNull GuiGraphics guiGraphics, int index, int top, int left,
                           int width, int height, int mouseX, int mouseY,
                           boolean hovering, float partialTick) {
            widget.setX(left);
            widget.setY(top);
            widget.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Nullable
        public Tooltip getTooltip() {
            return this.widget.getTooltip();
        }

        public void setTooltip(@Nullable Tooltip tooltip) {
            this.widget.setTooltip(tooltip);
        }
    }
}
