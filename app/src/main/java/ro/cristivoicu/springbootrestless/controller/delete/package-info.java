/**
 * Ground rules Phase 3 item 17: every reference type in this package is non-null
 * unless explicitly annotated {@code @Nullable} - documentation of intent via JSpecify, not a
 * wired-in null-checking build step (no NullAway/IntelliJ inspection enforces this here yet).
 */
@NullMarked
package ro.cristivoicu.springbootrestless.controller.delete;

import org.jspecify.annotations.NullMarked;
