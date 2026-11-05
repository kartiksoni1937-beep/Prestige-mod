package dev.zprestige.prestige.render;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

public class EntityTypeSet implements Iterable {
    private final Set elements = ConcurrentHashMap.newKeySet();
    private final Set readOnlyView = Collections.unmodifiableSet(this.elements);

    public EntityTypeSet(Object[] initialElements) {
        Collections.addAll(this.elements, initialElements);
    }

    public boolean add(Object element) {
        return this.elements.add(element);
    }

    public boolean isEmpty() {
        return this.elements.isEmpty();
    }

    public Set asSet() {
        return this.readOnlyView;
    }

    @NotNull
    @Override
    public Iterator iterator() {
        return this.readOnlyView.iterator();
    }
}
