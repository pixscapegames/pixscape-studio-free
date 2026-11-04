package games.pixscape.studio.helper;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/** Cold project-open upgrade of byte-identifiable supplied Material sources only. */
public final class BundledMaterialUpgrade {
    private static final String[] NAMES = {"glow_pulse", "outline_soft", "wave_uv"};
    private static final String[] VARIANTS = {"desktop-gl30", "es3-webgl2"};
    private static final Map<String, String> KNOWN = knownSources();
    private BundledMaterialUpgrade() { }

    private static Map<String, String> knownSources() {
        Map<String, String> known = new LinkedHashMap<>();
        known.put("includes/pixscape_common.glsl", "954dbba55464d2161b9dfd23174464f6d0de64cff51b135e01600cf2e2643862");
        known.put("includes/pixscape_entity_params.glsl", "2565ec6a0fea2c34ea53273284cfc0d83b17a7210f57abf8c0af80d3c5d35c42");
        known.put("core/desktop-gl30/texture-array.vert", "d1975c7b49fafcd338d07ef89712759d8f2d874b77ba8a408a95c5d036e04c7f");
        known.put("core/es3-webgl2/texture-array.vert", "1cb7025205d94ce49d3e5535a30b8e3a275c25aa85b44e445af306ca6c274d04");
        known.put("examples/material/desktop-gl30/glow_pulse.frag", "986f356f5423d3689780a6e7aa399ae3d0fa6e57ed19b5fab3bbf1baf580eb08");
        known.put("examples/material/es3-webgl2/glow_pulse.frag", "6bac8b9e42a9f596a453abefb6f194dcd49600ce6f44dfea17344df85a8c27b2");
        known.put("examples/material/desktop-gl30/outline_soft.frag", "242b3ee187d94e167e096c6709af9435fb7f0b2b67aa7e0145fb67046e210318");
        known.put("examples/material/es3-webgl2/outline_soft.frag", "8fe68bd43a12921af8e55b4aa79941e461b7fa0ccb4f075a1b79d325338fae5b");
        known.put("examples/material/desktop-gl30/wave_uv.frag", "054c620728ecaef471e7e77e857fb0bd66399c54acfd594bd3e9363d0cfc6c32");
        known.put("examples/material/es3-webgl2/wave_uv.frag", "6b5aba5adf0cdc856a9aac83f74bae8107c28990b4e0adc67eb2b33e8b5ff600");
        known.put("examples/surface/lit-dissolve/mask.glsl", "92bc521360727514dd3484d9aaa337f01b689bfa51a8d18dd131f92749076791");
        known.put("examples/surface/lit-dissolve/shader.json", "4ebf48f65e27ea265cc4673baa8b9a1415efd095b2c506012019b7e825235bd5");
        known.put("examples/surface/lit-dissolve/surface.glsl", "86c33f4abf154a85c269f0d17dfc7d5282e2aa7f4a2f8a9e52e883e05183c2e5");
        known.put("examples/surface/lit-pulse/shader.json", "087efb26bf8de520edeec5c50b8f452891632b8be62b3eb96f77473b5b1c2738");
        known.put("examples/surface/lit-pulse/surface.glsl", "86c33f4abf154a85c269f0d17dfc7d5282e2aa7f4a2f8a9e52e883e05183c2e5");
        known.put("examples/surface/unlit-pulse/shader.json", "146e345c07e11a8b4c9d3eeff1b7fdf3fd75ec2dd07d45bb76bdb5a6cc7e89f1");
        known.put("examples/surface/unlit-pulse/surface.glsl", "86c33f4abf154a85c269f0d17dfc7d5282e2aa7f4a2f8a9e52e883e05183c2e5");
        return known;
    }

    public static void upgrade(FileHandle root) {
        if (root == null || !root.exists()) return;
        upgradeSurfaceDirectories(root);
        FileHandle presetsFile = root.child("examples/params.json");
        JsonValue presets = presetsFile.exists() ? new JsonReader().parse(presetsFile) : null;
        for (String name : NAMES) {
            FileHandle supplied = Gdx.files.internal("shaders/examples/material/" + name);
            JsonValue replacement = new JsonReader().parse(supplied.child("shader.json"));
            if (presets != null && equalParameters(presets.get(name), replacement.get("parameters"))
                    && matchesDependencies(root, root) && matchesExample(root, name)
                    && !root.child("examples/material/" + name).exists()) {
                Map<FileHandle, byte[]> writes = new LinkedHashMap<>();
                FileHandle target = root.child("examples/material/" + name);
                writes.put(target.child("shader.json"), supplied.child("shader.json").readBytes());
                writes.put(target.child("surface.glsl"), supplied.child("surface.glsl").readBytes());
                for (String variant : VARIANTS) writes.put(root.child("examples/material/" + variant + "/" + name + ".frag"), null);
                JsonValue next = new JsonReader().parse(presets.toJson(JsonWriter.OutputType.json));
                next.remove(name);
                writes.put(presetsFile, next.toJson(JsonWriter.OutputType.json).getBytes(StandardCharsets.UTF_8));
                publish(writes);
                presets = next;
            }
        }
        // A copied example may have been installed as a structured shader. Its name/ID
        // need not match the supplied name; require all source/dependency/parameter checks.
        FileHandle custom = root.child("custom/material");
        if (!custom.exists()) return;
        for (FileHandle dir : custom.list()) {
            if (!dir.isDirectory() || !dir.child("shader.json").exists()
                    || dir.child("surface.glsl").exists() || dir.child("mask.glsl").exists()) continue;
            JsonValue metadata = new JsonReader().parse(dir.child("shader.json"));
            if (!"MATERIAL".equalsIgnoreCase(metadata.getString("kind", "MATERIAL")) || metadata.has("contract") || !"TEXTURE_ARRAY".equals(metadata.getString("mode", "TEXTURE_ARRAY"))) continue;
            for (String name : NAMES) {
                FileHandle supplied = Gdx.files.internal("shaders/examples/material/" + name);
                JsonValue replacement = new JsonReader().parse(supplied.child("shader.json"));
                if (!equalParameters(metadata.get("parameters"), replacement.get("parameters"))
                        || !matchesDependencies(root, dir) || !matchesStructured(dir, name)) continue;
                metadata.addChild("contract", new JsonValue("surface-v1"));
                metadata.addChild("lighting", new JsonValue(replacement.getString("lighting")));
                Map<FileHandle, byte[]> writes = new LinkedHashMap<>();
                writes.put(dir.child("surface.glsl"), supplied.child("surface.glsl").readBytes());
                writes.put(dir.child("shader.json"), metadata.toJson(JsonWriter.OutputType.json).getBytes(StandardCharsets.UTF_8));
                for (String variant : VARIANTS) {
                    writes.put(dir.child(variant + ".vert"), null);
                    writes.put(dir.child(variant + ".frag"), null);
                }
                publish(writes);
                break;
            }
        }
    }

