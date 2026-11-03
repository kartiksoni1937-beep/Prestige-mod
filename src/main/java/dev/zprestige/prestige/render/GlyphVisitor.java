package dev.zprestige.prestige.render;

import dev.zprestige.prestige.config.MsdfGlyph;

@FunctionalInterface
public interface GlyphVisitor {
    boolean visit(int codePoint, MsdfGlyph glyph);
}
