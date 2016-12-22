package rpulp.mouclade;

import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

class TerminalView {

    private final TerminalModel model;
    private final Group group = new Group();
    private Text[] cells;
    private Font font;

    TerminalView(TerminalModel model) {
        this.model = model;
        font = Font.font("Monospaced", 18);
        rebuild();
        model.setListener(new TerminalModel.Listener() {
            @Override
            public void onCellUpdate(int at) {
                Platform.runLater(() -> updateCell(at));
            }

            @Override
            public void onCellRangeUpdate(int start, int len) {
                Platform.runLater(() -> updateCells(start, len));
            }
        });
    }

    Group node() { return group; }

    private void rebuild() {
        final double cellWidth;
        final double cellHeight;
        {
            Text text = new Text(" ");
            text.setFont(font);
            cellWidth = text.getLayoutBounds().getWidth();
            cellHeight = text.getLayoutBounds().getHeight();
        }
        group.getChildren().clear();
        cells = new Text[model.width() * model.height()];
        for (int yy = 0; yy < model.height(); ++yy) {
            for (int xx = 0; xx < model.width(); ++xx) {
                int at = model.at(xx, yy);
                Text cell = new Text();
                cell.setFont(font);
                cell.setX(xx * cellWidth);
                cell.setY(yy * cellHeight);
                cells[at] = cell;
                updateCell(at);
                group.getChildren().add(cell);
            }
        }
    }

    public void updateCell(int at) {
        cells[at].setText(new String(model.chars(), at, 1));
    }

    public void updateCells(int start, int len) {
        for (int ii = 0; ii < len; ++ii) {
            updateCell(start + ii);
        }
    }
}
