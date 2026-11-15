/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.compat;

import dev.stolym.vyne.util.VyneLogger;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ServiceLoaderUtil {
    private ServiceLoaderUtil() {}

    private static final CopyOnWriteArrayList<ClassLoader> EXTRA_SPI_LOADERS =
        new CopyOnWriteArrayList<>();

    public static void registerSearchClassLoader(ClassLoader cl) {
        if (cl == null || EXTRA_SPI_LOADERS.contains(cl)) {
            return;
        }
        EXTRA_SPI_LOADERS.add(cl);
    }

    private static boolean spiDebug() {
        return Boolean.getBoolean("vyne.spi.debug");
    }

    private static void logSpiFailure(String phase, Class<?> service, ClassLoader cl, Throwable t) {
        if (!spiDebug()) {
            return;
        }
        String clName = cl == null ? "null" : cl.getClass().getName();
        VyneLogger.debug("[SPI] " + phase + " service=" + (service != null ? service.getName() : "null")
            + " loader=" + clName + " — " + (t != null ? t : ""));
        if (t != null) {
            t.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private static <S> Class<S> resolveServiceInLoader(Class<S> service, ClassLoader cl) {
        if (cl == null || service == null) {
            return null;
        }
        try {
            return (Class<S>) Class.forName(service.getName(), false, cl);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Class<?> classForNameInLoader(String fqn, ClassLoader cl) {
        if (cl == null) {
            return null;
        }
        try {
            return Class.forName(fqn, false, cl);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <S> S loadFirstForFqn(String fqn, ClassLoader preferred) {
        Class<S> shell = null;
        try {
            shell = (Class<S>) Class.forName(fqn, false, ServiceLoaderUtil.class.getClassLoader());
        } catch (Throwable ignored) {
        }
        if (shell == null) {
            shell = (Class<S>) classForNameInLoader(fqn, Thread.currentThread().getContextClassLoader());
        }
        if (shell == null) {
            shell = (Class<S>) classForNameInLoader(fqn,
                dev.stolym.vyne.vcl.VCLManager.getCanonicalKnotLoader());
        }
        if (shell == null) {
            return null;
        }
        for (ClassLoader cl : EXTRA_SPI_LOADERS) {
            Class<S> iface = (Class<S>) classForNameInLoader(fqn, cl);
            if (iface == null) {
                continue;
            }
            S hit = tryLoadServiceWithLoader(iface, cl);
            if (hit != null) {
                return hit;
            }
        }
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        Class<S> tcclIface = (Class<S>) classForNameInLoader(fqn, tccl);
        if (tcclIface != null) {
            try {
                ServiceLoader<S> boot = ServiceLoader.load(tcclIface, tccl);
                Iterator<S> bootIt = boot.iterator();
                while (bootIt.hasNext()) {
                    try {
                        S impl = bootIt.next();
                        if (impl != null) {
                            return impl;
                        }
                    } catch (Throwable t) {
                        logSpiFailure("tccl-iterator", shell, tccl, t);
                    }
                }
            } catch (Throwable t) {
                logSpiFailure("tccl-ServiceLoader", shell, tccl, t);
            }
        }
        try {
            ServiceLoader<S> boot = ServiceLoader.load(shell);
            Iterator<S> bootIt = boot.iterator();
            while (bootIt.hasNext()) {
                try {
                    S impl = bootIt.next();
                    if (impl != null) {
                        return impl;
                    }
                } catch (Throwable t) {
                    logSpiFailure("boot-iterator", shell, null, t);
                }
            }
        } catch (Throwable t) {
            logSpiFailure("boot-ServiceLoader", shell, null, t);
        }
        for (ClassLoader cl : loaders(preferred, shell)) {
            Class<S> iface = (Class<S>) classForNameInLoader(fqn, cl);
            if (iface == null) {
                continue;
            }
            S hit = tryLoadServiceWithLoader(iface, cl);
            if (hit != null) {
                return hit;
            }
        }
        for (ClassLoader cl : loaders(preferred, shell)) {
            Class<S> iface = (Class<S>) classForNameInLoader(fqn, cl);
            if (iface == null) {
                continue;
            }
            S manual = tryInstantiateFromMetaInf(iface, cl);
            if (manual != null) {
                return manual;
            }
        }
        return null;
    }

    public static <S> S loadFirst(Class<S> service, ClassLoader staticFieldLoader) {
        for (ClassLoader cl : EXTRA_SPI_LOADERS) {
            S hit = tryLoadServiceWithLoader(service, cl);
            if (hit != null) {
                return hit;
            }
        }
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        Class<S> tcclIface = resolveServiceInLoader(service, tccl);
        if (tcclIface != null) {
            try {
                ServiceLoader<S> boot = ServiceLoader.load(tcclIface, tccl);
                Iterator<S> bootIt = boot.iterator();
                while (bootIt.hasNext()) {
                    try {
                        S impl = bootIt.next();
                        if (impl != null) {
                            return impl;
                        }
                    } catch (Throwable t) {
                        logSpiFailure("tccl-iterator", service, tccl, t);
                    }
                }
            } catch (Throwable t) {
                logSpiFailure("tccl-ServiceLoader", service, tccl, t);
            }
        }
        try {
            ServiceLoader<S> boot = ServiceLoader.load(service);
            Iterator<S> bootIt = boot.iterator();
            while (bootIt.hasNext()) {
                try {
                    S impl = bootIt.next();
                    if (impl != null) {
                        return impl;
                    }
                } catch (Throwable t) {
                    logSpiFailure("boot-iterator", service, null, t);
                }
            }
        } catch (Throwable t) {
            logSpiFailure("boot-ServiceLoader", service, null, t);
        }
        for (ClassLoader cl : loaders(staticFieldLoader, service)) {
            S hit = tryLoadServiceWithLoader(service, cl);
            if (hit != null) {
                return hit;
            }
        }
        for (ClassLoader cl : loaders(staticFieldLoader, service)) {
            S manual = tryInstantiateFromMetaInf(service, cl);
            if (manual != null) {
                return manual;
            }
        }
        return null;
    }

    private static <S> S tryLoadServiceWithLoader(Class<S> service, ClassLoader cl) {
        if (cl == null) {
            return null;
        }
        Class<S> localIface = resolveServiceInLoader(service, cl);
        if (localIface == null) {
            return null;
        }
        try {
            ServiceLoader<S> sl = ServiceLoader.load(localIface, cl);
            Iterator<S> it = sl.iterator();
            while (true) {
                boolean more;
                try {
                    more = it.hasNext();
                } catch (Throwable t) {
                    logSpiFailure("loader-iterator-hasNext", service, cl, t);
                    warnSpiOnce(service, cl, "hasNext", t);
                    return null;
                }
                if (!more) {
                    break;
                }
                try {
                    S impl = it.next();
                    if (impl != null) {
                        return impl;
                    }
                } catch (Throwable t) {
                    logSpiFailure("loader-iterator-next", service, cl, t);
                    warnSpiOnce(service, cl, "next", t);
                }
            }
        } catch (Throwable t) {
            logSpiFailure("loader-ServiceLoader", service, cl, t);
            warnSpiOnce(service, cl, "ServiceLoader.load", t);
        }
        return null;
    }

    private static void warnSpiOnce(Class<?> service, ClassLoader cl, String phase, Throwable t) {
        if (service == null || t == null) {
            return;
        }
        String clName = cl == null ? "null" : cl.getClass().getName();
        VyneLogger.warn("[SPI] " + phase + " " + service.getName() + " loader=" + clName
            + ": " + t.getClass().getSimpleName()
            + (t.getMessage() != null ? (": " + t.getMessage()) : ""));
    }

    private static <S> S tryInstantiateFromMetaInf(Class<S> service, ClassLoader cl) {
        if (cl == null) {
            return null;
        }
        Class<S> localIface = resolveServiceInLoader(service, cl);
        if (localIface == null) {
            return null;
        }
        String path = "META-INF/services/" + service.getName();
        try (InputStream in = cl.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                try {
                    Class<?> impl = Class.forName(line, true, cl);
                    if (!localIface.isAssignableFrom(impl)) {
                        continue;
                    }
                    java.lang.reflect.Constructor<?> ctor = impl.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    return localIface.cast(ctor.newInstance());
                } catch (Throwable t) {
                    logSpiFailure("metaInf-line:" + line, service, cl, t);
                    VyneLogger.warn("[SPI] META-INF provider failed class=" + line + " for "
                        + service.getName() + ": " + t.getClass().getSimpleName()
                        + (t.getMessage() != null ? (": " + t.getMessage()) : ""));
                }
            }
        } catch (Throwable t) {
            logSpiFailure("metaInf-open", service, cl, t);
        }
        return null;
    }

    private static ClassLoader[] loaders(ClassLoader staticFieldLoader, Class<?> service) {
        LinkedHashSet<ClassLoader> set = new LinkedHashSet<>();
        for (ClassLoader ex : EXTRA_SPI_LOADERS) {
            add(set, ex);
        }
        add(set, dev.stolym.vyne.vcl.VCLManager.getCanonicalKnotLoader());
        for (ClassLoader cl = dev.stolym.vyne.vcl.VCLManager.getCanonicalKnotLoader();
             cl != null;
             cl = cl.getParent()) {
            add(set, cl);
        }
        for (ClassLoader cl = staticFieldLoader; cl != null; cl = cl.getParent()) {
            add(set, cl);
        }
        add(set, Thread.currentThread().getContextClassLoader());
        for (ClassLoader cl = service.getClassLoader(); cl != null; cl = cl.getParent()) {
            add(set, cl);
        }
        add(set, ServiceLoaderUtil.class.getClassLoader());
        ClassLoader sys = ClassLoader.getSystemClassLoader();
        add(set, sys);
        ClassLoader p = sys;
        while (p != null) {
            add(set, p);
            p = p.getParent();
        }
        try {
            add(set, ClassLoader.getPlatformClassLoader());
        } catch (Throwable ignored) {
        }
        return set.toArray(new ClassLoader[0]);
    }

    private static void add(LinkedHashSet<ClassLoader> set, ClassLoader cl) {
        if (cl != null) {
            set.add(cl);
        }
    }
}
