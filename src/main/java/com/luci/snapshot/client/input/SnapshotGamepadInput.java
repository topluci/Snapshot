package com.luci.snapshot.client.input;

import com.luci.snapshot.client.camera.SnapshotCameraController;
import com.luci.snapshot.config.SnapshotConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;

public final class SnapshotGamepadInput {
    private static final boolean[] PREVIOUS_BUTTONS = new boolean[GLFW.GLFW_GAMEPAD_BUTTON_LAST + 1];
    private static int activeJoystick = -1;
    private static boolean previousLeftTrigger;
    private static boolean previousRightTrigger;

    private SnapshotGamepadInput() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SnapshotGamepadInput::tick);
    }

    private static void tick(Minecraft client) {
        if (!SnapshotConfig.get().controllerSupport) {
            resetTriggers();
            return;
        }
        int joystick = findGamepad();
        if (joystick < 0) {
            resetTriggers();
            activeJoystick = -1;
            return;
        }
        if (activeJoystick != joystick) {
            java.util.Arrays.fill(PREVIOUS_BUTTONS, false);
            previousLeftTrigger = false;
            previousRightTrigger = false;
            activeJoystick = joystick;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            GLFWGamepadState state = GLFWGamepadState.malloc(stack);
            if (!GLFW.glfwGetGamepadState(joystick, state)) {
                resetTriggers();
                return;
            }
            boolean gameplay = client.gui.screen() == null;
            if (gameplay && pressed(state, GLFW.GLFW_GAMEPAD_BUTTON_START)) {
                click(SnapshotKeybinds.toggle());
            }
            if (gameplay && pressed(state, GLFW.GLFW_GAMEPAD_BUTTON_BACK)) {
                click(SnapshotKeybinds.lighttable());
            }

            if (SnapshotCameraController.active() && gameplay) {
                if (pressed(state, GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER)) {
                    click(SnapshotKeybinds.previousControl());
                }
                if (pressed(state, GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER)) {
                    click(SnapshotKeybinds.nextControl());
                }
                if (pressed(state, GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT)) {
                    click(SnapshotKeybinds.decrease());
                }
                if (pressed(state, GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT)) {
                    click(SnapshotKeybinds.increase());
                }
                if (pressed(state, GLFW.GLFW_GAMEPAD_BUTTON_A)) {
                    SnapshotCameraController.requestAutofocus();
                }
                if (pressed(state, GLFW.GLFW_GAMEPAD_BUTTON_X)) {
                    click(SnapshotKeybinds.focusPointSelector());
                }
                if (pressed(state, GLFW.GLFW_GAMEPAD_BUTTON_Y)) {
                    click(SnapshotKeybinds.quickMenu());
                }
                if (pressed(state, GLFW.GLFW_GAMEPAD_BUTTON_B)) {
                    click(SnapshotKeybinds.toggle());
                }

                boolean leftTrigger = state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER) > 0.35F;
                if (leftTrigger != previousLeftTrigger) {
                    SnapshotCameraController.setHalfPress(leftTrigger);
                }
                previousLeftTrigger = leftTrigger;

                boolean rightTrigger = state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER) > 0.55F;
                if (rightTrigger && !previousRightTrigger) {
                    SnapshotCameraController.triggerCapture();
                }
                previousRightTrigger = rightTrigger;

                float zoom = state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y);
                if (Math.abs(zoom) > 0.22F) {
                    SnapshotCameraController.handleZoomScroll(-zoom * 0.10);
                }
            } else {
                if (previousLeftTrigger) {
                    SnapshotCameraController.setHalfPress(false);
                }
                previousLeftTrigger = false;
                previousRightTrigger = false;
            }
            rememberButtons(state);
        }
    }

    private static int findGamepad() {
        for (int joystick = GLFW.GLFW_JOYSTICK_1; joystick <= GLFW.GLFW_JOYSTICK_LAST; joystick++) {
            if (GLFW.glfwJoystickIsGamepad(joystick)) {
                return joystick;
            }
        }
        return -1;
    }

    private static boolean pressed(GLFWGamepadState state, int button) {
        return state.buttons(button) == GLFW.GLFW_PRESS && !PREVIOUS_BUTTONS[button];
    }

    private static void rememberButtons(GLFWGamepadState state) {
        for (int button = 0; button < PREVIOUS_BUTTONS.length; button++) {
            PREVIOUS_BUTTONS[button] = state.buttons(button) == GLFW.GLFW_PRESS;
        }
    }

    private static void click(KeyMapping mapping) {
        KeyMapping.click(KeyMappingHelper.getBoundKeyOf(mapping));
    }

    private static void resetTriggers() {
        if (previousLeftTrigger) {
            SnapshotCameraController.setHalfPress(false);
        }
        previousLeftTrigger = false;
        previousRightTrigger = false;
    }
}
