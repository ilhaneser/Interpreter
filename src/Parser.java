import AST.*;

import java.beans.Expression;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Parser {

    TranNode tNode;
    TokenManager tokenManager;


    public Parser(TranNode top, List<Token> tokens) {
        this.tNode = top;
        this.tokenManager = new TokenManager(tokens);

    }

    // I sat down on Friday-Saturday and rewrote a lot of code because it broke the previous parsers
    // I still have to fix parser2tests
    // So there is that

    Optional<VariableDeclarationNode> parseVariableDeclaration() throws SyntaxErrorException {
        if (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.WORD)) {
            return Optional.empty();
        }

        VariableDeclarationNode varNode = new VariableDeclarationNode();

        // Get type (like "number", "string", etc)
        Token typeToken = tokenManager.matchAndRemove(Token.TokenTypes.WORD).get();
        varNode.type = typeToken.getValue();

        // Get variable name
        if (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.WORD)) {
            throw new SyntaxErrorException("Expected variable name",
                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }
        Token nameToken = tokenManager.matchAndRemove(Token.TokenTypes.WORD).get();
        varNode.name = nameToken.getValue();

        return Optional.of(varNode);
    }


    List<ConstructorNode> parseConstructor() throws SyntaxErrorException {
        List<ConstructorNode> constructors = new ArrayList<>();
        ConstructorNode constructor = new ConstructorNode();


        if (tokenManager.matchAndRemove(Token.TokenTypes.CONSTRUCT).isEmpty()) {
            throw new SyntaxErrorException("Constructor expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        if (tokenManager.matchAndRemove(Token.TokenTypes.LPAREN).isEmpty()) {
            throw new SyntaxErrorException("( expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        // Parameter handling
        if (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.RPAREN)) {
            do {
                Optional<VariableDeclarationNode> parameter = parseVariableDeclaration();
                if (parameter.isPresent()) {
                    constructor.parameters.add(parameter.get());
                }
            } while (tokenManager.matchAndRemove(Token.TokenTypes.COMMA).isPresent());
        }

        if (tokenManager.matchAndRemove(Token.TokenTypes.RPAREN).isEmpty()) {
            throw new SyntaxErrorException(") expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }


        if (tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE).isEmpty()) {
            throw new SyntaxErrorException("Newline expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }


        // Constructor:
        if (tokenManager.matchAndRemove(Token.TokenTypes.INDENT).isPresent()) {
            while (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.DEDENT)) {
                if (tokenManager.peek(0).get().getType().equals(Token.TokenTypes.WORD) && tokenManager.peek(1).get().getType().equals(Token.TokenTypes.WORD)) {
                    Optional<VariableDeclarationNode> local = parseVariableDeclaration();
                    // Constructor locals:
                    if (local.isPresent()) {
                        constructor.locals.add(local.get());
                        tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE);
                    }
                } else {
                    // Constructor statements:
                    Optional<StatementNode> statement = Statement();
                    if (statement.isPresent()) {
                        constructor.statements.add(statement.get());
                        if (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.DEDENT)) {
                            tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE);
                        }
                    }
                }
            }

            if (tokenManager.matchAndRemove(Token.TokenTypes.DEDENT).isEmpty()) {
                throw new SyntaxErrorException("Dedent expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            }
        }

        constructors.add(constructor);
        return constructors;
    }


    Optional<MethodHeaderNode> parseMethodHeader() throws SyntaxErrorException {
        if (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.WORD)) {
            return Optional.empty();
        }

        MethodHeaderNode methodHeader = new MethodHeaderNode();

        methodHeader.name = tokenManager.matchAndRemove(Token.TokenTypes.WORD).get().getValue();


        if (tokenManager.matchAndRemove(Token.TokenTypes.LPAREN).isEmpty()) {
            throw new SyntaxErrorException("( expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        // Methoder header parameters:
        if (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.RPAREN)) {
            do {
                Optional<VariableDeclarationNode> parameters = parseVariableDeclaration();
                if (parameters.isPresent()) {
                    methodHeader.parameters.add(parameters.get());
                }
            } while (tokenManager.matchAndRemove(Token.TokenTypes.COMMA).isPresent());
        }


        if (tokenManager.matchAndRemove(Token.TokenTypes.RPAREN).isEmpty()) {
            throw new SyntaxErrorException(") expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        // Return type:
        if (tokenManager.matchAndRemove(Token.TokenTypes.COLON).isPresent()) {
            do {
                Optional<VariableDeclarationNode> returnType = parseVariableDeclaration();
                if (returnType.isPresent()) {
                    methodHeader.returns.add(returnType.get());
                }
            } while (tokenManager.matchAndRemove(Token.TokenTypes.COMMA).isPresent());
        }

        return Optional.of(methodHeader);
    }


    // Member = VariableDeclaration ["accessor:" Statements] ["mutator:" Statements]
    List<MemberNode> parseMember() throws SyntaxErrorException {
        List<MemberNode> members = new ArrayList<>();

        // Members loop
        while (tokenManager.peek(0).get().getType() == Token.TokenTypes.WORD && tokenManager.peek(1).get().getType() == Token.TokenTypes.WORD) {
            MemberNode member = new MemberNode();

            // Parse the variable declaration
            Optional<VariableDeclarationNode> varDec = parseVariableDeclaration();
            if (varDec.isEmpty()) {
                break;
            }
            member.declaration = varDec.get();

            // Must have a newline after the member declaration
            if (!tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE).isPresent()) {
                throw new SyntaxErrorException("Newline expected after class member", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            }

            // Check for accessor/mutator
            if (tokenManager.matchAndRemove(Token.TokenTypes.INDENT).isPresent()) {
                // Handle accessor
                if (tokenManager.matchAndRemove(Token.TokenTypes.ACCESSOR).isPresent()) {
                    tokenManager.matchAndRemove(Token.TokenTypes.COLON);
                    tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE);
                    member.accessor = Optional.of(Statements());
                }

                // Handle mutator
                if (tokenManager.matchAndRemove(Token.TokenTypes.MUTATOR).isPresent()) {
                    tokenManager.matchAndRemove(Token.TokenTypes.COLON);
                    tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE);
                    member.mutator = Optional.of(Statements());
                }

                tokenManager.matchAndRemove(Token.TokenTypes.DEDENT);
            }

            members.add(member);

            // Break if we see a method or constructor declaration
            if ((tokenManager.peek(1).get().getType() == Token.TokenTypes.LPAREN || tokenManager.peek(0).get().getType() == Token.TokenTypes.CONSTRUCT)) {
                break;
            }
        }

        return members;
    }


    // [VariableReference "=" ] "loop" ( BoolExpTerm ) NEWLINE Statements
    Optional<StatementNode> parseLoop() throws SyntaxErrorException {
        if (!tokenManager.matchAndRemove(Token.TokenTypes.LOOP).isPresent()) {
            return Optional.empty();
        }

        LoopNode loopNode = new LoopNode();

        // Parse loop assignment (if any)
        if (tokenManager.peek(1).get().getType().equals(Token.TokenTypes.ASSIGN)) {
            loopNode.assignment = Optional.of(VariableReference());
            tokenManager.matchAndRemove(Token.TokenTypes.ASSIGN);
        } else {
            loopNode.assignment = Optional.empty();
        }

        // Parse loop condition
        Optional<ExpressionNode> condition = BoolExpTerm();
        if (condition.isEmpty()) {
            throw new SyntaxErrorException("No loop condition", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }
        loopNode.expression = condition.get();

        if (!tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE).isPresent()) {
            throw new SyntaxErrorException("Newline expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        if (!tokenManager.matchAndRemove(Token.TokenTypes.INDENT).isPresent()) {
            throw new SyntaxErrorException("Indent expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        // Parse loop body statements
        while (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.DEDENT)) {
            Optional<StatementNode> statement = Statement();
            if (statement.isPresent()) {
                loopNode.statements.add(statement.get());

                if (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.DEDENT)) {
                    if (!tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE).isPresent()) {
                        throw new SyntaxErrorException("Expected newline", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
                    }
                }
            }
        }

        if (!tokenManager.matchAndRemove(Token.TokenTypes.DEDENT).isPresent()) {
            throw new SyntaxErrorException("Expected dedent", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        return Optional.of(loopNode);
    }

    // BoolExpFactor {("and"|"or") BoolExpTerm} | "not" BoolExpTerm
    Optional<ExpressionNode> BoolExpTerm() throws SyntaxErrorException {

        // This is the ExpressionNode (Abstrat node) that will store the term itself
        Optional<ExpressionNode> BoolTerm = BoolExpFactor();

        // If its empty, return empty
        if (BoolTerm.isEmpty()) {
            return Optional.empty();
        }
        // While there is a && or ||, the bellow function repeats
        while ((tokenManager.peek(0).get().getType().equals(Token.TokenTypes.AND) || tokenManager.peek(0).get().getType().equals(Token.TokenTypes.OR))) {

            // Create a new boolean node that will store the right side of the Boolean Operation
            BooleanOpNode boolNode = new BooleanOpNode();
            boolNode.left = BoolTerm.get();

            // If it is &&, the program sets the operation to and, if it is || the program sets the operation to or
            if (tokenManager.matchAndRemove(Token.TokenTypes.AND).isPresent()) {
                boolNode.op = BooleanOpNode.BooleanOperations.and;
            } else if (tokenManager.matchAndRemove(Token.TokenTypes.OR).isPresent()) {
                boolNode.op = BooleanOpNode.BooleanOperations.or;
            }

            // Then the program gets the rght side of the boolean operation, which has to exist if left exists
            Optional<ExpressionNode> right = BoolExpFactor();
            if (right.isEmpty()) {
                throw new SyntaxErrorException("Right Side of Boolean Expression is empty", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            }

            boolNode.right = right.get();
            // And then the program puts these operations inside the main boolean term that will be returned
            BoolTerm = Optional.of(boolNode);
        }

        return BoolTerm;
    }

    // BoolExpFactor = MethodCallExpression | (Expression ( "==" | "!=" | "<=" | ">=" | ">" | "<" )Expression) | VariableReference
    Optional<ExpressionNode> BoolExpFactor() throws SyntaxErrorException {

        CompareNode compare = new CompareNode();

        // First try to get the left side
        Optional<ExpressionNode> leftExp = Expression();
        if (leftExp.isEmpty()) {
            return Optional.empty();
        }

        // Store the left expression
        compare.left = leftExp.get();

        // Check for comparison operator
        if (!tokenManager.peek(0).isPresent()) {
            return leftExp;  // Return if no more tokens
        }

        // Handle comparison operators
        Token.TokenTypes type = tokenManager.peek(0).get().getType();
        if (type == Token.TokenTypes.EQUAL || type == Token.TokenTypes.NOTEQUAL || type == Token.TokenTypes.LESSTHANEQUAL || type == Token.TokenTypes.GREATERTHANEQUAL ||
                type == Token.TokenTypes.GREATERTHAN || type == Token.TokenTypes.LESSTHAN) {


            tokenManager.matchAndRemove(type);

            // Get right side of comparison
            Optional<ExpressionNode> rightExp = Expression();
            if (rightExp.isEmpty()) {
                throw new SyntaxErrorException("Expected right side of comparison", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            }
            compare.right = rightExp.get();


            // Set the comparison operation
            if (type == Token.TokenTypes.EQUAL) {
                compare.op = CompareNode.CompareOperations.eq;
            } else if (type == Token.TokenTypes.NOTEQUAL) {
                compare.op = CompareNode.CompareOperations.ne;
            } else if (type == Token.TokenTypes.LESSTHANEQUAL) {
                compare.op = CompareNode.CompareOperations.le;
            } else if (type == Token.TokenTypes.GREATERTHANEQUAL) {
                compare.op = CompareNode.CompareOperations.ge;
            } else if (type == Token.TokenTypes.GREATERTHAN) {
                compare.op = CompareNode.CompareOperations.gt;
            } else if (type == Token.TokenTypes.LESSTHAN) {
                compare.op = CompareNode.CompareOperations.lt;
            }


            return Optional.of(compare);
        }

        // If no comparison operator, just return the left expression
        return leftExp;
    }


    // For Expression, Term and Factor I mainly used the discussion notes where we did these
    // Expression: Term { ("+"|"-") Term }
    Optional<ExpressionNode> Expression() throws SyntaxErrorException {

        Optional<ExpressionNode> term = Term();
        if (term.isEmpty()) {
            return Optional.empty();
        }


        ExpressionNode left = term.get();

        while (tokenManager.peek(0).isPresent() &&
                (tokenManager.peek(0).get().getType().equals(Token.TokenTypes.PLUS) ||
                        tokenManager.peek(0).get().getType().equals(Token.TokenTypes.MINUS))) {
            MathOpNode op = new MathOpNode();
            op.left = left;

            if (tokenManager.matchAndRemove(Token.TokenTypes.PLUS).isPresent()) {
                op.op = MathOpNode.MathOperations.add;
            } else {
                tokenManager.matchAndRemove(Token.TokenTypes.MINUS);
                op.op = MathOpNode.MathOperations.subtract;
            }

            Optional<ExpressionNode> right = Term();
            if (right.isEmpty()) {
                throw new SyntaxErrorException("Expected term after operator", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            }
            op.right = right.get();
            left = op;
        }

        return Optional.of(left);
    }

    // Term = Factor { ("*"|"/"|"%") Factor }
    Optional<ExpressionNode> Term() throws SyntaxErrorException {
        Optional<ExpressionNode> factor = Factor();
        if (factor.isEmpty()) {
            return Optional.empty();
        }

        ExpressionNode left = factor.get();

        while (tokenManager.peek(0).isPresent() &&
                (tokenManager.peek(0).get().getType().equals(Token.TokenTypes.TIMES) ||
                        tokenManager.peek(0).get().getType().equals(Token.TokenTypes.DIVIDE) ||
                        tokenManager.peek(0).get().getType().equals(Token.TokenTypes.MODULO))) {
            MathOpNode op = new MathOpNode();
            op.left = left;

            if (tokenManager.matchAndRemove(Token.TokenTypes.TIMES).isPresent()) {
                op.op = MathOpNode.MathOperations.multiply;
            } else if (tokenManager.matchAndRemove(Token.TokenTypes.DIVIDE).isPresent()) {
                op.op = MathOpNode.MathOperations.divide;
            } else {
                tokenManager.matchAndRemove(Token.TokenTypes.MODULO);
                op.op = MathOpNode.MathOperations.modulo;
            }

            Optional<ExpressionNode> right = Factor();
            if (right.isEmpty()) {
                throw new SyntaxErrorException("Expected factor after operator",
                        tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            }
            op.right = right.get();
            left = op;
        }

        return Optional.of(left);
    }

    // Factor = NumberLiteral | VariableReference | "true" | "false" | StringLiteral | CharacterLiteral
    //| MethodCallExpression | "(" Expression ")" | "new" Identifier "(" [Expression {"," Expression }]
    //")"
    private Optional<ExpressionNode> Factor() throws SyntaxErrorException {
        if (tokenManager.peek(0).isEmpty()) {
            return Optional.empty();
        }

        Token.TokenTypes type = tokenManager.peek(0).get().getType();

        if (type.equals(Token.TokenTypes.NUMBER)) {
            NumericLiteralNode num = new NumericLiteralNode();
            num.value = Float.parseFloat(tokenManager.matchAndRemove(Token.TokenTypes.NUMBER).get().getValue());
            return Optional.of(num);
        }

        if (type.equals(Token.TokenTypes.TRUE)) {
            tokenManager.matchAndRemove(Token.TokenTypes.TRUE);
            return Optional.of(new BooleanLiteralNode(true));
        }

        if (type.equals(Token.TokenTypes.FALSE)) {
            tokenManager.matchAndRemove(Token.TokenTypes.FALSE);
            return Optional.of(new BooleanLiteralNode(false));
        }

        if (type.equals(Token.TokenTypes.QUOTEDSTRING)) {
            StringLiteralNode str = new StringLiteralNode();
            str.value = tokenManager.matchAndRemove(Token.TokenTypes.QUOTEDSTRING).get().getValue();
            return Optional.of(str);
        }

        // Handle variable references and method calls
        if (type.equals(Token.TokenTypes.WORD)) {
            // Check if it's a method call
            if (tokenManager.peek(1).isPresent() &&
                    (tokenManager.peek(1).get().getType().equals(Token.TokenTypes.LPAREN) ||
                            tokenManager.peek(1).get().getType().equals(Token.TokenTypes.DOT))) {
                return handleMethodCall();
            }
            // Simple variable reference
            VariableReferenceNode ref = new VariableReferenceNode();
            ref.name = tokenManager.matchAndRemove(Token.TokenTypes.WORD).get().getValue().trim();
            return Optional.of(ref);
        }

        // Handle new object creation
        if (type.equals(Token.TokenTypes.NEW)) {
            tokenManager.matchAndRemove(Token.TokenTypes.NEW);
            NewNode newNode = new NewNode();

            if (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.WORD)) {
                throw new SyntaxErrorException("Expected class name after new",
                        tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            }

            newNode.className = tokenManager.matchAndRemove(Token.TokenTypes.WORD).get().getValue();

            if (!tokenManager.matchAndRemove(Token.TokenTypes.LPAREN).isPresent()) {
                throw new SyntaxErrorException("Expected ( after class name",
                        tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            }

            // Parse constructor parameters
            if (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.RPAREN)) {
                do {
                    Optional<ExpressionNode> param = Expression();
                    if (param.isEmpty()) {
                        throw new SyntaxErrorException("Expected parameter expression",
                                tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
                    }
                    newNode.parameters.add(param.get());
                } while (tokenManager.matchAndRemove(Token.TokenTypes.COMMA).isPresent());
            }

            if (!tokenManager.matchAndRemove(Token.TokenTypes.RPAREN).isPresent()) {
                throw new SyntaxErrorException("Expected ) after parameters",
                        tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            }

            return Optional.of(newNode);
        }

        // Handle parenthesized expressions
        if (type.equals(Token.TokenTypes.LPAREN)) {
            tokenManager.matchAndRemove(Token.TokenTypes.LPAREN);
            Optional<ExpressionNode> expr = Expression();
            if (expr.isEmpty()) {
                throw new SyntaxErrorException("expected (",
                        tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            }

            if (!tokenManager.matchAndRemove(Token.TokenTypes.RPAREN).isPresent()) {
                throw new SyntaxErrorException("Expected )",
                        tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            }

            return expr;
        }

        return Optional.empty();
    }

    Optional<StatementNode> MethodCall() throws SyntaxErrorException {
        MethodCallStatementNode node = new MethodCallStatementNode();
        boolean hasReturnValues = false;

        // Check if there are return values
        if (tokenManager.peek(1).get().getType().equals(Token.TokenTypes.COMMA) ||
                tokenManager.peek(1).get().getType().equals(Token.TokenTypes.ASSIGN)) {

            // Get the first return value
            node.returnValues.add(VariableReference());

            // Get any additional return values
            while (tokenManager.peek(0).get().getType().equals(Token.TokenTypes.COMMA)) {
                tokenManager.matchAndRemove(Token.TokenTypes.COMMA);
                node.returnValues.add(VariableReference());
            }

            hasReturnValues = true;

            // Must have equals sign after return values
            if (!tokenManager.matchAndRemove(Token.TokenTypes.ASSIGN).isPresent()) {
                throw new SyntaxErrorException("Expected = ", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            }
        }

        // Parse method call part
        if (tokenManager.peek(1).get().getType().equals(Token.TokenTypes.DOT)) {
            node.objectName = Optional.of(tokenManager.matchAndRemove(Token.TokenTypes.WORD).get().getValue().trim());
            tokenManager.matchAndRemove(Token.TokenTypes.DOT);
        } else {
            node.objectName = Optional.empty();
        }

        node.methodName = tokenManager.matchAndRemove(Token.TokenTypes.WORD).get().getValue().trim();

        // Handle parameters
        if (!tokenManager.matchAndRemove(Token.TokenTypes.LPAREN).isPresent()) {
            throw new SyntaxErrorException("Expected (", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        if (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.RPAREN)) {
            Optional<ExpressionNode> param = Expression();
            if (param.isPresent()) {
                node.parameters.add(param.get());

                while (tokenManager.peek(0).get().getType().equals(Token.TokenTypes.COMMA)) {
                    tokenManager.matchAndRemove(Token.TokenTypes.COMMA);
                    if (tokenManager.peek(0).get().getType().equals(Token.TokenTypes.RPAREN)) {
                        break;
                    }
                    param = Expression();
                    if (param.isPresent()) {
                        node.parameters.add(param.get());
                    }
                }
            }
        }

        tokenManager.matchAndRemove(Token.TokenTypes.RPAREN);
        return Optional.of(node);
    }


    // MethodCallExpression = [Identifier "."] Identifier "(" [Expression {"," Expression }] ")"
    Optional<MethodCallExpressionNode> MethodCallExpression() throws SyntaxErrorException {
        MethodCallExpressionNode exp = new MethodCallExpressionNode();

        // Handle optional object name (e.g., console.write)
        if (tokenManager.peek(1).isPresent() &&
                tokenManager.peek(1).get().getType().equals(Token.TokenTypes.DOT)) {
            exp.objectName = Optional.of(tokenManager.matchAndRemove(Token.TokenTypes.WORD).get().getValue().trim());
            tokenManager.matchAndRemove(Token.TokenTypes.DOT);
        } else {
            exp.objectName = Optional.empty();
        }

        exp.methodName = tokenManager.matchAndRemove(Token.TokenTypes.WORD).get().getValue().trim();

        if (!tokenManager.matchAndRemove(Token.TokenTypes.LPAREN).isPresent()) {
            return Optional.empty();
        }

        // Parse parameters
        if (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.RPAREN)) {
            Optional<ExpressionNode> param = Expression();
            if (param.isPresent()) {
                exp.parameters.add(param.get());
            }
        }

        tokenManager.matchAndRemove(Token.TokenTypes.RPAREN);
        return Optional.of(exp);
    }


    Optional<StatementNode> disambiguate() throws SyntaxErrorException {
        if (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.WORD)) {
            return Optional.empty();
        }

        // Check ahead to see if this is an assignment or method call
        if (tokenManager.peek(1).get().getType().equals(Token.TokenTypes.ASSIGN)) {
            AssignmentNode assign = new AssignmentNode();

            // Create variable reference node for target
            assign.target = VariableReference();
            tokenManager.matchAndRemove(Token.TokenTypes.ASSIGN);

            // Get the expression
            Optional<ExpressionNode> expr = Expression();
            if (expr.isEmpty()) {
                throw new SyntaxErrorException("Expected expression", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            }
            assign.expression = expr.get();

            return Optional.of(assign);
        } else {
            return MethodCall();
        }
    }


    // VariableReference = Identifier
    VariableReferenceNode VariableReference() throws SyntaxErrorException {
        if (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.WORD)) {
            throw new SyntaxErrorException("Expected variable name", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        Token token = tokenManager.peek(0).get();

        VariableReferenceNode ref = new VariableReferenceNode();
        ref.name = tokenManager.matchAndRemove(Token.TokenTypes.WORD).get().getValue().strip();


        return ref;
    }


    Optional<VariableReferenceNode> parseVariableReference() throws SyntaxErrorException {
        if (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.WORD)) {
            return Optional.empty();
        }

        VariableReferenceNode ref = new VariableReferenceNode();
        ref.name = tokenManager.matchAndRemove(Token.TokenTypes.WORD).get().getValue().trim();
        return Optional.of(ref);
    }



    Optional<ExpressionNode> handleMethodCall() throws SyntaxErrorException {
        MethodCallExpressionNode node = new MethodCallExpressionNode();

        // Handle object name if present
        if (tokenManager.peek(1).get().getType().equals(Token.TokenTypes.DOT)) {
            node.objectName = Optional.of(tokenManager.matchAndRemove(Token.TokenTypes.WORD).get().getValue().trim());
            tokenManager.matchAndRemove(Token.TokenTypes.DOT);
        } else {
            node.objectName = Optional.empty();
        }

        // Get method name
        node.methodName = tokenManager.matchAndRemove(Token.TokenTypes.WORD).get().getValue().trim();

        // Handle parameters
        tokenManager.matchAndRemove(Token.TokenTypes.LPAREN);

        if (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.RPAREN)) {
            Optional<ExpressionNode> param = Expression();
            if (param.isPresent()) {
                node.parameters.add(param.get());
            }
        }

        tokenManager.matchAndRemove(Token.TokenTypes.RPAREN);
        return Optional.of(node);
    }



    // "if" BoolExp NEWLINE Statements ["else" NEWLINE (Statement | Statements)]
    Optional<StatementNode> parseIf() throws SyntaxErrorException {
        if (!tokenManager.matchAndRemove(Token.TokenTypes.IF).isPresent()) {
            return Optional.empty();
        }

        IfNode ifNode = new IfNode();

        // Parse condition
        Optional<ExpressionNode> boolExp = BoolExpTerm();
        if (boolExp.isEmpty()) {
            throw new SyntaxErrorException("Expected boolean", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }
        ifNode.condition = boolExp.get();

        // Must have newline after condition
        if (!tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE).isPresent()) {
            throw new SyntaxErrorException("Expected newline", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        // Parse statements
        ifNode.statements = Statements();

        // Handle optional else
        if (tokenManager.matchAndRemove(Token.TokenTypes.ELSE).isPresent()) {
            if (!tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE).isPresent()) {
                throw new SyntaxErrorException("Expected newline after else", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            }

            ElseNode elseNode = new ElseNode();
            elseNode.statements = Statements();
            ifNode.elseStatement = Optional.of(elseNode);
        } else {
            ifNode.elseStatement = Optional.empty();
        }

        return Optional.of(ifNode);
    }


    // MethodDeclaration = ["private"] ["shared"] MethodHeader NEWLINE MethodBody
    Optional<MethodDeclarationNode> parseMethod() throws SyntaxErrorException {
        MethodDeclarationNode methodDec = new MethodDeclarationNode();

        // Parse shared/private keywords
        if (tokenManager.matchAndRemove(Token.TokenTypes.SHARED).isPresent()) {
            methodDec.isShared = true;
        }
        if (tokenManager.matchAndRemove(Token.TokenTypes.PRIVATE).isPresent()) {
            methodDec.isPrivate = true;
        }

        // Get method name
        if (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.WORD)) {
            return Optional.empty();
        }
        methodDec.name = tokenManager.matchAndRemove(Token.TokenTypes.WORD).get().getValue().strip();

        // Handle parameters
        tokenManager.matchAndRemove(Token.TokenTypes.LPAREN);
        while (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.RPAREN)) {
            Optional<VariableDeclarationNode> param = parseVariableDeclaration();
            if (param.isPresent()) {
                String name = param.get().name.strip();  // Ensure clean name

                param.get().name = name;  // Store cleaned name
                methodDec.parameters.add(param.get());
                if (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.RPAREN)) {
                    tokenManager.matchAndRemove(Token.TokenTypes.COMMA);
                }
            }
        }
        tokenManager.matchAndRemove(Token.TokenTypes.RPAREN);

        // Handle return types
        if (tokenManager.matchAndRemove(Token.TokenTypes.COLON).isPresent()) {
            do {
                Optional<VariableDeclarationNode> returnVar = parseVariableDeclaration();
                if (returnVar.isPresent()) {
                    methodDec.returns.add(returnVar.get());
                }
            } while (tokenManager.matchAndRemove(Token.TokenTypes.COMMA).isPresent());
        }

        tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE);

        // Parse method body
        if (tokenManager.matchAndRemove(Token.TokenTypes.INDENT).isPresent()) {
            // Parse local variables first
            while (tokenManager.peek(0).get().getType().equals(Token.TokenTypes.WORD) &&
                    tokenManager.peek(1).get().getType().equals(Token.TokenTypes.WORD)) {
                Optional<VariableDeclarationNode> local = parseVariableDeclaration();
                if (local.isPresent()) {
                    String name = local.get().name.strip();  // Ensure clean name

                    local.get().name = name;  // Store cleaned name
                    methodDec.locals.add(local.get());
                    tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE);
                }
            }

            // Parse statements
            while (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.DEDENT)) {
                Optional<StatementNode> stmt = Statement();
                if (stmt.isPresent()) {

                    methodDec.statements.add(stmt.get());
                    if (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.DEDENT)) {
                        tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE);
                    }
                }
            }
            tokenManager.matchAndRemove(Token.TokenTypes.DEDENT);
        }


        return Optional.of(methodDec);
    }

    // Statement = If | Loop | MethodCall | Assignment
    Optional<StatementNode> Statement() throws SyntaxErrorException {
        if (!tokenManager.peek(0).isPresent()) {
            return Optional.empty();
        }

        Token.TokenTypes type = tokenManager.peek(0).get().getType();

        if (type == Token.TokenTypes.IF) {
            return parseIf();
        }
        else if (type == Token.TokenTypes.LOOP) {
            return parseLoop();
        }
        else if (type == Token.TokenTypes.WORD) {
            return disambiguate();
        }

        return Optional.empty();
    }


    // Statements = INDENT {Statement NEWLINE } DEDENT
    private List<StatementNode> Statements() throws SyntaxErrorException {
        List<StatementNode> statements = new ArrayList<>();

        // Must start with indent
        if (!tokenManager.matchAndRemove(Token.TokenTypes.INDENT).isPresent()) {
            throw new SyntaxErrorException("Expected indented block", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        // Parse statements until we hit a DEDENT
        while (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.DEDENT)) {
            Optional<StatementNode> statement = Statement();
            if (statement.isPresent()) {
                statements.add(statement.get());
            }

            // Need newline after each statement unless we're at DEDENT
            if (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.DEDENT)) {
                if (!tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE).isPresent()) {
                    throw new SyntaxErrorException("Expected newline after statement",
                            tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
                }
            }
        }

        // Must end with dedent
        if (!tokenManager.matchAndRemove(Token.TokenTypes.DEDENT).isPresent()) {
            throw new SyntaxErrorException("Expected dedent at end of block",
                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        return statements;
    }

    // Rewrite entire parser
    // Class = "class" Identifier [ "implements" Identifier { "," Identifier } ] NEWLINE INDENT {
    //Constructor NEWLINE | MethodDeclaration NEWLINE | Member NEWLINE } DEDENT
    Optional<ClassNode> parseClass() throws SyntaxErrorException {
        if (!tokenManager.matchAndRemove(Token.TokenTypes.CLASS).isPresent()) {
            return Optional.empty();
        }

        ClassNode classNode = new ClassNode();

        // Get class name
        if (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.WORD)) {
            throw new SyntaxErrorException("Class name expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }
        classNode.name = tokenManager.matchAndRemove(Token.TokenTypes.WORD).get().getValue();

        // Handle implements clause
        if (tokenManager.matchAndRemove(Token.TokenTypes.IMPLEMENTS).isPresent()) {
            do {
                if (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.WORD)) {
                    throw new SyntaxErrorException("Interface name expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
                }
                classNode.interfaces.add(tokenManager.matchAndRemove(Token.TokenTypes.WORD).get().getValue());
            } while (tokenManager.matchAndRemove(Token.TokenTypes.COMMA).isPresent());
        }

        // Handle newline after class declaration
        if (!tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE).isPresent()) {
            throw new SyntaxErrorException("Newline expected after class declaration", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        if (!tokenManager.matchAndRemove(Token.TokenTypes.INDENT).isPresent()) {
            throw new SyntaxErrorException("Indent expected at class body", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        // Parse class body - keep going until we see a DEDENT
        while (!tokenManager.done() && !tokenManager.peek(0).get().getType().equals(Token.TokenTypes.DEDENT)) {
            // Skip empty lines
            while (tokenManager.peek(0).get().getType().equals(Token.TokenTypes.NEWLINE)) {
                tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE);
            }

            // Check for constructors, methods, and members
            if (tokenManager.peek(0).get().getType().equals(Token.TokenTypes.CONSTRUCT)) {
                classNode.constructors.addAll(parseConstructor());
            }
            else if (tokenManager.peek(0).get().getType().equals(Token.TokenTypes.SHARED)) {
                Optional<MethodDeclarationNode> method = parseMethod();
                if (method.isPresent()) {
                    classNode.methods.add(method.get());
                }
            }
            else if (tokenManager.peek(0).get().getType().equals(Token.TokenTypes.WORD)) {
                // Check if this is a method declaration by looking ahead for LPAREN
                if (tokenManager.peek(1).get().getType().equals(Token.TokenTypes.LPAREN)) {
                    Optional<MethodDeclarationNode> method = parseMethod();
                    if (method.isPresent()) {
                        classNode.methods.add(method.get());
                    }
                } else {
                    // Must be a member
                    classNode.members.addAll(parseMember());
                }
            }
        }

        if (!tokenManager.matchAndRemove(Token.TokenTypes.DEDENT).isPresent()) {
            throw new SyntaxErrorException("Dedent expected at end of class", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        return Optional.of(classNode);
    }

    // Re-written to fit for other parser tests
    Optional<InterfaceNode> parseInterface() throws SyntaxErrorException {

        if (tokenManager.matchAndRemove(Token.TokenTypes.INTERFACE).isEmpty()) {
            return Optional.empty();
        }

        InterfaceNode interfaceNode = new InterfaceNode();

        // interface name
        interfaceNode.name = tokenManager.matchAndRemove(Token.TokenTypes.WORD).get().getValue();


        if (tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE).isEmpty()) {
            throw new SyntaxErrorException("Newline expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        if (tokenManager.matchAndRemove(Token.TokenTypes.INDENT).isEmpty()) {
            throw new SyntaxErrorException("Indent expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        // Method header loops
        while (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.DEDENT)) {
            Optional<MethodHeaderNode> methodHeader = parseMethodHeader();
            if (methodHeader.isPresent()) {
                interfaceNode.methods.add(methodHeader.get());

                // Expect newline after method header unless at DEDENT
                if (!tokenManager.peek(0).get().getType().equals(Token.TokenTypes.DEDENT)) {
                    if (!tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE).isPresent()) {
                        throw new SyntaxErrorException("Newline expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
                    }
                }
            }
        }

        if (tokenManager.matchAndRemove(Token.TokenTypes.DEDENT).isEmpty()) {
            throw new SyntaxErrorException("Dedent expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        return Optional.of(interfaceNode);
    }



    // Tran = { Class | Interface }
    public void Tran() throws SyntaxErrorException {

        while (!tokenManager.done()) {  // Keep going until we run out of tokens
            if (tokenManager.peek(0).get().getType().equals(Token.TokenTypes.INTERFACE)) {
                Optional<InterfaceNode> interfaceNode = parseInterface();
                if (interfaceNode.isPresent()) {
                    tNode.Interfaces.add(interfaceNode.get());
                }
            } else if (tokenManager.peek(0).get().getType().equals(Token.TokenTypes.CLASS)) {
                Optional<ClassNode> classNode = parseClass();
                if (classNode.isPresent()) {
                    tNode.Classes.add(classNode.get());
                }
            } else if (tokenManager.peek(0).get().getType().equals(Token.TokenTypes.NEWLINE)) {
                // Skip extra newlines between declarations
                tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE);
            } else {
                break; // Exit if we find something unexpected
            }
        }
    }
}