package editor;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.util.Stack;
import java.io.File;
import java.io.*;
import java.util.List;

public class Editor extends Application implements EventHandler<KeyEvent> {

    class Node {
        Text input;
        Node previous;
        Node next;
        boolean isNewLine;

        Node(Text eachInput, Node p, Node n) {
            input = eachInput;
            previous = p;
            next = n;
        }
    }

    static final int WINDOW_WIDTH = 500;
    static final int WINDOW_HEIGHT = 500;
    static final int STARTING_FONT_SIZE = 50;

    Node sentinel;
    Node current;

    Rectangle cursor = new Rectangle(1, 1);

    int fontSize = STARTING_FONT_SIZE;
    double textHeight;

    String fontName = "Verdana";
    String fileToEdit;

    double cursorSize;

    Group root;
    Group textRoot = new Group();
    Scene scene;
    ScrollBar scrollBar;
    OperationController opController;

    void Init() {
        // Always set the text origin to be VPos.TOP! Setting the origin to be VPos.TOP means
        // that when the text is assigned a y-position, that position corresponds to the
        // highest position across all letters (for example, the top of a letter like "I", as
        // opposed to the top of a letter like "e"), which makes calculating positions much
        // simpler!
        sentinel = new Node(new Text("sentinel"), null, null);
        sentinel.input.setFont(Font.font(fontName, fontSize));
        sentinel.previous = sentinel;
        sentinel.next = sentinel;
        current = sentinel;
        cursorSize = Math.round(sentinel.input.getLayoutBounds().getHeight());
        textHeight = Math.round(sentinel.input.getLayoutBounds().getHeight());
        scrollBar = new ScrollBar();
        opController = new OperationController();

        List<String> arguments = this.getParameters().getRaw();
        if (arguments.size() < 1) {
            System.out.println("No filename was given");
            System.exit(0);
        }
        fileToEdit = arguments.get(0);

        try {
            File inputFile = new File(fileToEdit);
            FileReader reader = new FileReader(inputFile);
            BufferedReader bufferedReader = new BufferedReader(reader);
            int intRead;
            while ((intRead = bufferedReader.read()) != -1) {
                String letter = Character.toString((char) intRead);
                InsertOp insert = new InsertOp(current, letter);
                insert.execute();
            }
            current = sentinel;
        } catch (IOException ioe) {
            System.out.println(ioe);
        }

        scrollBar.setOrientation(Orientation.VERTICAL);
        repositionScrollBar();


        // Set values for scroll bar to scroll from top to bottom of text
        setScrollBar(0);

        // Scroll bar listener: gets position changes from moving and updates the layout of the text
        // lay out all texts again in response to text window resize
        scrollBar.valueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                textRoot.setLayoutY(-1 * newValue.doubleValue());
            }
        });

        scene.widthProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                layoutAllTexts();
                repositionScrollBar();
            }
        });

        scene.heightProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                layoutAllTexts();
                repositionScrollBar();
            }
        });


