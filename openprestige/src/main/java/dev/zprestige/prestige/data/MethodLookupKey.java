package dev.zprestige.prestige.data;

import java.util.Arrays;

public record MethodLookupKey(Class<?> ownerClass, String methodName, Class<?> returnType, Class<?>[] parameterTypes, Class<?> receiverClass) {
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other != null && this.getClass() == other.getClass()) {
            MethodLookupKey that = (MethodLookupKey)other;
            if (!this.ownerClass.equals(that.ownerClass)) {
                return false;
            } else if (!this.receiverClass.equals(that.receiverClass)) {
                return false;
            } else if (!this.methodName.equals(that.methodName)) {
                return false;
            } else if (!this.returnType.equals(that.returnType)) {
                return false;
            } else if (this.parameterTypes.length != that.parameterTypes.length) {
                return false;
            } else {
                for (int index = 0; index < this.parameterTypes.length; index++) {
                    if (!this.parameterTypes[index].equals(that.parameterTypes[index])) {
                        return false;
                    }
                }

                return true;
            }
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (31 * (31 * this.ownerClass.hashCode() + this.methodName.hashCode()) + this.returnType.hashCode()) + this.receiverClass.hashCode()) + Arrays.hashCode((Object[])this.parameterTypes);
    }
}
