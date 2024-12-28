package Interpreter;

import AST.*;

import java.util.*;

public class Interpreter {
    private TranNode top;

    /**
     * Constructor - get the interpreter ready to run. Set members from parameters and "prepare" the class.
     * <p>
     * Store the tran node.
     * Add any built-in methods to the AST
     *
     * @param top - the head of the AST
     */
    public Interpreter(TranNode top) {
        // Store the tran node
        this.top = top;


        // Add built-in methods to the AST - setting up console class
        ClassNode consoleClass = new ClassNode();
        consoleClass.name = "console";


        // Create and configure write method
        ConsoleWrite writeMethod = new ConsoleWrite();
        writeMethod.name = "write";
        writeMethod.isShared = true;
        writeMethod.isVariadic = true;

        // Add method to console class
        consoleClass.methods.add(writeMethod);

        // Add console class to AST
        top.Classes.add(consoleClass);
    }

    /**
     * This is the public interface to the interpreter. After parsing, we will create an interpreter and call start to
     * start interpreting the code.
     * <p>
     * Search the classes in Tran for a method that is "isShared", named "start", that is not private and has no parameters
     * Call "InterpretMethodCall" on that method, then return.
     * Throw an exception if no such method exists.
     */
    public void start() {
        // Search classes for start method meeting criteria
        for (ClassNode classNode : top.Classes) {
            for (MethodDeclarationNode method : classNode.methods) {
                if (method.name.equals("start") && method.isShared && !method.isPrivate && method.parameters.isEmpty()) {
                    // Call InterpretMethodCall and return
                    interpretMethodCall(Optional.empty(), method, new ArrayList<>());
                    return;
                }
            }
        }
        // Throw exception if no valid start method found
        throw new RuntimeException("No valid start method found");
    }

    //              Running Methods

    /**
     * Find the method (local to this class, shared (like Java's system.out.print), or a method on another class)
     * Evaluate the parameters to have a list of values
     * Use interpretMethodCall() to actually run the method.
     * <p>
     * Call GetParameters() to get the parameter value list
     * Find the method. This is tricky - there are several cases:
     * someLocalMethod() - has NO object name. Look in "object"
     * console.write() - the objectName is a CLASS and the method is shared
     * bestStudent.getGPA() - the objectName is a local or a member
     * <p>
     * Once you find the method, call InterpretMethodCall() on it. Return the list that it returns.
     * Throw an exception if we can't find a match.
     *
     * @param object - the object we are inside right now (might be empty)
     * @param locals - the current local variables
     * @param mc     - the method call
     * @return - the return values
     */
    private List<InterpreterDataType> findMethodForMethodCallAndRunIt(Optional<ObjectIDT> object, HashMap<String, InterpreterDataType> locals, MethodCallStatementNode mc) {
        // Evaluate parameters
        List<InterpreterDataType> parameterValues = getParameters(object, locals, mc);

        // Local method (no object name)
        if (mc.objectName.isEmpty() && object.isPresent()) {
            for (var method : object.get().astNode.methods) {
                if (doesMatch(method, mc, parameterValues)) {
                    return interpretMethodCall(object, method, parameterValues);
                }
            }
        }

        // Class method (like console.write)
        if (mc.objectName.isPresent()) {
            String objName = mc.objectName.get();
            if (objName.equals("console")) {
                for (var classNode : top.Classes) {
                    if (classNode.name.equals("console")) {
                        for (var method : classNode.methods) {
                            if (doesMatch(method, mc, parameterValues)) {
                                return interpretMethodCall(Optional.of(new ObjectIDT(classNode)), method, parameterValues);
                            }
                        }
                    }
                }
            }
        }

        // Object method (bestStudent.getGPA)
        if (mc.objectName.isPresent()) {
            var objName = mc.objectName.get();
            var obj = findVariable(objName, locals, object);

            if (obj instanceof ObjectIDT targetObj) {
                for (var method : targetObj.astNode.methods) {
                    if (doesMatch(method, mc, parameterValues)) {
                        return interpretMethodCall(Optional.of(targetObj), method, parameterValues);
                    }
                }
            }
            if (obj instanceof ReferenceIDT ref && ref.refersTo.isPresent()) {
                for (var method : ref.refersTo.get().astNode.methods) {
                    if (doesMatch(method, mc, parameterValues)) {
                        return interpretMethodCall(ref.refersTo, method, parameterValues);
                    }
                }
            }
        }

        throw new RuntimeException("No method" + mc.methodName);
    }

