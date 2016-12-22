package rpulp.mouclade;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;
import rpulp.mouclade.MockInputParserListener.*;

import java.util.ArrayList;

public class InputParserTest {

    @Test
    public void parse() {
        byte[] bytes = "\u001B]0;title\u0007\u001B[32mtruc\u001B[33mmachin\u001B[0mpoule".getBytes(Charsets.ISO_8859_1);
        MockInputParserListener listener = new MockInputParserListener();
        new InputParser(listener).parse(bytes);
        ArrayList<Action> expectedActions = Lists.newArrayList(
                new HandleOscAction(0, "title"),
                new SelectGfxRendition(32),
                new AddChars("truc"),
                new SelectGfxRendition(33),
                new AddChars("machin"),
                new SelectGfxRendition(0),
                new AddChars("poule")
        );
        Assert.assertEquals(expectedActions, listener.actions);
    }

    @Test
    public void parse2() {
        MockInputParserListener listener = new MockInputParserListener();
        new InputParser(listener).parse("\u001B[3J\u001B[H\u001B[2J".getBytes(Charsets.ISO_8859_1));
        ArrayList<Action> expectedActions = Lists.newArrayList(
                new EraseDisplay(3),
                new CursorPosition(1, 1),
                new EraseDisplay(2)
        );
        Assert.assertEquals(expectedActions, listener.actions);
    }
}