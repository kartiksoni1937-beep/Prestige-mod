package dev.zprestige.prestige.reflect;

import dev.zprestige.prestige.data.MethodLookupKey;
import dev.zprestige.prestige.render.BoundMethod;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import net.lenni0451.classtransform.TransformerManager;
import net.lenni0451.classtransform.utils.ASMUtils;

public class MethodAccessor extends ReflectionBase {
    private final Method method;
    private final MethodHandle methodHandle;
    private final boolean isStaticMethod;
    private final Object boundTarget;
    private volatile MethodHandle spreadInvoker;
    private volatile boolean spreadInvokerResolved;
    private static final Object[] NO_ARGS = new Object[0];
    private static final ConcurrentHashMap MAPPED_METHOD_ACCESSORS = new ConcurrentHashMap();

    private MethodAccessor(Method reflectMethod, MethodHandle handle, boolean staticMethod, Object target) {
        this.method = reflectMethod;
        this.methodHandle = handle;
        this.isStaticMethod = staticMethod;
        this.boundTarget = target;
    }

    private MethodAccessor(ResolvedMethod resolved, Object target) {
        this(resolved.method, resolved.handle, resolved.isStatic, target);
    }

    public static MethodAccessor of(Class owner, String name, Class[] parameterTypes) {
        return new MethodAccessor(ReflectionBase.resolveMethod(owner, name, parameterTypes), null);
    }

    public static MethodAccessor forInstance(Object instance, String name, Class[] parameterTypes) {
        if (instance == null) {
            throw new IllegalArgumentException("instance cannot be null");
        } else {
            return new MethodAccessor(ReflectionBase.resolveMethod(instance.getClass(), name, parameterTypes), instance);
        }
    }

    public static BoundMethod bindMapped(Object instance, String name, Class owner, Class returnType, Class[] parameterTypes) {
        MethodLookupKey cacheKey = new MethodLookupKey(owner, name, returnType, parameterTypes, instance.getClass());
        MethodAccessor cached = (MethodAccessor)MAPPED_METHOD_ACCESSORS.get(cacheKey);
        if (cached != null) {
            return cached.bindTo(instance);
        } else {
            String namedOwner = TransformerManager.getRemapper().getRemapper().reverse().map(owner.getName().replace('.', '/'));
            String namedDescriptor = TransformerManager.getRemapper().getRemapper().reverse().mapMethodDesc(ASMUtils.toDescriptor(returnType, parameterTypes));
            MethodAccessor accessor = forInstance(instance, TransformerManager.getRemapper().getRemapper().mapMethodName(namedOwner, name, namedDescriptor), parameterTypes);
            MethodAccessor existing = (MethodAccessor)MAPPED_METHOD_ACCESSORS.putIfAbsent(cacheKey, accessor);
            return (existing != null ? existing : accessor).bindTo(instance);
        }
    }

    public static MethodAccessor ofMapped(Class owner, String name, Class returnType, Class[] parameterTypes) {
        MethodLookupKey cacheKey = new MethodLookupKey(owner, name, returnType, parameterTypes, owner);
        MethodAccessor cached = (MethodAccessor)MAPPED_METHOD_ACCESSORS.get(cacheKey);
        if (cached != null) {
            return cached;
        } else {
            String namedOwner = TransformerManager.getRemapper().getRemapper().reverse().map(owner.getName().replace('.', '/'));
            String namedDescriptor = TransformerManager.getRemapper().getRemapper().reverse().mapMethodDesc(ASMUtils.toDescriptor(returnType, parameterTypes));
            MethodAccessor accessor = of(owner, TransformerManager.getRemapper().getRemapper().mapMethodName(namedOwner, name, namedDescriptor), parameterTypes);
            MethodAccessor existing = (MethodAccessor)MAPPED_METHOD_ACCESSORS.putIfAbsent(cacheKey, accessor);
            return existing != null ? existing : accessor;
        }
    }

    private MethodHandle getSpreadInvoker() {
        if (this.spreadInvokerResolved) {
            return this.spreadInvoker;
        } else {
            MethodHandle invoker = null;

            try {
                int parameterCount = this.methodHandle.type().parameterCount();
                invoker = this.methodHandle.asType(this.methodHandle.type().generic()).asSpreader(Object[].class, parameterCount);
            } catch (Throwable ignored) {
            }

            this.spreadInvoker = invoker;
            this.spreadInvokerResolved = true;
            return invoker;
        }
    }

    public Method getMethod() {
        return this.method;
    }

    public Object call(Object target, Object[] args) throws Throwable {
        return this.invoke(target, args);
    }

    public Object invoke(Object target, Object[] args) throws Throwable {
        Object[] fullArgs;
        if (this.isStaticMethod) {
            fullArgs = args == null ? NO_ARGS : args;
        } else {
            if (target == null) {
                throw new IllegalArgumentException("Target instance required for non-static method");
            }

            int argCount = args == null ? 0 : args.length;
            fullArgs = new Object[argCount + 1];
            fullArgs[0] = target;
            if (argCount > 0) {
                System.arraycopy(args, 0, fullArgs, 1, argCount);
            }
        }

        MethodHandle invoker = this.getSpreadInvoker();
        if (invoker != null) {
            return invoker.invoke(fullArgs);
        } else {
            return this.methodHandle.invokeWithArguments(fullArgs);
        }
    }

    public BoundMethod bindTo(Object target) {
        if (this.isStaticMethod) {
            throw new IllegalStateException("Cannot bind static method to instance");
        } else if (target == null) {
            throw new IllegalArgumentException("Target instance required for non-static method");
        } else {
            return new BoundMethod(this, target);
        }
    }
}
