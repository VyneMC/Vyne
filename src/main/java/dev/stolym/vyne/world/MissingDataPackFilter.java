/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the Licenses/LICENSE_VYNE file in the project root.
 */

package dev.stolym.vyne.world;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

public final class MissingDataPackFilter {
    private static final String NEEDLE = "Missing data pack ";

    private static volatile boolean installed;

    private MissingDataPackFilter() {}

    public static void install() {
        if (installed) return;
        installed = true;
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration cfg = ctx.getConfiguration();
            Logger root = (Logger) LogManager.getRootLogger();
            Filter filter = new Impl();
            root.addFilter(filter);
            cfg.addFilter(filter);
            ctx.updateLoggers();
        } catch (Throwable ignored) {
        }
    }

    private static final class Impl extends AbstractFilter {
        Impl() {
            super(Result.DENY, Result.NEUTRAL);
        }

        @Override
        public Result filter(LogEvent event) {
            if (event == null) return Result.NEUTRAL;
            Message m = event.getMessage();
            if (m == null) return Result.NEUTRAL;
            return decide(m.getFormattedMessage(), event.getLevel());
        }

        @Override
        public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
            return decide(formatMaybe(msg, params), level);
        }

        @Override
        public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
            return decide(String.valueOf(msg), level);
        }

        @Override
        public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
            return decide(msg == null ? null : msg.getFormattedMessage(), level);
        }

        private static Result decide(String formatted, Level level) {
            if (formatted == null) return Result.NEUTRAL;
            if (level == null || !level.isMoreSpecificThan(Level.WARN)) return Result.NEUTRAL;
            int idx = formatted.indexOf(NEEDLE);
            if (idx < 0) return Result.NEUTRAL;
            String tail = formatted.substring(idx + NEEDLE.length()).trim();
            if (RegistryHealthPolicy.shouldDropPack(tail)) {
                return Result.DENY;
            }
            return Result.NEUTRAL;
        }

        private static String formatMaybe(String template, Object[] params) {
            if (template == null) return null;
            if (params == null || params.length == 0) return template;

            StringBuilder sb = new StringBuilder(template.length() + 16);
            int pi = 0;
            for (int i = 0; i < template.length(); i++) {
                char c = template.charAt(i);
                if (c == '{' && i + 1 < template.length() && template.charAt(i + 1) == '}'
                    && pi < params.length) {
                    sb.append(String.valueOf(params[pi++]));
                    i++;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
    }
}
