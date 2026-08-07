package io.github.ghosthack.libjxlffm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Resolves the shared libraries used by the generated libjxl bindings.
 *
 * <p>Resolution order is an explicit install named by
 * {@code -Dlibjxlffm.libdir} or {@code LIBJXL_FFM_LIBDIR}, bundled natives for
 * the current platform, then libraries already loaded by the application.</p>
 */
public final class LibjxlLibs {
    private static final Arena LIBRARY_ARENA = Arena.ofAuto();
    private static volatile SymbolLookup lookup;

    private LibjxlLibs() {}

    /** Returns the lazily initialized lookup used by all generated downcalls. */
    public static SymbolLookup lookup() {
        SymbolLookup result = lookup;
        if (result == null) {
            synchronized (LibjxlLibs.class) {
                result = lookup;
                if (result == null) {
                    lookup = result = resolve();
                }
            }
        }
        return result;
    }

    private static SymbolLookup resolve() {
        String override = System.getProperty("libjxlffm.libdir",
                System.getenv("LIBJXL_FFM_LIBDIR"));
        SymbolLookup resolved = override == null ? fromBundle() : fromDirectory(Path.of(override));
        SymbolLookup fallback = SymbolLookup.loaderLookup().or(Linker.nativeLinker().defaultLookup());
        return resolved == null ? fallback : resolved.or(fallback);
    }

    private static SymbolLookup fromDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("libjxlffm.libdir does not exist: " + dir);
        }
        List<Path> libraries;
        Path manifest = dir.resolve("manifest.txt");
        try {
            if (Files.isRegularFile(manifest)) {
                libraries = Files.readAllLines(manifest, StandardCharsets.UTF_8).stream()
                        .map(String::trim).filter(s -> !s.isEmpty()).map(dir::resolve).toList();
            } else {
                try (var files = Files.list(dir)) {
                    libraries = files.filter(LibjxlLibs::isLibrary)
                            .sorted(Comparator.comparingInt(LibjxlLibs::loadRank)
                                    .thenComparing(path -> path.getFileName().toString()))
                            .toList();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (libraries.isEmpty()) {
            throw new IllegalStateException("no libjxl shared libraries found in " + dir);
        }
        for (Path library : libraries) {
            if (!Files.isRegularFile(library)) {
                throw new IllegalStateException("manifest library does not exist: " + library);
            }
        }
        return chain(libraries);
    }

    private static boolean isLibrary(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        boolean extension = name.endsWith(".dylib") || name.endsWith(".dll") || name.contains(".so");
        return extension && (name.contains("jxl") || name.contains("brotli")
                || name.contains("hwy") || name.contains("stdc++")
                || name.contains("gcc_s") || name.contains("winpthread"));
    }

    private static int loadRank(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.contains("winpthread") || name.contains("gcc_s") || name.contains("stdc++")) return 0;
        if (name.contains("brotlicommon")) return 1;
        if (name.contains("brotlidec") || name.contains("brotlienc")) return 2;
        if (name.contains("hwy")) return 3;
        if (name.contains("jxl_cms")) return 4;
        if (name.contains("jxl_threads")) return 6;
        return 5;
    }

    private static SymbolLookup fromBundle() {
        String classifier = classifier();
        String base = "natives/" + classifier + "/";
        List<String> manifest = readManifest(base);
        if (manifest == null) {
            return null;
        }
        Path cacheDir = Path.of(System.getProperty("user.home"), ".cache", "libjxl-ffm",
                Version.VERSION + "-" + classifier);
        try {
            Files.createDirectories(cacheDir);
            List<Path> libraries = new ArrayList<>();
            for (String name : manifest) {
                libraries.add(extract(base + name, cacheDir.resolve(name)));
            }
            return chain(libraries);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to extract bundled libjxl natives to " + cacheDir, e);
        }
    }

    private static List<String> readManifest(String base) {
        try (InputStream in = LibjxlLibs.class.getClassLoader()
                .getResourceAsStream(base + "manifest.txt")) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path extract(String resource, Path target) throws IOException {
        try (InputStream in = LibjxlLibs.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IOException("missing classpath resource " + resource);
            byte[] bytes = in.readAllBytes();
            if (Files.exists(target) && Files.size(target) == bytes.length) return target;
            Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
            Files.write(temp, bytes);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        }
    }

    private static SymbolLookup chain(List<Path> libraries) {
        SymbolLookup result = null;
        for (Path library : libraries) {
            SymbolLookup one = SymbolLookup.libraryLookup(
                    library.toAbsolutePath().toString(), LIBRARY_ARENA);
            result = result == null ? one : result.or(one);
        }
        return result;
    }

    /** Returns the native classifier for the running JVM. */
    public static String classifier() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        String osPart = os.contains("mac") ? "macos" : os.contains("win") ? "windows" : "linux";
        String archPart = arch.equals("aarch64") || arch.equals("arm64") ? "arm64" : "x64";
        return osPart + "-" + archPart;
    }
}
