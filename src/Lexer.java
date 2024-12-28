//Ilhan Eser
//Ieser@albany.edu


import java.util.LinkedList;
import java.util.List;
import java.lang.Character;
import java.util.*;


// I have left several lines of code intentionally for further reference and bug fixing
// OR I rewrite the entire method and kept old code just in case


public class Lexer {

    private TextManager inputManager = new TextManager("");


    HashMap<String, Token.TokenTypes> keywordMap = new HashMap<String, Token.TokenTypes>();;

    HashMap<String, Token.TokenTypes> punMap = new HashMap<String, Token.TokenTypes>();;

    Stack<Integer> indentationStack = new Stack<Integer>();;

    int lineNumber = 1;

    int characterPosition;


    /**
     * A constructor for the lexer
     * @param input lexer needs an input to run, since the whole purpose of the program is to tokenize the input text
     */
    public Lexer(String input) {

        //Collection of keywords inside a hashmap
        keywordMap.put("Accessor", Token.TokenTypes.ACCESSOR);
        keywordMap.put("accessor", Token.TokenTypes.ACCESSOR);
        keywordMap.put("Mutator", Token.TokenTypes.MUTATOR);
        keywordMap.put("mutator", Token.TokenTypes.MUTATOR);
        keywordMap.put("implements", Token.TokenTypes.IMPLEMENTS);
        keywordMap.put("Implements", Token.TokenTypes.IMPLEMENTS);
        keywordMap.put("Class", Token.TokenTypes.CLASS);
        keywordMap.put("class", Token.TokenTypes.CLASS);
        keywordMap.put("Interface", Token.TokenTypes.INTERFACE);
        keywordMap.put("interface", Token.TokenTypes.INTERFACE);
        keywordMap.put("Loop", Token.TokenTypes.LOOP);
        keywordMap.put("loop", Token.TokenTypes.LOOP);
        keywordMap.put("If", Token.TokenTypes.IF);
        keywordMap.put("if", Token.TokenTypes.IF);
        keywordMap.put("Else", Token.TokenTypes.ELSE);
        keywordMap.put("else", Token.TokenTypes.ELSE);
        keywordMap.put("shared", Token.TokenTypes.SHARED);
        keywordMap.put("Shared", Token.TokenTypes.SHARED);
        keywordMap.put("SHARED", Token.TokenTypes.SHARED);
        keywordMap.put("Construct", Token.TokenTypes.CONSTRUCT);
        keywordMap.put("construct", Token.TokenTypes.CONSTRUCT);
        keywordMap.put("NEW", Token.TokenTypes.NEW);
        keywordMap.put("new", Token.TokenTypes.NEW);
        keywordMap.put("private", Token.TokenTypes.PRIVATE);
        keywordMap.put("PRIVATE", Token.TokenTypes.PRIVATE);
        keywordMap.put("True", Token.TokenTypes.TRUE);
        keywordMap.put("true", Token.TokenTypes.TRUE);
        keywordMap.put("false", Token.TokenTypes.FALSE);
        keywordMap.put("False", Token.TokenTypes.FALSE);

        //Collection of punctuation inside a hashmap
        punMap.put("+", Token.TokenTypes.PLUS);
        punMap.put("-", Token.TokenTypes.MINUS);
        punMap.put("*", Token.TokenTypes.TIMES);
        punMap.put("/", Token.TokenTypes.DIVIDE);
        punMap.put("%", Token.TokenTypes.MODULO);
        punMap.put(",", Token.TokenTypes.COMMA);
        punMap.put("==", Token.TokenTypes.EQUAL);
        punMap.put(">", Token.TokenTypes.GREATERTHAN);
        punMap.put("<", Token.TokenTypes.LESSTHAN);
        punMap.put(">=", Token.TokenTypes.GREATERTHANEQUAL);
        punMap.put("<=", Token.TokenTypes.LESSTHANEQUAL);
        punMap.put("=", Token.TokenTypes.ASSIGN);
        punMap.put(")", Token.TokenTypes.RPAREN);
        punMap.put("(", Token.TokenTypes.LPAREN);
        punMap.put(".", Token.TokenTypes.DOT);
        punMap.put(":", Token.TokenTypes.COLON);
        punMap.put("!=", Token.TokenTypes.NOTEQUAL);
        punMap.put("&&", Token.TokenTypes.AND);
        punMap.put("||", Token.TokenTypes.OR);
        punMap.put("!", Token.TokenTypes.NOT);



        //Constructor input
        this.inputManager = new TextManager(input);

    }


