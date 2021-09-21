import org.crsh.text.*;
import org.crsh.text.ui.*;

import java.io.IOException;

import static org.crsh.text.ui.Element.label;

public class CrashTableTest {
    public static void main(String[] args) throws IOException {
        // header定义
        String[] fields = { "Duration (ms)", "No. of GCs" ,"Percentage"};

        // 设置两列的比例是1:1，如果不设置的话，列宽是自动按元素最长的处理。
        // 设置table的外部边框，默认是没有外边框
        // 还有内部的分隔线，默认内部没有分隔线
        TableElement tableElement = new TableElement(1, 1,1);

        // 设置单元格的左右边框间隔，默认是没有，看起来会有点挤，空间足够时，可以设置为1，看起来清爽
        tableElement.leftCellPadding(1).rightCellPadding(1);

        // 设置header
        tableElement.add(new RowElement().style(Decoration.bold.fg(Color.black).bg(Color.white)).add(fields));

        // 设置cell里的元素超出了处理方式，Overflow.HIDDEN 表示隐藏
        // Overflow.WRAP表示会向外面排出去，即当输出宽度有限时，右边的列可能会显示不出，被挤掉了
        tableElement.overflow(Overflow.HIDDEN);

        // 设置第一列输出字体蓝色，红色背景
        // 设置第二列字体加粗，加下划线
        for (int i = 0; i < 10; ++i) {
            tableElement.add(Element.row()
                    .add(label(i*100+" - " + (i+1)*100))
                    .add(label("" + i*15))
                            .add(label(""+99+"%"))
                    .style(Decoration.bold_off.fg(Color.white).bgnb    (Color.black)));
        }

        String body = RenderUtil.render(tableElement);

        System.out.println(body);

        LineRenderer renderer = tableElement.renderer();

        LineReader reader = renderer.reader(100);


        StringBuilder result = new StringBuilder(2048);


        while (reader.hasLine()){
            final ScreenBuffer buffer = new ScreenBuffer();
            reader.renderLine(new RenderAppendable(new ScreenContext() {
                @Override
                public int getWidth() {
                    return 20;
                }

                @Override
                public int getHeight() {
                    return 80;
                }

                public Screenable append(CharSequence s) throws IOException {
                    buffer.append(s);
                    return this;
                }

                public Appendable append(char c) throws IOException {
                    buffer.append(c);
                    return this;
                }

                public Appendable append(CharSequence csq, int start, int end) throws IOException {
                    buffer.append(csq, start, end);
                    return this;
                }

                public Screenable append(Style style) throws IOException {
                    buffer.append(style);
                    return this;
                }

                public Screenable cls() throws IOException {
                    buffer.cls();
                    return this;
                }

                public void flush() throws IOException {
                    buffer.flush();
                }
            }));
            StringBuilder sb = new StringBuilder();
            buffer.format(Format.ANSI,sb);
            result.append(sb);
            result.append("\n");
        }
        System.out.println(result);

        // 默认输出宽度是80
//        System.err.println(RenderUtil.render(tableElement));
    }


}
