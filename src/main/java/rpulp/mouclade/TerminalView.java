package rpulp.mouclade;

import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.ArrayList;

class TerminalView {

    private class Cell {
        final Group group = new Group();
        final Text text;
        final Rectangle bg;

        Cell(double cellW, double cellH) {
            this.text = new Text(" ");
            this.text.setFill(Color.WHITE);
            this.bg = new Rectangle(0, 0, cellW, cellH);
            this.bg.setFill(Color.BLACK);
            this.group.getChildren().add(this.bg);
            this.group.getChildren().add(text);
            this.bg.toBack();
        }

        void setSize(Font font, double cellW, double cellH) {
            text.setFont(font);
            bg.setWidth(cellW);
            bg.setHeight(cellH);
        }
    }

    private class Line {
        private final Group group = new Group();
        private final ArrayList<Cell> cells = new ArrayList<>(128);

        void resize(int len, double cellW, double cellH) {
            if (cells.size() == len) {
                return;

            } else if (cells.size() > len) {
                // too long
                group.getChildren().remove(len, group.getChildren().size());
                while (cells.size() > len) {
                    cells.remove(cells.size() - 1);
                }

            } else {
                // too small
                while (cells.size() < len) {
                    Cell cell = new Cell(cellW, cellH);
                    cell.group.setTranslateX(cells.size() * cellW);
                    cells.add(cell);
                    group.getChildren().add(cell.group);
                }
            }
        }

        void setFont(Font font, double cellW, double cellH) {
            for(int xx = 0; xx < cells.size(); ++xx) {
                Cell cell = cells.get(xx);
                cell.setSize(font, cellW, cellH);
                cell.group.setTranslateX(xx * cellW);
            }
        }
    }

    private final TerminalModel model;
    private final Group group = new Group();
    private final ArrayList<Line> lines = new ArrayList<>(128);
    private Font font;
    private double cellW;
    private double cellH;

    TerminalView(TerminalModel model) {
        this.model = model;
        resize(model.width(), model.height());
        setFont("Monospaced", 18);
        model.setListener(new TerminalModel.Listener() {
            @Override
            public void onCellUpdate(int x, int y) {
                Platform.runLater(() -> updateCell(x, y));
            }

            @Override
            public void onFullUpdate() {
                Platform.runLater(() -> updateAllCells());
            }
        });
    }

    Group node() { return group; }

    private void setFont(String family, double size) {
        font = Font.font(family, size);
        Text text = new Text(" ");
        text.setFont(font);
        cellW = text.getLayoutBounds().getWidth();
        cellH = text.getLayoutBounds().getHeight();
        for(int yy = 0; yy < lines.size(); ++yy) {
            Line line = lines.get(yy);
            line.group.translateYProperty().set(yy * cellH);
            line.setFont(font, cellW, cellH);
        }
    }

    private void resize(int width, int height) {
        if (lines.size() > height) {
            group.getChildren().remove(height, group.getChildren().size());
            while (lines.size() > height) {
                lines.remove(lines.size() - 1);
            }
        } else if (lines.size() < height) {
            while (lines.size() < height) {
                Line line = new Line();
                lines.add(line);
                group.getChildren().add(line.group);
            }
        }
        for(Line line : lines) {
            line.resize(width, cellW, cellH);
        }
    }

    public void updateCell(int x, int y) {
        Cell cell = lines.get(y).cells.get(x);
        cell.text.setText(new String(model.chars(), model.at(x, y), 1));
    }

    public void updateAllCells() {
        for (int yy = 0; yy < model.height(); ++yy) {
            for (int xx = 0; xx < model.width(); ++xx) {
                updateCell(xx, yy);
            }
        }
    }
}
