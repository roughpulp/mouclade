package rpulp.mouclade;

class InputParser {

    interface Listener {
        void handleOsc(int param, byte[] text, int offset, int len);
        void addChar(int byt);
        void cursorUp(int n);
        void cursorDown(int n);
        void cursorForward(int n);
        void cursorBack(int n);
        void cursorNextLine(int n);
        void cursorPreviousLine(int n);
        void cursorHorizontalAbsolute(int col);
        void cursorPosition(int row, int col);
        void eraseDisplay(int n);
        void eraseInLine(int n);
        void scrollUp(int n);
        void scrollDown(int n);
        void selectGfxRendition(int n);
        void auxPort(int n);
        void saveCursorPos();
        void restoreCursorPos();
        void hideCursor();
        void showCursor();
    }

    private interface Edges {
        State next(int byt);
    }

    private static class State {
        Edges edges;
    }

    private static final int NOT_A_NUMBER = -1;
    private int numericValue = NOT_A_NUMBER;
    private int[] numericParams = new int[16];
    private int numericParamsSize = 0;
    private byte[] textParam = new byte[128];
    private int textParamSize = 0;

    private byte[] bytes;
    private int idx;
    private int end;

    private final Listener model;

    private final State initialState;
    private State state;

    InputParser(Listener listener) {
        this.model = listener;
        this.initialState = buildParserGraph();
        this.state = this.initialState;
    }

    private State buildParserGraph() {
        // vertices
        final State top = new State();
        final State esc = new State();
        final State csiBegin = new State();
        final State csiNumericParam = new State();
        final State csiEnd = new State();
        final State oscBegin = new State();
        final State oscNumericParam = new State();
        final State oscTextParam = new State();
        final State oscEnd = new State();

        // edges
        top.edges = (int byt) -> {
            switch (byt) {
                case 0x1b: return esc;
                default: addChar(byt); return top;
            }
        };
        esc.edges = (int byt) -> {
            switch (byt) {
                case ']': clearParams(); return oscBegin;
                case '[': clearParams(); return csiBegin;
                default: return top;
            }
        };
        csiBegin.edges = (int byt) -> {
            switch (byt) {
                case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7': case '8': case '9':
                    rewind(); return csiNumericParam;
                default: rewind(); return csiEnd;
            }
        };
        csiNumericParam.edges = (int byt) -> {
            switch (byt) {
                case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7': case '8': case '9':
                    addNumDigit(byt); return csiNumericParam;
                case ';': addNumParam(); return csiNumericParam;
                default: addNumParam(); rewind(); return csiEnd;
            }
        };
        csiEnd.edges = (int byt) -> {
            switch (byt) {
                case 'A': cursorUp(); break;
                case 'B': cursorDown(); break;
                case 'C': cursorForward(); break;
                case 'D': cursorBack(); break;
                case 'E': cursorNextLine(); break;
                case 'F': cursorPreviousLine(); break;
                case 'G': cursorHorizontalAbsolute(); break;
                case 'H': cursorPosition(); break;
                case 'J': eraseDisplay(); break;
                case 'K': eraseInLine(); break;
                case 'S': scrollUp(); break;
                case 'T': scrollDown(); break;
                case 'f': cursorPosition(); break;
                case 'm': selectGfxRendition(); break;
                case 'i': auxPort(); break;
                case 'n': deviceStatusReport(); break;
                case 's': saveCursorPos(); break;
                case 'u': restoreCursorPos(); break;
                case 'l': hideCursor(); break;
                case 'h': showCursor(); break;
            }
            return top;
        };
        oscBegin.edges = (int byt) -> {
            switch (byt) {
                case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7': case '8': case '9':
                    rewind(); return oscNumericParam;
                default: rewind(); return oscTextParam;
            }
        };
        oscNumericParam.edges = (int byt) -> {
            switch (byt) {
                case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7': case '8': case '9':
                    addNumDigit(byt); return oscNumericParam;
                case ';': addNumParam(); return oscTextParam;
                default: addNumParam(); rewind(); return oscTextParam;
            }
        };
        oscTextParam.edges = (int byt) -> {
            switch (byt) {
                case 0x07: handleOsc(); return top;
                case 0x1b: handleOsc(); return oscEnd;
                default: addTextParamByte(byt); return oscTextParam;
            }
        };
        oscEnd.edges = (int byt) -> {
            switch (byt) {
                case '\\': return top;
                default: return top;
            }
        };
        return top;
    }

