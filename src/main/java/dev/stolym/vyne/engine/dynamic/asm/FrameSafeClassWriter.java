/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.engine.dynamic.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

public final class FrameSafeClassWriter extends ClassWriter {
    public FrameSafeClassWriter(int flags) {
        super(flags);
    }

    public FrameSafeClassWriter(ClassReader classReader, int flags) {
        super(classReader, flags);
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        try {
            return super.getCommonSuperClass(type1, type2);
        } catch (TypeNotPresentException e) {
            return "java/lang/Object";
        } catch (LinkageError e) {
            return "java/lang/Object";
        } catch (RuntimeException e) {
            Throwable c = e.getCause();
            if (c instanceof TypeNotPresentException || c instanceof ClassNotFoundException) {
                return "java/lang/Object";
            }
            throw e;
        }
    }
}
