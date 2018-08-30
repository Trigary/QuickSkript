package com.github.stefvanschie.quickskript.psi.function;

import com.github.stefvanschie.quickskript.context.Context;
import com.github.stefvanschie.quickskript.psi.PsiElement;
import com.github.stefvanschie.quickskript.psi.PsiElementUtil;
import com.github.stefvanschie.quickskript.psi.PsiElementFactory;
import com.github.stefvanschie.quickskript.psi.exception.ExecutionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
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
     * @param element an element containing an iterable of numbers
     * @since 0.1.0
     */
    private PsiProductFunction(PsiElement<?> element) {
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
        Object elementResult = element.execute(context);

        if (!(elementResult instanceof Iterable<?>))
            throw new ExecutionException("Result of expression should be an iterable, but it wasn't");

        Iterable<?> iterable = (Iterable<?>) elementResult;

        double result = 1; //negative double max value is the actual smallest value, double min value is still positive

        for (Object object : iterable) {
            if (!(object instanceof PsiElement<?>))
                throw new ExecutionException("Iterable should only contain psi elements, but it didn't");

            Object numberResult = ((PsiElement) object).execute(context);

            if (!(numberResult instanceof Number))
                throw new ExecutionException("Result of expression should be a number, but it wasn't");

           result *= ((Number) numberResult).doubleValue();
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
        private static final Pattern PATTERN = Pattern.compile("product\\(([\\s\\S]+)\\)");

        /**
         * {@inheritDoc}
         */
        @Nullable
        @Override
        public PsiProductFunction tryParse(@NotNull String text) {
            Matcher matcher = PATTERN.matcher(text);

            if (!matcher.matches())
                return null;

            String[] values = matcher.group(1).replace(" ", "").split(",");

            if (values.length == 1) {
                PsiElement<?> iterable = PsiElementUtil.tryParseText(values[0]);

                if (iterable != null)
                    return new PsiProductFunction(iterable);
            }

            Set<PsiElement<?>> numbers = new HashSet<>();

            for (String value : values)
                numbers.add(PsiElementUtil.tryParseText(value));

            PsiElement<Iterable<?>> iterable = new PsiElement<Iterable<?>>() {
                {
                    preComputed = numbers;
                }

                @Override
                protected Iterable<?> executeImpl(@Nullable Context context) {
                    throw new AssertionError("Since this preComputed variable is always set, this method should never get called");
                }
            };

            return new PsiProductFunction(iterable);
        }
    }
}
