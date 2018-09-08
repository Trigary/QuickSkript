package com.github.stefvanschie.quickskript.psi.function;

import com.github.stefvanschie.quickskript.context.Context;
import com.github.stefvanschie.quickskript.psi.PsiElement;
import com.github.stefvanschie.quickskript.psi.PsiElementFactory;
import com.github.stefvanschie.quickskript.psi.exception.ExecutionException;
import com.github.stefvanschie.quickskript.psi.util.PsiCollection;
import com.github.stefvanschie.quickskript.skript.SkriptLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calculates the product from a given collection of numbers
 *
 * @since 0.1.0
 */
public class PsiProductFunction extends PsiElement<Double> {

    /**
     * An element containing a bunch of numbers
     */
    private PsiElement<?> element;

    /**
     * Creates a new product function
     *
     * @param element an element containing a collection of elements of numbers
     * @since 0.1.0
     */
    private PsiProductFunction(PsiElement<?> element, int lineNumber) {
        super(lineNumber);

        this.element = element;

        if (this.element.isPreComputed()) {
            preComputed = executeImpl(null);
            this.element = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @NotNull
    @Override
    protected Double executeImpl(@Nullable Context context) {
        Collection<?> collection = element.execute(context, Collection.class);

        double result = 1;

        for (Object object : collection) {
            if (!(object instanceof PsiElement<?>))
                throw new ExecutionException("Collection should only contain psi elements, but it didn't", lineNumber);

            result *= ((PsiElement<?>) object).execute(context, Number.class).doubleValue();
        }

        return result;
    }

    /**
     * A factory for creating product functions
     *
     * @since 0.1.0
     */
    public static class Factory implements PsiElementFactory<PsiProductFunction> {

        /**
         * The pattern for matching product expressions
         */
        private final Pattern pattern = Pattern.compile("product\\(([\\s\\S]+)\\)");

        /**
         * {@inheritDoc}
         */
        @Nullable
        @Override
        public PsiProductFunction tryParse(@NotNull String text, int lineNumber) {
            Matcher matcher = pattern.matcher(text);

            if (!matcher.matches())
                return null;

            String[] values = matcher.group(1).replace(" ", "").split(",");

            if (values.length == 1) {
                PsiElement<?> collection = SkriptLoader.get().tryParseElement(values[0], lineNumber);

                if (collection != null)
                    return new PsiProductFunction(collection, lineNumber);
            }

            return new PsiProductFunction(new PsiCollection<>(Arrays.stream(values)
                .map(string -> SkriptLoader.get().forceParseElement(string, lineNumber)), lineNumber), lineNumber);
        }
    }
}
