/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.dynamic.scanner;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ReferenceReport {
    public static final class MethodRef {
        public final String owner;
        public final String name;
        public final String desc;
        public final boolean itf;
        public final int opcode; 
        public int callSites;    

        public MethodRef(String owner, String name, String desc, boolean itf, int opcode) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
            this.itf = itf;
            this.opcode = opcode;
        }

        public String key() {
            return owner + "." + name + desc + (itf ? "[itf]" : "");
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MethodRef mr)) return false;
            return owner.equals(mr.owner) && name.equals(mr.name) && desc.equals(mr.desc)
                && itf == mr.itf && opcode == mr.opcode;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(owner, name, desc, itf, opcode);
        }
    }

    public static final class FieldRef {
        public final String owner;
        public final String name;
        public final String desc;
        public final boolean isStatic;

        public FieldRef(String owner, String name, String desc, boolean isStatic) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
            this.isStatic = isStatic;
        }

        public String key() {
            return owner + "." + name + ":" + desc;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FieldRef fr)) return false;
            return owner.equals(fr.owner) && name.equals(fr.name) && desc.equals(fr.desc)
                && isStatic == fr.isStatic;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(owner, name, desc, isStatic);
        }
    }

    public static final class ClassUsage {
        public final String owner;
        public final Set<MethodRef> methods = new LinkedHashSet<>();
        public final Set<FieldRef> fields = new LinkedHashSet<>();
        public final Set<String> newCtorDescs = new LinkedHashSet<>();
        public boolean usedAsCheckcast;
        public boolean usedAsInstanceof;
        public boolean usedAsSupertype;
        public boolean usedAsInterface;
        public boolean usedInSignature;

        public ClassUsage(String owner) {
            this.owner = owner;
        }
    }

    public final Set<String> ownedClasses = new LinkedHashSet<>();

    public final Map<String, String[]> ownedClassHierarchy = new LinkedHashMap<>();

    public final Map<String, ClassUsage> externalUsage = new LinkedHashMap<>();

    public final Map<String, String> declaredDependencies = new LinkedHashMap<>();

    public String jarPathHint;
    public String jarSha256;
    public long totalClassCount;

    public ClassUsage usageFor(String owner) {
        return externalUsage.computeIfAbsent(owner, ClassUsage::new);
    }

    public Collection<ClassUsage> allExternal() {
        return externalUsage.values();
    }

    public boolean owns(String internalName) {
        return ownedClasses.contains(internalName);
    }
}
