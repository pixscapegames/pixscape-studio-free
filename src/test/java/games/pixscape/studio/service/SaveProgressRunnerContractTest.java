package games.pixscape.studio.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class SaveProgressRunnerContractTest {

    @Test
    public void asynchronousStepFailure_finishesRunnerAndHidesDialog() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/service/SaveProgressRunner.java"),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("Consumer<Throwable> fail")
                || source.contains("java.util.function.Consumer<Throwable> fail"));
        assertTrue(source.contains("failure -> Gdx.app.postRunnable(() -> finishWithError(failure, onError))"));

        String failureBody = methodBody(source, "private void finishWithError(");
        assertTrue(failureBody.contains("if (finished) return;"));
        assertTrue(failureBody.contains("dialog.hide();"));
        assertTrue(failureBody.contains("onError.accept(failure);"));
    }

    private static String methodBody(String source, String signaturePrefix) {
        int signatureIndex = source.indexOf(signaturePrefix);
        if (signatureIndex < 0) throw new AssertionError("Method signature not found: " + signaturePrefix);

        int bodyStart = source.indexOf('{', signatureIndex);
        if (bodyStart < 0) throw new AssertionError("Method body start not found: " + signaturePrefix);

        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart + 1, i);
                }
            }
        }
        throw new AssertionError("Method body end not found: " + signaturePrefix);
    }
}
