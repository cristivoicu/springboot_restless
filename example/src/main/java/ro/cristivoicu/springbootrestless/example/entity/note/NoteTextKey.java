package ro.cristivoicu.springbootrestless.example.entity.note;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.resource.RestlessDedupeKey;

import java.util.Locale;

/** Two notes with the same text (ignoring case and surrounding spaces) are the same note. */
@Component
public class NoteTextKey implements RestlessDedupeKey<Note, NoteCreateModel> {

    @Override
    public Object keyOfModel(NoteCreateModel model) {
        return normalize(model.getText());
    }

    @Override
    public Object keyOfExisting(Note existing) {
        return normalize(existing.getText());
    }

    private static String normalize(String text) {
        return text == null ? null : text.trim().toLowerCase(Locale.ROOT);
    }
}
