package dev.msf.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents that an annotated element must not be {@code null}.
 *
 * <p>Applied to method parameters and return types on all public APIs per MSF coding standards.
 * Violations result in {@link NullPointerException} at runtime.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD, ElementType.LOCAL_VARIABLE})
public @interface NotNull {
}