    /**
     * Run a "prepared" method (found, parameters evaluated)
     * This is split from findMethodForMethodCallAndRunIt() because there are a few cases where we don't need to do the finding:
     * in start() and dealing with loops with iterator objects, for example.
     * <p>
     * Check to see if "m" is a built-in. If so, call Execute() on it and return
     * Make local variables, per "m"
     * If the number of passed in values doesn't match m's "expectations", throw
     * Add the parameters by name to locals.
     * Call InterpretStatementBlock
     * Build the return list - find the names from "m", then get the values for those names and add them to the list.
     *
     * @param object - The object this method is being called on (might be empty for shared)
     * @param m      - Which method is being called
     * @param values - The values to be passed in
     * @return the returned values from the method
     */
    private List<InterpreterDataType> interpretMethodCall(Optional<ObjectIDT> object, MethodDeclarationNode m, List<InterpreterDataType> values) {
        // Check if built-in method
        if (m instanceof BuiltInMethodDeclarationNode bm) {
            return bm.Execute(values);
        }

        // Make local variables
        var locals = new HashMap<String, InterpreterDataType>();

        // Verify parameter count matches expectations
        if (m.parameters.size() != values.size()) {
            throw new RuntimeException("Parameter count mismatch");
        }

        // Add parameters by name to locals
        for (int i = 0; i < m.parameters.size(); i++) {
            locals.put(m.parameters.get(i).name, values.get(i));
        }

        // Create local variables
        for (var local : m.locals) {
            locals.put(local.name, instantiate(local.type));
        }

        // Initialize return variables
        for (var ret : m.returns) {
            locals.put(ret.name, instantiate(ret.type));
        }

        // Call InterpretStatementBlock
        interpretStatementBlock(object, m.statements, locals);

        // Build and return the return list
        var returnValues = new ArrayList<InterpreterDataType>();
        for (var ret : m.returns) {
            returnValues.add(locals.get(ret.name));
        }
        return returnValues;
    }


    /**
     * This is a special case of the code for methods. Just different enough to make it worthwhile to split it out.
     * <p>
     * Call GetParameters() to populate a list of IDT's
     * Call GetClassByName() to find the class for the constructor
     * If we didn't find the class, throw an exception
     * Find a constructor that is a good match - use DoesConstructorMatch()
     * Call InterpretConstructorCall() on the good match
     *
     * @param callerObj - the object that we are inside when we called the constructor
     * @param locals    - the current local variables (used to fill parameters)
     * @param mc        - the method call for this construction
     * @param newOne    - the object that we just created that we are calling the constructor for
     */
    private void findConstructorAndRunIt(Optional<ObjectIDT> callerObj, HashMap<String, InterpreterDataType> locals, MethodCallStatementNode mc, ObjectIDT newOne) {
        // Call GetParameters() to populate a list of IDT's
        var paramValues = getParameters(callerObj, locals, mc);

        // Call GetClassByName() to find the class for the constructor
        var classNode = getClassByName(mc.methodName);

        // If we didn't find the class, throw an exception
        if (classNode.isEmpty()) {
            throw new RuntimeException("Class not found: " + mc.methodName);
        }

        // Find a constructor that is a good match - use DoesConstructorMatch()
        for (var constructor : newOne.astNode.constructors) {
            if (doesConstructorMatch(constructor, mc, paramValues)) {
                // Call InterpretConstructorCall() on the good match
                interpretConstructorCall(newOne, constructor, paramValues);
                return;
            }
        }

        throw new RuntimeException("No matching constructor found for " + mc.methodName);
    }

    /**
     * Similar to interpretMethodCall, but "just different enough" - for example, constructors don't return anything.
     * <p>
     * Creates local variables (as defined by the ConstructorNode), calls Instantiate() to do the creation
     * Checks to ensure that the right number of parameters were passed in, if not throw.
     * Adds the parameters (with the names from the ConstructorNode) to the locals.
     * Calls InterpretStatementBlock
     *
     * @param object - the object that we allocated
     * @param c      - which constructor is being called
     * @param values - the parameter values being passed to the constructor
     */
    private void interpretConstructorCall(ObjectIDT object, ConstructorNode c, List<InterpreterDataType> values) {
        var locals = new HashMap<String, InterpreterDataType>();

        // Check parameter count matches
        if (c.parameters.size() != values.size()) {
            throw new RuntimeException("Construct count does not match");
        }

        // Add parameters to locals and member variables
        for (int i = 0; i < c.parameters.size(); i++) {
            var paramName = c.parameters.get(i).name;
            locals.put(paramName, values.get(i));
            object.members.put(paramName, values.get(i));
        }

        // Create local variables
        for (var local : c.locals) {
            locals.put(local.name, instantiate(local.type));
        }

        // Execute constructor body
        interpretStatementBlock(Optional.of(object), c.statements, locals);
    }

