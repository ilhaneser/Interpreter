import AST.BuiltInMethodDeclarationNode;
import AST.TranNode;
import Interpreter.Interpreter;
import Interpreter.ConsoleWrite;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class InterpreterTests {
    @Test
    public void SimpleAdd() {
        String program = """
                class SimpleAdd
                
                    shared start()
                        number x
                        number y
                        number z
                
                        x = 6
                        y = 6
                        z = x + y 
                        console.write(z)
                """;
        var tranNode = run(program);
        var c = getConsole(tranNode);
        Assertions.assertEquals(1,c.size());
        Assertions.assertEquals("12.0",c.getFirst());
    }

    @Test
    public void SimpleAddInstantiate() {
        String program = """
                class SimpleAdd
                    number x
                    number y
                
                    construct()
                        x = 6
                        y = 6
                
                    add()
                        number z
                        z = x + y
                        console.write(z)
                
                    shared start()
                        SimpleAdd t
                        t = new SimpleAdd()
                        t.add()
                
                """;
        var tranNode = run(program);
        var c = getConsole(tranNode);
        Assertions.assertEquals(1,c.size());
        Assertions.assertEquals("12.0",c.getFirst());
    }

    @Test
    public void SimpleAddInstantiateAndPrint() {
        String program = """
                class SimpleAdd
                    number x
                    number y
                    
                    construct()
                        x = 6
                        y = 6
                        
                    add()
                        number z
                        z = x + y 
                        console.write(z)
                        
                    shared start()
                        SimpleAdd t
                        t = new SimpleAdd()
                        t.add()
                        
                """;
        var tranNode = run(program);
        var c = getConsole(tranNode);
        Assertions.assertEquals(1,c.size());
        Assertions.assertEquals("12.0",c.getFirst());
    }

    @Test
    public void Loop1() {
        String program = "class LoopOne\n" +
                         "    shared start()\n" +
                         "        boolean keepGoing\n" +
                         "        number n\n" +
                         "        n = 0\n" +
                         "        keepGoing = true\n" +
                         "        loop keepGoing\n" +
                         "        	  if n >= 15\n" +
                         "                keepGoing = false\n" +
                         "            else\n" +
                         "                n = n + 1\n" +
                         "                console.write(n)\n";
        var tranNode = run(program);
        var c = getConsole(tranNode);
        Assertions.assertEquals(15,c.size());
        Assertions.assertEquals("1.0",c.getFirst());
        Assertions.assertEquals("15.0",c.getLast());
    }

    @Test
    public void student() {
        String program = "class student\n" +
                "    number gradea\n" +
                "    number gradeb\n" +
                "    number gradec\n" +
                "    string firstname\n" +
                "    string lastname\n" +
                "    \n" +
                "    construct (string fname, string lname, number ga, number gb, number gc)\n" +
                "        firstname = fname\n" +
                "        lastname = lname\n" +
                "        gradea = ga\n" +
                "        gradeb = gb\n" +
                "        gradec = gc\n" +
                "    \n" +
                "    getAverage() : number avg \n" +
                "        avg = (gradea + gradeb + gradec)/3\n" +
                "    \n" +
                "    print() \n" +
                "        console.write(firstname, \" \", lastname, \" \", getAverage())\n" +
                "    \n" +
                "    shared start()\n" +
                "        student sa\n" +
                "        student sb\n" +
                "        student sc\n" +
                "        sa = new student(\"michael\",\"phipps\",100,99,98)\n" +
                "        sb = new student(\"tom\",\"johnson\",80,75,83)\n" +
                "        sc = new student(\"bart\",\"simpson\",32,25,33)\n" +
                "        sa.print()\n" +
                "        sb.print()\n" +
                "        sc.print()\n";
        var tranNode = run(program);
        var c = getConsole(tranNode);
        Assertions.assertEquals(3,c.size());
        Assertions.assertEquals("michael phipps 99.0",c.getFirst());
        Assertions.assertEquals("bart simpson 30.0",c.getLast());
    }

    @Test
    public void testInterface() {
        String program = """
        interface Measurable
            getArea() : number area
            
        class Rectangle implements Measurable
            number width
            number height
            
            construct(number w, number h)
                width = w
                height = h
                
            getArea() : number area
                area = width * height
                
            shared start()
                Rectangle r
                r = new Rectangle(5, 3)
                console.write(r.getArea())
        """;
        var tranNode = run(program);
        var c = getConsole(tranNode);
        Assertions.assertEquals("15.0", c.getFirst());
    }

    @Test
    public void testAccessorMutator() {
        String program = """
        class Person
            string name
            accessor:
                console.write("Getting name: ", name)
            mutator:
                console.write("Setting name to: ", name)
                    
            construct(string n)
                name = n
                
            shared start()
                Person p
                p = new Person("Initial")
                p.name = "John"      // Should trigger mutator
                console.write(p.name) // Should trigger accessor
        """;
        var tranNode = run(program);
        var c = getConsole(tranNode);
        Assertions.assertEquals("Setting name to: John", c.get(0));
        Assertions.assertEquals("Getting name: John", c.get(1));
        Assertions.assertEquals("John", c.get(2));
    }

    @Test
    public void testComplexBooleanExpression() {
        String program = """
        class Logic
            shared start()
                boolean a
                boolean b
                boolean c
                a = true
                b = false
                c = true
                if (a and not b) or (c and not b)
                    console.write("Complex boolean expression works!")
        """;
        var tranNode = run(program);
        var c = getConsole(tranNode);
        Assertions.assertEquals("Complex boolean expression works!", c.getFirst());
    }

    @Test
    public void testMethodWithMultipleReturns() {
        String program = """
        class Calculator
            getDivisionResults(number x, number y) : number quotient, number remainder
                quotient = x / y
                remainder = x % y
                
            shared start()
                number q
                number r
                q, r = getDivisionResults(17, 5)
                console.write("Quotient:", q, "Remainder:", r)
        """;
        var tranNode = run(program);
        var c = getConsole(tranNode);
        Assertions.assertEquals("Quotient:3.0Remainder:2.0", c.getFirst());
    }

    @Test
    public void testPrivateSharedMethod() {
        String program = """
        class Utils
            private shared helper()
                console.write("Helper called")
                
            shared callHelper()
                helper()
                
            shared start()
                callHelper()
        """;
        var tranNode = run(program);
        var c = getConsole(tranNode);
        Assertions.assertEquals("Helper called", c.getFirst());
    }



    private static List<String> getConsole(TranNode tn) {
        for (var c : tn.Classes)
            if (c.name.equals("console")) {
                for (var m : c.methods)  {
                    if (m.name.equals("write")) {
                        return ((ConsoleWrite)m).console;
                    }
                }
            }
        throw new RuntimeException("Unable to find console");
    }

    private static TranNode run(String program) {
        var l  = new Lexer(program);
        try {
            var tokens = l.Lex();
            var tran = new TranNode();
            var p = new Parser(tran,tokens);
            p.Tran();
            System.out.println(tran.toString());
            var i = new Interpreter(tran);
            i.start();
            return tran;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
