package dev.zprestige.prestige.config;

import java.io.InputStream;
import java.util.Objects;

public class ClasspathResource {
    private final String path;
    private static final String NOT_FOUND_MESSAGE = "resource not found: ";

    private ClasspathResource(String resourcePath) {
        this.path = resourcePath;
    }

    public static ClasspathResource of(String resourcePath) {
        return new ClasspathResource(resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath);
    }

    public InputStream openStream() {
        InputStream stream = ClasspathResource.class.getClassLoader().getResourceAsStream(this.path);
        if (stream == null) {
            throw new IllegalArgumentException(NOT_FOUND_MESSAGE + this);
        } else {
            return stream;
        }
    }

    public String getPath() {
        return this.path;
    }

    @Override
    public String toString() {
        return this.path;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            return !(other instanceof ClasspathResource resource) ? false : this.path.equals(resource.path);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.path);
    }
}
