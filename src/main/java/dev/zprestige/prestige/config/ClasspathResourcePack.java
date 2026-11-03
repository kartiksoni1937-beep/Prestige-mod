package dev.zprestige.prestige.config;

import dev.zprestige.prestige.client.Prestige;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.ResourcePack.ResultConsumer;
import net.minecraft.resource.metadata.ResourceMetadataSerializer;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public class ClasspathResourcePack implements ResourcePack {
    @Nullable
    @Override
    public InputSupplier openRoot(String[] segments) {
        return null;
    }

    @Nullable
    @Override
    public InputSupplier open(ResourceType type, Identifier id) {
        return null;
    }

    @Override
    public void findResources(ResourceType type, String namespace, String prefix, ResultConsumer consumer) {
    }

    @Override
    public Set getNamespaces(ResourceType type) {
        return Set.of();
    }

    @Nullable
    @Override
    public Object parseMetadata(ResourceMetadataSerializer metadataSerializer) throws IOException {
        return null;
    }

    @Override
    public String getId() {
        return "Prestige";
    }

    @Override
    public Optional getKnownPackInfo() {
        return ResourcePack.super.getKnownPackInfo();
    }

    @Override
    public ResourcePackInfo getInfo() {
        return null;
    }

    @Override
    public void close() {
    }

    private static InputStream openOrThrow(String path) throws IOException {
        InputStream stream = Prestige.class.getClassLoader().getResourceAsStream(path);
        if (stream == null) {
            throw new FileNotFoundException(path);
        }

        return stream;
    }

    public static Resource createAssetResource(Identifier id) {
        return new Resource(new ClasspathResourcePack(), () -> openOrThrow("assets/prestige/" + id.getPath()));
    }

    public static Resource createResource(String path) {
        return new Resource(new ClasspathResourcePack(), () -> openOrThrow(path));
    }

    public static InputSupplier createInputSupplier(String path) {
        return () -> openOrThrow(path);
    }
}