    //              Running Instructions

    /**
     * Given a block (which could be from a method or an "if" or "loop" block, run each statement.
     * Blocks, by definition, do ever statement, so iterating over the statements makes sense.
     * <p>
     * For each statement in statements:
     * check the type:
     * For AssignmentNode, FindVariable() to get the target. Evaluate() the expression. Call Assign() on the target with the result of Evaluate()
     * For MethodCallStatementNode, call doMethodCall(). Loop over the returned values and copy the into our local variables
     * For LoopNode - there are 2 kinds.
     * Setup:
     * If this is a Loop over an iterator (an Object node whose class has "iterator" as an interface)
     * Find the "getNext()" method; throw an exception if there isn't one
     * Loop:
     * While we are not done:
     * if this is a boolean loop, Evaluate() to get true or false.
     * if this is an iterator, call "getNext()" - it has 2 return values. The first is a boolean (was there another?), the second is a value
     * If the loop has an assignment variable, populate it: for boolean loops, the true/false. For iterators, the "second value"
     * If our answer from above is "true", InterpretStatementBlock() on the body of the loop.
     * For If - Evaluate() the condition. If true, InterpretStatementBlock() on the if's statements. If not AND there is an else, InterpretStatementBlock on the else body.
     *
     * @param object     - the object that this statement block belongs to (used to get member variables and any members without an object)
     * @param statements - the statements to run
     * @param locals     - the local variables
     */
    private void interpretStatementBlock(Optional<ObjectIDT> object, List<StatementNode> statements, HashMap<String, InterpreterDataType> locals) {
        for (var statement : statements) {
            if (statement instanceof AssignmentNode an) {
                // Handle assignment
                var target = findVariable(an.target.name, locals, object);
                var value = evaluate(locals, object, an.expression);
                target.Assign(value);
            }
            else if (statement instanceof MethodCallStatementNode mc) {
                // Handle method call
                var returnValues = findMethodForMethodCallAndRunIt(object, locals, mc);
                for (int i = 0; i < mc.returnValues.size(); i++) {
                    var target = findVariable(mc.returnValues.get(i).name, locals, object);
                    target.Assign(returnValues.get(i));
                }
            }
            else if (statement instanceof IfNode in) {
                // Handle if statement
                var condition = evaluate(locals, object, in.condition);
                if (condition instanceof BooleanIDT bidt && bidt.Value) {
                    interpretStatementBlock(object, in.statements, locals);
                } else
                    in.elseStatement.ifPresent(elseNode -> interpretStatementBlock(object, elseNode.statements, locals));
            }
            else if (statement instanceof LoopNode ln) {
                // Handle loop
                InterpreterDataType loopCondition = evaluate(locals, object, ln.expression);
                VariableReferenceNode assignmentVar = ln.assignment.orElse(null);

                // Loop while condition is true
                while (loopCondition instanceof BooleanIDT bidt && bidt.Value) {
                    interpretStatementBlock(object, ln.statements, locals);

                    // Update assignment variable if it exists
                    if (assignmentVar != null) {
                        loopCondition = evaluate(locals, object, ln.expression);
                        findVariable(assignmentVar.name, locals, object).Assign(loopCondition);
                    }
                }
            }
        }
    }

