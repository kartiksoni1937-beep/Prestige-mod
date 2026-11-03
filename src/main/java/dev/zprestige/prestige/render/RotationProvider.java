package dev.zprestige.prestige.render;

public interface RotationProvider {
    Rotation provideRotation();

    default int getRotationPriority() {
        return 0;
    }
}
