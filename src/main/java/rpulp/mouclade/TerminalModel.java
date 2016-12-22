package rpulp.mouclade;

import java.util.Arrays;

class TerminalModel implements InputParser.Listener{

    interface Listener {
        void onCellUpdate(int at);

        void onCellRangeUpdate(int start, int len);
    }

    private int width = 0;
    private int height = 0;

    private char[] chars = new char[0];
    private int[] fgColors = new int[0];
    private int[] bgColors = new int[0];
    private int[] attrs = new int[0];

    private int caretX = 0;
    private int caretY = 0;

    private Listener listener;

    TerminalModel() {
        resize(80, 40);
    }

    int width() { return width; }

    int height() { return height; }

    char[] chars() { return chars; }

    int[] fgColors() { return fgColors; }

    int[] bgColors() { return bgColors; }

    int[] attrs() { return attrs; }

    void resize(int width, int height) {
        this.width = width;
        this.height = height;
        int size = width * height;
        chars = new char[size];
        fgColors = new int[size];
        bgColors = new int[size];
        attrs = new int[size];
        Arrays.fill(chars, ' ');
    }

    int at(int xx, int yy) { return yy * width + xx; }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    private void updateCell(int at) {
        if (listener != null) {
            listener.onCellUpdate(at);
        }
    }

    //FIXME: ring buffer instead ?
    private void badMethod() {
        System.arraycopy(chars, width, chars, 0, chars.length - width);
        Arrays.fill(chars, chars.length - width, chars.length, ' ');
        listener.onCellRangeUpdate(0, chars.length);
    }

    @Override public void addChar(int byt) {
        char car = (char) byt;
        switch (car) {
            case 0x07: break;  //bell
            case 0x08: backspace(); break;
            case 0x09: htab(); break;
            case 0x0a: lineFeed(); break;
            case 0x0b: lineFeed(); break; // vtab
            case 0x0c: formFeed(); break;
            case 0x0d: carriageReturn(); break;
            default: {
                if (car < 0x20) {
                    car = ' ';
                }
                addNormalChar(car);
            }
        }
    }

    private void addNormalChar(char car) {
        int at = at(caretX, caretY);
        chars[at] = car;
        updateCell(at);
        cursorNext();
    }

    void backspace() {
        if (caretX > 0) {
            --caretX;
        } else if (caretY > 0) {
            --caretY;
            caretX = width - 1;
        }
    }

    void lineFeed() {
        if (caretY == height - 1) {
            badMethod();
        } else {
            ++caretY;
        }
    }

    void carriageReturn() {
        caretX = 0;
    }

    void cursorReturn() {
        lineFeed();
        carriageReturn();
    }

    void htab() {}

    void formFeed() {}

    void cursorNext() {
        ++caretX;
        if (caretX == width) {
            cursorReturn();
        }
    }

    @Override public void cursorUp(int n) {
    }

    @Override public void cursorDown(int n) {
    }

    @Override public void cursorForward(int n) {
    }

    @Override public void cursorBack(int n) {
    }

    @Override public void cursorNextLine(int n) {
    }

    @Override public void cursorPreviousLine(int n) {
    }

    @Override public void cursorHorizontalAbsolute(int col) {
    }

    @Override public void cursorPosition(int row, int col) {
    }

    @Override public void eraseDisplay(int n) {
    }

    @Override public void eraseInLine(int n) {
    }

    @Override public void scrollUp(int n) {
    }

    @Override public void scrollDown(int n) {
    }

    @Override public void selectGfxRendition(int n) {
    }

    @Override public void auxPort(int n) {
    }

    @Override public void saveCursorPos() {
    }

    @Override public void restoreCursorPos() {
    }

    @Override public void hideCursor() {
    }

    @Override public void showCursor() {
    }

    @Override public void handleOsc(int param, byte[] text, int offset, int len) {}
}
