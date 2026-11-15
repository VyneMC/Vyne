/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.vcl;

import java.util.ArrayList;
import java.util.LinkedHashSet;

public final class FabricLoaderProbes {
    private FabricLoaderProbes() {}

    public static ClassLoader[] reflectionProbesForFabricLauncherBase() {
        ArrayList<ClassLoader> list = new ArrayList<>(8);
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl != null) {
            list.add(tccl);
        }
        try {
            ClassLoader ck = VCLManager.getCanonicalKnotLoader();
            if (ck != null) {
                list.add(ck);
            }
        } catch (Throwable ignored) {
        }
        ClassLoader sys = ClassLoader.getSystemClassLoader();
        if (sys != null) {
            list.add(sys);
        }
        ClassLoader here = FabricLoaderProbes.class.getClassLoader();
        if (here != null) {
            list.add(here);
        }
        return new LinkedHashSet<>(list).toArray(new ClassLoader[0]);
    }
}
