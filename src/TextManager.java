public class TextManager {

    //Input text
    private String text = "";
    private int  position;

    /**
     * Consturctor for the text manager
     * @param input given text
     */
    public TextManager(String input) {
        text = input;
    }

    /**
     * Checks if the input is at the end
     * @return
     */
    public boolean isAtEnd() {
        boolean atEnd = false;
        if (position >= text.length())
            atEnd = true;
        return atEnd;
    }

    /**
     * Peeks the next character of the given character
     * @return next character
     */
    public char peekCharacter() {
        char currentPosition = text.charAt(position);
        return currentPosition;
    }

    //This is to beat the one off issue
    public char peekCharacter(int distance) {

        if (position == 0 && distance == 0) {
            throw new RuntimeException("Position = -1");
        }
        return text.charAt(position + distance - 1); 

    }

    /**
     * Same thing as the peek character except increments the position
     * @return
     */
    public char getCharacter() {
    char currentPosition = text.charAt(position);
    position++;
    return currentPosition;

    }
}
