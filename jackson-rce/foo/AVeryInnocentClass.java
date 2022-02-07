import java.io.IOException;

package foo;

class AVeryInnocentClass {
    static {
        try {
            Runtime.getRuntime().exec("echo 'Holla' > dump.txt");
            System.out.println("Foobar!");
        } catch(IOException e) {
            // Shhh! Don't log anything... For stealth!
        }
    }
}