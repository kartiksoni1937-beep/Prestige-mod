package dev.zprestige.prestige.render;

import dev.zprestige.prestige.type.ShaderMode;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;

public class EspShaderManager implements MinecraftAccess {
    private static PostShader activeShader = ShaderMode.NORMAL.getShader();
    public static final Map modelMatchCache = new ConcurrentHashMap();
    public static EntityTypeSet targetTypes = new EntityTypeSet(new EntityType[0]);
    public static boolean enabled = false;
    public static boolean renderInvisible = false;
    private static final Map rendererModelCache = new ConcurrentHashMap();
    private static final Class MISSING_MODEL = Void.class;

    public static void setShaderMode(ShaderMode mode) {
        if (mode != null && mode != ShaderMode.NONE && mode.getShader() != null) {
            activeShader = mode.getShader();
        }
    }

    public static PostShader getActiveShader() {
        return activeShader;
    }

    public static boolean isModelOfTypes(EntityModel model, EntityTypeSet types) {
        Class modelClass = model.getClass();
        Boolean cached = (Boolean)modelMatchCache.get(modelClass);
        if (cached != null) {
            return cached;
        } else if (!types.isEmpty() && mc.world != null) {
            Iterator typeIterator = types.iterator();

            while (true) {
                Entity entity;
                boolean discardAfter;
                while (true) {
                    if (!typeIterator.hasNext()) {
                        modelMatchCache.put(modelClass, false);
                        return false;
                    }

                    EntityType type = (EntityType)typeIterator.next();
                    if (type == EntityType.PLAYER) {
                        entity = mc.player;
                        discardAfter = false;
                        if (entity == null) {
                            continue;
                        }
                        break;
                    } else {
                        entity = createEntity(type);
                        if (entity != null) {
                            discardAfter = true;
                            break;
                        }
                    }
                }

                try {
                    EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
                    Class rendererModelClass = getRendererModelClass(renderer);
                    if (rendererModelClass != null && rendererModelClass == modelClass) {
                        modelMatchCache.put(modelClass, true);
                        return true;
                    }
                } finally {
                    if (discardAfter) {
                        entity.discard();
                    }
                }
            }
        } else {
            return false;
        }
    }

    private static Entity createEntity(EntityType type) {
        try {
            return type.create(mc.world, null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Class getRendererModelClass(EntityRenderer renderer) {
        Class rendererClass = renderer.getClass();
        Class cached = (Class)rendererModelCache.get(rendererClass);
        if (cached != null) {
            return cached == MISSING_MODEL ? null : cached;
        } else {
            Class modelClass = null;
            if (renderer instanceof LivingEntityRenderer livingRenderer) {
                modelClass = livingRenderer.getModel().getClass();
            } else {
                for (Field field : rendererClass.getDeclaredFields()) {
                    if (EntityModel.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);

                        try {
                            EntityModel model = (EntityModel)field.get(renderer);
                            if (model != null) {
                                modelClass = model.getClass();
                                break;
                            }
                        } catch (IllegalAccessException ignored) {
                        }
                    }
                }
            }

            rendererModelCache.put(rendererClass, modelClass == null ? MISSING_MODEL : modelClass);
            return modelClass;
        }
    }

    public static boolean isTargetModel(EntityModel model) {
        Class modelClass = model.getClass();
        Boolean cached = (Boolean)modelMatchCache.get(modelClass);
        if (cached != null) {
            return cached;
        } else if (!targetTypes.isEmpty() && mc.world != null) {
            Iterator typeIterator = targetTypes.iterator();

            while (true) {
                Entity entity;
                boolean discardAfter;
                while (true) {
                    if (!typeIterator.hasNext()) {
                        modelMatchCache.put(modelClass, false);
                        return false;
                    }

                    EntityType type = (EntityType)typeIterator.next();
                    if (type == EntityType.PLAYER) {
                        entity = mc.player;
                        discardAfter = false;
                        if (entity == null) {
                            continue;
                        }
                        break;
                    } else {
                        entity = createEntity(type);
                        if (entity != null) {
                            discardAfter = true;
                            break;
                        }
                    }
                }

                try {
                    EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
                    Class rendererModelClass = getRendererModelClass(renderer);
                    if (rendererModelClass != null && rendererModelClass == modelClass) {
                        modelMatchCache.put(modelClass, true);
                        return true;
                    }
                } finally {
                    if (discardAfter) {
                        entity.discard();
                    }
                }
            }
        } else {
            return false;
        }
    }

    public static boolean matchesTargetTypes(EntityModel model) {
        return isModelOfTypes(model, targetTypes);
    }

    static {
        RenderEvents.AFTER_HUD_RENDER.register((RenderCallback)(buffers, context) -> {
            if (activeShader != null && enabled) {
                activeShader.renderComposite(buffers, context);
            }
        });
        RenderEvents.WORLD_RENDER.register((RenderCallback)(worldBuffers, worldContext) -> {
            if (activeShader != null && enabled) {
                activeShader.prepareMask(worldBuffers, worldContext);
            }
        });
    }
}
