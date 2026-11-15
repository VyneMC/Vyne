/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.compat;

import dev.stolym.vyne.util.VyneLogger;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.IContainerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public final class MenuTypeCompat {
    private MenuTypeCompat() {}

    public static Object createForgeLikeMenuType(IContainerFactory<?> factory) {
        ClassLoader knot = dev.stolym.vyne.vcl.VCLManager.getCanonicalKnotLoader();
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        ClassLoader cl = knot != null ? knot
            : (tccl != null ? tccl : MenuTypeCompat.class.getClassLoader());
        try {
            return createExtendedScreenHandlerTypeSync(cl, factory);
        } catch (ClassNotFoundException e) {
            VyneLogger.warn("[IForgeMenuType] Fabric screen-handler API not on classpath: " + e.getMessage());
            return null;
        } catch (ClassCircularityError cce) {
            return retryMenuBridgeOnWorker(cl, factory, cce);
        } catch (Throwable t) {
            if (hasClassCircularityInChain(t)) {
                return retryMenuBridgeOnWorker(cl, factory, t);
            }
            VyneLogger.warn("[IForgeMenuType] ExtendedScreenHandlerType bridge failed: " + t);
            return null;
        }
    }

    private static boolean hasClassCircularityInChain(Throwable t) {
        for (Throwable x = t; x != null; x = x.getCause()) {
            if (x instanceof ClassCircularityError) {
                return true;
            }
        }
        return false;
    }

    private static Object retryMenuBridgeOnWorker(
        ClassLoader cl,
        IContainerFactory<?> factory,
        Throwable trigger
    ) {
        try {
            Object r = ForkJoinPool.commonPool().submit(() -> {
                Thread.currentThread().setContextClassLoader(cl);
                try {
                    return createExtendedScreenHandlerTypeSync(cl, factory);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }).get(60L, TimeUnit.SECONDS);
            if (r != null) {
                VyneLogger.info("[IForgeMenuType] ExtendedScreenHandlerType bridge succeeded on worker after: "
                    + trigger.getClass().getSimpleName());
            }
            return r;
        } catch (Exception e) {
            Throwable root = e.getCause() != null ? e.getCause() : e;
            VyneLogger.warn("[IForgeMenuType] ExtendedScreenHandlerType bridge failed (worker retry): "
                + trigger.getMessage() + " → " + root);
            return null;
        }
    }

    private static Object createExtendedScreenHandlerTypeSync(
        ClassLoader cl,
        IContainerFactory<?> factory
    ) throws Exception {
        Class<?> extended = Class.forName(
            "net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType",
            false,
            cl
        );
        Class<?> extendedFactory = Class.forName(
            "net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType$ExtendedFactory",
            false,
            cl
        );
        Class<?> packetCodecClass = loadMcClass(cl, "net.minecraft.network.codec.PacketCodec", "net.minecraft.class_9139");
        Class<?> valueFirstEncoder = loadMcClass(cl, "net.minecraft.network.codec.ValueFirstEncoder", "net.minecraft.class_9143");
        Class<?> packetDecoder = loadMcClass(cl, "net.minecraft.network.codec.PacketDecoder", "net.minecraft.class_9141");

        Object codec = buildPassthroughRegistryBufCodec(cl, packetCodecClass, valueFirstEncoder, packetDecoder);
        if (codec == null) {
            VyneLogger.warn("[IForgeMenuType] Could not build PacketCodec passthrough for ExtendedScreenHandlerType.");
            return null;
        }

        Object extFactoryProxy = Proxy.newProxyInstance(
            cl,
            new Class[]{extendedFactory},
            (proxy, method, args) -> {
                if (args == null || args.length != 3) {
                    return null;
                }
                int syncId = (Integer) args[0];
                Inventory inv = (Inventory) args[1];
                FriendlyByteBuf data = (FriendlyByteBuf) args[2];
                return factory.create(syncId, inv, data);
            }
        );

        Constructor<?> ctor = extended.getDeclaredConstructor(extendedFactory, packetCodecClass);
        ctor.setAccessible(true);
        Object instance = ctor.newInstance(extFactoryProxy, codec);
        VyneLogger.info("[IForgeMenuType] Created Fabric ExtendedScreenHandlerType for menu factory "
            + factory.getClass().getName());
        return instance;
    }

    private static Class<?> loadMcClass(ClassLoader cl, String yarn, String intermediary)
        throws ClassNotFoundException {
        Class<?> warmed = dev.stolym.vyne.vcl.VCLManager.getWarmedGameClass(intermediary);
        if (warmed != null) return warmed;
        warmed = dev.stolym.vyne.vcl.VCLManager.getWarmedGameClass(yarn);
        if (warmed != null) return warmed;
        try {
            return Class.forName(yarn, false, cl);
        } catch (ClassNotFoundException | ClassCircularityError firstMiss) {
            try {
                return Class.forName(intermediary, false, cl);
            } catch (ClassCircularityError cce) {
                ClassLoader knot = dev.stolym.vyne.vcl.VCLManager.getCanonicalKnotLoader();
                if (knot != null && knot != cl) {
                    return Class.forName(intermediary, false, knot);
                }
                throw cce;
            }
        }
    }

    private static Object buildPassthroughRegistryBufCodec(
        ClassLoader cl,
        Class<?> packetCodecClass,
        Class<?> valueFirstEncoderClass,
        Class<?> packetDecoderClass
    ) throws Exception {
        Object encoder = Proxy.newProxyInstance(
            cl,
            new Class[]{valueFirstEncoderClass},
            (proxy, method, args) -> {
                if (args != null && args.length == 2) {
                    encodeOpeningValueIntoBuf(args[0], args[1]);
                }
                return null;
            }
        );

        Object decoder = Proxy.newProxyInstance(
            cl,
            new Class[]{packetDecoderClass},
            (proxy, method, args) -> {
                if (args == null || args.length != 1) return null;
                return decodeOpeningValueFromBuf(args[0]);
            }
        );

        return invokePacketCodecTwoArgFactory(packetCodecClass, encoder, decoder);
    }

    private static Object invokePacketCodecTwoArgFactory(
        Class<?> packetCodecType,
        Object encoder,
        Object decoder
    ) throws Exception {
        Class<?> encIface = valueFirstEncoderClass(encoder);
        Class<?> decIface = packetDecoderClass(decoder);
        for (String name : new String[]{"of", "method_56438"}) {
            try {
                Method m = packetCodecType.getDeclaredMethod(name, encIface, decIface);
                m.setAccessible(true);
                if (Modifier.isStatic(m.getModifiers())) {
                    return m.invoke(null, encoder, decoder);
                }
            } catch (Throwable ignored) {
            }
        }
        for (Method m : packetCodecType.getDeclaredMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 2) {
                continue;
            }
            if (!packetCodecType.isAssignableFrom(m.getReturnType())) {
                continue;
            }
            Class<?>[] p = m.getParameterTypes();
            if (!p[0].isInstance(encoder) || !p[1].isInstance(decoder)) {
                continue;
            }
            m.setAccessible(true);
            return m.invoke(null, encoder, decoder);
        }
        throw new NoSuchMethodException("No static 2-arg PacketCodec factory on "
            + packetCodecType.getName() + " for encoder=" + encIface.getName()
            + " decoder=" + decIface.getName());
    }

    private static Class<?> valueFirstEncoderClass(Object encoder) {
        for (Class<?> i : encoder.getClass().getInterfaces()) {
            if (i.getName().contains("ValueFirstEncoder") || i.getName().endsWith("class_9143")) {
                return i;
            }
        }
        throw new IllegalStateException("Could not resolve ValueFirstEncoder type");
    }

    private static Class<?> packetDecoderClass(Object decoder) {
        for (Class<?> i : decoder.getClass().getInterfaces()) {
            if (i.getName().contains("PacketDecoder") || i.getName().endsWith("class_9141")) {
                return i;
            }
        }
        throw new IllegalStateException("Could not resolve PacketDecoder type");
    }

    private static void encodeOpeningValueIntoBuf(Object value, Object buf) throws Exception {
        if (buf == null) {
            return;
        }
        byte[] payload = snapshotPayload(value, buf);
        writeVarInt(buf, payload.length);
        if (payload.length > 0) {
            Method writeBytesArr = buf.getClass().getMethod("writeBytes", byte[].class);
            writeBytesArr.invoke(buf, (Object) payload);
        }
    }

    private static Object decodeOpeningValueFromBuf(Object buf) throws Exception {
        if (buf == null) {
            return null;
        }
        int len = readVarInt(buf);
        byte[] out = new byte[len];
        if (len > 0) {
            Method readBytesArr = buf.getClass().getMethod("readBytes", byte[].class);
            readBytesArr.invoke(buf, (Object) out);
        }
        return wrapBytesAsFriendlyBuf(buf, out);
    }

    private static byte[] snapshotPayload(Object value, Object fallbackBuf) throws Exception {
        Object source = value != null ? value : fallbackBuf;
        try {
            Method readable = source.getClass().getMethod("readableBytes");
            int n = (Integer) readable.invoke(source);
            if (n <= 0) {
                return new byte[0];
            }

            byte[] out = new byte[n];
            try {
                Method readerIndex = source.getClass().getMethod("readerIndex");
                int rIdx = (Integer) readerIndex.invoke(source);
                Method getBytes = source.getClass().getMethod(
                    "getBytes", int.class, byte[].class, int.class, int.class);
                getBytes.invoke(source, rIdx, out, 0, n);
                return out;
            } catch (NoSuchMethodException ignored) {
            }
            Method readBytes = source.getClass().getMethod("readBytes", byte[].class);
            readBytes.invoke(source, (Object) out);
            return out;
        } catch (NoSuchMethodException e) {
            return new byte[0];
        }
    }

    private static void writeVarInt(Object buf, int value) throws Exception {
        try {
            Method m = buf.getClass().getMethod("writeVarInt", int.class);
            m.invoke(buf, value);
            return;
        } catch (NoSuchMethodException ignored) {
        }

        int v = value;
        Method writeByte = buf.getClass().getMethod("writeByte", int.class);
        while (true) {
            if ((v & ~0x7F) == 0) {
                writeByte.invoke(buf, v);
                return;
            }
            writeByte.invoke(buf, (v & 0x7F) | 0x80);
            v >>>= 7;
        }
    }

    private static int readVarInt(Object buf) throws Exception {
        try {
            Method m = buf.getClass().getMethod("readVarInt");
            return (Integer) m.invoke(buf);
        } catch (NoSuchMethodException ignored) {
        }
        Method readByte = buf.getClass().getMethod("readByte");
        int result = 0, shift = 0;
        while (true) {
            int b = ((Byte) readByte.invoke(buf)) & 0xFF;
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return result;
            shift += 7;
            if (shift >= 35) throw new IllegalStateException("VarInt too big");
        }
    }

    private static Object wrapBytesAsFriendlyBuf(Object templateBuf, byte[] bytes) throws Exception {
        ClassLoader cl = templateBuf.getClass().getClassLoader();
        Class<?> unpooled = Class.forName("io.netty.buffer.Unpooled", false, cl);
        Method wrappedBuffer = unpooled.getMethod("wrappedBuffer", byte[].class);
        Object wrapped = wrappedBuffer.invoke(null, (Object) bytes);

        for (String fqn : new String[]{
            "net.minecraft.class_2540",
            "net.minecraft.network.FriendlyByteBuf"
        }) {
            try {
                Class<?> fbb = Class.forName(fqn, false, cl);
                Class<?> byteBuf = Class.forName("io.netty.buffer.ByteBuf", false, cl);
                Constructor<?> ctor = fbb.getConstructor(byteBuf);
                return ctor.newInstance(wrapped);
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            }
        }
        return wrapped;
    }
}
