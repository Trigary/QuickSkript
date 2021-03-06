package com.github.stefvanschie.quickskript.core.skript;

import com.github.stefvanschie.quickskript.core.file.skript.SkriptFileSection;
import com.github.stefvanschie.quickskript.core.pattern.SkriptMatchResult;
import com.github.stefvanschie.quickskript.core.pattern.SkriptPattern;
import com.github.stefvanschie.quickskript.core.pattern.group.SkriptPatternGroup;
import com.github.stefvanschie.quickskript.core.pattern.group.TypeGroup;
import com.github.stefvanschie.quickskript.core.psi.*;
import com.github.stefvanschie.quickskript.core.psi.exception.ParseException;
import com.github.stefvanschie.quickskript.core.psi.section.PsiIf;
import com.github.stefvanschie.quickskript.core.psi.util.parsing.Fallback;
import com.github.stefvanschie.quickskript.core.psi.util.parsing.exception.IllegalFallbackAnnotationAmountException;
import com.github.stefvanschie.quickskript.core.psi.util.parsing.pattern.Pattern;
import com.github.stefvanschie.quickskript.core.psi.util.parsing.pattern.PatternTypeOrder;
import com.github.stefvanschie.quickskript.core.psi.util.parsing.pattern.PatternTypeOrderHolder;
import com.github.stefvanschie.quickskript.core.psi.util.parsing.pattern.exception.ParsingAnnotationInvalidValueException;
import com.github.stefvanschie.quickskript.core.util.registry.BiomeRegistry;
import com.github.stefvanschie.quickskript.core.util.Pair;
import com.github.stefvanschie.quickskript.core.util.registry.EntityTypeRegistry;
import com.github.stefvanschie.quickskript.core.util.registry.InventoryTypeRegistry;
import com.github.stefvanschie.quickskript.core.util.registry.ItemTypeRegistry;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Instances of this class contain everything necessary for loading Skript files.
 * This allows addons to be easily loaded, as well as dropping all data after
 * all Skripts have been loaded (in order to save memory).
 * This means that the static modifier should only be used when the data is required
 * to also be present when the Skripts are being ran, not only when they are being loaded.
 *
 * @since 0.1.0
 */
public abstract class SkriptLoader implements Closeable {

    /**
     * The current loader instance or null if there is none present.
     */
    @Nullable
    private static SkriptLoader instance;

    /**
     * Gets the current loader instance. Returns null if there is none present.
     *
     * @return the current instance
     * @since 0.1.0
     */
    @Contract(pure = true)
    public static SkriptLoader get() {
        return instance;
    }


    /**
     * A list of all psi element factories.
     */
    @NotNull
    private final List<PsiElementFactory> elements = new ArrayList<>();

    /**
     * A cache for a factory and a list of methods with their patterns. This is gradually built up for element factories
     * that are being tested and can be used in the future to avoid having to do lookups.
     */
    @NotNull
    private final Map<PsiElementFactory, Map<Method, SkriptPattern[]>> elementsCached = new HashMap<>();

    /**
     * A list of all psi section factories.
     */
    @NotNull
    private final List<PsiSectionFactory<?>> sections = new ArrayList<>();

    /**
     * A map indexing converters by their name.
     */
    @NotNull
    private final Map<String, PsiConverter<?>> converters = new HashMap<>();

    /**
     * A biome registry for working with biomes
     */
    private BiomeRegistry biomeRegistry;

    /**
     * An entity type registry for working with entity types
     */
    private EntityTypeRegistry entityTypeRegistry;

    /**
     * An inventory type registry for working with inventory types
     */
    private InventoryTypeRegistry inventoryTypeRegistry;

    /**
     * An item type registry for working with item types
     */
    private ItemTypeRegistry itemTypeRegistry;

    /**
     * Create a new instance, initializing it with all default (non-addon) data.
     *
     * @since 0.1.0
     */
    protected SkriptLoader() {
        if (instance != null) {
            throw new IllegalStateException("A SkriptLoader is already present, can't create another one.");
        }

        CompletableFuture.allOf(CompletableFuture.runAsync(() -> {
            biomeRegistry = new BiomeRegistry();
            entityTypeRegistry = new EntityTypeRegistry();
            inventoryTypeRegistry = new InventoryTypeRegistry();

            registerDefaultElements();
            registerDefaultSections();
            registerDefaultConverters();
            registerDefaultEvents();
        }), CompletableFuture.runAsync(() ->
            itemTypeRegistry = new ItemTypeRegistry()
        )).join();

        instance = this;
    }


