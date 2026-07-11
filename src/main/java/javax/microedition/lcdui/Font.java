package javax.microedition.lcdui;

import lombok.Getter;

import javax.annotation.processing.Generated;

@Generated("me")
@Getter
public final class Font {

  public static final int STYLE_PLAIN = 0;
  public static final int STYLE_BOLD = 1;
  public static final int STYLE_ITALIC = 2;
  public static final int STYLE_UNDERLINED = 4;

  public static final int SIZE_SMALL = 8;
  public static final int SIZE_MEDIUM = 0;
  public static final int SIZE_LARGE = 16;

  public static final int FACE_SYSTEM = 0;
  public static final int FACE_MONOSPACE = 32;
  public static final int FACE_PROPORTIONAL = 64;

  public static final int FONT_STATIC_TEXT = 0;
  public static final int FONT_INPUT_TEXT = 1;

  private static final Font DEFAULT = new Font(FACE_SYSTEM, STYLE_PLAIN, SIZE_MEDIUM);

  public static Font getFont(int fontSpecifier) {
    return DEFAULT;
  }

  public static Font getDefaultFont() {
    return DEFAULT;
  }

  public static Font getFont(int face, int style, int size) {
    return new Font(face, style, size);
  }

  private final int face;
  private final int height;
  private final int size;
  private final int style;

  private Font(int face, int style, int size) {
    this.face = face;
    this.size = size;
    this.style = style;
    this.height =
        switch (size) {
          case SIZE_SMALL -> 8;
          case SIZE_LARGE -> 16;
          default -> 12;
        };
  }

  public boolean isPlain() {
    return style == STYLE_PLAIN;
  }

  public boolean isBold() {
    return (style & STYLE_BOLD) != 0;
  }

  public boolean isItalic() {
    return (style & STYLE_ITALIC) != 0;
  }

  public boolean isUnderlined() {
    return (style & STYLE_UNDERLINED) != 0;
  }

  public int getBaselinePosition() {
    return Math.max(1, height - 2);
  }

  public int charWidth(char ch) {
    return Math.max(4, height / 2);
  }

  public int charsWidth(char[] chars, int offset, int length) {
    if (chars == null || length <= 0) {
      return 0;
    }
    return length * charWidth(' ');
  }

  public int stringWidth(String text) {
    return text == null ? 0 : text.length() * Math.max(4, height / 2);
  }

  public int substringWidth(String text, int offset, int length) {
    return text == null || length <= 0
        ? 0
        : Math.clamp(text.length() - offset, 0, length) * Math.max(4, height / 2);
  }
}
