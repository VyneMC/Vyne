/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 *
 * Temporary stand-in for a Minecraft class, only here so Vyne compiles.
 * It will be removed once Vyne compiles against the real Minecraft jar with Mojang names.
 */

package net.minecraft.util;

public abstract class RandomSource {
    public abstract int nextInt();

    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
        return nextInt() % bound;
    }

    public abstract long nextLong();

    public abstract float nextFloat();

    public abstract double nextDouble();

    public abstract boolean nextBoolean();

    public void nextBytes(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) nextInt();
        }
    }
}