    /**
     * A parser for keeping track of indentation and dedentation of the text
     * @return List of Tokens created by the parser
     */
    private List<Token> parseIndentation() {
        var retInd = new LinkedList<Token>();
        int spaceCounter = 0;


        // Count spaces at the beginning of the line
        while (!inputManager.isAtEnd() && Character.isWhitespace(inputManager.peekCharacter())) {
            char currentChar = inputManager.peekCharacter();
            if (currentChar == ' ') {
                spaceCounter++;
                inputManager.getCharacter();
                characterPosition++;

            } else if (currentChar == '\t') {
                spaceCounter += 4;
                inputManager.getCharacter();
                characterPosition += 4;

            } else if (currentChar == '\n') {
                // Handle empty lines
                inputManager.getCharacter();
                lineNumber++;
                characterPosition = 0;
                spaceCounter = 0;  // Reset counter for new line

            } else {
                break;
            }
        }

        int currentIndentLevel = spaceCounter / 4;


        // If stack is empty, initialize with 0
        if (indentationStack.isEmpty()) {
            indentationStack.push(0);

        }

        // Compare with previous indentation level
        int previousLevel = indentationStack.peek();


        if (currentIndentLevel > previousLevel) {
            // Add INDENT tokens for each level increased
            for (int i = previousLevel; i < currentIndentLevel; i++) {
                retInd.add(new Token(Token.TokenTypes.INDENT, lineNumber, characterPosition));

            }
            indentationStack.push(currentIndentLevel);
        } else if (currentIndentLevel < previousLevel) {
            // Add DEDENT tokens for each level decreased
            while (!indentationStack.isEmpty() && indentationStack.peek() > currentIndentLevel) {
                indentationStack.pop();
                retInd.add(new Token(Token.TokenTypes.DEDENT, lineNumber, characterPosition));

            }
            if (!indentationStack.isEmpty() && indentationStack.peek() != currentIndentLevel) {
                indentationStack.push(currentIndentLevel);
            }
        }

        return retInd;
    }


    /**
     *
     * @param b Current character read by the lexer, this is to bug fix an inherent issue of program not including
     *          the first character while parsing
     * @return List of Tokens based on parsed words and keywords
     */
    private List<Token> parseWord(char b) {
        // This variable will keep the tokens
        var retWord = new LinkedList<Token>();
        //The programs current word starts with the first character, I think a stringbuilder might have been more useful
        String CurrentWord = Character.toString(b);

        //While the text is not at the end and the next given character is a letter the program loops
        while (!inputManager.isAtEnd() && (Character.isLetter(inputManager.peekCharacter(1)))) {
            //Program gets the next character
            char C = inputManager.getCharacter();
            characterPosition++;
            //If the next character is not a letter, the program first checks if the word is not empty
            //This implies that we have reached the end of the current word and we tokenize it
            if (!Character.isLetter(C)) {

                if (!CurrentWord.isEmpty()) {
                    Token hold = new Token(Token.TokenTypes.WORD, lineNumber, characterPosition, CurrentWord);
                    retWord.add(hold);
                }
                CurrentWord = "";
                //The program continues to build the word until the next character is not a letter
            } else {
                CurrentWord += C;
            }

        }

        //This whole section of if statements is about handling keywords

        //If the text is not at the end
        if (!inputManager.isAtEnd()) {
            //We check the hash map to see if the current word is a keyword and tokenize it if it is
            if (keywordMap.containsKey(CurrentWord)) {
                retWord.add((new Token(keywordMap.get(CurrentWord), lineNumber, characterPosition, CurrentWord)));

            } else {
                //if not it is a word
                retWord.add(new Token(Token.TokenTypes.WORD, lineNumber, characterPosition, CurrentWord));
            }

            //This whole section is about if the input is at the end we check it to makes sure it tokenizes correctly
        } else if (inputManager.isAtEnd()) {
            if (keywordMap.containsKey(CurrentWord)) {
                retWord.add((new Token(keywordMap.get(CurrentWord), lineNumber, characterPosition, CurrentWord)));

            } else {
                retWord.add(new Token(Token.TokenTypes.WORD, lineNumber, characterPosition, CurrentWord));
            }

        }

        return retWord;
    }

    /**
     *
     * @param b  Current character read by the lexer, this is to bug fix an inherent issue of program not including
     *           the first character while parsing
     * @return   List of Tokens based on parsed number
     */
    private List<Token> parseNumber(char b) {
        //Explained before, check other parsers
        var retNumber = new LinkedList<Token>();
        String CurrentNumber = Character.toString(b);

        //While the text is not at the end and the next character is a digit or a dot, loop the program
        while (!inputManager.isAtEnd() && (Character.isDigit(inputManager.peekCharacter(1)) || inputManager.peekCharacter(1) == '.' )) {

            //Number grows by the given condition above
            CurrentNumber = CurrentNumber + inputManager.getCharacter();
            characterPosition++;
        }
        //If the program reaches the end, it tokenizes the number
        if (!CurrentNumber.isEmpty()) {
            retNumber.add(new Token(Token.TokenTypes.NUMBER, lineNumber, characterPosition, CurrentNumber));
        }

        return retNumber;
    }

