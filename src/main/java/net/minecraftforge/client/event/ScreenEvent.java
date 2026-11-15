/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.minecraftforge.client.event;

import net.neoforged.bus.api.Event;

public class ScreenEvent extends Event {
    public static class Init extends ScreenEvent {
        public static class Pre extends Init { public Pre() {} }
        public static class Post extends Init { public Post() {} }
    }
    public static class MouseButtonPressed extends ScreenEvent {
        public static class Pre extends MouseButtonPressed { public Pre() {} }
        public static class Post extends MouseButtonPressed { public Post() {} }
    }
    public static class MouseButtonReleased extends ScreenEvent {
        public static class Pre extends MouseButtonReleased { public Pre() {} }
        public static class Post extends MouseButtonReleased { public Post() {} }
    }
    public static class MouseDragged extends ScreenEvent {
        public static class Pre extends MouseDragged { public Pre() {} }
        public static class Post extends MouseDragged { public Post() {} }
    }
    public static class MouseScrolled extends ScreenEvent {
        public static class Pre extends MouseScrolled { public Pre() {} }
        public static class Post extends MouseScrolled { public Post() {} }
    }

    public static class Render extends ScreenEvent {
        public Object getGuiGraphics() { return null; }
        public int getMouseX() { return 0; }
        public int getMouseY() { return 0; }
        public float getPartialTick() { return 0f; }
        public static class Pre extends Render { public Pre() {} }
        public static class Post extends Render { public Post() {} }
    }

    public static class CharacterTyped extends ScreenEvent {
        public char getCodePoint() { return 0; }
        public int getModifiers() { return 0; }
        public static class Pre extends CharacterTyped {}
        public static class Post extends CharacterTyped {}
    }

    public static class KeyPressed extends ScreenEvent {
        public int getKeyCode() { return 0; }
        public int getScanCode() { return 0; }
        public int getModifiers() { return 0; }
        public static class Pre extends KeyPressed {}
        public static class Post extends KeyPressed {}
    }

    public static class KeyReleased extends ScreenEvent {
        public int getKeyCode() { return 0; }
        public int getScanCode() { return 0; }
        public int getModifiers() { return 0; }
        public static class Pre extends KeyReleased {}
        public static class Post extends KeyReleased {}
    }

    public static class BackgroundRendered extends ScreenEvent {
        public Object getGuiGraphics() { return null; }
    }

    public static class Opening extends ScreenEvent {
        public Object getScreen() { return null; }
        public Object getNewScreen() { return null; }
        public Object getCurrentScreen() { return null; }
    }
}
