package javax.microedition.lcdui;

public class Font {
    public static Font getFont(int face, int style, int size) {
        return new Font(size <= 8 ? 8 : size);
    }

    private final int height;

    private Font(int height) {
        this.height = height;
    }

    public int getHeight() {
        return height;
    }

    public int stringWidth(String text) {
        return text == null ? 0 : text.length() * Math.max(4, height / 2);
    }
}
