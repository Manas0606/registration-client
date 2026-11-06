package io.mosip.registration.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.ListResourceBundle;
import java.util.ResourceBundle;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.BeforeClass;
import org.junit.Test;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;

/**
 * Real JavaFX event-dispatch regression tests for mapped physical keyboard input.
 * The test bundle deliberately maps A/B to different characters so incorrectly
 * consuming a KEY_TYPED event cannot pass unnoticed with an English mapping.
 *
 * Run these tests with a graphical display (Xvfb on headless Linux).
 */
public class VirtualKeyboardTest {
    private static final long FX_TIMEOUT_SECONDS = 15L;

    private static final class Fixture {
        final TextField field = new TextField();
        final VirtualKeyboard keyboard = new VirtualKeyboard("eng");

        Fixture() throws Exception {
            Field bundleField = VirtualKeyboard.class.getDeclaredField("keyboard");
            bundleField.setAccessible(true);
            bundleField.set(keyboard, new ListResourceBundle() {
                @Override
                protected Object[][] getContents() {
                    return new Object[][] {
                        {"unshift_A", "α"}, {"shift_A", "Α"},
                        {"unshift_B", "β"}, {"shift_B", "Β"}
                    };
                }
            });
            new Scene(new VBox(field));
            keyboard.changeControlOfKeyboard(field);
        }

        void press(KeyCode code) {
            key(KeyEvent.KEY_PRESSED, code, "", false, false, false, false);
        }

        void press(KeyCode code, boolean shift, boolean ctrl, boolean alt, boolean meta) {
            key(KeyEvent.KEY_PRESSED, code, "", shift, ctrl, alt, meta);
        }

        void release(KeyCode code) {
            key(KeyEvent.KEY_RELEASED, code, "", false, false, false, false);
        }

        void typed(String character) {
            key(KeyEvent.KEY_TYPED, KeyCode.UNDEFINED, character, false, false, false, false);
        }

        void typed(String character, boolean shift, boolean ctrl, boolean alt, boolean meta) {
            key(KeyEvent.KEY_TYPED, KeyCode.UNDEFINED, character, shift, ctrl, alt, meta);
        }

        void key(javafx.event.EventType<KeyEvent> type, KeyCode code, String character,
                 boolean shift, boolean ctrl, boolean alt, boolean meta) {
            field.fireEvent(new KeyEvent(type, character, "", code, shift, ctrl, alt, meta));
        }

        void spaceALetterRollover(boolean releaseSpaceFirst) {
            press(KeyCode.SPACE);
            press(KeyCode.A);
            typed(" ");
            typed("a");
            if (releaseSpaceFirst) {
                release(KeyCode.SPACE);
                release(KeyCode.A);
            } else {
                release(KeyCode.A);
                release(KeyCode.SPACE);
            }
        }
    }

    @BeforeClass
    public static void initializeToolkit() throws Exception {
        // JavaFX can only be started once per JVM; other tests may have started it.
        if (Platform.isFxApplicationThread()) {
            return;
        }
        FutureTask<Void> initialized = new FutureTask<>(() -> null);
        try {
            Platform.startup(initialized);
        } catch (IllegalStateException alreadyStarted) {
            return;
        }
        initialized.get(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);
    }