    /**
     * evaluate() processes everything that is an expression - math, variables, boolean expressions.
     * There is a good bit of recursion in here, since math and comparisons have left and right sides that need to be evaluated.
     * <p>
     * See the How To Write an Interpreter document for examples
     * For each possible ExpressionNode, do the work to resolve it:
     * BooleanLiteralNode - create a new BooleanLiteralNode with the same value
     * - Same for all of the basic data types
     * BooleanOpNode - Evaluate() left and right, then perform either and/or on the results.
     * CompareNode - Evaluate() both sides. Do good comparison for each data type
     * MathOpNode - Evaluate() both sides. If they are both numbers, do the math using the built-in operators. Also handle String + String as concatenation (like Java)
     * MethodCallExpression - call doMethodCall() and return the first value
     * VariableReferenceNode - call findVariable()
     *
     * @param locals     the local variables
     * @param object     - the current object we are running
     * @param expression - some expression to evaluate
     * @return a value
     */
    private InterpreterDataType evaluate(HashMap<String, InterpreterDataType> locals, Optional<ObjectIDT> object, ExpressionNode expression) {
        if (expression instanceof NumericLiteralNode n) {
            return new NumberIDT(n.value);
        } else if (expression instanceof StringLiteralNode s) {
            return new StringIDT(s.value);
        } else if (expression instanceof BooleanLiteralNode b) {
            return new BooleanIDT(b.value);
        } else if (expression instanceof CharLiteralNode c) {
            return new CharIDT(c.value);
        } else if (expression instanceof VariableReferenceNode v) {
            return findVariable(v.name, locals, object);
        } else if (expression instanceof MathOpNode m) {
            var left = evaluate(locals, object, m.left);
            var right = evaluate(locals, object, m.right);

            if (left instanceof NumberIDT ln && right instanceof NumberIDT rn) {
                float result;
                if (m.op == MathOpNode.MathOperations.add) {
                    result = ln.Value + rn.Value;
                } else if (m.op == MathOpNode.MathOperations.subtract) {
                    result = ln.Value - rn.Value;
                } else if (m.op == MathOpNode.MathOperations.multiply) {
                    result = ln.Value * rn.Value;
                } else if (m.op == MathOpNode.MathOperations.divide) {
                    result = ln.Value / rn.Value;
                } else if (m.op == MathOpNode.MathOperations.modulo) {
                    result = ln.Value % rn.Value;
                } else {
                    throw new RuntimeException("Unknown");
                }
                return new NumberIDT(result);
            }
            throw new RuntimeException("Invalid");
        } else if (expression instanceof CompareNode c) {
            var left = evaluate(locals, object, c.left);
            var right = evaluate(locals, object, c.right);

            if (left instanceof NumberIDT ln && right instanceof NumberIDT rn) {
                boolean result;
                if (c.op == CompareNode.CompareOperations.lt) {
                    result = ln.Value < rn.Value;
                } else if (c.op == CompareNode.CompareOperations.le) {
                    result = ln.Value <= rn.Value;
                } else if (c.op == CompareNode.CompareOperations.gt) {
                    result = ln.Value > rn.Value;
                } else if (c.op == CompareNode.CompareOperations.ge) {
                    result = ln.Value >= rn.Value;
                } else if (c.op == CompareNode.CompareOperations.eq) {
                    result = ln.Value == rn.Value;
                } else if (c.op == CompareNode.CompareOperations.ne) {
                    result = ln.Value != rn.Value;
                } else {
                    throw new RuntimeException("Unknown");
                }
                return new BooleanIDT(result);
            }
            throw new RuntimeException("Invalid");
        } else if (expression instanceof NewNode n) {
            var newObject = new ObjectIDT(getClassByName(n.className).orElseThrow());
            for (var member : newObject.astNode.members) {
                newObject.members.put(member.declaration.name, instantiate(member.declaration.type));
            }

            var methodCall = new MethodCallStatementNode();
            methodCall.methodName = n.className;
            methodCall.parameters.addAll(n.parameters);

            findConstructorAndRunIt(object, locals, methodCall, newObject);

            return newObject;
        } else if (expression instanceof MethodCallExpressionNode mcn) {
            var mc = new MethodCallStatementNode(mcn);
            var results = findMethodForMethodCallAndRunIt(object, locals, mc);
            return results.getFirst();
        }
        throw new RuntimeException("Unknown expression type: " + expression.getClass());
    }

    //              Utility Methods