//        scene.setOnMouseClicked(new MouseClickEventHandler(textRoot));
        // All new Nodes need to be added to the root in order to be displayed.
        root.getChildren().add(textRoot);
        root.getChildren().add(scrollBar);
        textRoot.getChildren().add(cursor);
        scene.setOnMouseClicked(new MouseClickEventHandler());
        makeCursorBlink();
        layoutAllTexts();
    }

    @Override
    public void handle(KeyEvent keyEvent) {
        if (keyEvent.getEventType() == KeyEvent.KEY_TYPED) {
            // Use the KEY_TYPED event rather than KEY_PRESSED for letter keys, because with
            // the KEY_TYPED event, javafx handles the "Shift" key and associated
            // capitalization.
            String characterTyped = keyEvent.getCharacter();

            if (characterTyped.length() > 0 && characterTyped.charAt(0) != 8 && !keyEvent.isShortcutDown()) {
                // Ignore control keys, which have zero length, as well as the backspace
                // key, which is represented as a character of value = 8 on Windows.
                InsertOp insert = new InsertOp(current, characterTyped);
                opController.execute(insert);
            }

            keyEvent.consume();
        } else if (keyEvent.getEventType() == KeyEvent.KEY_PRESSED) {
            // Arrow keys should be processed using the KEY_PRESSED event, because KEY_PRESSED
            // events have a code that we can check (KEY_TYPED events don't have an associated
            // KeyCode).
            KeyCode code = keyEvent.getCode();
            if (code == KeyCode.UP) {
                moveCursor(Direction.UP);
            } else if (code == KeyCode.DOWN) {
                moveCursor(Direction.DOWN);
            } else if (code == KeyCode.LEFT) {
                moveCursor(Direction.LEFT);
            } else if (code == KeyCode.RIGHT) {
                moveCursor(Direction.RIGHT);
            } else if (code == KeyCode.BACK_SPACE) {
                DeleteOp delete = new DeleteOp(current);
                opController.execute(delete);
            } else if (keyEvent.isShortcutDown()) {
                if (code == KeyCode.PLUS || code == KeyCode.EQUALS) {
                    FontResizeOp op = new FontResizeOp(fontSize + 4);
                    opController.execute(op);
                } else if (code == KeyCode.MINUS) {
                    if (fontSize > 4) {
                        FontResizeOp op = new FontResizeOp(fontSize - 4);
                        opController.execute(op);
                    }
                } else if (code == KeyCode.P) {
                    printCursorPosition();
                } else if (code == KeyCode.S) {
                    save();
                } else if (code == KeyCode.Z) {
                    opController.undo();
                } else if (code == KeyCode.Y) {
                    opController.redo();
                }
            }
        }
    }

    interface Operation {
        void execute();
        void undo();
        void redo();
    }

    class FontResizeOp implements Operation {
        int newFontSize;
        int oldFontSize;

        FontResizeOp(int newSize) {
            newFontSize = newSize;
        }

        @Override
        public void execute() {
            oldFontSize = fontSize;
            changeFontSize(newFontSize);
        }

        @Override
        public void undo() {
            changeFontSize(oldFontSize);
        }

        @Override
        public void redo() {
            changeFontSize(newFontSize);
        }

        private void changeFontSize(int s) {
            Node p = sentinel;
            while (true) {
                if (p.input != null) {
                    p.input.setFont(Font.font(fontName, s));
                }
                p = p.next;
                if (p == sentinel) {
                    break;
                }
            }
            textHeight = sentinel.next.input.getLayoutBounds().getHeight();
            cursorSize = sentinel.next.input.getLayoutBounds().getHeight();
            layoutAllTexts();
            setScrollBar(0);
            fontSize = s;
        }
    }

    class InsertOp implements Operation {
        Node currentNode;
        Node previousNode;
        String letter;

        InsertOp(Node currentNode, String letter) {
            this.previousNode = currentNode;
            this.letter = letter;
        }

        @Override
        public void execute() {
            Text text;
            if (letter.charAt(0) != '\r' && letter.charAt(0) != '\n') {
                text = new Text(letter);
            } else {
                text = new Text("");
            }

            Node newNode = new Node(text, null, null);
            if (letter.charAt(0) != '\r' && letter.charAt(0) != '\n') {
                newNode.isNewLine = false;
            } else {
                newNode.isNewLine = true;
            }
            insert(newNode);
            currentNode = newNode;
        }

        @Override
        public void undo() {
            current = this.currentNode;
            delete();
        }

        @Override
        public void redo() {
            current = this.previousNode;
            insert(this.currentNode);
        }
    }

    class DeleteOp implements Operation {
        Node currentNode;
        Node previousNode;

        DeleteOp(Node current) {
            this.currentNode = current;
        }

        @Override
        public void execute() {
            previousNode = currentNode.previous;
            delete();
        }

        @Override
        public void undo() {
            current = this.previousNode;
            insert(this.currentNode);
        }

        @Override
        public void redo() {
            current = currentNode;
            delete();
        }
    }


    class OperationController {
        Stack<Operation> undoStack = new Stack<>();
        Stack<Operation> redoStack = new Stack<>();

        void execute(Operation op) {
            op.execute();
            undoStack.push(op);
            redoStack.clear();
        }

        void undo() {
            if (undoStack.isEmpty()) {
                return;
            }
            Operation top = undoStack.pop();
            top.undo();
            redoStack.push(top);
        }

        void redo() {
            if (redoStack.isEmpty()) {
                return;
            }
            Operation top = redoStack.pop();
            top.redo();
            undoStack.push(top);
        }
    }



    enum Direction {
        LEFT, RIGHT, UP, DOWN
    }

    void moveCursor(Direction direction) {
        if (direction == Direction.LEFT) {
            if (current == sentinel) {
                return;
            }
            current = current.previous;
        }

        if (direction == Direction.RIGHT) {
            if (current.next == sentinel) {
                return;
            }
            current = current.next;
        }

        if (direction == Direction.DOWN) {
            Node temp = current;
            while (true) {
                if (temp.next == sentinel) {
                    break;
                }

                if (temp.next.input.getY() > temp.input.getY()) {
                    temp = temp.next;
                    break;
                }
                temp = temp.next;
            }

            while (true) {
                if (temp.next == sentinel) {
                    break;
                }

                if (temp.next.input.getY() > temp.input.getY()) {
                    break;
                }

                if (temp.next.isNewLine) {
                    break;
                }
                // decides whether the cursor will be in front or behind the char below 
                // the current cursor
                double position1 = temp.input.getX() + temp.input.getLayoutBounds().getWidth();
                double position2 = temp.next.input.getX() + temp.next.input.getLayoutBounds().getWidth();
                double distance1 = Math.abs(cursor.getX() - position1);
                double distance2 = Math.abs(cursor.getX() - position2);

                if (distance1 < distance2) {
                    break;
                }
                temp = temp.next;
            }
            current = temp;
        }
        if (direction == Direction.UP) {
            Node temp = current;
            while (temp != sentinel) {
                if (temp.previous == sentinel) {
                    temp = sentinel;
                    break;
                }

                if (temp.previous.input.getY() < temp.input.getY()) {
                    temp = temp.previous;
                    break;
                }
                temp = temp.previous;
            }

            while (temp != sentinel) {
                if (temp.previous == sentinel) {
                    break;
                }

                if (temp.previous.input.getY() < temp.input.getY()) {
                    break;
                }

                if (temp.previous.isNewLine) {
                    break;
                }

                // decides whether the cursor will be in front or behind the char above 
                // the current cursor
                double position1 = temp.input.getX() + temp.input.getLayoutBounds().getWidth();
                double position2 = temp.previous.input.getX() + temp.previous.input.getLayoutBounds().getWidth();
                double distance1 = Math.abs(cursor.getX() - position1);
                double distance2 = Math.abs(cursor.getX() - position2);

                if (distance1 < distance2) {
                    break;
                }
                temp = temp.previous;
            }
            current = temp;
        }
        layoutAllTexts();
        snapToCursor();
    }

    void printCursorPosition() {
        System.out.println("Cursor is at x : " + cursor.getX() + " and y is at : " + cursor.getY());
//        System.out.println("Input : " + current.input.getText());
//        System.out.println("Current x: " + current.input.getLayoutBounds().getWidth());
    }

    class MouseClickEventHandler implements EventHandler<MouseEvent> {
        @Override
        public void handle(MouseEvent mouseEvent) {
            double mousePressedX = mouseEvent.getX();
            double mousePressedY = mouseEvent.getY();
            Node p = sentinel.next;
            double yPosOnText = mousePressedY + scrollBar.getValue();
            double desiredLineY;

            // find the correct line where mouse click happened
            while (p != sentinel) {
                if ((yPosOnText >= p.input.getY()) && (yPosOnText <= p.input.getY() + p.input.getLayoutBounds().getHeight())) {
                    break;
                }
                p = p.next;
            }

            desiredLineY = p.input.getY();

            // get last char in desired line
            while (p.input.getX() <= mousePressedX && p != sentinel) {
                if (p.input.getY() == desiredLineY) {
                    p = p.next;
                } else {
                    break;
                }
            }

            p = p.previous;

            // position1 is the top left x coord
            // position2 is the top right x coord
            // determines whether cursor will be on the left or right of a char
            double position1 = p.input.getX();
            double position2 = p.input.getX() + p.input.getLayoutBounds().getWidth();
            double distance1 = Math.abs(mousePressedX - position1);
            double distance2 = Math.abs(mousePressedX - position2);
            if (distance1 <= distance2) {
                current = p.previous;
            } else {
                current = p;
            }
            layoutAllTexts();
        }
    }

    void save() {
        try {
            File outputFile = new File(fileToEdit);
            FileWriter writer = new FileWriter(outputFile);
            Node temp = sentinel.next;
            while (temp != sentinel) {
                writer.write(temp.input.getText());
                temp = temp.next;
            }
            writer.close();
        } catch (IOException ioe) {
            System.out.println(ioe);
        }
    }

    class CursorBlinkEventHandler implements EventHandler<ActionEvent> {
        int currentColorIndex = 0;
        Color[] boxColors = {Color.BLACK, Color.WHITE};

        CursorBlinkEventHandler() {
            blink();
        }

        void blink() {
            cursor.setFill(boxColors[currentColorIndex]);
            currentColorIndex = (currentColorIndex + 1) % boxColors.length;
        }

        @Override
        public void handle(ActionEvent event) {
            blink();
        }
    }

    void makeCursorBlink() {
        final Timeline timeline = new Timeline();
        timeline.setCycleCount(Timeline.INDEFINITE);
        KeyFrame keyFrame = new KeyFrame(Duration.seconds(0.5), new CursorBlinkEventHandler());
        timeline.getKeyFrames().add(keyFrame);
        timeline.play();
    }

    void repositionScrollBar() {
        double positionScrollBar = Math.round(scene.getWidth() - scrollBar.getLayoutBounds().getWidth());
        scrollBar.setPrefHeight(scene.getHeight());
        scrollBar.setPrefWidth(20);
        scrollBar.setLayoutX(positionScrollBar);
    }

    void setScrollBar(double value) {
        scrollBar.setMin(0);
        scrollBar.setMax(textRoot.getLayoutBounds().getHeight() - scene.getHeight());
        scrollBar.setValue(value);
    }


    void insert(Node newNode) {
        newNode.previous = current;
        newNode.next = current.next;
        current.next = newNode;
        newNode.next.previous = newNode;
        current = newNode;
        newNode.input.setTextOrigin(VPos.TOP);
        newNode.input.setFont(Font.font(fontName, fontSize));
        textRoot.getChildren().add(newNode.input);

        layoutAllTexts();
        snapToCursor();
    }

    // when the cursor moves off the screen vertically, the scrollbar will snap to
    // the cursor and display it on the screen
    void snapToCursor() {
        if (cursor.getY() + cursor.getHeight() > scene.getHeight() + scrollBar.getValue()) {
            setScrollBar(cursor.getY() + cursor.getHeight() - scene.getHeight());
        }

        if (cursor.getY() < scrollBar.getValue()) {
            setScrollBar(cursor.getY());
        }
    }

    void delete() {
        if (current == sentinel) {
            return;
        }
        Node beforeNode = current.previous;
        Node removeThisNode = current;
        textRoot.getChildren().remove(removeThisNode.input);
        removeThisNode.next.previous = beforeNode;
        beforeNode.next = removeThisNode.next;
        current = beforeNode;
        layoutAllTexts();
    }

    void layoutAllTexts() {
        // Re-position the text.
        double x = 0;
        double y = 0;

        Node p = sentinel.next;

        while (p != sentinel) {

            double textWidth = Math.round(p.input.getLayoutBounds().getWidth());
            double widthWithBar = scene.getWidth() - scrollBar.getLayoutBounds().getWidth();
            // if text goes off screen horizontally
            // if in the middle of a word, keep getting previous chars until
            // you find a space so that the entire word can move to the next line
            if (x + textWidth >= widthWithBar && p.previous != sentinel) {
                Node temp = p;
                p = p.previous;
                while (p != sentinel) {
                    if (p.input.getText().charAt(0) == ' ') {
                        p = p.next;
                        textWidth = Math.round(p.input.getLayoutBounds().getWidth());
                        break;
                    }
                    // if char already at position 0, move to next line
                    // ex: ab on the first line, if you put cursor before a and hit enter
                    // ab will be on the second line
                    if (p.input.getX() == 0) {
                        p = temp;
                        break;
                    }
                    p = p.previous;
                }
                x = 0;
                y += textHeight;
            }
            p.input.setX(x);
            p.input.setY(y);
            p.input.toFront();
            x += textWidth;

            if (p.isNewLine) {
                y += textHeight;
                x = 0;
            }

            if (p == current) {
                cursor.setHeight(cursorSize);
                cursor.setX(x);
                cursor.setY(y);
            }
            p = p.next;
        }
        if (current == sentinel) {
            cursor.setHeight(cursorSize);
            cursor.setX(0);
            cursor.setY(0);
        }
        setScrollBar(scrollBar.getValue());
    }

    @Override
    public void start(Stage primaryStage) {
        root = new Group();
        scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT, Color.WHITE);
        Init();
        scene.setOnKeyTyped(this);
        scene.setOnKeyPressed(this);
        primaryStage.setTitle("My Editor");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}