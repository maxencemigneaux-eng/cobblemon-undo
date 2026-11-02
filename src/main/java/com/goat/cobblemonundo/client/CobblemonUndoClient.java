package com.goat.cobblemonundo.client;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.goat.cobblemonundo.network.UndoRequestPayload;
import com.goat.cobblemonundo.network.UndoStatePayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/**
 * Côté client. Deux modes, choisis automatiquement :
 *
 * 1) Le serveur a aussi le mod  -> comme Showdown : après ton choix, "En attente de l'adversaire..."
 *    + bouton Annuler, utilisable tant que l'adversaire n'a pas joué.
 * 2) Le serveur n'a pas le mod  -> ton choix est retenu quelques secondes (3 s par défaut) avec
 *    les boutons Annuler / Envoyer, puis il part tout seul.
 *
 * Les boutons s'affichent en bas à gauche de l'écran de combat, là où sont normalement
 * Attaque / Sac / Pokémon / Fuite.
 */
public final class CobblemonUndoClient implements ClientModInitializer {
    // Calé sur BattleGUI.OPTION_ROOT_X (12) / OPTION_VERTICAL_OFFSET (85) de Cobblemon
    private static final int X = 12;
    private static final int OFFSET_FROM_BOTTOM = 66;
    private static final int BUTTON_WIDTH = 74;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;

    /** Mode serveur : dernier état reçu du serveur (c'est lui qui décide). */
    private static volatile boolean serverAllowsUndo = false;

    private static KeyMapping undoKey;

    @Override
    public void onInitializeClient() {
        UndoConfig.load();

        ClientPlayNetworking.registerGlobalReceiver(UndoStatePayload.TYPE,
                (payload, context) -> serverAllowsUndo = payload.canUndo());

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            serverAllowsUndo = false;
            GraceHold.reset();
        });

        undoKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.cobblemon_undo.undo",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_BACKSPACE,
                "category.cobblemon_undo"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            GraceHold.tick();
            // Touche utilisable aussi quand l'écran de combat est réduit
            while (undoKey.consumeClick()) {
                undo();
            }
        });

        // On greffe nos boutons sur l'écran de combat de Cobblemon, sans le modifier
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof BattleGUI)) {
                return;
            }

            ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, tickDelta) ->
                    render(s, graphics, mouseX, mouseY));

            ScreenMouseEvents.allowMouseClick(screen).register((s, mouseX, mouseY, button) -> {
                if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    return true;
                }
                Mode mode = currentMode();
                int y = buttonY(s);
                if (mode != Mode.NONE && isOver(mouseX, mouseY, X, y)) {
                    click();
                    undo();
                    return false;
                }
                if (mode == Mode.GRACE && isOver(mouseX, mouseY, X + BUTTON_WIDTH + GAP, y)) {
                    click();
                    GraceHold.sendNow();
                    return false;
                }
                return true;
            });

            ScreenKeyboardEvents.allowKeyPress(screen).register((s, key, scancode, modifiers) -> {
                if (currentMode() != Mode.NONE && undoKey.matches(key, scancode)) {
                    undo();
                    return false;
                }
                return true;
            });
        });
    }

    // ---------------------------------------------------------------------------------------

    private enum Mode { NONE, SERVER, GRACE }

    private static Mode currentMode() {
        if (GraceHold.isHolding()) {
            return Mode.GRACE;
        }
        if (serverAllowsUndo && ClientPlayNetworking.canSend(UndoRequestPayload.TYPE)) {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            if (battle != null && !battle.getSpectating() && !battle.getMustChoose()) {
                return Mode.SERVER;
            }
        }
        return Mode.NONE;
    }

    private static void undo() {
        switch (currentMode()) {
            case GRACE -> GraceHold.cancel();
            case SERVER -> {
                serverAllowsUndo = false; // on masque tout de suite le bouton (anti double-clic)
                ClientPlayNetworking.send(UndoRequestPayload.INSTANCE);
            }
            case NONE -> {
            }
        }
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static int buttonY(Screen screen) {
        return screen.height - OFFSET_FROM_BOTTOM;
    }

    private static boolean isOver(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }

    // ---------------------------------------------------------------------------------------

    private static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY) {
        Mode mode = currentMode();
        if (mode == Mode.NONE) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int y = buttonY(screen);

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F); // au-dessus de l'interface de combat

        Component label = mode == Mode.GRACE
                ? Component.translatable("cobblemon_undo.sending_in", String.format("%.1f", GraceHold.secondsLeft()))
                : Component.translatable("cobblemon_undo.waiting");
        graphics.drawString(font, label.copy().withStyle(ChatFormatting.ITALIC), X, y - 13, 0xFFFFFFFF, true);

        drawButton(graphics, font, Component.translatable("cobblemon_undo.button"), X, y, mouseX, mouseY);

        if (mode == Mode.GRACE) {
            int sendX = X + BUTTON_WIDTH + GAP;
            drawButton(graphics, font, Component.translatable("cobblemon_undo.send_now"), sendX, y, mouseX, mouseY);

            // Barre de progression sous les boutons
            int barWidth = BUTTON_WIDTH * 2 + GAP;
            int barY = y + BUTTON_HEIGHT + 3;
            graphics.fill(X, barY, X + barWidth, barY + 3, 0x90000000);
            graphics.fill(X, barY, X + Math.round(barWidth * GraceHold.progress()), barY + 3, 0xFF55C8FF);
        }

        graphics.pose().popPose();
    }

    private static void drawButton(GuiGraphics graphics, Font font, Component text, int x, int y, int mouseX, int mouseY) {
        boolean hovered = isOver(mouseX, mouseY, x, y);
        graphics.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, hovered ? 0xE0505050 : 0xD0303030);
        graphics.renderOutline(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, hovered ? 0xFFFFFFFF : 0xFFA0A0A0);
        graphics.drawCenteredString(font, text, x + BUTTON_WIDTH / 2, y + (BUTTON_HEIGHT - 8) / 2, 0xFFFFFFFF);
    }
}
