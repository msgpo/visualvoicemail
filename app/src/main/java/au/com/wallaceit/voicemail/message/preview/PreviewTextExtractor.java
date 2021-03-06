package au.com.wallaceit.voicemail.message.preview;


import android.support.annotation.NonNull;

import au.com.wallaceit.voicemail.helper.HtmlConverter;
import com.fsck.k9.mail.Part;
import com.fsck.k9.mail.internet.MessageExtractor;

import static com.fsck.k9.mail.internet.MimeUtility.isSameMimeType;


class PreviewTextExtractor {
    private static final int MAX_PREVIEW_LENGTH = 512;
    private static final int MAX_CHARACTERS_CHECKED_FOR_PREVIEW = 8192;


    @NonNull
    public String extractPreview(@NonNull Part textPart) throws PreviewExtractionException {
        String text = MessageExtractor.getTextFromPart(textPart);
        if (text == null) {
            throw new PreviewExtractionException("Couldn't get text from part");
        }

        String plainText = convertFromHtmlIfNecessary(textPart, text);

        return stripTextForPreview(plainText);
    }

    private String convertFromHtmlIfNecessary(Part textPart, String text) {
        String mimeType = textPart.getMimeType();
        if (!isSameMimeType(mimeType, "text/html")) {
            return text;
        }

        return HtmlConverter.htmlToText(text);
    }

    private String stripTextForPreview(String text) {
        // Only look at the first 8k of a message when calculating
        // the preview.  This should avoid unnecessary
        // memory usage on large messages
        if (text.length() > MAX_CHARACTERS_CHECKED_FOR_PREVIEW) {
            text = text.substring(0, MAX_CHARACTERS_CHECKED_FOR_PREVIEW);
        }

        // Remove (correctly delimited by '-- \n') signatures
        text = text.replaceAll("(?ms)^-- [\\r\\n]+.*", "");
        // try to remove lines of dashes in the preview
        text = text.replaceAll("(?m)^----.*?$", "");
        // remove quoted text from the preview
        text = text.replaceAll("(?m)^[#>].*$", "");
        // Remove a common quote header from the preview
        text = text.replaceAll("(?m)^On .*wrote.?$", "");
        // Remove a more generic quote header from the preview
        text = text.replaceAll("(?m)^.*\\w+:$", "");
        // Remove horizontal rules.
        text = text.replaceAll("\\s*([-=_]{30,}+)\\s*", " ");

        // URLs in the preview should just be shown as "..." - They're not
        // clickable and they usually overwhelm the preview
        text = text.replaceAll("https?://\\S+", "...");
        // Don't show newlines in the preview
        text = text.replaceAll("(\\r|\\n)+", " ");
        // Collapse whitespace in the preview
        text = text.replaceAll("\\s+", " ");
        // Remove any whitespace at the beginning and end of the string.
        text = text.trim();

        return (text.length() > MAX_PREVIEW_LENGTH) ? text.substring(0, MAX_PREVIEW_LENGTH - 1) + "…" : text;
    }
}
