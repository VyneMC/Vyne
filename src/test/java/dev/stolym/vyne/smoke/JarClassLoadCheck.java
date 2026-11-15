/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.smoke;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class JarClassLoadCheck {
    private JarClassLoadCheck() {}

    public static void main(String[] args) throws Exception {
        File jar = new File(args[0]);
        List<String> skip = new ArrayList<>();
        for (int i = 1; i < args.length; i++) skip.add(args[i]);

        List<URL> urls = new ArrayList<>();
        urls.add(jar.toURI().toURL());
        String extra = System.getProperty("check.classpath", "");
        for (String p : extra.split(File.pathSeparator)) {
            if (!p.isBlank()) urls.add(new File(p).toURI().toURL());
        }

        try (URLClassLoader loader = new URLClassLoader(
                urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
             JarFile jf = new JarFile(jar)) {
            int checked = 0;
            List<String> failures = new ArrayList<>();

            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.endsWith(".class")
                        || name.equals("module-info.class")
                        || name.startsWith("META-INF/")) continue;

                String className = name.substring(0, name.length() - 6).replace('/', '.');
                if (skip.stream().anyMatch(className::startsWith)) continue;

                checked++;
                try {
                    Class<?> c = Class.forName(className, false, loader);
                    c.getDeclaredFields();
                    c.getDeclaredMethods();
                    c.getDeclaredConstructors();
                } catch (Throwable t) {
                    failures.add(className + "  ->  " + t.getClass().getSimpleName()
                            + ": " + t.getMessage());
                }
            }

            System.out.println("Checked " + checked + " classes, " + failures.size() + " failed.");
            failures.forEach(f -> System.out.println("  FAIL " + f));
            if (!failures.isEmpty()) System.exit(1);
        }
    }
}
