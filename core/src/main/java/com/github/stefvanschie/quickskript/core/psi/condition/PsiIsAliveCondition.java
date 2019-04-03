package com.github.stefvanschie.quickskript.core.psi.condition;

import com.github.stefvanschie.quickskript.core.context.Context;
import com.github.stefvanschie.quickskript.core.psi.PsiElement;
import com.github.stefvanschie.quickskript.core.psi.PsiElementFactory;
import com.github.stefvanschie.quickskript.core.skript.SkriptLoader;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks whether a living entity is alive or not. This cannot be pre computed, since the living entity may be
 * killed/revived during game play.
 *
 * @since 0.1.0
 */
public class PsiIsAliveCondition extends PsiElement<Boolean> {

    /**
     * The living entity to check the alive state for
     */
    @NotNull
    protected PsiElement<?> livingEntity;

    /**
     * False if the execution result needs to be inverted
     */
    protected boolean positive;

    /**
     * Creates a new element with the given line number
     *
     * @param livingEntity the living entity to check the alive state for, see {@link #livingEntity}
     * @param positive false if the result of execution needs to be inverted
     * @param lineNumber the line number this element is associated with
     * @since 0.1.0
     */
    protected PsiIsAliveCondition(@NotNull PsiElement<?> livingEntity, boolean positive, int lineNumber) {
        super(lineNumber);

        this.livingEntity = livingEntity;
        this.positive = positive;
    }

    /**
     * @throws UnsupportedOperationException implementation is required for this functionality
     */
    @Nullable
    @Override
    protected Boolean executeImpl(@Nullable Context context) {
        throw new UnsupportedOperationException("Cannot execute expression without implementation.");
    }

    /**
     * A factory for creating {@link PsiIsAliveCondition}s
     *
     * @since 0.1.0
     */
    public static class Factory implements PsiElementFactory<PsiIsAliveCondition> {

        /**
         * The pattern for matching positive is alive conditions
         */
        @NotNull
        private final Pattern positivePattern =
            Pattern.compile("([\\s\\S]+) (?:(?:is|are) alive|(?:isn't|is not|aren't|are not) dead)");

        /**
         * The pattern for matching negative is alive conditions
         */
        @NotNull
        private final Pattern negativePattern =
            Pattern.compile("([\\s\\S]+) (?:(?:is|are) dead|(?:isn't|is not|aren't|are not) alive)");

        /**
         * {@inheritDoc}
         */
        @Nullable
        @Contract(pure = true)
        @Override
        public PsiIsAliveCondition tryParse(@NotNull String text, int lineNumber) {
            Matcher positiveMatcher = positivePattern.matcher(text);

            if (positiveMatcher.matches()) {
                PsiElement<?> livingEntity = SkriptLoader.get().forceParseElement(positiveMatcher.group(1), lineNumber);

                return create(livingEntity, true, lineNumber);
            }

            Matcher negativeMatcher = negativePattern.matcher(text);

            if (negativeMatcher.matches()) {
                PsiElement<?> livingEntity = SkriptLoader.get().forceParseElement(negativeMatcher.group(1), lineNumber);

                return create(livingEntity, false, lineNumber);
            }

            return null;
        }

        /**
         * Provides a default way for creating the specified object for this factory with the given parameters as
         * constructor parameters. This should be overridden by impl, instead of the {@link #tryParse(String, int)}
         * method.
         *
         * @param livingEntity the living entity to check the alive state for
         * @param positive false if the result of the execution should be negated, true otherwise
         * @param lineNumber the line number
         * @return the condition
         * @since 0.1.0
         */
        @NotNull
        @Contract(pure = true)
        public PsiIsAliveCondition create(@NotNull PsiElement<?> livingEntity, boolean positive, int lineNumber) {
            return new PsiIsAliveCondition(livingEntity, positive, lineNumber);
        }
    }
}