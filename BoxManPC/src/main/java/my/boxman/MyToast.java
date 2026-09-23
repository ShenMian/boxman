package my.boxman;

import javax.swing.*;
import java.awt.*;

public class MyToast {
    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG = 1;

    public static void showToast(Object context, String message, int duration) {
        System.out.println("[Toast] " + message);
    }
}
