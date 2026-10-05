package ro.cristivoicu.springbootrestless.fixtures.unrelated;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately not a {@code RestlessResourceHandler}/hand-subclassed {@code *Controller} - a
 * stand-in for a consumer's own, entirely unrelated {@code @RestController}, proving {@code
 * RestlessExceptionHandlerScopeTest} that {@code RestlessExceptionHandler}'s {@code
 * assignableTypes = RestlessErrorScope.class} scoping doesn't touch it at all (Ground rules
 * item 7 - before this, every exception thrown from here would have been caught by the same
 * catch-all {@code @ExceptionHandler(Exception.class)} every Restless route gets).
 */
@RestController
public class UnrelatedController {

    @GetMapping("/unrelated/boom")
    public String boom() {
        throw new IllegalStateException("not a restless failure");
    }
}
