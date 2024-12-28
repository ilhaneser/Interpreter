import java.util.List;
import java.util.Optional;

public class TokenManager {
    private List<Token> tokens;
    private int index = 0;

    public TokenManager(List<Token> tokens) {
    this.tokens = tokens;
    }

    public boolean done() {
        return index >= tokens.size();
    }

    public Optional<Token> matchAndRemove(Token.TokenTypes t) {
        if (tokens.get(index).getType() == t) {
            return Optional.of(tokens.remove(index));
        } else
            return Optional.empty();
    }
    public boolean nextTwoTokensMatch(Token.TokenTypes first, Token.TokenTypes second) {
    if (tokens.get(index).getType() == first && tokens.get(index + 1).getType() == second) {
        return true;
        } else
            return false;
    }

    public int getCurrentLine() {
        return tokens.get(index).getLineNumber();
    }

    public int getCurrentColumnNumber() {
        return tokens.get(index).getColumnNumber();
    }



    public Optional<Token> peek(int i) {
        return Optional.of(tokens.get(i));
    }
}
