/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.vcl;

import java.net.URL;
import java.net.URLClassLoader;

public final class CompositeClassLoader extends ClassLoader {
    private final ClassLoader primary;
    private final ClassLoader fallback;

    public CompositeClassLoader(ClassLoader primary, ClassLoader fallback) {
        super(primary != null ? primary.getParent() : null);
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            try {
                return primary.loadClass(name);
            } catch (ClassNotFoundException ignored) {}

            try {
                return fallback.loadClass(name);
            } catch (ClassNotFoundException ignored) {}

            return super.loadClass(name, resolve);
        }
    }
}
