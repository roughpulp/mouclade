package rpulp.mouclade;

import com.google.common.base.Charsets;

import java.util.ArrayList;
import java.util.Arrays;

public class MockInputParserListener implements InputParser.Listener{

    static abstract class Action {
        @Override public String toString() { return this.getClass().getSimpleName(); }
    }

    static abstract class UnaryAction extends Action {
        UnaryAction(int param0) {
            this.param0 = param0;
        }

        final int param0;

        @Override public boolean equals(Object that) {
            return that.getClass() == this.getClass() && (param0 == ((UnaryAction)that).param0);
        }

        @Override public String toString() { return this.getClass().getSimpleName() + "(" + param0 + ")"; }
    }

    static abstract class BinaryAction extends Action {
        BinaryAction(int param0, int param1) {
            this.param0 = param0;
            this.param1 = param1;
        }

        final int param0;
        final int param1;

        @Override public boolean equals(Object that) {
            return that.getClass() == this.getClass() && (param0 == ((BinaryAction)that).param0) && (param1 == ((BinaryAction)that).param1);
        }

        @Override public String toString() { return this.getClass().getSimpleName() + "(" + param0 + ", " + param1 + ")"; }
    }

    static class HandleOscAction extends Action {
        final int param;
        final byte[] bytes;

        HandleOscAction(int param, String text) {
            this.param = param;
            this.bytes = text.getBytes(Charsets.UTF_8);
        }

        HandleOscAction(int param, byte[] bytes, int offset, int len) {
            this.param = param;
            this.bytes = new byte[len];
            System.arraycopy(bytes, offset, this.bytes, 0, len);
        }

        @Override public boolean equals(Object other) {
            if (! (other instanceof HandleOscAction)) {
                return false;
            }
            HandleOscAction that = (HandleOscAction) other;
            return this.param == that.param && Arrays.equals(this.bytes, that.bytes);
        }
    }

    static class AddChars extends Action {
        final StringBuilder sb = new StringBuilder();

        AddChars() {}

        AddChars(String text) { sb.append(text); }

        void appendByte(int byt) { sb.append((char)byt); }

        @Override public boolean equals(Object that) {
            return that.getClass() == this.getClass() && (sb.toString().equals(((AddChars)that).sb.toString()));
        }

        @Override public String toString() {
            return this.getClass().getSimpleName() + "(" + sb + ")";
        }
    }

    static class CursorUp extends UnaryAction { CursorUp(int param0) { super(param0); } }

    static class CursorDown extends UnaryAction { CursorDown(int param0) { super(param0); } }

    static class CursorForward extends UnaryAction { CursorForward(int param0) { super(param0); } }

    static class CursorBack extends UnaryAction { CursorBack(int param0) { super(param0); } }

    static class CursorNextLine extends UnaryAction { CursorNextLine(int param0) { super(param0); } }

    static class CursorPreviousLine extends UnaryAction { CursorPreviousLine(int param0) { super(param0); } }

    static class SelectGfxRendition extends UnaryAction { SelectGfxRendition(int param0) { super(param0); } }

    static class EraseDisplay extends UnaryAction { EraseDisplay(int param0) { super(param0); } }

    static class CursorPosition extends BinaryAction { CursorPosition(int p0, int p1) { super(p0, p1); } }

    final ArrayList<Action> actions = new ArrayList<>();

    @Override public void handleOsc(int param, byte[] text, int offset, int len) { actions.add(new HandleOscAction(param, text, offset, len)); }

    @Override public void addChar(int byt) {
        int last = actions.size() - 1;
        if (last < 0 || !(actions.get(last) instanceof AddChars)) {
            actions.add(new AddChars());
            last = actions.size() - 1;
        }
        ((AddChars)actions.get(last)).appendByte(byt);
    }

    @Override public void cursorUp(int n) { actions.add(new CursorUp(n)); }

    @Override public void cursorDown(int n) { actions.add(new CursorDown(n)); }

    @Override public void cursorForward(int n) { actions.add(new CursorForward(n)); }

    @Override public void cursorBack(int n) { actions.add(new CursorBack(n)); }

    @Override public void cursorNextLine(int n) { actions.add(new CursorNextLine(n)); }

    @Override public void cursorPreviousLine(int n) { actions.add(new CursorPreviousLine(n)); }

    @Override public void cursorHorizontalAbsolute(int col) {}

    @Override public void cursorPosition(int row, int col) { actions.add(new CursorPosition(row, col)); }

    @Override public void eraseDisplay(int n) { actions.add(new EraseDisplay(n)); }

    @Override public void eraseInLine(int n) {}

    @Override public void scrollUp(int n) {}

    @Override public void scrollDown(int n) {}

    @Override public void selectGfxRendition(int n) {actions.add(new SelectGfxRendition(n)); }

    @Override public void auxPort(int n) {}

    @Override public void saveCursorPos() {}

    @Override public void restoreCursorPos() {}

    @Override public void hideCursor() {}

    @Override public void showCursor() {}
}