    /**
     * Used when trying to find a match to a method call. Given a method declaration, does it match this methoc call?
     * We double check with the parameters, too, although in theory JUST checking the declaration to the call should be enough.
     *
     * Match names, parameter counts (both declared count vs method call and declared count vs value list), return counts.
     * If all of those match, consider the types (use TypeMatchToIDT).
     * If everything is OK, return true, else return false.
     * Note - if m is a built-in and isVariadic is true, skip all of the parameter validation.
     * @param m - the method declaration we are considering
     * @param mc - the method call we are trying to match
     * @param parameters - the parameter values for this method call
     * @return does this method match the method call?
     */
    private boolean doesMatch(MethodDeclarationNode m, MethodCallStatementNode mc, List<InterpreterDataType> parameters) {
        // Check variadic built-in method
        if (m instanceof BuiltInMethodDeclarationNode bm && bm.isVariadic) {
            return m.name.equals(mc.methodName);
        }

        // Match names
        if (!m.name.equals(mc.methodName)) {
            return false;
        }

        // Match parameter counts
        if (m.parameters.size() != parameters.size()) {
            return false;
        }

        // Match return counts if present
        if (!mc.returnValues.isEmpty() && m.returns.size() != mc.returnValues.size()) {
            return false;
        }

        // Check parameter types match
        for (int i = 0; i < parameters.size(); i++) {
            if (!typeMatchToIDT(m.parameters.get(i).type, parameters.get(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Very similar to DoesMatch() except simpler - there are no return values, the name will always match.
     * @param c - a particular constructor
     * @param mc - the method call
     * @param parameters - the parameter values
     * @return does this constructor match the method call?
     */
    private boolean doesConstructorMatch(ConstructorNode c, MethodCallStatementNode mc, List<InterpreterDataType> parameters) {
        // Check parameter counts match both call and values
        int size = parameters.size();
        if (c.parameters.size() != size || mc.parameters.size() != size) {
            return false;
        }

        // Check parameter types match
        for (int i = 0; i < size; i++) {
            if (!typeMatchToIDT(c.parameters.get(i).type, parameters.get(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Used when we call a method to get the list of values for the parameters.
     *
     * for each parameter in the method call, call Evaluate() on the parameter to get an IDT and add it to a list
     * @param object - the current object
     * @param locals - the local variables
     * @param mc - a method call
     * @return the list of method values
     */
    private List<InterpreterDataType> getParameters(Optional<ObjectIDT> object, HashMap<String,InterpreterDataType> locals, MethodCallStatementNode mc) {
        // Create list for parameter values
        List<InterpreterDataType> result = new ArrayList<>(mc.parameters.size());

        // Evaluate each parameter and add to list
        for (ExpressionNode param : mc.parameters) {
            result.add(evaluate(locals, object, param));
        }

        return result;
    }


    /**
     * Used when we have an IDT and we want to see if it matches a type definition
     * Commonly, when someone is making a function call - do the parameter values match the method declaration?
     *
     * If the IDT is a simple type (boolean, number, etc) - does the string type match the name of that IDT ("boolean", etc)
     * If the IDT is an object, check to see if the name matches OR the class has an interface that matches
     * If the IDT is a reference, check the inner (refered to) type
     * @param type the name of a data type (parameter to a method)
     * @param idt the IDT someone is trying to pass to this method
     * @return is this OK?
     */
    private boolean typeMatchToIDT(String type, InterpreterDataType idt) {
        type = type.toLowerCase();

        // Check simple types
        switch (type) {
            case "number" -> {
                return idt instanceof NumberIDT;
            }
            case "string" -> {
                return idt instanceof StringIDT;
            }
            case "boolean" -> {
                return idt instanceof BooleanIDT;
            }
            case "character" -> {
                return idt instanceof CharIDT;
            }
        }

        // Check reference type
        if (idt instanceof ReferenceIDT ref) {
            if (ref.refersTo.isEmpty()) {
                return true;  // null reference can be assigned to any reference type
            }
            return typeMatchToIDT(type, ref.refersTo.get());
        }

        // Check object type and interfaces
        if (idt instanceof ObjectIDT obj) {
            return obj.astNode.name.equals(type) ||
                    obj.astNode.interfaces.contains(type);
        }

        return false;
    }


    /**
     * Find a class, given the name. Just loops over the TranNode's classes member, matching by name.
     *
     * Loop over each class in the top node, comparing names to find a match.
     * @param name Name of the class to find
     * @return either a class node or empty if that class doesn't exist
     */
    private Optional<ClassNode> getClassByName(String name) {
        return top.Classes.stream().filter(c -> c.name.equals(name)).findFirst();
    }

    /**
     * Given an execution environment (the current object, the current local variables), find a variable by name.
     *
     * @param name  - the variable that we are looking for
     * @param locals - the current method's local variables
     * @param object - the current object (so we can find members)
     * @return the IDT that we are looking for or throw an exception
     */
    private InterpreterDataType findVariable(String name, HashMap<String,InterpreterDataType> locals, Optional<ObjectIDT> object) {
        // Check local variables first
        InterpreterDataType local = locals.get(name);
        if (local != null) {
            return local;
        }

        // Check object members if present
        if (object.isPresent()) {
            InterpreterDataType member = object.get().members.get(name);
            if (member != null) {
                return member;
            }
        }

        throw new RuntimeException("Unable to find variable " + name);
    }

    /**
     * Given a string (the type name), make an IDT for it.
     *
     * @param type The name of the type (string, number, boolean, character). Defaults to ReferenceIDT if not one of those.
     * @return an IDT with default values (0 for number, "" for string, false for boolean, ' ' for character)
     */
    private InterpreterDataType instantiate(String type) {
        return switch (type) {
            case "number" -> new NumberIDT(0);
            case "string" -> new StringIDT("");
            case "boolean" -> new BooleanIDT(false);
            case "character" -> new CharIDT(' ');
            default -> new ReferenceIDT();
        };
    }
}
