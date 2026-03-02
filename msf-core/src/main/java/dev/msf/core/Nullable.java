package dev.msf.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents that an annotated element may be {@code null}.
 *
 * <p>Applied to method parameters and return types on all public APIs per MSF coding standards.
 * Callers must null-check values annotated with this annotation before use.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD, ElementType.LOCAL_VARIABLE})
public @interface Nullable {
}
