package com.github.stefvanschie.quickskript.core.psi.condition;

import com.github.stefvanschie.quickskript.core.context.Context;
import com.github.stefvanschie.quickskript.core.psi.PsiElement;
import com.github.stefvanschie.quickskript.core.psi.PsiElementFactory;
import com.github.stefvanschie.quickskript.core.skript.SkriptLoader;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Checks to see if values on both sides are or aren't equal. If this value is pre computed, we'll throw a warning to
 * signal the user that the expression is unneeded and can be replaced by a direct boolean value.
 *
 * @since 0.1.0
 */
public class PsiIsCondition extends PsiElement<Boolean> {

    /**
     * The left and right side which will be used for comparison
     */
    private PsiElement<?> leftSide, rightSide;

    /**
     * True if the result stays the same, false if it needs to be inverted
     */
    private final boolean positive;

    /**
     * Creates a new element with the given line number
     *
     * @param leftSide the left hand side of the expression
     * @param rightSide the right hand side of the expression
     * @param positive true if the value stays the same, false if it will be inverted
     * @param lineNumber the line number this element is associated with
     * @since 0.1.0
     */
    private PsiIsCondition(@NotNull PsiElement<?> leftSide, @NotNull PsiElement<?> rightSide, boolean positive,
                           int lineNumber) {
        super(lineNumber);

        this.leftSide = leftSide;
        this.rightSide = rightSide;
        this.positive = positive;

        if (this.leftSide.isPreComputed() && this.rightSide.isPreComputed()) {
            //TODO: Show warning
            preComputed = executeImpl(null);
            this.leftSide = null;
            this.rightSide = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    protected Boolean executeImpl(@Nullable Context context) {
        Object leftResult = leftSide.execute(context);
        Object rightResult = rightSide.execute(context);

        if (leftResult == null && rightResult == null) {
            return true;
        } else if (leftResult == null || rightResult == null) {
            return false;
        }

        return positive == leftResult.equals(rightResult);
    }

    /**
     * A factory for creating {@link PsiIsCondition}s.
     *
     * @since 0.1.0
     */
    public static class Factory implements PsiElementFactory<PsiIsCondition> {

        /**
         * A pattern for matching positive elements
         */
        @NotNull
        private static final Pattern POSITIVE_PATTERN = Pattern.compile(
            "(?<leftSide>[\\s\\S]+) (?:(?:is)|(?:are)|(?:=)) (?:(?:equal to)|(?:the same as) )?(?<rightSide>[\\s\\S]+)"
        );

        /**
         * A pattern for matching negative elements
         */
        @NotNull
        private static final Pattern NEGATIVE_PATTERN = Pattern.compile(
            "(?<leftSide>[\\s\\S]+) (?:(?:(?:(?:is)|(?:are)) (?:(?:not)|(?:neither)))|(?:!=)|(?:isn't)|(?:aren't)) (?:(?:equal to)|(?:the same as) )?(?<rightSide>[\\s\\S]+)"
        );

        /**
         * {@inheritDoc}
         */
        @Nullable
        @Contract(pure = true)
        @Override
        public PsiIsCondition tryParse(@NotNull String text, int lineNumber) {
            var skriptLoader = SkriptLoader.get();

            var positiveMatcher = POSITIVE_PATTERN.matcher(text);

            if (positiveMatcher.matches()) {
                String leftSideGroup = positiveMatcher.group("leftSide");
                String rightSideGroup = positiveMatcher.group("rightSide");

                var leftSideElement = skriptLoader.forceParseElement(leftSideGroup, lineNumber);
                var rightSideElement = skriptLoader.forceParseElement(rightSideGroup, lineNumber);

                return create(leftSideElement, rightSideElement, true, lineNumber);
            }

            var negativeMatcher = NEGATIVE_PATTERN.matcher(text);

            if (negativeMatcher.matches()) {
                String leftSideGroup = negativeMatcher.group("leftSide");
                String rightSideGroup = negativeMatcher.group("rightSide");

                var leftSideElement = skriptLoader.forceParseElement(leftSideGroup, lineNumber);
                var rightSideElement = skriptLoader.forceParseElement(rightSideGroup, lineNumber);

                return create(leftSideElement, rightSideElement, false, lineNumber);
            }

            return null;
        }

        /**
         * Provides a default way for creating the specified object for this factory with the given parameters as
         * constructor parameters. This should be overridden by impl, instead of the {@link #tryParse(String, int)}
         * method.
         *
         * @param leftSide the left hand side element
         * @param rightSide the right hand side element
         * @param positive false if the result should be negated, true otherwise
         * @param lineNumber the line number
         * @return the condition
         * @since 0.1.0
         */
        @NotNull
        @Contract(pure = true)
        protected PsiIsCondition create(@NotNull PsiElement<?> leftSide, @NotNull PsiElement<?> rightSide,
                                        boolean positive, int lineNumber) {
            return new PsiIsCondition(leftSide, rightSide, positive, lineNumber);
        }
    }
}