    /**
     *
     * @param b Current character read by the lexer, this is to bug fix an inherent issue of program not including
     *          the first character while parsing
     * @return List of Tokens based on parsed number
     */
    private List<Token> parsePunctuation(char b) throws SyntaxErrorException {
        //Explained before
        var retPun = new LinkedList<Token>();
        String currentPun = Character.toString(b);
        char nextPun;

        //This if statement is to control the end of the punctuation or else it loops
        if (inputManager.isAtEnd()) {
            nextPun = ' ';
        } else {
            nextPun = inputManager.peekCharacter();
        }

        //The program peeks at the next punctuation to decide whether it is a two or one character punctuation
        String peeked = currentPun + nextPun;

        if (b == '.' && Character.isDigit(inputManager.peekCharacter(1))) {
            return parseNumber(b);
        } else

        //If the two characters next to each other make up a punctuation i.e "<=", we tokenize it else, its single
        if (punMap.containsKey(peeked)) {
            inputManager.getCharacter();
            characterPosition++;
            retPun.add((new Token(punMap.get(peeked), lineNumber, characterPosition)));

        } else if (punMap.containsKey(currentPun)) {
            retPun.add(new Token(punMap.get(currentPun), lineNumber, characterPosition));
            //This is for handling new lines
        } else if (b == '\n') {
            lineNumber++;
            characterPosition = 0;
            retPun.add(new Token(Token.TokenTypes.NEWLINE, lineNumber, characterPosition));
            retPun.addAll(parseIndentation());

        }

        //Check for invalid characters, such as '[',
        else {
            throw new SyntaxErrorException("Invalid Character", lineNumber, characterPosition);
        }

        return retPun;
    }


    /**
     *
     * Main method for lexing the input
     *
     * @return list of all tokens
     *
     */
    public List<Token> Lex() throws Exception {
        var retVal = new LinkedList<Token>();

        // Start with indentation check at the beginning of file
        retVal.addAll(parseIndentation());

        while (!inputManager.isAtEnd()) {
            char C = inputManager.getCharacter();
            characterPosition++;

            // Skip plain spaces (indentation is handled separately)
            if (C == ' ') {
                continue;
            }

            // Handle letters - parse words/keywords
            if (Character.isLetter(C)) {
                retVal.addAll(parseWord(C));
            }
            // Handle numbers
            else if (Character.isDigit(C)) {
                retVal.addAll(parseNumber(C));
            }
            // Handle punctuation and special characters
            else if (C == '=' || C == ')' || C == '+' || C == '-' || C == '*' || C == '/' ||
                    C == '%' || C == '<' || C == '>' || C == '!' || C == '\n' || C == ':' ||
                    C == '(' || C == '&' || C == '|' || C == '.' || C == '\t' || C == ',') {

                // Handle newlines specially - reset position and check indentation
                if (C == '\n') {
                    lineNumber++;
                    characterPosition = 0;
                    retVal.add(new Token(Token.TokenTypes.NEWLINE, lineNumber, characterPosition));
                    retVal.addAll(parseIndentation());
                } else {
                    retVal.addAll(parsePunctuation(C));
                }
            }
            // Handle character literals
            else if (C == '\'') {
                char c = inputManager.getCharacter();
                characterPosition++;
                if (inputManager.getCharacter() != '\'') {
                    characterPosition++;
                    throw new SyntaxErrorException("Quote Ends In The Middle", lineNumber, characterPosition);
                }
                retVal.add(new Token(Token.TokenTypes.QUOTEDCHARACTER, lineNumber, characterPosition, Character.toString(c)));
            }
            // Handle string literals
            else if (C == '\"') {
                String currentQuote = "";
                char c = ' ';
                while (!inputManager.isAtEnd()) {
                    c = inputManager.getCharacter();
                    characterPosition++;
                    if (c == '\"') {
                        retVal.add(new Token(Token.TokenTypes.QUOTEDSTRING, lineNumber, characterPosition, currentQuote));
                        break;
                    }
                    currentQuote += c;
                }
                if (c != '\"') {
                    throw new SyntaxErrorException("Quote Ends In The Middle", lineNumber, characterPosition);
                }
            }
            // Handle comments
            else if (C == '{') {
                String currentComment = " ";
                char c = ' ';
                while (!inputManager.isAtEnd()) {
                    c = inputManager.getCharacter();
                    characterPosition++;
                    if (c == '}') {
                        break;
                    }
                    currentComment += c;
                }
                if (c != '}') {
                    throw new SyntaxErrorException("Comment Ends In The Middle", lineNumber, characterPosition);
                }
            }
            else {
                throw new SyntaxErrorException("Invalid Character", lineNumber, characterPosition);
            }
        }

        // Add any remaining DEDENT tokens for proper nesting closure
        if (!indentationStack.isEmpty()) {
            int currentLevel = indentationStack.peek();
            while (currentLevel > 0) {
                retVal.add(new Token(Token.TokenTypes.DEDENT, lineNumber, characterPosition));
                currentLevel--;
            }
            indentationStack.clear();
        }

        return retVal;
    }
}
