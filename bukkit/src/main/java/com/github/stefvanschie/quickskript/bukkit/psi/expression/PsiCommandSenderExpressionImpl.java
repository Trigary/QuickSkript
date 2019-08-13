package com.github.stefvanschie.quickskript.bukkit.psi.expression;

import com.github.stefvanschie.quickskript.bukkit.context.EventContextImpl;
import com.github.stefvanschie.quickskript.core.context.Context;
import com.github.stefvanschie.quickskript.core.context.EventContext;
import com.github.stefvanschie.quickskript.core.psi.exception.ExecutionException;
import com.github.stefvanschie.quickskript.core.psi.expression.PsiCommandSenderExpression;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * An expression to get the object that executed a command
 *
 * @since 0.1.0
 */
public class PsiCommandSenderExpressionImpl extends PsiCommandSenderExpression {

    /**
     * A map mapping event classes and methods that return a {@link CommandSender} to avoid unnecessarily using
     * reflection too often.
     */
    private static final Map<Class<? extends Event>, Method> CACHED_METHODS = new HashMap<>();

    /**
     * Creates a new element with the given line number
     *
     * @param lineNumber the line number this element is associated with
     * @since 0.1.0
     */
    private PsiCommandSenderExpressionImpl(int lineNumber) {
        super(lineNumber);
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Contract(pure = true)
    @Override
    protected Object executeImpl(@Nullable Context context) {
        if (!(context instanceof EventContext)) {
            throw new ExecutionException("Command sender expression can only be used from inside an event", lineNumber);
        }

        Event event = ((EventContextImpl) context).getEvent();
        Class<? extends Event> eventClass = event.getClass();
        Method cached = CACHED_METHODS.get(eventClass);

        if (cached != null) {
            try {
                return cached.invoke(event);
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }

        for (Method method : eventClass.getMethods()) {
            if (method.getParameterCount() != 0 || !CommandSender.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }

            CACHED_METHODS.put(eventClass, method);

            try {
                return method.invoke(event);
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }

        throw new ExecutionException("Command sender expression cannot be called from this event", lineNumber);
    }

    /**
     * A factory for creating {@link PsiCommandSenderExpressionImpl}s
     *
     * @since 0.1.0
     */
    public static class Factory extends PsiCommandSenderExpression.Factory {

        /**
         * {@inheritDoc}
         */
        @NotNull
        @Contract(pure = true)
        @Override
        public PsiCommandSenderExpression create(int lineNumber) {
            return new PsiCommandSenderExpressionImpl(lineNumber);
        }
    }
}