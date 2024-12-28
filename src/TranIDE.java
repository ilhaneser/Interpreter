import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.util.concurrent.*;

import AST.TranNode;
import Interpreter.*;

public class TranIDE extends JFrame {
    private final JEditorPane codeEditor;
    private final JTextArea outputConsole;
    private final PrintStream standardOut;
    private final JButton runButton;
    private volatile ExecutorService executor;
    private volatile Future<?> currentTask;

    public TranIDE() {
        standardOut = System.out;
        executor = Executors.newFixedThreadPool(2);  // One for execution, one for timeout

        setTitle("TRAN IDE");
        setSize(1000, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Create main components
        codeEditor = new JEditorPane();
        codeEditor.setFont(new Font("Monospaced", Font.PLAIN, 14));
        outputConsole = new JTextArea();
        outputConsole.setEditable(false);
        outputConsole.setFont(new Font("Monospaced", Font.PLAIN, 14));
        outputConsole.setBackground(new Color(30, 30, 30));
        outputConsole.setForeground(new Color(200, 200, 200));

        // Create toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        runButton = new JButton("Run");
        runButton.addActionListener(e -> runCode());

        JButton stopButton = new JButton("Stop");
        stopButton.addActionListener(e -> stopExecution());

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> {
            codeEditor.setText("");
            outputConsole.setText("");
        });

        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> saveFile());

        JButton loadButton = new JButton("Load");
        loadButton.addActionListener(e -> loadFile());

        toolbar.add(runButton);
        toolbar.add(stopButton);
        toolbar.add(clearButton);
        toolbar.add(saveButton);
        toolbar.add(loadButton);