    private static void upgradeSurfaceDirectories(FileHandle root) {
        for (String name : new String[]{"lit-pulse", "unlit-pulse", "lit-dissolve"}) {
            FileHandle from = root.child("examples/surface/" + name), to = root.child("examples/material/" + name);
            if (!from.isDirectory() || to.exists()) continue;
            String[] files = name.equals("lit-dissolve") ? new String[]{"shader.json", "surface.glsl", "mask.glsl"}
                    : new String[]{"shader.json", "surface.glsl"};
            if (from.list().length != files.length) continue;
            boolean known = true;
            for (String file : files) known &= matches(from.child(file), "examples/surface/" + name + "/" + file);
            if (!known) continue;
            Map<FileHandle, byte[]> writes = new LinkedHashMap<>();
            for (String file : files) writes.put(to.child(file), Gdx.files.internal("shaders/examples/material/" + name + "/" + file).readBytes());
            for (String file : files) writes.put(from.child(file), null);
            publish(writes);
            from.delete(); // It contained exactly the verified files removed above.
        }
    }

    private static boolean matchesExample(FileHandle root, String name) {
        for (String variant : VARIANTS) {
            String key = "examples/material/" + variant + "/" + name + ".frag";
            if (!matches(root.child(key), key)) return false;
        }
        return true;
    }
    private static boolean matchesStructured(FileHandle dir, String name) {
        for (String variant : VARIANTS)
            if (!matches(dir.child(variant + ".frag"), "examples/material/" + variant + "/" + name + ".frag")
                    || !matches(dir.child(variant + ".vert"), "core/" + variant + "/texture-array.vert")) return false;
        return true;
    }
    private static boolean matchesDependencies(FileHandle root, FileHandle local) {
        for (String include : new String[]{"pixscape_common.glsl", "pixscape_entity_params.glsl"}) {
            String key = "includes/" + include;
            FileHandle file = local.child(key);
            if (!file.exists()) file = root.child(key);
            if (!file.exists()) file = Gdx.files.internal("shaders/" + key);
            if (!matches(file, key)) return false;
        }
        if (local.equals(root)) for (String variant : VARIANTS) {
            String key = "core/" + variant + "/texture-array.vert";
            FileHandle file = root.child(key);
            if (!file.exists()) file = Gdx.files.internal("shaders/" + key);
            if (!matches(file, key)) return false;
        }
        return true;
    }
    private static boolean equalParameters(JsonValue a, JsonValue b) {
        if (a == null || b == null || !a.isObject() || a.size != b.size) return false;
        JsonValue x = a.child, y = b.child;
        for (; x != null; x = x.next, y = y.next)
            if (!x.name.equals(y.name) || !x.isNumber() || Float.compare(x.asFloat(), y.asFloat()) != 0) return false;
        return true;
    }
    private static boolean matches(FileHandle file, String key) {
        if (!file.exists() || file.isDirectory()) return false;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(file.readString("UTF-8")
                    .replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest) hex.append(Character.forDigit((value >>> 4) & 15, 16)).append(Character.forDigit(value & 15, 16));
            return hex.toString().equals(KNOWN.get(key));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void publish(Map<FileHandle, byte[]> writes) {
        Map<FileHandle, byte[]> before = new LinkedHashMap<>();
        List<FileHandle> newParents = new ArrayList<>();
        for (FileHandle file : writes.keySet()) {
            before.put(file, file.exists() ? file.readBytes() : null);
            for (FileHandle parent = file.parent(); !parent.exists(); parent = parent.parent())
                if (!newParents.contains(parent)) newParents.add(parent);
        }
        newParents.sort((a, b) -> Integer.compare(b.path().length(), a.path().length()));
        try {
            // Publish complete new sources before removing old runnable variants.
            for (Map.Entry<FileHandle, byte[]> entry : writes.entrySet())
                if (entry.getValue() != null) entry.getKey().writeBytes(entry.getValue(), false);
            for (Map.Entry<FileHandle, byte[]> entry : writes.entrySet())
                if (entry.getValue() == null && !entry.getKey().delete()) throw new IllegalStateException("Cannot remove obsolete supplied shader: " + entry.getKey());
        } catch (RuntimeException failure) {
            for (Map.Entry<FileHandle, byte[]> entry : before.entrySet()) try {
                if (entry.getValue() == null) entry.getKey().delete();
                else entry.getKey().writeBytes(entry.getValue(), false);
            } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            for (FileHandle parent : newParents) try {
                if (parent.isDirectory() && parent.list().length == 0) parent.delete();
            } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
    }
}
