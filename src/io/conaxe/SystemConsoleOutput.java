package io.conaxe;

public class SystemConsoleOutput implements ConsoleOutput {
    @Override
    public void println(String text) {
       System.out.println(text);
    }

    @Override
    public void print(String text) {
       System.out.print(text);
    }
}
