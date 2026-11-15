/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package cpw.mods.modlauncher.log;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class TransformingThrowablePatternConverter {
    private TransformingThrowablePatternConverter() {}

    public static String generateEnhancedStackTrace(Throwable throwable) {
        StringWriter out = new StringWriter();
        throwable.printStackTrace(new PrintWriter(out));
        return out.toString();
    }
}