        // Create split pane with line-numbered editor
        LineNumberedScrollPane scrollPane = new LineNumberedScrollPane(codeEditor);
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                scrollPane,
                new JScrollPane(outputConsole)
        );
        splitPane.setResizeWeight(0.7);

        // Layout
        setLayout(new BorderLayout());
        add(toolbar, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        // Add keyboard shortcut for run (Ctrl/Cmd + R)
        KeyStroke runKey = KeyStroke.getKeyStroke(KeyEvent.VK_R,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());

        codeEditor.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(runKey, "run");
        codeEditor.getActionMap().put("run", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runCode();
            }
        });

        // Add document listener to update line numbers
        codeEditor.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) { scrollPane.updateLineNumbers(); }
            public void insertUpdate(DocumentEvent e) { scrollPane.updateLineNumbers(); }
            public void removeUpdate(DocumentEvent e) { scrollPane.updateLineNumbers(); }
        });

        // Handle window closing
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopExecution();
                executor.shutdownNow();
                try {
                    executor.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
                super.windowClosing(e);
            }
        });
    }

    // Custom ScrollPane that adds line numbers
    private static class LineNumberedScrollPane extends JScrollPane {
        private final JEditorPane editor;
        private final LineNumberView lineNumberView;

        public LineNumberedScrollPane(JEditorPane editor) {
            super(editor);
            this.editor = editor;
            this.lineNumberView = new LineNumberView();
            setRowHeaderView(lineNumberView);

            // Update line numbers when scrolling
            getVerticalScrollBar().addAdjustmentListener(e -> lineNumberView.repaint());
        }

        public void updateLineNumbers() {
            lineNumberView.repaint();
        }

        private class LineNumberView extends JComponent {
            private static final int MARGIN = 5;
            private final Font font;
            private final FontMetrics fontMetrics;
            private final int lineHeight;
            private final int fontAscent;

            public LineNumberView() {
                font = editor.getFont();
                fontMetrics = getFontMetrics(font);
                lineHeight = fontMetrics.getHeight();
                fontAscent = fontMetrics.getAscent();

                setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Color.GRAY));
                setBackground(new Color(245, 245, 245));
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // Paint background
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());

                // Set up for line number drawing
                g.setColor(new Color(100, 100, 100));
                g.setFont(font);

                // Calculate visible lines
                Rectangle clip = g.getClipBounds();
                int startOffset = editor.viewToModel2D(new Point(0, clip.y));
                int endOffset = editor.viewToModel2D(new Point(0, clip.y + clip.height));

                // Get the document
                Document doc = editor.getDocument();
                Element root = doc.getDefaultRootElement();

                // Get line counts
                int startLine = root.getElementIndex(startOffset);
                int endLine = root.getElementIndex(endOffset);
                int totalLines = root.getElementCount();

                // Calculate margin based on total number of lines
                int maxLineNumWidth = fontMetrics.stringWidth(String.valueOf(totalLines));
                int rightMargin = MARGIN * 2 + maxLineNumWidth;

                // Update preferred size if needed
                if (getPreferredSize().width != rightMargin) {
                    setPreferredSize(new Dimension(rightMargin, 0));
                    revalidate();
                }

                // Draw line numbers
                for (int line = startLine; line <= endLine; line++) {
                    String lineNum = String.valueOf(line + 1);
                    int width = fontMetrics.stringWidth(lineNum);
                    int x = rightMargin - width - MARGIN;

                    try {
                        Rectangle2D r = editor.modelToView2D(root.getElement(line).getStartOffset());
                        int y = (int) r.getY() + fontAscent;
                        g.drawString(lineNum, x, y);
                    } catch (BadLocationException ex) {
                        // Handle potential errors
                    }
                }
            }
        }
    }

    private void stopExecution() {
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(true);
            System.setOut(standardOut);
            SwingUtilities.invokeLater(() -> {
                showOutput("Execution stopped by user");
                runButton.setEnabled(true);
            });
        }
    }

    private void runCode() {
        String code = codeEditor.getText();
        if (code.trim().isEmpty()) {
            showOutput("Error: No code to execute!");
            return;
        }

        // Stop any existing execution
        stopExecution();

        // Create new executor if previous one was shutdown
        if (executor.isShutdown()) {
            executor = Executors.newFixedThreadPool(2);
        }

        // Disable run button
        runButton.setEnabled(false);
        outputConsole.setText("Running...\n");

        // Run code in separate thread
        currentTask = executor.submit(() -> {
            Thread.currentThread().setName("TRAN-Execution");
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PrintStream printStream = new PrintStream(outputStream);

            try {
                // Redirect System.out to capture output
                System.setOut(printStream);

                // Run the code
                Lexer lexer = new Lexer(code);
                var tokens = lexer.Lex();
                var tranNode = new TranNode();
                var parser = new Parser(tranNode, tokens);
                parser.Tran();
                var interpreter = new Interpreter(tranNode);
                interpreter.start();

                // Check if interrupted
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Execution interrupted");
                }

                // Restore standard output and show results
                System.setOut(standardOut);
                SwingUtilities.invokeLater(() -> {
                    showOutput(outputStream.toString());
                    runButton.setEnabled(true);
                });

            } catch (Exception e) {
                System.setOut(standardOut);
                SwingUtilities.invokeLater(() -> {
                    if (e instanceof InterruptedException) {
                        showOutput("Execution interrupted");
                    } else {
                        showOutput("Error: " + e.getMessage());
                        e.printStackTrace(System.err);
                    }
                    runButton.setEnabled(true);
                });
            }
        });

        // Add timeout
        executor.submit(() -> {
            try {
                currentTask.get(5, TimeUnit.SECONDS);  // 5 second timeout
            } catch (TimeoutException e) {
                currentTask.cancel(true);
                SwingUtilities.invokeLater(() -> {
                    showOutput("Error: Program execution timed out (exceeded 5 seconds)");
                    runButton.setEnabled(true);
                });
            } catch (CancellationException e) {
                // Task was cancelled, message already shown
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    showOutput("Error: " + e.getMessage());
                    runButton.setEnabled(true);
                });
            }
        });
    }

    private void showOutput(String text) {
        outputConsole.setText(text);
        outputConsole.setCaretPosition(0);
    }

    private void saveFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".tran");
            }
            public String getDescription() {
                return "TRAN Files (*.tran)";
            }
        });

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".tran")) {
                file = new File(file.getParentFile(), file.getName() + ".tran");
            }
            try (PrintWriter writer = new PrintWriter(file)) {
                writer.write(codeEditor.getText());
                showOutput("File saved successfully: " + file.getName());
            } catch (Exception e) {
                showOutput("Error saving file: " + e.getMessage());
            }
        }
    }

    private void loadFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".tran");
            }
            public String getDescription() {
                return "TRAN Files (*.tran)";
            }
        });

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = fileChooser.getSelectedFile();
                StringBuilder content = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                }
                codeEditor.setText(content.toString());
                showOutput("File loaded successfully: " + file.getName());
            } catch (Exception e) {
                showOutput("Error loading file: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new TranIDE().setVisible(true);
        });
    }
}