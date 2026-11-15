/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.tools.hooks;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.lang.model.element.Modifier;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class HookBodies {
    private static final String PACKAGE = "dev.stolym.vyne.hooks.mixin.gen";
    private static final String SHADOW = "@org.spongepowered.asm.mixin.Shadow";
    private static final String FINAL = "@org.spongepowered.asm.mixin.Final";
    private static final String OVERWRITE = "@org.spongepowered.asm.mixin.Overwrite";

    private record Wanted(String name, String desc, boolean overrides) {}

    private record Span(int startLine, int endLine, String key) {}

    private final Map<String, List<Wanted>> wanted = new TreeMap<>();
    private final Map<String, Set<String>> addedFields = new TreeMap<>();
    private final Set<String> unmatched = new TreeSet<>();
    private int initialisers;
    private final Map<String, String> accessRules = new HashMap<>();
    private final Set<String> excluded = new TreeSet<>();
    private final Map<String, ClassNode> open = new HashMap<>();
    private final Map<String, List<Span>> spans = new LinkedHashMap<>();
    private final Map<String, String> mixinSide = new TreeMap<>();
    private int methods;
    private int shadows;
    private final Map<String, Integer> skipped = new TreeMap<>();

    public static void main(String[] args) throws Exception {
        Path surface = Path.of(args[0]);
        Path decompiled = Path.of(args[1]);
        Path openJar = Path.of(args[2]);
        Path outRoot = Path.of(args[3]);
        Path config = Path.of(args[4]);
        Path exclude = Path.of(args[5]);
        Path spanFile = Path.of(args[6]);
        Path errors = args.length > 7 ? Path.of(args[7]) : null;

        HookBodies gen = new HookBodies();
        if (Files.isRegularFile(exclude)) {
            for (String line : Files.readAllLines(exclude)) {
                if (!line.isBlank() && !line.startsWith("#")) gen.excluded.add(line.trim());
            }
        }
        if (errors != null && Files.isRegularFile(errors) && Files.isRegularFile(spanFile)) {
            int added = gen.excludeFromErrors(errors, spanFile, outRoot);
            Files.write(exclude, header(gen.excluded), StandardCharsets.UTF_8);
            System.out.println("Excluded " + added + " element(s) that did not compile");
        }
        gen.loadSurface(surface);
        gen.loadAccess(surface.resolveSibling("accesstransformer.cfg"));
        gen.loadOpen(openJar);
        gen.generate(decompiled, outRoot);
        gen.writeConfig(config);
        gen.writeSpans(spanFile);
        Files.write(spanFile.resolveSibling("hook-unmatched.txt"), gen.unmatched);
        System.out.println("Generated " + gen.mixinSide.size() + " mixins, " + gen.methods + " method bodies, "
                + gen.initialisers + " field initialisers, " + gen.shadows + " shadows; skipped " + gen.skipped);
    }

    private static List<String> header(Set<String> excluded) {
        List<String> out = new ArrayList<>();
        out.add("# Members the hook generator leaves out because the copied code does not compile.");
        out.add("# owner#method(desc) = a method body to write by hand; shadow:owner#member = a shadow left out.");
        out.addAll(excluded);
        return out;
    }

    private void loadSurface(Path file) throws IOException {
        for (String line : Files.readAllLines(file)) {
            String[] p = line.split("\t", -1);
            if (p.length >= 8 && p[1].equals("field")) {
                addedFields.computeIfAbsent(p[0], k -> new TreeSet<>()).add(p[2]);
                wanted.computeIfAbsent(p[0], k -> new ArrayList<>());
                continue;
            }
            if (p.length < 8 || !p[1].equals("method")) continue;
            if (p[2].equals("<clinit>")) continue;
            if (p[2].equals("<init>")) {
                skipped.merge("constructors", 1, Integer::sum);
                continue;
            }
            wanted.computeIfAbsent(p[0], k -> new ArrayList<>())
                    .add(new Wanted(p[2], p[3], p[7].equals("override") || p[7].equals("default")));
        }
    }

    private void loadAccess(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return;
        for (String line : Files.readAllLines(file)) {
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash);
            String[] p = line.trim().split("\\s+");
            if (p.length < 3) continue;
            accessRules.put(p[1].replace('.', '/') + "#" + p[2], p[0].replace("-f", "").replace("+f", ""));
        }
    }

    private String widenSource(String owner, Wanted w, String code) {
        String rule = accessRules.get(owner + "#" + w.name() + w.desc());
        if (rule == null) rule = accessRules.get(owner + "#*()");
        if (rule == null) return code;
        if (rule.equals("public")) {
            return code.replaceFirst("^((?:@[\\w.]+(?:\\([^)]*\\))?\\s+)*)(?:(?:private|protected|public)\\s+)?", "$1public ");
        }
        if (rule.equals("protected") && code.matches("(?s)^((?:@[\\w.]+(?:\\([^)]*\\))?\\s+)*)private\\s.*")) {
            return code.replaceFirst("private\\s", "protected ");
        }
        return code;
    }

    private void loadOpen(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
                if (!entry.getName().endsWith(".class")) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    ClassNode node = new ClassNode();
                    new ClassReader(in).accept(node, ClassReader.SKIP_CODE);
                    open.put(node.name, node);
                }
            }
        }
    }

    private void generate(Path decompiled, Path outRoot) throws IOException {
        Path genDir = outRoot.resolve(PACKAGE.replace('.', '/'));
        if (Files.isDirectory(genDir)) {
            try (Stream<Path> walk = Files.walk(genDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
        Map<String, List<String>> byFile = new TreeMap<>();
        for (String owner : wanted.keySet()) {
            int dollar = owner.indexOf('$');
            byFile.computeIfAbsent(dollar < 0 ? owner : owner.substring(0, dollar), k -> new ArrayList<>()).add(owner);
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, List<String>> entry : byFile.entrySet()) {
                Path source = decompiled.resolve(entry.getKey() + ".java");
                if (!Files.isRegularFile(source)) {
                    skipped.merge("no decompiled source", entry.getValue().size(), Integer::sum);
                    continue;
                }
                Iterable<? extends JavaFileObject> units = files.getJavaFileObjects(source.toFile());
                JavacTask task = (JavacTask) compiler.getTask(null, files, d -> { }, List.of("-proc:none"), null, units);
                CompilationUnitTree unit = task.parse().iterator().next();
                SourcePositions positions = Trees.instance(task).getSourcePositions();
                String text = Files.readString(source);
                for (String owner : entry.getValue()) {
                    ClassTree type = find(unit, owner);
                    if (type == null) {
                        skipped.merge("class not found in source", 1, Integer::sum);
                        continue;
                    }
                    emit(owner, type, unit, positions, text, outRoot);
                }
            }
        }
    }

    private static ClassTree find(CompilationUnitTree unit, String owner) {
        String[] parts = owner.substring(owner.lastIndexOf('/') + 1).split("\\$");
        List<? extends Tree> scope = unit.getTypeDecls();
        ClassTree found = null;
        for (String part : parts) {
            found = null;
            for (Tree t : scope) {
                if (t instanceof ClassTree c && c.getSimpleName().contentEquals(part)) {
                    found = c;
                    break;
                }
            }
            if (found == null) return null;
            scope = found.getMembers();
        }
        return found;
    }

    private void emit(String owner, ClassTree type, CompilationUnitTree unit, SourcePositions positions, String text,
                      Path outRoot) throws IOException {
        Tree.Kind kind = type.getKind();
        if (kind == Tree.Kind.INTERFACE || kind == Tree.Kind.ANNOTATION_TYPE) {
            skipped.merge("interfaces", wanted.get(owner).size(), Integer::sum);
            return;
        }
        String targetName = owner.replace('/', '.').replace('$', '.');
        String binaryName = owner.replace('/', '.');
        String targetPackage = owner.substring(0, owner.lastIndexOf('/')).replace('/', '.');
        String simple = owner.substring(owner.lastIndexOf('/') + 1).replace('$', '_') + "Hooks";
        String mixinPackage = PACKAGE + "." + targetPackage;

        List<Wanted> wants = new ArrayList<>(wanted.get(owner));
        Map<MethodTree, Wanted> implement = new LinkedHashMap<>();
        Map<String, String> classVars = new HashMap<>();
        for (var p : type.getTypeParameters()) classVars.put(p.getName().toString(), bound(p));
        for (Tree member : type.getMembers()) {
            if (!(member instanceof MethodTree m)) continue;
            Map<String, String> vars = new HashMap<>(classVars);
            for (var p : m.getTypeParameters()) vars.put(p.getName().toString(), bound(p));
            for (Wanted w : wants) {
                if (excluded.contains(owner + "#" + w.name() + w.desc())) continue;
                if (matches(m, w, vars)) {
                    implement.put(m, w);
                    wants.remove(w);
                    break;
                }
            }
        }
        for (Wanted w : wants) {
            if (!excluded.contains(owner + "#" + w.name() + w.desc())) {
                skipped.merge("not matched in source", 1, Integer::sum);
                unmatched.add(owner + "#" + w.name() + w.desc());
            }
        }
        Set<String> fieldNames = addedFields.getOrDefault(owner, Set.of());
        List<VariableTree> initFields = new ArrayList<>();
        for (Tree member : type.getMembers()) {
            if (member instanceof VariableTree v && fieldNames.contains(v.getName().toString()) && v.getInitializer() != null
                    && positions.getEndPosition(unit, v.getInitializer()) > 0
                    && !slice(text, positions, unit, v).startsWith(v.getName().toString())
                    && !excluded.contains("init:" + owner + "#" + v.getName())) {
                initFields.add(v);
            }
        }
        if (implement.isEmpty() && initFields.isEmpty()) return;

        List<String> body = new ArrayList<>();
        List<Span> fileSpans = new ArrayList<>();
        Set<String> notPresent = new TreeSet<>();
        for (Wanted w : wanted.get(owner)) {
            boolean done = implement.containsValue(w);
            if (w.overrides() && !done) notPresent.add(w.name() + w.desc());
        }

        Set<String> used = new java.util.HashSet<>();
        for (MethodTree m : implement.keySet()) used.addAll(referencedNames(m, unit));
        Set<String> mutable = new java.util.HashSet<>();
        for (VariableTree v : initFields) {
            used.add(v.getName().toString());
            mutable.add(v.getName().toString());
            used.addAll(referencedNames(v.getInitializer(), unit));
        }

        for (Tree member : type.getMembers()) {
            if (member instanceof VariableTree v) {
                if (!used.contains(v.getName().toString())) continue;
                String key = "shadow:" + owner + "#" + v.getName();
                if (excluded.contains(key)) continue;
                String decl = slice(text, positions, unit, v);
                if (kind == Tree.Kind.ENUM && decl.startsWith(v.getName().toString())) {
                    addSpan(body, fileSpans, key, List.of(SHADOW + " " + FINAL + " public static " + targetName + " " + v.getName() + ";"));
                } else {
                    String shadow = shadowField(v, text, positions, unit);
                    if (mutable.contains(v.getName().toString())) shadow = "@org.spongepowered.asm.mixin.Mutable " + shadow;
                    addSpan(body, fileSpans, key, List.of(shadow));
                }
                shadows++;
            } else if (member instanceof MethodTree m && !implement.containsKey(m)) {
                if (m.getName().contentEquals("<init>") || !used.contains(m.getName().toString())) continue;
                String sig = signatureKey(m);
                String key = "shadow:" + owner + "#" + m.getName() + "/" + sig;
                if (excluded.contains(key) || isNotPresent(m, notPresent)) continue;
                String shadow = shadowMethod(m, text, positions, unit, kind);
                if (shadow == null) continue;
                addSpan(body, fileSpans, key, List.of(shadow));
                shadows++;
            }
        }
        for (Map.Entry<MethodTree, Wanted> entry : implement.entrySet()) {
            MethodTree m = entry.getKey();
            Wanted w = entry.getValue();
            String code = rewriteThis(m, text, positions, unit, targetName);
            code = stripOverride(code);
            code = widenSource(owner, w, code);
            List<String> lines = new ArrayList<>();
            if (!w.overrides()) lines.add(OVERWRITE);
            lines.addAll(List.of(code.split("\n", -1)));
            addSpan(body, fileSpans, owner + "#" + w.name() + w.desc(), lines);
            methods++;
        }

        List<String[]> staticInit = new ArrayList<>();
        List<String[]> instanceInit = new ArrayList<>();
        for (VariableTree v : initFields) {
            boolean isStatic = v.getModifiers().getFlags().contains(Modifier.STATIC);
            String value = rewriteThis(v.getInitializer(), text, positions, unit, targetName);
            String assign = (isStatic ? "" : "this.") + v.getName() + " = " + value + ";";
            (isStatic ? staticInit : instanceInit).add(new String[] {"init:" + owner + "#" + v.getName(), assign});
            initialisers++;
        }
        if (!staticInit.isEmpty()) {
            addInitBlock(body, fileSpans, List.of("static {"), staticInit);
        }
        if (!instanceInit.isEmpty()) {
            addInitBlock(body, fileSpans, List.of(
                    "@org.spongepowered.asm.mixin.injection.Inject(method = \"<init>*\", at = @org.spongepowered.asm.mixin.injection.At(\"RETURN\"))",
                    "private void vyne$initNeoForgeFields(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {"),
                    instanceInit);
        }

        StringBuilder out = new StringBuilder();
        out.append("/*\n * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.\n")
                .append(" * Full license text is available in the LICENSE file in the project root.\n *\n")
                .append(" * Generated from NeoForge's patched ").append(targetName)
                .append(" (LGPL-2.1-only) by ./gradlew generateHookBodies; owned by Vyne from here on.\n */\n\n");
        out.append("package ").append(mixinPackage).append(";\n\n");
        Set<String> importedNames = new java.util.HashSet<>();
        for (var imp : unit.getImports()) {
            String line = slice(text, positions, unit, imp).replace('$', '.');
            String importedName = line.replaceAll(";\\s*$", "");
            importedName = importedName.substring(importedName.lastIndexOf('.') + 1);
            if (!importedName.equals("*") && !imp.isStatic() && !importedNames.add(importedName)) continue;
            out.append(line).append('\n');
        }
        out.append("import ").append(targetPackage).append(".*;\n");
        String top = owner.contains("$") ? owner.substring(0, owner.indexOf('$')) : owner;
        out.append("import ").append(top.replace('/', '.')).append(".*;\n");
        if (!owner.equals(top) && owner.lastIndexOf('$') != owner.indexOf('$')) {
            out.append("import ").append(owner.substring(0, owner.lastIndexOf('$')).replace('/', '.').replace('$', '.')).append(".*;\n");
        }
        out.append('\n');
        int headerStart = (int) out.chars().filter(c -> c == '\n').count() + 1;
        out.append("@org.spongepowered.asm.mixin.Mixin(targets = \"").append(binaryName).append("\")\n");
        out.append("@SuppressWarnings({\"unchecked\", \"rawtypes\"})\n");
        out.append("public abstract class ").append(simple);
        if (!type.getTypeParameters().isEmpty()) {
            List<String> params = new ArrayList<>();
            for (Tree p : type.getTypeParameters()) params.add(slice(text, positions, unit, p));
            out.append('<').append(String.join(", ", params)).append('>');
        }
        String constructor = null;
        if (kind == Tree.Kind.CLASS && type.getExtendsClause() != null) {
            ClassNode node = open.get(owner);
            String superCall = node == null ? null : superCall(node.superName);
            if (superCall != null) {
                out.append(" extends ").append(slice(text, positions, unit, type.getExtendsClause()));
                constructor = "    protected " + simple + "() {\n        " + superCall + "\n    }";
            }
        }
        if (!type.getImplementsClause().isEmpty() && kind == Tree.Kind.CLASS && !excluded.contains("implements:" + owner)) {
            List<String> interfaces = new ArrayList<>();
            for (Tree t : type.getImplementsClause()) interfaces.add(slice(text, positions, unit, t));
            out.append(" implements ").append(String.join(", ", interfaces));
        }
        out.append(" {\n");
        int headerLines = (int) out.chars().filter(c -> c == '\n').count();
        if (constructor != null) {
            out.append(constructor).append("\n\n");
            headerLines += constructor.split("\n").length + 1;
        }
        int line = headerLines + 1;
        List<Span> shifted = new ArrayList<>();
        shifted.add(new Span(headerStart, headerLines, "implements:" + owner));
        for (Span s : fileSpans) shifted.add(new Span(s.startLine() + line - 1, s.endLine() + line - 1, s.key()));
        for (String l : body) out.append(l.isEmpty() ? "" : "    ").append(l).append('\n');
        out.append("}\n");

        Path file = outRoot.resolve(mixinPackage.replace('.', '/')).resolve(simple + ".java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, out.toString());
        spans.put(outRoot.relativize(file).toString(), shifted);
        String side = owner.startsWith("net/minecraft/client/") || owner.startsWith("com/mojang/blaze3d/")
                || owner.startsWith("com/mojang/realmsclient/") ? "client"
                : owner.startsWith("net/minecraft/server/dedicated/") ? "server" : "mixins";
        mixinSide.put(targetPackage + "." + simple, side);
    }

    private static void addInitBlock(List<String> body, List<Span> spans, List<String> head, List<String[]> assignments) {
        body.addAll(head);
        for (String[] assignment : assignments) {
            int start = body.size() + 1;
            for (String l : assignment[1].split("\n", -1)) body.add("    " + l);
            spans.add(new Span(start, body.size(), assignment[0]));
        }
        body.add("}");
        body.add("");
    }

    private static void addSpan(List<String> body, List<Span> spans, String key, List<String> lines) {
        int start = body.size() + 1;
        for (String l : lines) body.addAll(List.of(l.split("\n", -1)));
        spans.add(new Span(start, body.size(), key));
        body.add("");
    }

    private static String bound(com.sun.source.tree.TypeParameterTree parameter) {
        if (parameter.getBounds().isEmpty()) return "Object";
        return simpleOfSource(parameter.getBounds().get(0).toString());
    }

    private static boolean matches(MethodTree m, Wanted w, Map<String, String> typeVars) {
        if (!m.getName().contentEquals(w.name())) return false;
        Type[] args = Type.getArgumentTypes(w.desc());
        if (args.length != m.getParameters().size()) return false;
        for (int i = 0; i < args.length; i++) {
            String source = rawSource(m.getParameters().get(i).getType().toString());
            String variable = source.replace("[]", "");
            if (typeVars.containsKey(variable)) {
                String dims = source.substring(variable.length());
                if (!simpleOf(args[i]).equals(typeVars.get(variable) + dims)) return false;
                continue;
            }
            if (!simpleOf(args[i]).equals(simpleOfSource(m.getParameters().get(i).getType().toString()))) return false;
        }
        return true;
    }

    private static String rawSource(String source) {
        String s = source.replaceAll("@[\\w.]+(\\([^)]*\\))?\\s*", "").replace("...", "[]").trim();
        int angle = s.indexOf('<');
        return angle >= 0 ? s.substring(0, angle) : s;
    }

    private static String simpleOf(Type type) {
        if (type.getSort() == Type.ARRAY) {
            return simpleOf(type.getElementType()) + "[]".repeat(type.getDimensions());
        }
        String name = type.getClassName();
        name = name.substring(name.lastIndexOf('.') + 1);
        return name.substring(name.lastIndexOf('$') + 1);
    }

    private static String simpleOfSource(String source) {
        String s = source.replaceAll("@[\\w.]+(\\([^)]*\\))?\\s*", "");
        s = s.replace("...", "[]");
        StringBuilder out = new StringBuilder();
        int depth = 0;
        for (char c : s.toCharArray()) {
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (depth == 0 && !Character.isWhitespace(c)) out.append(c);
        }
        String result = out.toString();
        String dims = "";
        while (result.endsWith("[]")) {
            dims += "[]";
            result = result.substring(0, result.length() - 2);
        }
        result = result.substring(result.lastIndexOf('.') + 1);
        if (result.length() == 1 && Character.isUpperCase(result.charAt(0))) result = "Object";
        return result + dims;
    }

    private static String signatureKey(MethodTree m) {
        List<String> params = new ArrayList<>();
        for (VariableTree p : m.getParameters()) params.add(simpleOfSource(p.getType().toString()));
        return String.join(",", params);
    }

    private static boolean isNotPresent(MethodTree m, Set<String> notPresent) {
        for (String key : notPresent) {
            String name = key.substring(0, key.indexOf('('));
            if (!m.getName().contentEquals(name)) continue;
            Type[] args = Type.getArgumentTypes(key.substring(key.indexOf('(')));
            if (args.length != m.getParameters().size()) continue;
            boolean same = true;
            for (int i = 0; i < args.length && same; i++) {
                same = simpleOf(args[i]).equals(simpleOfSource(m.getParameters().get(i).getType().toString()));
            }
            if (same) return true;
        }
        return false;
    }

    private static String shadowField(VariableTree v, String text, SourcePositions positions, CompilationUnitTree unit) {
        Set<Modifier> flags = v.getModifiers().getFlags();
        StringBuilder out = new StringBuilder(SHADOW).append(' ');
        if (flags.contains(Modifier.FINAL)) out.append(FINAL).append(' ');
        for (Modifier m : flags) {
            if (m != Modifier.FINAL && m != Modifier.TRANSIENT && m != Modifier.VOLATILE) out.append(m).append(' ');
        }
        out.append(slice(text, positions, unit, v.getType())).append(' ').append(v.getName()).append(';');
        return out.toString();
    }

    private static String shadowMethod(MethodTree m, String text, SourcePositions positions, CompilationUnitTree unit,
                                       Tree.Kind kind) {
        if (m.getReturnType() == null) return null;
        Set<Modifier> flags = m.getModifiers().getFlags();
        boolean isAbstract = flags.contains(Modifier.ABSTRACT);
        StringBuilder out = new StringBuilder(SHADOW).append(' ');
        for (Modifier f : flags) {
            if (f == Modifier.SYNCHRONIZED || f == Modifier.NATIVE || f == Modifier.STRICTFP || f == Modifier.DEFAULT
                    || f == Modifier.FINAL) continue;
            out.append(f).append(' ');
        }
        if (!m.getTypeParameters().isEmpty()) {
            List<String> params = new ArrayList<>();
            for (Tree p : m.getTypeParameters()) params.add(slice(text, positions, unit, p));
            out.append('<').append(String.join(", ", params)).append("> ");
        }
        out.append(slice(text, positions, unit, m.getReturnType())).append(' ').append(m.getName()).append('(');
        List<String> params = new ArrayList<>();
        for (VariableTree p : m.getParameters()) {
            params.add(slice(text, positions, unit, p.getType()) + " " + p.getName());
        }
        out.append(String.join(", ", params)).append(')');
        if (isAbstract) {
            out.append(';');
        } else {
            out.append(" { throw new AssertionError(); }");
        }
        return out.toString();
    }

    private static Set<String> referencedNames(Tree m, CompilationUnitTree unit) {
        Set<String> out = new java.util.HashSet<>();
        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitIdentifier(IdentifierTree node, Void unused) {
                out.add(node.getName().toString());
                return super.visitIdentifier(node, unused);
            }

            @Override
            public Void visitMemberSelect(MemberSelectTree node, Void unused) {
                if (node.getExpression() instanceof IdentifierTree id && id.getName().contentEquals("this")) {
                    out.add(node.getIdentifier().toString());
                }
                return super.visitMemberSelect(node, unused);
            }
        }.scan(new TreePath(new TreePath(unit), m), null);
        return out;
    }

    private static String rewriteThis(Tree m, String text, SourcePositions positions, CompilationUnitTree unit,
                                      String targetName) {
        int start = (int) positions.getStartPosition(unit, m);
        int end = (int) positions.getEndPosition(unit, m);
        List<int[]> replace = new ArrayList<>();
        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitIdentifier(IdentifierTree node, Void unused) {
                if (node.getName().contentEquals("this")) {
                    TreePath parent = getCurrentPath().getParentPath();
                    boolean receiver = parent.getLeaf() instanceof MemberSelectTree select && select.getExpression() == node;
                    if (!receiver) {
                        replace.add(new int[] {(int) positions.getStartPosition(unit, node), (int) positions.getEndPosition(unit, node)});
                    }
                }
                return super.visitIdentifier(node, unused);
            }
        }.scan(new TreePath(new TreePath(unit), m), null);
        StringBuilder code = new StringBuilder(text.substring(start, end));
        replace.sort((a, b) -> b[0] - a[0]);
        for (int[] r : replace) {
            code.replace(r[0] - start, r[1] - start, "((" + targetName + ") (Object) this)");
        }
        String result = code.toString();
        int indent = 0;
        int lineStart = text.lastIndexOf('\n', start) + 1;
        while (lineStart + indent < start && text.charAt(lineStart + indent) == ' ') indent++;
        StringBuilder dedented = new StringBuilder();
        for (String line : result.split("\n", -1)) {
            int strip = 0;
            while (strip < indent && strip < line.length() && line.charAt(strip) == ' ') strip++;
            if (dedented.length() > 0) dedented.append('\n');
            dedented.append(line.substring(strip));
        }
        return dedented.toString();
    }

    private static String stripOverride(String code) {
        return code.replaceAll("@Override\\s*", "");
    }

    private String superCall(String superName) {
        if (superName == null || superName.equals("java/lang/Object")) return null;
        ClassNode parent = open.get(superName);
        String desc = null;
        if (parent != null) {
            for (MethodNode m : parent.methods) {
                if (!m.name.equals("<init>") || (m.access & Opcodes.ACC_SYNTHETIC) != 0) continue;
                if (desc == null || Type.getArgumentTypes(m.desc).length < Type.getArgumentTypes(desc).length) desc = m.desc;
            }
        } else {
            try {
                Class<?> type = Class.forName(superName.replace('/', '.'), false, ClassLoader.getPlatformClassLoader());
                for (var c : type.getDeclaredConstructors()) {
                    if (java.lang.reflect.Modifier.isPrivate(c.getModifiers())) continue;
                    String d = Type.getConstructorDescriptor(c);
                    if (desc == null || c.getParameterCount() < Type.getArgumentTypes(desc).length) desc = d;
                }
            } catch (ClassNotFoundException | LinkageError e) {
                return null;
            }
        }
        if (desc == null) return null;
        int arity = Type.getArgumentTypes(desc).length;
        int sameArity = 0;
        if (parent != null) {
            for (MethodNode m : parent.methods) {
                if (m.name.equals("<init>") && (m.access & Opcodes.ACC_SYNTHETIC) == 0
                        && Type.getArgumentTypes(m.desc).length == arity) sameArity++;
            }
        }
        boolean unique = sameArity <= 1;
        List<String> args = new ArrayList<>();
        for (Type t : Type.getArgumentTypes(desc)) {
            args.add(switch (t.getSort()) {
                case Type.BOOLEAN -> "false";
                case Type.CHAR -> "'\\0'";
                case Type.BYTE -> "(byte) 0";
                case Type.SHORT -> "(short) 0";
                case Type.INT -> "0";
                case Type.LONG -> "0L";
                case Type.FLOAT -> "0.0F";
                case Type.DOUBLE -> "0.0";
                default -> unique ? "null" : "(" + t.getClassName().replace('$', '.') + ") null";
            });
        }
        return "super(" + String.join(", ", args) + ");";
    }

    private static String slice(String text, SourcePositions positions, CompilationUnitTree unit, Tree tree) {
        return text.substring((int) positions.getStartPosition(unit, tree), (int) positions.getEndPosition(unit, tree));
    }

    private void writeConfig(Path config) throws IOException {
        Map<String, List<String>> sides = new TreeMap<>();
        for (Map.Entry<String, String> e : mixinSide.entrySet()) {
            sides.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        }
        StringBuilder out = new StringBuilder("{\n  \"required\": true,\n  \"package\": \"").append(PACKAGE)
                .append("\",\n  \"compatibilityLevel\": \"JAVA_21\",\n  \"plugin\": \"dev.stolym.vyne.hooks.HooksMixinPlugin\",\n");
        for (String side : List.of("mixins", "client", "server")) {
            List<String> list = sides.getOrDefault(side, List.of());
            out.append("  \"").append(side).append("\": [");
            for (int i = 0; i < list.size(); i++) {
                out.append(i == 0 ? "\n" : ",\n").append("    \"").append(list.get(i)).append('"');
            }
            out.append(list.isEmpty() ? "],\n" : "\n  ],\n");
        }
        out.append("  \"injectors\": {\n    \"defaultRequire\": 1\n  }\n}\n");
        Files.createDirectories(config.getParent());
        Files.writeString(config, out.toString());
    }

    private void writeSpans(Path file) throws IOException {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, List<Span>> e : spans.entrySet()) {
            for (Span s : e.getValue()) out.add(e.getKey() + "\t" + s.startLine() + "\t" + s.endLine() + "\t" + s.key());
        }
        Files.createDirectories(file.getParent());
        Files.write(file, out);
    }

    private int excludeFromErrors(Path errors, Path spanFile, Path outRoot) throws IOException {
        Map<String, List<Span>> known = new HashMap<>();
        for (String line : Files.readAllLines(spanFile)) {
            String[] p = line.split("\t");
            known.computeIfAbsent(p[0], k -> new ArrayList<>())
                    .add(new Span(Integer.parseInt(p[1]), Integer.parseInt(p[2]), p[3]));
        }
        Pattern error = Pattern.compile("^(.*\\.java):(\\d+): error:");
        int added = 0;
        String root = outRoot.toAbsolutePath().toString() + "/";
        for (String line : Files.readAllLines(errors)) {
            Matcher m = error.matcher(line);
            if (!m.find()) continue;
            String file = m.group(1);
            if (!file.startsWith(root)) continue;
            String relative = file.substring(root.length());
            int number = Integer.parseInt(m.group(2));
            for (Span s : known.getOrDefault(relative, List.of())) {
                if (number >= s.startLine() && number <= s.endLine() && excluded.add(s.key())) added++;
            }
        }
        return added;
    }
}