    /**
     * Parses text into psi elements.
     * Returns null if no element was found.
     *
     * @param input the text to be parsed
     * @param lineNumber the line number of the element which will potentially be parsed
     * @return the parsed psi element, or null if none were found
     * @since 0.1.0
     */
    @Nullable
    @Contract(pure = true)
    public PsiElement<?> tryParseElement(@NotNull String input, int lineNumber) {
        input = input.trim();

        for (PsiElementFactory factory : elements) {
            Map<Method, SkriptPattern[]> methods = elementsCached.get(factory);

            if (methods == null) {
                methods = new HashMap<>();
                List<Method> newMethods = new ArrayList<>(Arrays.asList(factory.getClass().getMethods()));

                for (Method method : newMethods) {
                    Pattern pattern = method.getAnnotation(Pattern.class);

                    if (pattern == null) {
                        continue;
                    }

                    Class<?> searching = factory.getClass();
                    Field field = null;

                    do {
                        try {
                            field = searching.getDeclaredField(pattern.value());
                        } catch (NoSuchFieldException ignore) {}

                        searching = searching.getSuperclass();
                    } while (searching != null);

                    if (field == null) {
                        continue;
                    }

                    field.setAccessible(true);

                    Class<?> type = field.getType();

                    SkriptPattern[] skriptPatterns;

                    try {
                        if (type == SkriptPattern.class) {
                            skriptPatterns = new SkriptPattern[]{
                                (SkriptPattern) field.get(factory)
                            };
                        } else if (type == SkriptPattern[].class) {
                            skriptPatterns = (SkriptPattern[]) field.get(factory);
                        } else {
                            continue;
                        }
                    } catch (IllegalAccessException exception) {
                        exception.printStackTrace();
                        continue;
                    }

                    methods.put(method, skriptPatterns);
                }

                elementsCached.put(factory, methods);
            }

            for (Map.Entry<Method, SkriptPattern[]> entry : methods.entrySet()) {
                Method method = entry.getKey();
                SkriptPattern[] skriptPatterns = entry.getValue();

                try {
                    for (int skriptPatternIndex = 0; skriptPatternIndex < skriptPatterns.length; skriptPatternIndex++) {
                        PatternTypeOrderHolder holder = method.getAnnotation(PatternTypeOrderHolder.class);
                        PatternTypeOrder patternTypeOrder = null;

                        if (holder != null) {
                            int amount = 0;
                            PatternTypeOrder order = null;

                            for (PatternTypeOrder typeOrder : holder.value()) {
                                for (int patternIndex : typeOrder.patterns()) {
                                    if (patternIndex != skriptPatternIndex) {
                                        continue;
                                    }

                                    amount++;
                                    order = typeOrder;
                                }
                            }

                            if (amount > 1) {
                                throw new ParsingAnnotationInvalidValueException(
                                    "Multiple PatternMetadata on the same method specify the same pattern"
                                );
                            }

                            patternTypeOrder = order;
                        }

                        SkriptPattern skriptPattern = skriptPatterns[skriptPatternIndex];
                        int typeGroupAmount = 0;

                        for (SkriptPatternGroup group : skriptPattern.getGroups()) {
                            if (group instanceof TypeGroup) {
                                typeGroupAmount++;
                            }
                        }

                        if (method.getParameterCount() < typeGroupAmount + 1) {
                            throw new IllegalStateException("Method '" + method.getName() + "' has "
                                + method.getParameterCount() + " parameters, but we expected at least " +
                                (typeGroupAmount + 1) + " parameters");
                        }

                        results:
                        for (SkriptMatchResult result : skriptPattern.match(input)) {
                            if (result.hasUnmatchedParts()) {
                                continue;
                            }

                            Collection<Pair<SkriptPatternGroup, String>> matchedGroups = result.getMatchedGroups();

                            List<TypeGroup> groups = new ArrayList<>();

                            for (Pair<SkriptPatternGroup, String> matchedGroup : matchedGroups) {
                                SkriptPatternGroup group = matchedGroup.getX();

                                if (group instanceof TypeGroup) {
                                    groups.add((TypeGroup) group);
                                }
                            }

                            List<String> matchedTypeTexts = new ArrayList<>();

                            for (Pair<SkriptPatternGroup, String> matchedGroup : matchedGroups) {
                                if (matchedGroup.getX() instanceof TypeGroup) {
                                    matchedTypeTexts.add(matchedGroup.getY());
                                }
                            }

                            Object[] elements = new Object[typeGroupAmount];

                            for (int i = 0; i < elements.length && i < groups.size(); i++) {
                                List<TypeGroup> typeGroups = new ArrayList<>();

                                for (SkriptPatternGroup group : skriptPattern.getGroups()) {
                                    if (group instanceof TypeGroup) {
                                        typeGroups.add((TypeGroup) group);
                                    }
                                }

                                int elementIndex = typeGroups.indexOf(groups.get(i));

                                if (patternTypeOrder != null && !Arrays.equals(patternTypeOrder.typeOrder(), new int[]{})) {
                                    elementIndex = patternTypeOrder.typeOrder()[i];

                                    if (elements[elementIndex] != null) {
                                        throw new ParsingAnnotationInvalidValueException(
                                            "Type order of PatternMetadata contains duplicate number '" + elementIndex + "'"
                                        );
                                    }
                                }

                                String matchedTypeText = matchedTypeTexts.get(i);

                                if (groups.get(i).getConstraint() == TypeGroup.Constraint.LITERAL) {
                                    elements[elementIndex] = matchedTypeText;
                                } else {
                                    elements[elementIndex] = tryParseElement(matchedTypeText, lineNumber);
                                }

                                //recursive retry
                                if (elements[elementIndex] == null) {
                                    continue results;
                                }
                            }

                            method.setAccessible(true);

                            Object[] parameters;

                            //allow an optional SkriptMatchResult in front
                            if (method.getParameterTypes()[0] == SkriptMatchResult.class) {
                                parameters = new Object[elements.length + 2];

                                parameters[0] = result;

                                System.arraycopy(elements, 0, parameters, 1, parameters.length - 2);
                            } else {
                                parameters = new Object[elements.length + 1];

                                System.arraycopy(elements, 0, parameters, 0, parameters.length - 1);
                            }

                            parameters[parameters.length - 1] = lineNumber;

                            PsiElement<?> element = (PsiElement<?>) method.invoke(factory, parameters);

                            if (element == null) {
                                continue;
                            }

                            for (Object child : elements) {
                                if (child instanceof PsiElement<?>) {
                                    ((PsiElement<?>) child).setParent(element);
                                }
                            }

                            return element;
                        }
                    }
                } catch (IllegalAccessException | InvocationTargetException exception) {
                    exception.printStackTrace();
                }
            }

            Set<Method> fallbackMethods = new HashSet<>();

            for (Method method : factory.getClass().getMethods()) {
                if (method.getAnnotation(Fallback.class) != null) {
                    fallbackMethods.add(method);
                }
            }

            int size = fallbackMethods.size();

            if (size > 1) {
                throw new IllegalFallbackAnnotationAmountException(
                    "Illegal amount of fallback annotations detected. Maximum is 1, but there were '" + size + "'."
                );
            }

            if (size == 1) {
                //will only loop once
                for (Method method : fallbackMethods) {
                    try {
                        Object result = method.invoke(factory, input, lineNumber);

                        if (result != null) {
                            return (PsiElement<?>) result;
                        }
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return null;
    }

    /**
     * Parses text into psi elements.
     * Throws a {@link ParseException} if no element was found.
     *
     * @param input the text to be parsed
     * @param lineNumber the line number of the element that will be parsed
     * @return the parsed psi element
     * @since 0.1.0
     */
    @NotNull
    @Contract(pure = true)
    public PsiElement<?> forceParseElement(@NotNull String input, int lineNumber) {
        PsiElement<?> result = tryParseElement(input, lineNumber);

        if (result != null) {
            return result;
        }

        throw new ParseException("Unable to find an expression named: " + input, lineNumber);
    }


    /**
     * Parses a file section into a psi section.
     * Throws a {@link ParseException} if the parsing wasn't successful.
     *
     * @param input the text to be parsed
     * @param elementsSupplier the action which parses the contained elements on demand
     * @param lineNumber the line number of the section that will be parsed
     * @return the parsed psi section, or null if none were found
     * @since 0.1.0
     */
    @NotNull
    @Contract(pure = true)
    public PsiSection forceParseSection(@NotNull String input,
                                        @NotNull Supplier<PsiElement<?>[]> elementsSupplier, int lineNumber) {
        for (PsiSectionFactory<?> factory : sections) {
            PsiSection result = factory.tryParse(input, elementsSupplier, lineNumber);

            if (result != null) {
                return result;
            }
        }

        //fall back to an 'if' statement which doesn't start with 'if'
        PsiElement<?> condition = forceParseElement(input, lineNumber);
        //try to parse the condition before all elements inside the section
        return new PsiIf(elementsSupplier.get(), condition, lineNumber);
    }

    /**
     * Returns a converter based on the specified name.
     * Returns null if no converter was found.
     *
     * @param name the name of the converter
     * @return the converter
     * @since 0.1.0
     */
    @Nullable
    @Contract(pure = true)
    private PsiConverter<?> tryGetConverter(@NotNull String name) {
        return converters.get(name);
    }

    /**
     * Returns a converter based on the specified name.
     * Throws a {@link ParseException} if no converter was found.
     *
     * @param name the name of the converter
     * @param lineNumber the line number of the element that tried to retrieve a converter
     * @return the converter
     * @since 0.1.0
     */
    @NotNull
    @Contract(pure = true)
    public PsiConverter<?> forceGetConverter(@NotNull String name, int lineNumber) {
        PsiConverter<?> result = tryGetConverter(name);

        if (result != null) {
            return result;
        }

        throw new ParseException("Unable to find a converter named: " + name, lineNumber);
    }

    /**
     * Registers the specified factory.
     *
     * @param factory the element factory to register
     * @since 0.1.0
     */
    protected void registerElement(@NotNull PsiElementFactory factory) {
        elements.add(factory);
    }

    /**
     * Registers the specified factory.
     *
     * @param factory the section factory to register
     * @since 0.1.0
     */
    protected void registerSection(@NotNull PsiSectionFactory<?> factory) {
        sections.add(factory);
    }

    /**
     * Registers the specified converter.
     *
     * @param name the name of the converter
     * @param converter the converter to register
     * @since 0.1.0
     */
    protected void registerConverter(@NotNull String name, @NotNull PsiConverter<?> converter) {
        if (converters.put(name, converter) != null) {
            throw new IllegalArgumentException("A PsiConverter with the same name has already been registered.");
        }
    }

    /**
     * Deletes the current loader instance.
     * Normally should only be called if you are the one who created it.
     *
     * @since 0.1.0
     */
    @Override
    public void close() {
        if (instance == null) {
            throw new IllegalStateException("No SkriptLoader is present, can't close it.");
        }

        instance = null;
    }

    /**
     * Gets the biome registry attached to this skript loader
     *
     * @return the biome registry
     * @since 0.1.0
     */
    @NotNull
    @Contract(pure = true)
    public BiomeRegistry getBiomeRegistry() {
        return biomeRegistry;
    }

    /**
     * Gets the entity type registry attached to this skript loader
     *
     * @return the entity type registry
     * @since 0.1.0
     */
    @NotNull
    @Contract(pure = true)
    public EntityTypeRegistry getEntityTypeRegistry() {
        return entityTypeRegistry;
    }

    /**
     * Gets the inventory type registry attached to this skript loader
     *
     * @return the inventory type registry
     * @since 0.1.0
     */
    @NotNull
    @Contract(pure = true)
    public InventoryTypeRegistry getInventoryTypeRegistry() {
        return inventoryTypeRegistry;
    }

    /**
     * Gets the item type registry attached to this skript loader
     *
     * @return the item type registry
     * @since 0.1.0
     */
    @NotNull
    @Contract(pure = true)
    public ItemTypeRegistry getItemTypeRegistry() {
        return itemTypeRegistry;
    }

    /**
     * Register the default elements to be used by this parser.
     *
     * @since 0.1.0
     */
    protected abstract void registerDefaultElements();

    /**
     * Register the default sections to be used by this parser.
     *
     * @since 0.1.0
     */
    protected abstract void registerDefaultSections();

    /**
     * Register the default converters to be used by this parser.
     *
     * @since 0.1.0
     */
    protected abstract void registerDefaultConverters();

    /**
     * Register the default events to be used by this parser.
     *
     * @since 0.1.0
     */
    protected abstract void registerDefaultEvents();

    /**
     * Tries to register the command in the given section. If this is not a command, or if it could not be registered
     * for some reason, this will do nothing.
     *
     * @param skript the skript this command belongs to
     * @param section the section to parse
     * @since 0.1.0
     */
    public abstract void tryRegisterCommand(Skript skript, SkriptFileSection section);

    /**
     * Tries to register the event in the given section. If this is not an event, or if it could not be registered
     * for some reason, this will do nothing.
     *
     * @param skript the skript this command belongs to
     * @param section the section to parse
     * @since 0.1.0
     */
    public abstract void tryRegisterEvent(Skript skript, SkriptFileSection section);
}
