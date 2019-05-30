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
 * Checks whether a player is sneaking. This cannot be pre computed, since players may start or end sneaking during game
 * play.
 *
 * @since 0.1.0
 */
public class PsiIsSneakingCondition extends PsiElement<Boolean> {

    /**
     * The player to check whether they are sneaking
     */
    @NotNull
    protected final PsiElement<?> player;

    /**
     * If false, the execution result will be inverted
     */
    protected final boolean positive;

    /**
     * Creates a new element with the given line number
     *
     * @param player the player to check whether they are sneaking
     * @param positive if false, the execution result will be inverted
     * @param lineNumber the line number this element is associated with
     * @since 0.1.0
     */
    protected PsiIsSneakingCondition(@NotNull PsiElement<?> player, boolean positive, int lineNumber) {
        super(lineNumber);

        this.player = player;
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
     * A factory for creating {@link PsiIsSneakingCondition}s
     *
     * @since 0.1.0
     */
    public static class Factory implements PsiElementFactory<PsiIsSneakingCondition> {

        /**
         * A pattern for matching positive {@link PsiIsSneakingCondition}s
         */
        @NotNull
        private final Pattern positivePattern = Pattern.compile("(?<player>[\\s\\S]+) (?:is|are) sneaking");

        /**
         * A pattern for matching negative {@link PsiIsSneakingCondition}s
         */
        @NotNull
        private final Pattern negativePattern =
            Pattern.compile("(?<player>[\\s\\S]+) (?:isn't|is not|aren't|are not) sneaking");

        /**
         * {@inheritDoc}
         */
        @Nullable
        @Contract(pure = true)
        @Override
        public PsiIsSneakingCondition tryParse(@NotNull String text, int lineNumber) {
            var skriptLoader = SkriptLoader.get();

            Matcher positiveMatcher = positivePattern.matcher(text);

            if (positiveMatcher.matches()) {
                String playerGroup = positiveMatcher.group("player");

                PsiElement<?> player = skriptLoader.forceParseElement(playerGroup, lineNumber);

                return create(player, true, lineNumber);
            }

            Matcher negativeMatcher = negativePattern.matcher(text);

            if (negativeMatcher.matches()) {
                String playerGroup = negativeMatcher.group("player");

                PsiElement<?> player = skriptLoader.forceParseElement(playerGroup, lineNumber);

                return create(player, false, lineNumber);
            }

            return null;
        }

        /**
         * Provides a default way for creating the specified object for this factory with the given parameters as
         * constructor parameters. This should be overridden by impl, instead of the {@link #tryParse(String, int)}
         * method.
         *
         * @param player the player to check whether they are sneaking
         * @param positive false if the result of the execution should be negated, true otherwise
         * @param lineNumber the line number
         * @return the condition
         * @since 0.1.0
         */
        @NotNull
        @Contract(pure = true)
        public PsiIsSneakingCondition create(@NotNull PsiElement<?> player, boolean positive, int lineNumber) {
            return new PsiIsSneakingCondition(player, positive, lineNumber);
        }
    }
}