    private static void onFxThread(FxAssertion assertion) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            assertion.run();
            return null;
        });
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
        task.get(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @FunctionalInterface
    private interface FxAssertion {
        void run() throws Exception;
    }

    @Test
    public void spaceThenMappedLetterWithRolloverRetainsBothInOrder() throws Exception {
        onFxThread(() -> {
            Fixture f = new Fixture();
            f.spaceALetterRollover(false);
            assertEquals(" α", f.field.getText());
            assertTrue(f.field.isEditable());
        });
    }

    @Test
    public void rolloverWorksWhenSpaceIsReleasedFirst() throws Exception {
        onFxThread(() -> {
            Fixture f = new Fixture();
            f.spaceALetterRollover(true);
            assertEquals(" α", f.field.getText());
        });
    }

    @Test
    public void repeatedFastRolloverDoesNotDropOrDuplicateCharacters() throws Exception {
        onFxThread(() -> {
            Fixture f = new Fixture();
            for (int i = 0; i < 20; i++) {
                f.spaceALetterRollover(i % 2 == 0);
            }
            assertEquals(" α".repeat(20), f.field.getText());
        });
    }

    @Test
    public void mappedLetterBeforeUnmappedSpacePreservesOrder() throws Exception {
        onFxThread(() -> {
            Fixture f = new Fixture();
            f.press(KeyCode.A);
            f.press(KeyCode.SPACE);
            f.typed("a");
            f.typed(" ");
            f.release(KeyCode.SPACE);
            f.release(KeyCode.A);
            assertEquals("α ", f.field.getText());
        });
    }

    @Test
    public void releasedNonHeadKeyDoesNotLeaveStaleMapping() throws Exception {
        onFxThread(() -> {
            Fixture f = new Fixture();
            f.press(KeyCode.A);
            f.press(KeyCode.B);
            f.release(KeyCode.B); // B is not at the head of the queue.
            f.typed("a");
            f.release(KeyCode.A);
            f.typed("z"); // No key press: should not consume stale B mapping.
            assertEquals("αz", f.field.getText());
        });
    }

    @Test
    public void releasedMappedKeyWithoutTypedEventDoesNotCorruptNextKey() throws Exception {
        onFxThread(() -> {
            Fixture f = new Fixture();
            f.press(KeyCode.A);
            f.release(KeyCode.A);
            f.press(KeyCode.B);
            f.typed("b");
            f.release(KeyCode.B);
            assertEquals("β", f.field.getText());
        });
    }

    @Test
    public void unmappedSpaceDoesNotConsumeLaterMappedLetter() throws Exception {
        onFxThread(() -> {
            Fixture f = new Fixture();
            f.press(KeyCode.SPACE);
            f.press(KeyCode.B);
            f.typed(" ");
            f.typed("b");
            f.release(KeyCode.B);
            f.release(KeyCode.SPACE);
            assertEquals(" β", f.field.getText());
        });
    }

    @Test
    public void mappedCharactersAreInsertedOnlyOnce() throws Exception {
        onFxThread(() -> {
            Fixture f = new Fixture();
            f.press(KeyCode.A);
            f.typed("a");
            f.release(KeyCode.A);
            f.press(KeyCode.B);
            f.typed("b");
            f.release(KeyCode.B);
            assertEquals("αβ", f.field.getText());
        });
    }

    @Test
    public void shiftAndCapsMappingsRemainCorrect() throws Exception {
        onFxThread(() -> {
            Fixture f = new Fixture();
            f.press(KeyCode.A, true, false, false, false);
            f.typed("A", true, false, false, false);
            f.release(KeyCode.A);
            f.press(KeyCode.CAPS);
            f.release(KeyCode.CAPS);
            f.press(KeyCode.B);
            f.typed("B");
            f.release(KeyCode.B);
            assertEquals("ΑΒ", f.field.getText());
        });
    }

    @Test
    public void controlShortcutDoesNotQueueACharacter() throws Exception {
        onFxThread(() -> {
            Fixture f = new Fixture();
            f.field.setText("old");
            f.field.positionCaret(f.field.getLength());
            f.press(KeyCode.A, false, true, false, false);
            f.release(KeyCode.A);
            // A normal subsequent key must not pick up a stale Ctrl+A mapping.
            f.press(KeyCode.B);
            f.typed("b");
            f.release(KeyCode.B);
            assertEquals("oldβ", f.field.getText());
        });
    }

    @Test
    public void altAndMetaModifiedKeysDoNotLeaveStaleMappings() throws Exception {
        onFxThread(() -> {
            Fixture f = new Fixture();
            f.press(KeyCode.A, false, false, true, false);
            f.release(KeyCode.A);
            f.press(KeyCode.B, false, false, false, true);
            f.release(KeyCode.B);
            f.press(KeyCode.A);
            f.typed("a");
            f.release(KeyCode.A);
            assertEquals("α", f.field.getText());
        });
    }

    @Test
    public void virtualKeyboardEventsBypassPhysicalMappings() throws Exception {
        onFxThread(() -> {
            Fixture f = new Fixture();
            Field typeField = VirtualKeyboard.class.getDeclaredField("vkType");
            typeField.setAccessible(true);
            StringBuilder vkType = (StringBuilder) typeField.get(f.keyboard);
            vkType.append("vk");
            try {
                f.press(KeyCode.A);
                f.typed("a");
                f.release(KeyCode.A);
            } finally {
                vkType.setLength(0);
            }
            assertEquals("a", f.field.getText());
        });
    }

    @Test
    public void unmappedLetterUsesNormalJavaFxTextInsertion() throws Exception {
        onFxThread(() -> {
            Fixture f = new Fixture();
            f.press(KeyCode.Z);
            f.typed("z");
            f.release(KeyCode.Z);
            assertEquals("z", f.field.getText());
        });
    }

    @Test
    public void typingAfterSelectionReplacesOnlySelectedText() throws Exception {
        onFxThread(() -> {
            Fixture f = new Fixture();
            f.field.setText("hello");
            f.field.selectRange(1, 4);
            f.press(KeyCode.A);
            f.typed("a");
            f.release(KeyCode.A);
            assertEquals("hαo", f.field.getText());
        });
    }
}
