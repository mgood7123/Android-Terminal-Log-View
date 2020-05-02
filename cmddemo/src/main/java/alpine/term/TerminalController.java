package alpine.term;

public class TerminalController {
    public static int createPseudoTerminal() {
        return JNI.createPseudoTerminal(false);
    }
}