    void parse(byte[] bytes) { parse(bytes, 0, bytes.length); }

    void parse(byte[] bytes, int off, int len) {
        this.bytes = bytes;
        this.idx = off;
        this.end = off + len;

        for (;;) {
            int byt = nextByte();
            if (byt == -1) {
                return;
            }
            state = state.edges.next(byt);
        }
    }

    private byte nextByte() {
        if (idx >= end) {
            return -1;
        }
        return bytes[idx++];
    }

    private void rewind() { --idx; }

    private void clearParams() {
        numericParamsSize = 0;
        resetNumValue();
        textParamSize = 0;
    }

    private void resetNumValue() {
        numericValue = NOT_A_NUMBER;
    }

    private void addNumDigit(int digitChar) {
        numericValue = (Math.max(numericValue, 0) * 10) + (digitChar - '0');
    }

    private void addNumParam() {
        numericParams[numericParamsSize++] = numericValue;
        resetNumValue();
    }

    private void addTextParamByte(int byt) {
        if (textParamSize == textParam.length) {
            byte[] old = textParam;
            textParam = new byte[((textParamSize * 3) / 2) + 1];
            System.arraycopy(old, 0, textParam, 0, textParamSize);
        }
        textParam[textParamSize++] = (byte) byt;
    }

    private int numericParam(int idx, int defaultValue) {
        if (idx >= numericParamsSize) {
            return defaultValue;
        } else {
            final int value = numericParams[idx];
            return value == NOT_A_NUMBER ? defaultValue : value;
        }
    }

    private int numericParam0(int defaultValue) {
        return numericParam(0, defaultValue);
    }

    private int numericParam1(int defaultValue) {
        return numericParam(1, defaultValue);
    }

    private void handleOsc() { model.handleOsc(numericParam0(1), textParam, 0, textParamSize); }

    private void addChar(int byt) { model.addChar(byt); }

    private void resetToInitialState() { }

    private void cursorUp() { model.cursorUp(numericParam0(1)); }

    private void cursorDown() { model.cursorDown(numericParam0(1)); }

    private void cursorForward() { model.cursorForward(numericParam0(1)); }

    private void cursorBack() { model.cursorBack(numericParam0(1)); }

    private void cursorNextLine() { model.cursorNextLine(numericParam0(1)); }

    private void cursorPreviousLine() { model.cursorPreviousLine(numericParam0(1)); }

    private void cursorHorizontalAbsolute() { model.cursorHorizontalAbsolute(numericParam0(1)); }

    private void cursorPosition() { model.cursorPosition(numericParam0(1), numericParam1(1)); }

    private void eraseDisplay() { model.eraseDisplay(numericParam0(0)); }

    private void eraseInLine() { model.eraseInLine(numericParam0(0)); }

    private void scrollUp() { model.scrollUp(numericParam0(1)); }

    private void scrollDown() { model.scrollDown(numericParam0(1)); }

    private void selectGfxRendition() { model.selectGfxRendition(numericParam0(0)); }

    private void auxPort() { model.auxPort(numericParam0(0)); }

    private void deviceStatusReport() { /* FIXME: gniii ? */ }

    private void saveCursorPos() { model.saveCursorPos(); }

    private void restoreCursorPos() { model.restoreCursorPos(); }

    private void hideCursor() { model.hideCursor(); }

    private void showCursor() { model.showCursor(); }
}
