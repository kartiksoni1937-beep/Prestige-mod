package dev.zprestige.prestige.type;

import java.util.function.DoubleUnaryOperator;

public enum Easing {
    LINEAR(progress -> progress),
    SINE_IN(progress -> 1.0 - Math.cos(progress * Math.PI / 2.0)),
    SINE_OUT(progress -> Math.sin(progress * Math.PI / 2.0)),
    SINE_IN_OUT(progress -> -(Math.cos(Math.PI * progress) - 1.0) / 2.0),
    CUBIC_IN(progress -> progress * progress * progress),
    CUBIC_OUT(progress -> 1.0 - Math.pow(1.0 - progress, 3.0)),
    CUBIC_IN_OUT(progress -> progress < 0.5 ? 4.0 * progress * progress * progress : 1.0 - Math.pow(-2.0 * progress + 2.0, 3.0) / 2.0),
    QUAD_IN(progress -> progress * progress),
    QUAD_OUT(progress -> 1.0 - (1.0 - progress) * (1.0 - progress)),
    QUAD_IN_OUT(progress -> progress < 0.5 ? 2.0 * progress * progress : 1.0 - Math.pow(-2.0 * progress + 2.0, 2.0) / 2.0),
    QUART_IN(progress -> progress * progress * progress * progress),
    QUART_OUT(progress -> 1.0 - Math.pow(1.0 - progress, 4.0)),
    QUART_IN_OUT(progress -> progress < 0.5 ? 8.0 * progress * progress * progress * progress : 1.0 - Math.pow(-2.0 * progress + 2.0, 4.0) / 2.0),
    QUINT_IN(progress -> progress * progress * progress * progress * progress),
    QUINT_OUT(progress -> 1.0 - Math.pow(1.0 - progress, 5.0)),
    QUINT_IN_OUT(progress -> progress < 0.5 ? 16.0 * progress * progress * progress * progress * progress : 1.0 - Math.pow(-2.0 * progress + 2.0, 5.0) / 2.0),
    CIRC_IN(progress -> 1.0 - Math.sqrt(1.0 - Math.pow(progress, 2.0))),
    CIRC_OUT(progress -> Math.sqrt(1.0 - Math.pow(progress - 1.0, 2.0))),
    CIRC_IN_OUT(
        progress -> progress < 0.5
            ? (1.0 - Math.sqrt(1.0 - Math.pow(2.0 * progress, 2.0))) / 2.0
            : (Math.sqrt(1.0 - Math.pow(-2.0 * progress + 2.0, 2.0)) + 1.0) / 2.0
    ),
    EXPO_IN(progress -> progress == 0.0 ? 0.0 : Math.pow(2.0, 10.0 * progress - 10.0)),
    EXPO_OUT(progress -> progress == 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * progress)),
    EXPO_IN_OUT(
        progress -> progress == 0.0
            ? 0.0
            : (progress == 1.0 ? 1.0 : (progress < 0.5 ? Math.pow(2.0, 20.0 * progress - 10.0) / 2.0 : (2.0 - Math.pow(2.0, -20.0 * progress + 10.0)) / 2.0))
    ),
    ELASTIC_IN(
        progress -> progress == 0.0
            ? 0.0
            : (progress == 1.0 ? 1.0 : -Math.pow(2.0, 10.0 * progress - 10.0) * Math.sin((progress * 10.0 - 10.75) * (Math.PI * 2.0 / 3.0)))
    ),
    ELASTIC_OUT(
        progress -> progress == 0.0
            ? 0.0
            : (progress == 1.0 ? 1.0 : Math.pow(2.0, -10.0 * progress) * Math.sin((progress * 10.0 - 0.75) * (Math.PI * 2.0 / 3.0)) + 1.0)
    ),
    ELASTIC_IN_OUT(
        progress -> progress == 0.0
            ? 0.0
            : (
                progress == 1.0
                    ? 1.0
                    : (
                        progress < 0.5
                            ? -(Math.pow(2.0, 20.0 * progress - 10.0) * Math.sin((20.0 * progress - 11.125) * (Math.PI * 4.0 / 9.0))) / 2.0
                            : Math.pow(2.0, -20.0 * progress + 10.0) * Math.sin((20.0 * progress - 11.125) * (Math.PI * 4.0 / 9.0)) / 2.0 + 1.0
                    )
            )
    ),
    BACK_IN(progress -> 2.70158 * progress * progress * progress - 1.70158 * progress * progress),
    BACK_OUT(progress -> 1.0 + 2.70158 * Math.pow(progress - 1.0, 3.0) + 1.70158 * Math.pow(progress - 1.0, 2.0)),
    BACK_IN_OUT(
        progress -> progress < 0.5
            ? Math.pow(2.0 * progress, 2.0) * (7.189819 * progress - 2.5949095) / 2.0
            : (Math.pow(2.0 * progress - 2.0, 2.0) * (3.5949095 * (progress * 2.0 - 2.0) + 2.5949095) + 2.0) / 2.0
    );

    private final DoubleUnaryOperator function;

    private Easing(DoubleUnaryOperator operator) {
        this.function = operator;
    }

    public double apply(double progress) {
        return this.function.applyAsDouble(progress);
    }
}
