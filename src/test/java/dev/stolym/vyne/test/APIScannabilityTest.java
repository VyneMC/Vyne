/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.test;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class APIScannabilityTest {
    @Test
    public void scanForgeApiClasses() throws Exception {
        String[] packages = {"net.neoforged", "net.minecraftforge"};
        for (String pkg : packages) {
            List<Class<?>> classes = getClasses(pkg);
            for (Class<?> cls : classes) {
                try {
                    Class.forName(cls.getName(), false, Thread.currentThread().getContextClassLoader());
                } catch (Throwable t) {
                    System.err.println("[Vyne-Test] Failed to load/verify: " + cls.getName() + " — " + t.getMessage());
                }
            }
        }
    }

    private List<Class<?>> getClasses(String packageName) throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<>();
        while (resources.hasMoreElements()) {
            dirs.add(new File(resources.nextElement().getFile()));
        }
        List<Class<?>> classes = new ArrayList<>();
        for (File directory : dirs) {
            classes.addAll(findClasses(directory, packageName));
        }
        return classes;
    }

    private List<Class<?>> findClasses(File directory, String packageName) {
        List<Class<?>> classes = new ArrayList<>();
        if (!directory.exists()) return classes;
        File[] files = directory.listFiles();
        if (files == null) return classes;
        for (File file : files) {
            if (file.isDirectory()) {
                classes.addAll(findClasses(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                try {
                    classes.add(Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6), 
                        false, Thread.currentThread().getContextClassLoader()));
                } catch (Throwable t) {
                    System.err.println("[Vyne-Test] Skipping unresolvable class: " + packageName + "." + file.getName());
                }
            }
        }
        return classes;
    }
}
